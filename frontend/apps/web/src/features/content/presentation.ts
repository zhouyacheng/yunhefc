import type { AlbumPhoto } from '@yunhefc/shared';
import { CONTENT_ALBUM_TITLE_MAX_LENGTH } from '@yunhefc/shared';

/** CNT-01/09 列表分页白名单。 */
export const CONTENT_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;
/** CNT-03 照片分页白名单与相册/动态不同。 */
export const CONTENT_PHOTO_PAGE_SIZE_OPTIONS = [30, 60, 100] as const;
/** 通知落点定位：最近 5 页、每页 100（与小程序 content-feed 同口径）。 */
export const CONTENT_LOCATE_MAX_PAGES = 5;
export const CONTENT_LOCATE_PAGE_SIZE = 100;

/** 从比赛/活动详情进入且无相册时，用归属标题生成默认相册名。 */
export function defaultAlbumTitle(ownerTitle: string): string {
  const trimmed = ownerTitle.trim();
  if (!trimmed) {
    return '球队相册';
  }
  return Array.from(`${trimmed} 照片`).slice(0, CONTENT_ALBUM_TITLE_MAX_LENGTH).join('');
}

/** 动态作者/照片上传人本人或管理员（content:delete:any）可删除。 */
export function canDeleteOwned(
  ownerUserId: number,
  userId: number | null | undefined,
  canDeleteAny: boolean,
): boolean {
  return canDeleteAny || (typeof userId === 'number' && ownerUserId === userId);
}

/** 照片删除规则（CNT-08）。 */
export function canDeletePhoto(
  photo: Pick<AlbumPhoto, 'uploadedBy'>,
  userId: number | null | undefined,
  canDeleteAny: boolean,
): boolean {
  return canDeleteOwned(photo.uploadedBy, userId, canDeleteAny);
}

/** 路由/查询参数中的正整数 ID；非法时返回 null。 */
export function toPositiveId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  const parsed = Number(raw);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}
