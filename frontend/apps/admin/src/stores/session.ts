import { isObjectForbidden, isSectionForbidden, isUnauthorized } from '@yunhefc/api-client';
import { ADMIN_SECTIONS, isAdminSection, type AdminSection } from '@yunhefc/permissions';
import type { BLoginResponse, BMeView, User } from '@yunhefc/shared';
import { defineStore } from 'pinia';
import { computed, ref } from 'vue';

import { api } from '../api/client';

export const useSessionStore = defineStore('admin-session', () => {
  const user = ref<User | null>(null);
  const permissions = ref<string[]>([]);
  const sections = ref<AdminSection[]>([]);
  const loaded = ref(false);
  const isAuthenticated = computed(() => user.value !== null);

  const homePath = computed(() => {
    if (sections.value.includes('matches')) {
      return '/matches';
    }
    const first = ADMIN_SECTIONS.find((section) => sections.value.includes(section));
    return first ? `/${first}` : '/forbidden';
  });

  let pending: Promise<void> | null = null;

  function apply(payload: BMeView): void {
    user.value = payload.user;
    permissions.value = [...payload.permissions];
    sections.value = payload.sections.filter(isAdminSection);
  }

  function clearSession(): void {
    user.value = null;
    permissions.value = [];
    sections.value = [];
    loaded.value = true;
  }

  async function ensureLoaded(): Promise<void> {
    if (loaded.value) {
      return;
    }
    if (pending) {
      return pending;
    }
    pending = (async () => {
      try {
        const me = await api.get<BMeView>('/me', { redirectOnUnauthorized: false });
        apply(me);
        loaded.value = true;
      } catch (error) {
        if (isUnauthorized(error) || isSectionForbidden(error) || isObjectForbidden(error)) {
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
    const response = await api.post<BLoginResponse>('/auth/login', { phone, password });
    api.setCsrfToken(response.csrfToken);
    const me = await api.get<BMeView>('/me', { redirectOnUnauthorized: false });
    apply(me);
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
    permissions,
    sections,
    loaded,
    isAuthenticated,
    homePath,
    ensureLoaded,
    login,
    logout,
    clearSession,
  };
});
