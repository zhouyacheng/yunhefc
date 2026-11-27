// MP04/MP11 头部「照片」入口：有相册进入按比赛/活动过滤的相册列表，
// 无相册时自动创建默认相册并进入相册详情（02 MP04/MP11、MP14）。
import { createAlbum, listAlbums } from './api-client';
import { defaultAlbumTitle } from './content';

export type ContentOwnerEntry =
  { matchId: number; title: string } | { activityId: number; title: string };

function navigate(url: string): Promise<void> {
  return new Promise<void>((resolve, reject) => {
    wx.navigateTo({
      url,
      success: () => resolve(),
      fail: (error) => reject(new Error(error.errMsg || '页面打开失败')),
    });
  });
}

export async function openRelatedAlbum(owner: ContentOwnerEntry): Promise<void> {
  const scope = 'matchId' in owner ? { matchId: owner.matchId } : { activityId: owner.activityId };
  const existing = await listAlbums({ ...scope, page: 1, pageSize: 10 });
  if (existing.total > 0) {
    const query =
      'matchId' in owner ? `matchId=${owner.matchId}` : `activityId=${owner.activityId}`;
    await navigate(`/pages/posts/index?tab=albums&${query}`);
    return;
  }
  const album = await createAlbum({ title: defaultAlbumTitle(owner.title), ...scope });
  await navigate(`/pages/album-detail/index?id=${album.id}`);
}
