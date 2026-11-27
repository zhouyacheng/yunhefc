// MP14 照片：客户端压缩（长边 2048 / JPEG q80）与本地校验。
// 校验文案镜像 frontend/packages/shared/src/content.ts；压缩实现改为小程序离屏 Canvas。
// 压缩失败时回退原图，由服务端按文件头与 5 MiB 上限兜底。
import {
  CONTENT_PHOTO_JPEG_QUALITY,
  CONTENT_PHOTO_MAX_BYTES,
  CONTENT_PHOTO_MAX_EDGE,
} from './content';

export const CONTENT_PHOTO_TYPE_ERROR = '仅支持 JPG/PNG/WebP';
export const CONTENT_PHOTO_SIZE_ERROR = '图片不能超过 5MB';

const ACCEPTED_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp']);

export interface ContentPhotoInput {
  tempFilePath: string;
  /** chooseMedia 返回的原图大小；读取压缩结果失败时兜底。 */
  size: number;
  width: number;
  height: number;
}

export interface PreparedContentPhoto {
  path: string;
  size: number;
}

/** 按长边等比缩放；小图保持原尺寸，非法尺寸返回 0×0 交由调用方回退。 */
export function contentPhotoTargetSize(
  width: number,
  height: number,
  maxEdge: number = CONTENT_PHOTO_MAX_EDGE,
): { width: number; height: number } {
  if (!(width > 0) || !(height > 0) || !(maxEdge > 0)) {
    return { width: 0, height: 0 };
  }
  const longest = Math.max(width, height);
  if (longest <= maxEdge) {
    return { width: Math.round(width), height: Math.round(height) };
  }
  const ratio = maxEdge / longest;
  return {
    width: Math.max(1, Math.round(width * ratio)),
    height: Math.max(1, Math.round(height * ratio)),
  };
}

/** 临时路径扩展名不在白名单时提示；无扩展名交给服务端按文件头判断。 */
export function contentPhotoTypeError(path: string): string | null {
  const matched = /\.([a-z0-9]+)$/i.exec(path);
  if (!matched) {
    return null;
  }
  return ACCEPTED_EXTENSIONS.has(matched[1].toLowerCase()) ? null : CONTENT_PHOTO_TYPE_ERROR;
}

/** 压缩后按上传字节数校验 5 MiB 上限。 */
export function contentPhotoSizeError(size: number): string | null {
  return size > CONTENT_PHOTO_MAX_BYTES ? CONTENT_PHOTO_SIZE_ERROR : null;
}

function readFileSize(path: string): Promise<number> {
  return new Promise<number>((resolve, reject) => {
    wx.getFileSystemManager().getFileInfo({
      filePath: path,
      success: (result) => resolve(result.size),
      fail: (error) => reject(new Error(error.errMsg || '读取图片失败')),
    });
  });
}

/** 离屏 Canvas 等比重绘并导出 JPEG q80。 */
function compressToJpeg(src: string, target: { width: number; height: number }): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const canvas = wx.createOffscreenCanvas({
      type: '2d',
      width: target.width,
      height: target.height,
    });
    const image = canvas.createImage();
    image.onload = () => {
      try {
        const context = canvas.getContext(
          '2d',
        ) as WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D | null;
        if (!context) {
          reject(new Error('图片压缩失败'));
          return;
        }
        // JPEG 无透明通道，先铺白底避免 PNG 透明区域变黑。
        context.fillStyle = '#ffffff';
        context.fillRect(0, 0, target.width, target.height);
        context.drawImage(image, 0, 0, target.width, target.height);
        wx.canvasToTempFilePath({
          canvas,
          x: 0,
          y: 0,
          width: target.width,
          height: target.height,
          destWidth: target.width,
          destHeight: target.height,
          fileType: 'jpg',
          quality: CONTENT_PHOTO_JPEG_QUALITY,
          success: (result) => resolve(result.tempFilePath),
          fail: (error) => reject(new Error(error.errMsg || '图片压缩失败')),
        });
      } catch {
        reject(new Error('图片压缩失败'));
      }
    };
    image.onerror = () => reject(new Error('图片解析失败'));
    image.src = src;
  });
}

/** 压缩并返回可上传的本地路径与字节数；压缩失败回退原图。 */
export async function prepareContentPhoto(file: ContentPhotoInput): Promise<PreparedContentPhoto> {
  const typeError = contentPhotoTypeError(file.tempFilePath);
  if (typeError) {
    throw new Error(typeError);
  }
  const original: PreparedContentPhoto = { path: file.tempFilePath, size: file.size };
  const target = contentPhotoTargetSize(file.width, file.height);
  if (target.width < 1 || target.height < 1) {
    return original;
  }
  try {
    const path = await compressToJpeg(file.tempFilePath, target);
    return { path, size: await readFileSize(path) };
  } catch {
    return original;
  }
}
