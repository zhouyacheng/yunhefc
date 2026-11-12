import { isUnauthorized } from '@yunhefc/api-client';
import { onMounted, onUnmounted, watch } from 'vue';
import { useRoute } from 'vue-router';

import { useSessionStore } from '../stores/session';

const POLL_INTERVAL_MS = 60_000;

/** 登录后每 60 秒刷新 NTF-02；页面重新可见与路由切换时补刷，访客不启动。 */
export function useNotificationPolling(): void {
  const session = useSessionStore();
  const route = useRoute();

  let timer: ReturnType<typeof setInterval> | null = null;
  let inFlight = false;

  async function refresh(): Promise<void> {
    if (!session.isAuthenticated || inFlight) {
      return;
    }
    inFlight = true;
    try {
      await session.refreshUnreadCount();
    } catch (error) {
      // 401 静默清会话；其他错误等下一周期重试，不影响页面。
      if (isUnauthorized(error)) {
        session.clearSession();
      }
    } finally {
      inFlight = false;
    }
  }

  function start(): void {
    if (timer !== null) {
      return;
    }
    void refresh();
    timer = setInterval(() => {
      void refresh();
    }, POLL_INTERVAL_MS);
  }

  function stop(): void {
    if (timer !== null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function onVisibilityChange(): void {
    if (document.visibilityState === 'visible') {
      void refresh();
    }
  }

  watch(
    () => session.isAuthenticated,
    (authenticated) => {
      if (authenticated) {
        start();
      } else {
        stop();
      }
    },
    { immediate: true },
  );

  watch(
    () => route.fullPath,
    () => {
      if (session.isAuthenticated) {
        void refresh();
      }
    },
  );

  onMounted(() => document.addEventListener('visibilitychange', onVisibilityChange));

  onUnmounted(() => {
    stop();
    document.removeEventListener('visibilitychange', onVisibilityChange);
  });
}
