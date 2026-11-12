/** FIL-01/FIL-02 头像上限：JPG/PNG/WebP，≤2MiB（02 §5.6）。 */
export const AVATAR_MAX_BYTES = 2 * 1024 * 1024;
export const AVATAR_MAX_EDGE = 1080;
export const AVATAR_JPEG_QUALITY = 0.85;
export const AVATAR_TYPE_ERROR = '仅支持 JPG/PNG/WebP';
export const AVATAR_SIZE_ERROR = '图片不能超过 2MB，请重新选择';
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
  const longest = Math.max(width, height);
  if (width <= 0 || height <= 0 || longest <= AVATAR_MAX_EDGE) {
    return { width, height };
  }
  const scale = AVATAR_MAX_EDGE / longest;
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
}

/** 头像为空、加载失败或非 http(s) 时显示姓名首字。 */
export function avatarPlaceholder(name: string | null | undefined): string {
  const trimmed = (name ?? '').trim();
  return trimmed ? Array.from(trimmed)[0]! : '?';
}

export function isDisplayableAvatarUrl(url: string | null | undefined): url is string {
  return typeof url === 'string' && /^https?:\/\//i.test(url);
}

function loadSource(file: File): Promise<ImageBitmap | HTMLImageElement> {
  if (typeof createImageBitmap === 'function') {
    return createImageBitmap(file);
  }
  return new Promise((resolve, reject) => {
    const url = URL.createObjectURL(file);
    const image = new Image();
    image.onload = () => {
      URL.revokeObjectURL(url);
      resolve(image);
    };
    image.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error('图片读取失败'));
    };
    image.src = url;
  });
}

/**
 * Canvas 重绘到达 1080px 长边并输出 JPEG 0.85，顺带去除 EXIF。
 */
export async function compressAvatar(file: File): Promise<File> {
  const source = await loadSource(file);
  const sourceWidth = source instanceof HTMLImageElement ? source.naturalWidth : source.width;
  const sourceHeight = source instanceof HTMLImageElement ? source.naturalHeight : source.height;
  const { width, height } = avatarDimensions(sourceWidth, sourceHeight);

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const context = canvas.getContext('2d');
  if (!context) {
    throw new Error('当前浏览器不支持图片处理');
  }
  context.drawImage(source, 0, 0, width, height);
  if (!(source instanceof HTMLImageElement)) {
    source.close();
  }

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(
      (result) => {
        if (result) {
          resolve(result);
        } else {
          reject(new Error('图片处理失败'));
        }
      },
      'image/jpeg',
      AVATAR_JPEG_QUALITY,
    );
  });
  return new File([blob], 'avatar.jpg', { type: 'image/jpeg' });
}
