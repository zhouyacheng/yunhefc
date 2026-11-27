// M17 动态与相册：权限门禁、文案校验与列表派生态（05 §3.18、02 MP14）。
// 常量与校验镜像 frontend/packages/shared/src/content.ts；小程序不依赖 workspace 包，保持同值。
// 服务端仍是权威校验，这里只服务小程序展示与入口可见性。
import type { AlbumPhoto, AlbumSummary, AuthUser, ContentPost, PostLikeResult } from '../types/api';
import { avatarInitial, avatarSrc } from './avatar';
import { formatDateTime } from './datetime';
import { CONTENT_VIEW_ANY, hasPermission, isPending } from './permissions';

/** 照片单张 ≤5 MiB（P-147）。 */
export const CONTENT_PHOTO_MAX_BYTES = 5 * 1024 * 1024;
/** 客户端压缩长边 2048、JPEG q80（05 §2.7）。 */
export const CONTENT_PHOTO_MAX_EDGE = 2048;
export const CONTENT_PHOTO_JPEG_QUALITY = 0.8;
/** 单相册 ≤200 张。 */
export const CONTENT_ALBUM_MAX_PHOTOS = 200;
export const CONTENT_POST_MAX_LENGTH = 2000;
export const CONTENT_ALBUM_TITLE_MAX_LENGTH = 64;

export const CONTENT_ALBUM_FULL_ERROR = '相册已满，最多 200 张';
export const CONTENT_ALBUM_TITLE_REQUIRED = '请输入相册名称';

/** CONTENT-10 举报目标：0-动态；1-照片。 */
export const CONTENT_REPORT_POST = 0 as const;
export const CONTENT_REPORT_PHOTO = 1 as const;
export type ContentReportTargetType = typeof CONTENT_REPORT_POST | typeof CONTENT_REPORT_PHOTO;

/** C14/MP14 访问态：未登录、待审核、无 content:view:any（外挂）与正常可见。 */
export type ContentAccessState = 'guest' | 'pending' | 'blocked' | 'ok';

export function contentAccessState(
  user: AuthUser | null | undefined,
  permissions: readonly string[] | null | undefined,
): ContentAccessState {
  if (!user) {
    return 'guest';
  }
  if (isPending(user.status)) {
    return 'pending';
  }
  return hasPermission(permissions, CONTENT_VIEW_ANY) ? 'ok' : 'blocked';
}

/** 入口可见性：仅登录的正式球员与管理员。 */
export function contentVisible(
  user: AuthUser | null | undefined,
  permissions: readonly string[] | null | undefined,
): boolean {
  return contentAccessState(user, permissions) === 'ok';
}

/** 归属文案：比赛/活动标题，无归属为球队通用相册。 */
export function contentOwnerText(
  album: Pick<AlbumSummary, 'matchId' | 'activityId' | 'ownerName'>,
): string {
  if (album.matchId !== null || album.activityId !== null) {
    return album.ownerName ?? '归属已删除';
  }
  return '球队通用相册';
}

/** 正文去首尾空白后 1–2000 字（内部换行保留）。 */
export function contentPostTextError(text: string): string | null {
  const trimmed = text.trim();
  if (!trimmed) {
    return '请输入动态内容';
  }
  if (trimmed.length > CONTENT_POST_MAX_LENGTH) {
    return `动态内容不能超过 ${CONTENT_POST_MAX_LENGTH} 字`;
  }
  return null;
}

/** 相册名去首尾空白后 1–64 字。 */
export function contentAlbumTitleError(title: string): string | null {
  const trimmed = title.trim();
  if (!trimmed) {
    return CONTENT_ALBUM_TITLE_REQUIRED;
  }
  if (trimmed.length > CONTENT_ALBUM_TITLE_MAX_LENGTH) {
    return `相册名称不能超过 ${CONTENT_ALBUM_TITLE_MAX_LENGTH} 字`;
  }
  return null;
}

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

/** 查询参数中的正整数 ID；非法时返回 null。 */
export function contentIdFromQuery(value: string | undefined | null): number | null {
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
}

export interface AlbumCardView {
  id: number;
  title: string;
  ownerText: string;
  photoCountText: string;
  timeText: string;
  coverUrl: string;
}

export function buildAlbumCardView(album: AlbumSummary): AlbumCardView {
  return {
    id: album.id,
    title: album.title,
    ownerText: contentOwnerText(album),
    photoCountText: `${album.photoCount} 张`,
    timeText: formatDateTime(album.createdAt),
    // 与头像同口径：只接受现签 http(s) URL，对象键或空值回落占位。
    coverUrl: avatarSrc(album.coverThumbnailUrl) ?? '',
  };
}

export interface ContentPostRow {
  id: number;
  authorName: string;
  authorInitial: string;
  avatarUrl: string;
  timeText: string;
  content: string;
  album: AlbumCardView | null;
  likeCount: number;
  likedByMe: boolean;
  canDelete: boolean;
}

export interface ContentViewer {
  userId: number | null;
  canDeleteAny: boolean;
}

/** CNT-09 行映射：头像回落姓名首字，删除入口按本人/管理员判定。 */
export function buildContentPostRow(post: ContentPost, viewer: ContentViewer): ContentPostRow {
  return {
    id: post.id,
    authorName: post.author.name,
    authorInitial: avatarInitial(post.author.name),
    avatarUrl: avatarSrc(post.author.avatarUrl) ?? '',
    timeText: formatDateTime(post.createdAt),
    content: post.content,
    album: post.album ? buildAlbumCardView(post.album) : null,
    likeCount: post.likeCount,
    likedByMe: post.likedByMe,
    canDelete: canDeleteOwned(post.author.id, viewer.userId, viewer.canDeleteAny),
  };
}

/** CNT-12 响应即目标状态，点赞/取消都直接覆盖本地行。 */
export function applyPostLike(row: ContentPostRow, result: PostLikeResult): ContentPostRow {
  return { ...row, likedByMe: result.liked, likeCount: result.likeCount };
}

export interface ContentPhotoRow extends AlbumPhoto {
  timeText: string;
  canDelete: boolean;
}

/** CNT-03 行映射：上传时间与删除入口按本人/管理员判定。 */
export function buildContentPhotoRow(photo: AlbumPhoto, viewer: ContentViewer): ContentPhotoRow {
  return {
    ...photo,
    timeText: formatDateTime(photo.createdAt),
    canDelete: canDeleteOwned(photo.uploadedBy, viewer.userId, viewer.canDeleteAny),
  };
}
