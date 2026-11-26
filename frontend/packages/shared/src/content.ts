/** M17 动态与相册（05 §3.18、§4）。 */
import { compressImage, scaledDimensions } from './image';

/** 照片单张 ≤5 MiB（P-147）。 */
export const CONTENT_PHOTO_MAX_BYTES = 5 * 1024 * 1024;
/** 客户端压缩长边 2048、JPEG q80（05 §2.7）。 */
export const CONTENT_PHOTO_MAX_EDGE = 2048;
export const CONTENT_PHOTO_JPEG_QUALITY = 0.8;
/** 单相册 ≤200 张。 */
export const CONTENT_ALBUM_MAX_PHOTOS = 200;
export const CONTENT_POST_MAX_LENGTH = 2000;
export const CONTENT_ALBUM_TITLE_MAX_LENGTH = 64;

export const CONTENT_PHOTO_TYPE_ERROR = '仅支持 JPG/PNG/WebP';
export const CONTENT_PHOTO_SIZE_ERROR = '图片不能超过 5MB';
export const CONTENT_ALBUM_FULL_ERROR = '相册已满，最多 200 张';
export const CONTENT_ALBUM_TITLE_REQUIRED = '请输入相册名称';

const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

/** CONTENT-10 举报目标：0-动态；1-照片。 */
export const CONTENT_REPORT_POST = 0 as const;
export const CONTENT_REPORT_PHOTO = 1 as const;
export type ContentReportTargetType = typeof CONTENT_REPORT_POST | typeof CONTENT_REPORT_PHOTO;

export interface AlbumSummary {
  id: number;
  title: string;
  matchId: number | null;
  activityId: number | null;
  /** 归属比赛/活动标题；归属对象已删除时为 null。 */
  ownerName: string | null;
  coverThumbnailUrl: string | null;
  photoCount: number;
  createdBy: number;
  createdAt: string;
}

export interface AlbumDetail extends AlbumSummary {
  /** 封面原图（不带缩略处理）。 */
  coverUrl: string | null;
}

export interface AlbumPhoto {
  id: number;
  thumbnailUrl: string;
  originalUrl: string;
  uploadedBy: number;
  uploaderName: string;
  createdAt: string;
}

export interface PostAuthor {
  id: number;
  name: string;
  avatarUrl: string | null;
}

/** CNT-09/10：列表与发布共用同一结构。 */
export interface ContentPost {
  id: number;
  author: PostAuthor;
  content: string;
  /** 引用的相册；相册已删除时为 null，正文保留。 */
  album: AlbumSummary | null;
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
}

/** 仅校验格式；客户端会先压缩再判断 5 MiB 上限。 */
export function contentPhotoTypeError(type: string): string | null {
  return ACCEPTED_TYPES.has(type) ? null : CONTENT_PHOTO_TYPE_ERROR;
}

/** 压缩后按上传字节数校验 5 MiB 上限。 */
export function contentPhotoSizeError(size: number): string | null {
  return size > CONTENT_PHOTO_MAX_BYTES ? CONTENT_PHOTO_SIZE_ERROR : null;
}

/** 返回本地校验错误文案；通过返回 null。 */
export function validateContentPhoto(file: { type: string; size: number }): string | null {
  return contentPhotoTypeError(file.type) ?? contentPhotoSizeError(file.size);
}

/** 等比缩到长边 2048px；小图保持原尺寸。 */
export function contentPhotoDimensions(
  width: number,
  height: number,
): { width: number; height: number } {
  return scaledDimensions(width, height, CONTENT_PHOTO_MAX_EDGE);
}

/** Canvas 重绘到长边 2048px 并输出 JPEG q80。 */
export async function compressContentPhoto(file: File): Promise<File> {
  return compressImage(file, CONTENT_PHOTO_MAX_EDGE, CONTENT_PHOTO_JPEG_QUALITY, 'photo.jpg');
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

/** 归属文案：比赛/活动标题，无归属为球队通用相册。 */
export function contentOwnerText(
  album: Pick<AlbumSummary, 'matchId' | 'activityId' | 'ownerName'>,
): string {
  if (album.matchId !== null || album.activityId !== null) {
    return album.ownerName ?? '归属已删除';
  }
  return '球队通用相册';
}
