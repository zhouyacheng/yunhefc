import { afterEach, describe, expect, it, vi } from 'vitest';

import { CONTENT_PHOTO_MAX_BYTES, CONTENT_PHOTO_MAX_EDGE } from '../miniprogram/utils/content';
import {
  CONTENT_PHOTO_SIZE_ERROR,
  CONTENT_PHOTO_TYPE_ERROR,
  contentPhotoSizeError,
  contentPhotoTargetSize,
  contentPhotoTypeError,
  prepareContentPhoto,
} from '../miniprogram/utils/content-photo';

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('content photo target size', () => {
  it('scales the long edge to 2048 and keeps small images', () => {
    expect(contentPhotoTargetSize(4000, 3000)).toEqual({ width: 2048, height: 1536 });
    expect(contentPhotoTargetSize(3000, 4000)).toEqual({ width: 1536, height: 2048 });
    expect(contentPhotoTargetSize(1024, 800)).toEqual({ width: 1024, height: 800 });
    expect(contentPhotoTargetSize(2048, 2048)).toEqual({ width: 2048, height: 2048 });
    expect(contentPhotoTargetSize(0, 800)).toEqual({ width: 0, height: 0 });
    expect(CONTENT_PHOTO_MAX_EDGE).toBe(2048);
  });

  it('validates type and compressed size', () => {
    expect(contentPhotoTypeError('/tmp/a.jpg')).toBeNull();
    expect(contentPhotoTypeError('/tmp/a.PNG')).toBeNull();
    expect(contentPhotoTypeError('/tmp/a.webp')).toBeNull();
    expect(contentPhotoTypeError('/tmp/a.gif')).toBe(CONTENT_PHOTO_TYPE_ERROR);
    expect(contentPhotoTypeError('/tmp/a')).toBeNull();
    expect(contentPhotoSizeError(CONTENT_PHOTO_MAX_BYTES)).toBeNull();
    expect(contentPhotoSizeError(CONTENT_PHOTO_MAX_BYTES + 1)).toBe(CONTENT_PHOTO_SIZE_ERROR);
  });
});

interface CanvasStubOptions {
  exportFailure?: string;
  compressedSize?: number;
}

function stubPhotoCanvas(options: CanvasStubOptions = {}) {
  const calls = {
    canvasSize: null as { width: number; height: number } | null,
    export: null as Record<string, unknown> | null,
    fills: [] as string[],
  };
  vi.stubGlobal('wx', {
    createOffscreenCanvas: (input: { width: number; height: number }) => {
      calls.canvasSize = { width: input.width, height: input.height };
      const context = {
        fillStyle: '',
        fillRect: () => {
          calls.fills.push('#ffffff');
        },
        drawImage: () => undefined,
      };
      const image: {
        onload?: () => void;
        onerror?: () => void;
        src?: string;
      } = {};
      Object.defineProperty(image, 'src', {
        set: () => setTimeout(() => image.onload?.(), 0),
      });
      return {
        width: input.width,
        height: input.height,
        getContext: () => context,
        createImage: () => image,
      };
    },
    canvasToTempFilePath: (input: Record<string, unknown>) => {
      calls.export = input;
      if (options.exportFailure) {
        (input.fail as (error: { errMsg: string }) => void)({ errMsg: options.exportFailure });
        return;
      }
      (input.success as (result: { tempFilePath: string }) => void)({
        tempFilePath: 'wxfile://compressed.jpg',
      });
    },
    getFileSystemManager: () => ({
      getFileInfo: (input: { filePath: string; success: (result: { size: number }) => void }) => {
        input.success({ size: options.compressedSize ?? 900_000 });
      },
    }),
  });
  return calls;
}

describe('prepare content photo', () => {
  it('redraws to the target size as JPEG q80 and returns the compressed size', async () => {
    const calls = stubPhotoCanvas({ compressedSize: 640_000 });
    const prepared = await prepareContentPhoto({
      tempFilePath: 'wxfile://original.jpg',
      size: 8_000_000,
      width: 4000,
      height: 3000,
    });

    expect(calls.canvasSize).toEqual({ width: 2048, height: 1536 });
    expect(calls.export).toMatchObject({
      width: 2048,
      height: 1536,
      destWidth: 2048,
      destHeight: 1536,
      fileType: 'jpg',
      quality: 0.8,
    });
    expect(prepared).toEqual({ path: 'wxfile://compressed.jpg', size: 640_000 });
  });

  it('falls back to the original file when compression fails', async () => {
    stubPhotoCanvas({ exportFailure: 'export failed' });
    const prepared = await prepareContentPhoto({
      tempFilePath: 'wxfile://original.png',
      size: 1_200_000,
      width: 3000,
      height: 2000,
    });

    expect(prepared).toEqual({ path: 'wxfile://original.png', size: 1_200_000 });
  });

  it('rejects unsupported extensions before compressing', async () => {
    stubPhotoCanvas();
    await expect(
      prepareContentPhoto({
        tempFilePath: 'wxfile://clip.gif',
        size: 1000,
        width: 100,
        height: 100,
      }),
    ).rejects.toThrow(CONTENT_PHOTO_TYPE_ERROR);
  });
});
