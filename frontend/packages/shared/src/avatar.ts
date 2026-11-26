/** FIL-01/FIL-02 头像上限：JPG/PNG/WebP，≤5MiB（02 §5.6）。 */
import { compressImage, scaledDimensions } from './image';

export const AVATAR_MAX_BYTES = 5 * 1024 * 1024;
export const AVATAR_MAX_EDGE = 1080;
export const AVATAR_JPEG_QUALITY = 0.85;
export const AVATAR_TYPE_ERROR = '仅支持 JPG/PNG/WebP';
export const AVATAR_SIZE_ERROR = '图片不能超过 5MB';
export const AVATAR_UPLOAD_ERROR = '上传失败，请稍后重试';

const ACCEPTED_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

/** 返回本地校验错误文案；通过返回 null。 */
export function validateAvatarFile(file: { type: string; size: number }): string | null {
  if (!ACCEPTED_TYPES.has(file.type)) {
    return AVATAR_TYPE_ERROR;
  }
  if (file.size > AVATAR_MAX_BYTES) {
    return AVATAR_SIZE_ERROR;
  }
  return null;
}

/** 等比缩到长边 1080px；小图保持原尺寸。 */
export function avatarDimensions(width: number, height: number): { width: number; height: number } {
  return scaledDimensions(width, height, AVATAR_MAX_EDGE);
}

/** 头像为空、加载失败或非 http(s) 时显示姓名首字。 */
export function avatarPlaceholder(name: string | null | undefined): string {
  const trimmed = (name ?? '').trim();
  return trimmed ? Array.from(trimmed)[0]! : '?';
}

export function isDisplayableAvatarUrl(url: string | null | undefined): url is string {
  return typeof url === 'string' && /^https?:\/\//i.test(url);
}

/**
 * Canvas 重绘到达 1080px 长边并输出 JPEG 0.85，顺带去除 EXIF。
 */
export async function compressAvatar(file: File): Promise<File> {
  return compressImage(file, AVATAR_MAX_EDGE, AVATAR_JPEG_QUALITY, 'avatar.jpg');
}
