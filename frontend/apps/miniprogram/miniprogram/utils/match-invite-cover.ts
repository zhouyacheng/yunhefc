// 比赛邀请分享封面：按当前比赛绘制 5:4 PNG（500×400），失败时页面回退静态品牌图。
// 微信分享缩略图只取发起页截图或显式 imageUrl；这里用 canvas 生成，避免分享比赛详情截图。
import type { InviteCreated, MatchDetail } from '../types/api';
import { formatDateWithYear, formatDateTime, formatTimeRange } from './datetime';

export const SHARE_COVER_WIDTH = 500;
export const SHARE_COVER_HEIGHT = 400;
/** Canvas 生成失败时的静态 5:4 品牌封面。 */
export const SHARE_COVER_FALLBACK = '/assets/share-match-invite.png';

export interface ShareCoverData {
  title: string;
  dateText: string;
  timeRangeText: string;
  venue: string;
  quotaText: string;
  deadlineText: string;
  expiresText: string;
}

function textOr(value: string): string {
  return value || '—';
}

/** 分享封面只依赖比赛详情与邀请有效期；没有邀请 token。 */
export function buildShareCoverData(
  detail: Pick<
    MatchDetail,
    'title' | 'startAt' | 'endAt' | 'venue' | 'confirmedCount' | 'capacity' | 'signupDeadline'
  >,
  invite: Pick<InviteCreated, 'expiresAt'>,
): ShareCoverData {
  return {
    title: detail.title || '比赛邀请',
    dateText: textOr(formatDateWithYear(detail.startAt)),
    timeRangeText: textOr(formatTimeRange(detail.startAt, detail.endAt)),
    venue: detail.venue || '待定',
    quotaText: `${detail.confirmedCount} / ${detail.capacity}`,
    deadlineText: textOr(formatDateTime(detail.signupDeadline)),
    expiresText: `邀请有效期至 ${textOr(formatDateTime(invite.expiresAt))}`,
  };
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

function roundRect(
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

function drawBackground(
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

/** 把当前比赛画到隐藏 canvas 并导出临时 PNG；任一步失败由调用方回退静态图。 */
export async function renderShareCover(
  canvas: WechatMiniprogram.Canvas,
  data: ShareCoverData,
): Promise<string> {
  canvas.width = SHARE_COVER_WIDTH;
  canvas.height = SHARE_COVER_HEIGHT;
  const ctx = canvas.getContext('2d');
  const measure = (text: string): number => ctx.measureText(text).width;

  drawBackground(ctx);

  // 标签胶囊
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
  ctx.fillText('比赛邀请', 54, 48);

  // 品牌
  ctx.fillStyle = '#f5c451';
  ctx.font = '700 15px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'right';
  ctx.fillText('云河FC', 460, 48);

  // 标题：最多两行
  ctx.fillStyle = '#ffffff';
  ctx.font = '800 32px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'alphabetic';
  const titleLines = wrapText(measure, data.title, 420, 2);
  titleLines.forEach((line, index) => {
    ctx.fillText(line, 40, 126 + index * 42);
  });

  ctx.fillStyle = '#e2e8f0';
  ctx.font = '600 22px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(data.dateText, 40, 222);

  ctx.fillStyle = '#cbd5e1';
  ctx.font = '400 18px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(data.timeRangeText, 40, 250);
  ctx.fillText(fitText(measure, `场地 · ${data.venue}`, 420), 40, 280);

  ctx.fillStyle = '#94a3b8';
  ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(
    fitText(measure, `名额 ${data.quotaText} · 报名截止 ${data.deadlineText}`, 420),
    40,
    308,
  );
  ctx.fillStyle = '#64748b';
  ctx.font = '400 12px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText(data.expiresText, 40, 330);

  // 底部说明
  ctx.fillStyle = '#16a34a';
  ctx.beginPath();
  ctx.arc(44, 365, 4, 0, Math.PI * 2);
  ctx.fill();
  ctx.fillStyle = '#94a3b8';
  ctx.font = '400 14px "PingFang SC", "Microsoft YaHei", sans-serif';
  ctx.fillText('查看比赛详情，报名或加入候补', 58, 370);

  // 等一次视图层 flush，避免刚绘制完立即导出在部分真机上拿到空画布。
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

export function queryMatchInviteCoverCanvas(): Promise<WechatMiniprogram.Canvas> {
  return new Promise((resolve, reject) => {
    wx.createSelectorQuery()
      .select('#invite-cover')
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
