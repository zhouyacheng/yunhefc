import { computed } from 'vue';

import { useSessionStore } from '../../stores/session';
import { hasSessionHint } from '../../stores/session-hint';
import { isPending } from '../auth/user-status';
import { contentVisible } from './access';

/** C14 页面访问态：登录引导、待审核/外挂不可见、正常可见。 */
export type ContentAccess = 'loading' | 'guest' | 'pending' | 'blocked' | 'ok';

export function useContentAccess() {
  const session = useSessionStore();

  const state = computed<ContentAccess>(() => {
    if (!session.loaded) {
      // 与 AppShell 同口径：没有本地会话提示时按访客处理，避免必然的 401 探测。
      return hasSessionHint() ? 'loading' : 'guest';
    }
    if (!session.isAuthenticated) {
      return 'guest';
    }
    if (isPending(session.user?.status ?? 1)) {
      return 'pending';
    }
    return contentVisible(session.user, session.permissions) ? 'ok' : 'blocked';
  });

  /** 有会话提示但尚未探测时补一次 /c/me；无提示或已加载时直接返回。 */
  async function ensureLoaded(): Promise<void> {
    if (!session.loaded && hasSessionHint()) {
      await session.ensureLoaded().catch(() => undefined);
    }
  }

  return { state, ensureLoaded };
}
