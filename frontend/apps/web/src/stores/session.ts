import { isUnauthorized } from '@yunhefc/api-client';
import type { AuthResponse, MeView, NotificationUnreadCount, Player, User } from '@yunhefc/shared';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { api } from '../api/client';
import { clearSessionHint, markSessionHint } from './session-hint';

export const useSessionStore = defineStore('session', () => {
  const user = ref<User | null>(null);
  const player = ref<Player | null>(null);
  const permissions = ref<string[]>([]);
  const unreadCount = ref(0);
  const loaded = ref(false);
  const isAuthenticated = computed(() => user.value !== null);

  let pending: Promise<void> | null = null;
  // 会话代次：登出/清会话或重新登录后，丢弃旧请求的迟到结果。
  let sessionVersion = 0;

  function apply(payload: {
    user: User;
    player?: Player | null;
    permissions: string[];
    unreadCount?: number;
  }): void {
    user.value = payload.user;
    player.value = payload.player ?? null;
    permissions.value = [...payload.permissions];
    unreadCount.value = payload.unreadCount ?? 0;
  }

  function clearSession(): void {
    sessionVersion += 1;
    user.value = null;
    player.value = null;
    permissions.value = [];
    unreadCount.value = 0;
    loaded.value = true;
    clearSessionHint();
  }

  async function probe(): Promise<void> {
    const version = sessionVersion;
    try {
      const me = await api.get<MeView>('/me', { redirectOnUnauthorized: false });
      if (version !== sessionVersion) {
        return;
      }
      apply(me);
      loaded.value = true;
    } catch (error) {
      if (isUnauthorized(error)) {
        if (version === sessionVersion) {
          clearSession();
        }
        return;
      }
      throw error;
    }
  }

  /** 守卫内静默探测会话；401 不触发整页跳转。 */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) {
      return;
    }
    if (pending) {
      return pending;
    }
    pending = probe().finally(() => {
      pending = null;
    });
    return pending;
  }

  /** 即使已加载也重取 /me，用于注册补权限、邀请接受后刷新。 */
  async function refresh(): Promise<void> {
    await probe();
  }

  /** NTF-02 角标轮询源；401 由调用方静默处理。 */
  async function refreshUnreadCount(): Promise<void> {
    const version = sessionVersion;
    const result = await api.get<NotificationUnreadCount>('/notifications/unread-count', {
      redirectOnUnauthorized: false,
    });
    if (version !== sessionVersion || !isAuthenticated.value) {
      return;
    }
    unreadCount.value = result.count;
  }

  async function login(phone: string, password: string): Promise<void> {
    const response = await api.post<AuthResponse>('/auth/login', { phone, password });
    sessionVersion += 1;
    api.setCsrfToken(response.csrfToken);
    apply(response);
    loaded.value = true;
    markSessionHint();
  }

  async function logout(): Promise<void> {
    try {
      await api.post('/auth/logout');
    } catch {
      // 会话已失效时仍清理本地状态。
    }
    api.clearCsrfToken();
    clearSession();
  }

  return {
    user,
    player,
    permissions,
    unreadCount,
    loaded,
    isAuthenticated,
    ensureLoaded,
    refresh,
    refreshUnreadCount,
    login,
    logout,
    clearSession,
  };
});
