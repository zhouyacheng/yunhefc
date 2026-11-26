// 小程序 5:4 分享封面共用绘制原语：尺寸、文本处理、版式、品牌背景、画布查询/生成回退与导出。
// 封面统一 500×400，由各页面按自身领域数据绘制。
export const SHARE_COVER_WIDTH = 500;
export const SHARE_COVER_HEIGHT = 400;

/** 封面字段空值兜底。 */
export function textOr(value: string): string {
  return value || '—';
}

/** 单行超宽时截断并加省略号。 */
export function fitText(measure: (text: string) => number, text: string, maxWidth: number): string {
  if (measure(text) <= maxWidth) {
    return text;
  }
  let left = text;
  while (left.length > 0 && measure(`${left}…`) > maxWidth) {
    left = left.slice(0, -1);
  }
  return `${left}…`;
}

/** 按宽度折行，最多 maxLines 行；超出末行截断。 */
export function wrapText(
  measure: (text: string) => number,
  text: string,
  maxWidth: number,
  maxLines: number,
): string[] {
  if (maxLines <= 0) {
    return [];
  }
  const lines: string[] = [];
  let rest = text;
  while (rest && lines.length < maxLines) {
    if (measure(rest) <= maxWidth) {
      lines.push(rest);
      rest = '';
      break;
    }
    let cut = rest.length;
    while (cut > 1 && measure(rest.slice(0, cut)) > maxWidth) {
      cut -= 1;
    }
    lines.push(rest.slice(0, cut));
    rest = rest.slice(cut);
  }
  if (rest) {
    const last = lines.length - 1;
    lines[last] = fitText(measure, `${lines[last]}${rest}`, maxWidth);
  }
  return lines;
}

export function roundRect(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
  x: number,
  y: number,
  width: number,
  height: number,
  radius: number,
): void {
  const r = Math.min(radius, width / 2, height / 2);
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + width - r, y);
  ctx.arcTo(x + width, y, x + width, y + r, r);
  ctx.lineTo(x + width, y + height - r);
  ctx.arcTo(x + width, y + height, x + width - r, y + height, r);
  ctx.lineTo(x + r, y + height);
  ctx.arcTo(x, y + height, x, y + height - r, r);
  ctx.lineTo(x, y + r);
  ctx.arcTo(x, y, x + r, y, r);
  ctx.closePath();
}

/** 深色品牌底：藏青到墨绿渐变、右上绿色光晕、右下同心圆与竖向分隔线。 */
export function drawShareBackground(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
): void {
  const bg = ctx.createLinearGradient(0, 0, SHARE_COVER_WIDTH, SHARE_COVER_HEIGHT);
  bg.addColorStop(0, '#0b1220');
  bg.addColorStop(0.58, '#111c33');
  bg.addColorStop(1, '#0f2a24');
  ctx.fillStyle = bg;
  ctx.fillRect(0, 0, SHARE_COVER_WIDTH, SHARE_COVER_HEIGHT);

  const glow = ctx.createRadialGradient(440, -40, 0, 440, -40, 260);
  glow.addColorStop(0, 'rgba(22, 163, 74, 0.45)');
  glow.addColorStop(1, 'rgba(22, 163, 74, 0)');
  ctx.fillStyle = glow;
  ctx.fillRect(0, 0, SHARE_COVER_WIDTH, SHARE_COVER_HEIGHT);

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.12)';
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.arc(430, 410, 170, 0, Math.PI * 2);
  ctx.stroke();
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
  ctx.beginPath();
  ctx.arc(430, 410, 105, 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(290, -20);
  ctx.lineTo(290, 420);
  ctx.stroke();
}

/** 等一次视图层 flush 后导出 500×400 PNG；刚绘制完立即导出在部分真机会拿到空画布。 */
export async function exportShareCover(canvas: WechatMiniprogram.Canvas): Promise<string> {
  await new Promise<void>((resolve) => wx.nextTick(resolve));
  return new Promise((resolve, reject) => {
    wx.canvasToTempFilePath({
      canvas,
      x: 0,
      y: 0,
      width: SHARE_COVER_WIDTH,
      height: SHARE_COVER_HEIGHT,
      destWidth: SHARE_COVER_WIDTH,
      destHeight: SHARE_COVER_HEIGHT,
      fileType: 'png',
      success: (result) => resolve(result.tempFilePath),
      fail: (error) => reject(new Error(error.errMsg || '分享封面导出失败')),
    });
  });
}

/** 读取页面内隐藏 Canvas 节点；选择器不存在时抛错，由调用方回退静态图。 */
export function queryCoverCanvas(selector: string): Promise<WechatMiniprogram.Canvas> {
  return new Promise((resolve, reject) => {
    wx.createSelectorQuery()
      .select(selector)
      .fields({ node: true })
      .exec((result) => {
        const node = (result?.[0] as { node?: WechatMiniprogram.Canvas } | undefined)?.node;
        if (node) {
          resolve(node);
          return;
        }
        reject(new Error('分享封面画布不存在'));
      });
  });
}

/** 分享封面页头：左上标签胶囊与右上品牌，两种封面共用。 */
export function drawShareHeader(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
  label: string,
): void {
  roundRect(ctx, 40, 32, 96, 30, 15);
  ctx.fillStyle = 'rgba(22, 163, 74, 0.18)';
  ctx.fill();
  ctx.strokeStyle = 'rgba(134, 239, 172, 0.45)';
  ctx.lineWidth = 1;
  ctx.stroke();
  ctx.fillStyle = '#dcfce7';
  ctx.font = '600 14px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'middle';
  ctx.fillText(label, 54, 48);

  ctx.fillStyle = '#f5c451';
  ctx.font = '700 15px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'right';
  ctx.fillText('云河FC', 460, 48);
  // 正文固定左对齐，调用方无需再复位。
  ctx.textAlign = 'left';
}

/** 分享封面标题：最多两行，超出末行截断。 */
export function drawShareTitle(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
  title: string,
): void {
  const measure = (text: string): number => ctx.measureText(text).width;
  ctx.fillStyle = '#ffffff';
  ctx.font = '800 32px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  wrapText(measure, title, 420, 2).forEach((line, index) => {
    ctx.fillText(line, 40, 126 + index * 42);
  });
}

export interface ShareMeta {
  dateText: string;
  timeRangeText: string;
  /** 已带前缀的地点/场地文案。 */
  placeText: string;
  /** 人数与截止等一行元信息。 */
  quotaText: string;
  /** 可选小字行，空串不绘制。 */
  extraText: string;
}

/** 分享封面正文：日期、时段、地点、人数/截止与可选小字行。 */
export function drawShareMeta(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
  meta: ShareMeta,
): void {
  const measure = (text: string): number => ctx.measureText(text).width;
  ctx.fillStyle = '#e2e8f0';
  ctx.font = '600 22px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(meta.dateText, 40, 222);

  ctx.fillStyle = '#cbd5e1';
  ctx.font = '400 18px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(meta.timeRangeText, 40, 250);
  ctx.fillText(fitText(measure, meta.placeText, 420), 40, 280);

  ctx.fillStyle = '#94a3b8';
  ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(fitText(measure, meta.quotaText, 420), 40, 308);
  if (meta.extraText) {
    ctx.fillStyle = '#64748b';
    ctx.font = '400 12px "PingFang SC", "Microsoft YaHei", sans-serif';
    ctx.fillText(meta.extraText, 40, 330);
  }
}

/** 分享封面页脚：绿色圆点与引导文案，两种封面共用。 */
export function drawShareFooter(
  ctx: WechatMiniprogram.CanvasRenderingContext.CanvasRenderingContext2D,
  hint: string,
): void {
  ctx.fillStyle = '#16a34a';
  ctx.beginPath();
  ctx.arc(44, 365, 4, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#94a3b8';
  ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(hint, 58, 370);
}

/** 查询页面隐藏画布并绘制；等待首帧、查询、绘制或导出任一步失败都回退 fallback。 */
export async function generateShareCover(
  selector: string,
  render: (canvas: WechatMiniprogram.Canvas) => Promise<string>,
  fallback: string,
): Promise<string> {
  try {
    await new Promise<void>((resolve) => wx.nextTick(resolve));
    const canvas = await queryCoverCanvas(selector);
    return await render(canvas);
  } catch {
    return fallback;
  }
}
