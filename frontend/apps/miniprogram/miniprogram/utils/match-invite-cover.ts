// 比赛邀请分享封面：按当前比赛绘制 5:4 PNG（500×400），失败时页面回退静态品牌图。
// 微信分享缩略图只取发起页截图或显式 imageUrl；这里用 canvas 生成，避免分享比赛详情截图。
import type { InviteCreated, MatchDetail } from '../types/api';
import { formatDateWithYear, formatDateTime, formatTimeRange } from './datetime';
import { renderShareCoverFrame, textOr } from './share-cover';
import { MATCH_COVER_PALETTE } from './share-cover-palette';

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

/** 把当前比赛画到隐藏 canvas 并导出临时 PNG；任一步失败由调用方回退静态图。 */
export async function renderShareCover(
  canvas: WechatMiniprogram.Canvas,
  data: ShareCoverData,
): Promise<string> {
  return renderShareCoverFrame(
    canvas,
    {
      headerLabel: '比赛邀请',
      title: data.title,
      meta: {
        dateText: data.dateText,
        timeRangeText: data.timeRangeText,
        placeText: `场地 · ${data.venue}`,
        quotaText: `名额 ${data.quotaText} · 报名截止 ${data.deadlineText}`,
        extraText: data.expiresText,
      },
      hint: '查看比赛详情，报名或加入候补',
    },
    MATCH_COVER_PALETTE,
  );
}
