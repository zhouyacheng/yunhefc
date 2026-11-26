import type { AlbumPhoto } from '@yunhefc/shared';
import { CONTENT_ALBUM_TITLE_MAX_LENGTH } from '@yunhefc/shared';

/** CNT-01/09 列表分页白名单。 */
export const CONTENT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
/** CNT-03 照片分页白名单与相册/动态不同。 */
export const CONTENT_PHOTO_PAGE_SIZE_OPTIONS = [30, 60, 100] as const;

/** 从比赛/活动详情进入且无相册时，用归属标题生成默认相册名。 */
export function defaultAlbumTitle(ownerTitle: string): string {
  const trimmed = ownerTitle.trim();
  if (!trimmed) {
    return '球队相册';
  }
  return Array.from(`${trimmed} 照片`).slice(0, CONTENT_ALBUM_TITLE_MAX_LENGTH).join('');
}

/** 照片本人或管理员（content:delete:any）可删除。 */
export function canDeletePhoto(
  photo: Pick<AlbumPhoto, 'uploadedBy'>,
  userId: number | null | undefined,
  canDeleteAny: boolean,
): boolean {
  return canDeleteAny || (typeof userId === 'number' && photo.uploadedBy === userId);
}
