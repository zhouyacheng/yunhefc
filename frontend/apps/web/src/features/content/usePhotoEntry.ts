import { describeError } from '@yunhefc/api-client';
import { computed, ref, type Ref } from 'vue';
import { useRouter } from 'vue-router';

import { useSessionStore } from '../../stores/session';
import { contentVisible } from './access';
import { openRelatedAlbum, type ContentOwnerEntry } from './entry';

/**
 * C03/C12 头部「照片」入口：可见性、防重与打开/建默认相册的编排。
 * owner 在读不到详情时为 null，按钮保持隐藏。
 */
export function usePhotoEntry(
  owner: Ref<ContentOwnerEntry | null>,
  setError: (message: string) => void,
) {
  const session = useSessionStore();
  const router = useRouter();
  const canViewContent = computed(() => contentVisible(session.user, session.permissions));
  const busy = ref(false);

  async function openPhotos(): Promise<void> {
    const value = owner.value;
    if (!value || busy.value) {
      return;
    }
    busy.value = true;
    setError('');
    try {
      await openRelatedAlbum(router, value);
    } catch (caught) {
      setError(describeError(caught, '相册打开失败，请稍后重试'));
    } finally {
      busy.value = false;
    }
  }

  return { canViewContent, busy, openPhotos };
}
