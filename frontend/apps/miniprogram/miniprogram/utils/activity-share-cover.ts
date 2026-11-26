// 活动分享封面：按当前活动绘制 5:4 PNG（500×400），失败时页面回退静态品牌图。
// 活动分享沿用稳定详情 URL，不引入邀请 token；封面只使用公开活动信息。
import type { ActivityDetail } from '../types/api';
import { formatDateWithYear, formatDateTime, formatTimeRange } from './datetime';
import {
  SHARE_COVER_HEIGHT,
  SHARE_COVER_WIDTH,
  drawShareBackground,
  drawShareFooter,
  drawShareHeader,
  drawShareMeta,
  drawShareTitle,
  exportShareCover,
  textOr,
} from './share-cover';

/** Canvas 生成失败时的静态 5:4 品牌封面。 */
export const ACTIVITY_SHARE_COVER_FALLBACK = '/assets/share-activity-invite.png';

export interface ActivityShareCoverData {
  title: string;
  dateText: string;
  timeRangeText: string;
  locationText: string;
  quotaText: string;
  deadlineText: string;
  costText: string;
  hintText: string;
}

type ActivityShareSource = Pick<
  ActivityDetail,
  | 'title'
  | 'startAt'
  | 'endAt'
  | 'location'
  | 'signupCount'
  | 'capacity'
  | 'signupDeadline'
  | 'costPerPerson'
  | 'signupOpen'
>;

/** 分享封面只依赖活动详情；已截止/满员/终态活动不再引导报名。 */
export function buildActivityShareCoverData(activity: ActivityShareSource): ActivityShareCoverData {
  return {
    title: activity.title || '活动邀请',
    dateText: textOr(formatDateWithYear(activity.startAt)),
    timeRangeText: textOr(formatTimeRange(activity.startAt, activity.endAt)),
    locationText: `地点 · ${activity.location || '待定'}`,
    quotaText: `已报名 ${activity.signupCount} / ${activity.capacity} 人`,
    deadlineText: `报名截止 ${textOr(formatDateTime(activity.signupDeadline))}`,
    costText: activity.costPerPerson === null ? '' : `人均 ¥${activity.costPerPerson.toFixed(2)}`,
    hintText: activity.signupOpen ? '查看活动详情，报名参加' : '查看活动详情',
  };
}

/** 把当前活动画到隐藏 canvas 并导出临时 PNG；任一步失败由调用方回退静态图。 */
export async function renderActivityShareCover(
  canvas: WechatMiniprogram.Canvas,
  data: ActivityShareCoverData,
): Promise<string> {
  canvas.width = SHARE_COVER_WIDTH;
  canvas.height = SHARE_COVER_HEIGHT;
  const ctx = canvas.getContext('2d');

  drawShareBackground(ctx);
  drawShareHeader(ctx, '活动邀请');
  drawShareTitle(ctx, data.title);
  drawShareMeta(ctx, {
    dateText: data.dateText,
    timeRangeText: data.timeRangeText,
    placeText: data.locationText,
    quotaText: `${data.quotaText} · ${data.deadlineText}`,
    extraText: data.costText,
  });
  drawShareFooter(ctx, data.hintText);

  return exportShareCover(canvas);
}
