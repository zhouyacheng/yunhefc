/** 浏览器端图片压缩：等比缩到长边 maxEdge，经 Canvas 重绘后输出 JPEG（同时去除 EXIF）。 */

/** 等比缩到长边 maxEdge；小图保持原尺寸，缩放后最小边不小于 1px。 */
export function scaledDimensions(
  width: number,
  height: number,
  maxEdge: number,
): { width: number; height: number } {
  const longest = Math.max(width, height);
  if (width <= 0 || height <= 0 || longest <= maxEdge) {
    return { width, height };
  }
  const scale = maxEdge / longest;
  return {
    width: Math.max(1, Math.round(width * scale)),
    height: Math.max(1, Math.round(height * scale)),
  };
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

export async function compressImage(
  file: File,
  maxEdge: number,
  quality: number,
  filename: string,
): Promise<File> {
  const source = await loadSource(file);
  const sourceWidth = source instanceof HTMLImageElement ? source.naturalWidth : source.width;
  const sourceHeight = source instanceof HTMLImageElement ? source.naturalHeight : source.height;
  const { width, height } = scaledDimensions(sourceWidth, sourceHeight, maxEdge);

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
      quality,
    );
  });
  return new File([blob], filename, { type: 'image/jpeg' });
}
