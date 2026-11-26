import { vi } from 'vitest';

export interface ShareCanvasStub {
  canvas: WechatMiniprogram.Canvas;
  /** 记录 Canvas 收到的 fillText 文本，便于断言封面内容。 */
  drawn: string[];
}

export interface ShareCanvasOptions {
  /** false 模拟隐藏画布节点不存在。 */
  node?: boolean;
  /** 设置后模拟 canvasToTempFilePath 失败。 */
  exportFailure?: string;
}

/** 模拟 wx 的隐藏画布查询与导出；调用方用 afterEach(vi.unstubAllGlobals) 复原。 */
export function stubShareCanvas(options: ShareCanvasOptions = {}): ShareCanvasStub {
  const drawn: string[] = [];
  const context = new Proxy(
    {},
    {
      get: (_target, property) => {
        if (property === 'measureText') {
          return (text: string) => ({ width: text.length * 10 });
        }
        if (property === 'fillText') {
          return (text: string) => {
            drawn.push(text);
          };
        }
        if (property === 'createLinearGradient' || property === 'createRadialGradient') {
          return () => ({ addColorStop: () => undefined });
        }
        return () => undefined;
      },
      set: () => true,
    },
  );
  const canvas = { width: 0, height: 0, getContext: () => context };

  vi.stubGlobal('wx', {
    nextTick: (callback: () => void) => callback(),
    createSelectorQuery: () => ({
      select: () => ({
        fields: () => ({
          exec: (callback: (result: unknown[]) => void) =>
            callback([options.node === false ? {} : { node: canvas }]),
        }),
      }),
    }),
    canvasToTempFilePath: (opts: {
      success: (result: { tempFilePath: string }) => void;
      fail: (error: { errMsg: string }) => void;
    }) => {
      if (options.exportFailure) {
        opts.fail({ errMsg: options.exportFailure });
        return;
      }
      opts.success({ tempFilePath: '/tmp/share-cover.png' });
    },
  });

  return { canvas: canvas as unknown as WechatMiniprogram.Canvas, drawn };
}
