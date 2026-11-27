import { computed, ref } from 'vue';

import { useSessionStore } from '../../stores/session';
import { hasSessionHint } from '../../stores/session-hint';
import { isPending } from '../auth/user-status';
import { contentVisible } from './access';

/** C14 页面访问态：加载中、加载失败、登录引导、待审核/外挂不可见、正常可见。 */
export type ContentAccess = 'loading' | 'error' | 'guest' | 'pending' | 'blocked' | 'ok';

export function useContentAccess() {
  const session = useSessionStore();
  const probeFailed = ref(false);

  const state = computed<ContentAccess>(() => {
    if (!session.loaded) {
      // 与 AppShell 同口径：没有本地会话提示时按访客处理，避免必然的 401 探测。
      if (!hasSessionHint()) {
        return 'guest';
      }
      return probeFailed.value ? 'error' : 'loading';
    }
    if (!session.isAuthenticated) {
      return 'guest';
    }
    if (isPending(session.user?.status ?? 1)) {
      return 'pending';
    }
    return contentVisible(session.user, session.permissions) ? 'ok' : 'blocked';
  });

  /** 有会话提示但尚未探测时补一次 /c/me；失败进入可重试的错误态。 */
  async function ensureLoaded(): Promise<void> {
    if (session.loaded || !hasSessionHint()) {
      return;
    }
    probeFailed.value = false;
    try {
      await session.ensureLoaded();
    } catch {
      probeFailed.value = true;
    }
  }

  return { state, ensureLoaded };
}
