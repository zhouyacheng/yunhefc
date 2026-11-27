import type { Router } from 'vue-router';

import { createAlbum, listAlbums } from './api';
import { defaultAlbumTitle } from './presentation';

export type ContentOwnerEntry =
  { matchId: number; title: string } | { activityId: number; title: string };

/**
 * C03/C12 头部「照片」入口：有相册进入按归属过滤的相册列表，
 * 无相册时自动创建默认相册并进入相册详情（02 §5.3、§5.13）。
 */
export async function openRelatedAlbum(router: Router, owner: ContentOwnerEntry): Promise<void> {
  const ownerQuery =
    'matchId' in owner ? { matchId: owner.matchId } : { activityId: owner.activityId };
  const existing = await listAlbums({ ...ownerQuery, page: 1, pageSize: 10 });
  if (existing.total > 0) {
    await router.push({ path: '/posts', query: { tab: 'albums', ...ownerQuery } });
    return;
  }
  const album = await createAlbum({ title: defaultAlbumTitle(owner.title), ...ownerQuery });
  await router.push(`/posts/albums/${album.id}`);
}
