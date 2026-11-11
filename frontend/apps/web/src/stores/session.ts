import { isUnauthorized } from '@yunhefc/api-client';
import type { AuthResponse, MeView, Player, User } from '@yunhefc/shared';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { api } from '../api/client';

export const useSessionStore = defineStore('session', () => {
  const user = ref<User | null>(null);
  const player = ref<Player | null>(null);
  const permissions = ref<string[]>([]);
  const loaded = ref(false);
  const isAuthenticated = computed(() => user.value !== null);

  let pending: Promise<void> | null = null;

  function apply(payload: { user: User; player?: Player | null; permissions: string[] }): void {
    user.value = payload.user;
    player.value = payload.player ?? null;
    permissions.value = [...payload.permissions];
  }

  function clearSession(): void {
    user.value = null;
    player.value = null;
    permissions.value = [];
    loaded.value = true;
  }

  /** 守卫内静默探测会话；401 不触发整页跳转。 */
  async function ensureLoaded(): Promise<void> {
    if (loaded.value) {
      return;
    }
    if (pending) {
      return pending;
    }
    pending = (async () => {
      try {
        const me = await api.get<MeView>('/me', { redirectOnUnauthorized: false });
        apply(me);
        loaded.value = true;
      } catch (error) {
        if (isUnauthorized(error)) {
          clearSession();
          return;
        }
        throw error;
      } finally {
        pending = null;
      }
    })();
    return pending;
  }

  async function login(phone: string, password: string): Promise<void> {
    const response = await api.post<AuthResponse>('/auth/login', { phone, password });
    api.setCsrfToken(response.csrfToken);
    apply(response);
    loaded.value = true;
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
    loaded,
    isAuthenticated,
    ensureLoaded,
    login,
    logout,
    clearSession,
  };
});
