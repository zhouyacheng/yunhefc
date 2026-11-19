import { isUnauthorized } from '@yunhefc/api-client';
import type {
  AuthResponse,
  MeView,
  NotificationUnreadCount,
  PhoneBindPayload,
  Player,
  User,
  WechatRegisterPayload,
} from '@yunhefc/shared';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { api } from '../api/client';
import { clearSessionHint, markSessionHint } from './session-hint';

export interface RegisterPayload {
  phone: string;
  code: string;
  password: string;
  name: string;
  position: number;
  inviteToken?: string;
}

/** AUTH-03 注册响应不含 permissions，需由 refresh() 补取。 */
interface RegisterResponse {
  user: User;
  player?: Player | null;
  csrfToken: string;
}

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

  /** 注册成功后建立会话并补取 AUTH-10 的权限与未读数。 */
  async function completeRegistration(response: RegisterResponse): Promise<void> {
    sessionVersion += 1;
    api.setCsrfToken(response.csrfToken);
    apply({
      user: response.user,
      player: response.player ?? null,
      permissions: [],
      unreadCount: 0,
    });
    markSessionHint();
    try {
      await refresh();
    } catch {
      // 注册已成功；保持 loaded=false，后续守卫的 ensureLoaded() 会重试补权限。
      loaded.value = false;
    }
  }

  /** AUTH-03：手机号注册。 */
  async function register(payload: RegisterPayload): Promise<void> {
    await completeRegistration(await api.post<RegisterResponse>('/auth/register', payload));
  }

  /** AUTH-09：微信注册或绑定已有手机号账号。 */
  async function registerViaWechat(payload: WechatRegisterPayload): Promise<void> {
    await completeRegistration(
      await api.post<RegisterResponse>('/auth/wechat/bind-phone', payload),
    );
  }

  /** AUTH-20：补绑手机号；服务端返回后刷新 AUTH-10 的用户视图。 */
  async function bindPhone(payload: PhoneBindPayload): Promise<void> {
    await api.post('/me/phone', payload);
    await refresh();
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
    register,
    registerViaWechat,
    bindPhone,
    logout,
    clearSession,
  };
});
