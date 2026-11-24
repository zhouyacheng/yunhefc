// MP10/MP11 活动派生态：列表卡片、报名按钮与家属摘要。
// 服务端 signupOpen 仍是权威门禁，本地只负责即时展示与文案矩阵。
import type { ActivitySignup, ActivityStatus, ActivitySummary, ActivityType } from '../types/api';
import { formatDayTimeRange } from './datetime';

export type ActivityStatusTone = 'open' | 'closed' | 'finished' | 'cancelled';

/** 与 frontend/packages/shared/src/activity.ts 保持一致；服务端 label 是英文枚举名，不可直接展示。 */
export const ACTIVITY_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '聚餐',
  1: '年会',
  2: '其他',
};

export const ACTIVITY_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '报名中',
  1: '报名截止',
  2: '已结束',
  3: '已取消',
};

export function activityTypeText(type: ActivityType): string {
  return ACTIVITY_TYPE_TEXT[type] ?? '—';
}

export function activityStatusText(status: ActivityStatus): string {
  return ACTIVITY_STATUS_TEXT[status] ?? '—';
}

/** 仅报名中（0）与报名截止（1）可编辑或流转；已结束/已取消为终态。 */
export function activityEditable(status: ActivityStatus): boolean {
  return status === 0 || status === 1;
}

export interface ActivityActionView {
  /** 已取消/已结束只显示状态徽标，不渲染按钮。 */
  visible: boolean;
  disabled: boolean;
  label: string;
  reason: string;
}

export interface ActivityCardView {
  id: number;
  title: string;
  typeText: string;
  statusText: string;
  statusTone: ActivityStatusTone;
  timeText: string;
  locationText: string;
  countText: string;
  imageUrl: string;
  actionLabel: string;
  actionVisible: boolean;
  actionDisabled: boolean;
}

export function activityStatusTone(status: ActivityStatus): ActivityStatusTone {
  switch (status) {
    case 0:
      return 'open';
    case 1:
      return 'closed';
    case 2:
      return 'finished';
    case 3:
      return 'cancelled';
    default:
      return 'closed';
  }
}

export function isActivityFull(
  activity: Pick<ActivitySummary, 'capacity' | 'signupCount'>,
): boolean {
  return activity.capacity !== null && activity.signupCount >= activity.capacity;
}

/** 本地镜像服务端口径：报名中、未过截止且未满员。 */
export function activitySignupOpen(activity: ActivitySummary, now: number): boolean {
  return (
    activity.status === 0 && now < Date.parse(activity.signupDeadline) && !isActivityFull(activity)
  );
}

const CLOSED_ACTION: ActivityActionView = {
  visible: true,
  disabled: true,
  label: '已截止',
  reason: '报名已截止',
};

/** 首个命中项：已取消 → 已结束 → 已截止 → 已满 → 可报名。 */
export function activityAction(activity: ActivitySummary, now: number): ActivityActionView {
  if (activity.status === 3) {
    return { visible: false, disabled: true, label: '已取消', reason: '活动已取消' };
  }
  if (activity.status === 2) {
    return { visible: false, disabled: true, label: '已结束', reason: '活动已结束' };
  }
  if (activity.status === 1 || now >= Date.parse(activity.signupDeadline)) {
    return CLOSED_ACTION;
  }
  if (isActivityFull(activity)) {
    return { visible: true, disabled: true, label: '已满', reason: '报名人数已满' };
  }
  // 服务端 signupOpen 兜底（时钟偏差/状态刚变化），不覆盖「已满」文案。
  if (!activity.signupOpen) {
    return CLOSED_ACTION;
  }
  return { visible: true, disabled: false, label: '报名', reason: '' };
}

/** 人数只计报名球员，不含家属。 */
export function activityCountText(
  activity: Pick<ActivitySummary, 'capacity' | 'signupCount'>,
): string {
  return activity.capacity === null
    ? `${activity.signupCount} 人报名`
    : `${activity.signupCount} / ${activity.capacity} 人`;
}

/** 报名人数进度百分比；与 packages/shared 的同名派生保持一致，上限未知或非法时返回 0。 */
export function activityCapacityPercent(
  activity: Pick<ActivitySummary, 'capacity' | 'signupCount'>,
): number {
  if (activity.capacity === null || activity.capacity <= 0) {
    return 0;
  }
  return Math.max(
    0,
    Math.min(100, Math.round((activity.signupCount / activity.capacity) * 100)),
  );
}

/** 单张图片加载失败时只移除该张，其余有效图片继续展示。 */
export function withoutFailedImage(urls: string[], index: number): string[] {
  if (!Number.isInteger(index) || index < 0 || index >= urls.length) {
    return urls;
  }
  return urls.filter((_, current) => current !== index);
}

export function activityTimeText(activity: Pick<ActivitySummary, 'startAt' | 'endAt'>): string {
  return formatDayTimeRange(activity.startAt, activity.endAt);
}

/** 本人取消仅限报名中且未过截止；管理员移除不走此判断。 */
export function activityCanCancel(activity: ActivitySummary, now: number): boolean {
  return (
    activity.status === 0 &&
    activity.mySignup?.status === 0 &&
    now < Date.parse(activity.signupDeadline)
  );
}

export function activityCostText(activity: Pick<ActivitySummary, 'costPerPerson'>): string {
  return activity.costPerPerson === null ? '—' : `¥${activity.costPerPerson.toFixed(2)} / 人`;
}

export function familySummary(signup: ActivitySignup | null | undefined): string {
  if (!signup || signup.status !== 0) {
    return '';
  }
  if (!signup.withFamily) {
    return '仅本人';
  }
  const note = (signup.familyNote || '').trim();
  return note ? `携带家属 · ${note}` : '携带家属';
}

export function buildActivityCard(activity: ActivitySummary, now: number): ActivityCardView {
  const action = activityAction(activity, now);
  return {
    id: activity.id,
    title: activity.title,
    typeText: activityTypeText(activity.type),
    statusText: activityStatusText(activity.status),
    statusTone: activityStatusTone(activity.status),
    timeText: activityTimeText(activity),
    locationText: activity.location,
    countText: activityCountText(activity),
    imageUrl: activity.locationImageUrls[0] || '',
    actionLabel: action.label,
    actionVisible: action.visible,
    actionDisabled: action.disabled,
  };
}
