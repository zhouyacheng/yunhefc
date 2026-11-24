// MP10/MP11 活动派生态：列表卡片、报名按钮与家属摘要。
// 服务端 signupOpen 仍是权威门禁，本地只负责即时展示与文案矩阵。
import type { ActivitySignup, ActivityStatus, ActivitySummary } from '../types/api';
import { formatDayStartTime, formatTimeRange } from './datetime';

export type ActivityStatusTone = 'open' | 'closed' | 'finished' | 'cancelled';

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
  actionReason: string;
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

/** 首个命中项：已取消 → 已结束 → 已截止 → 已满 → 可报名。 */
export function activityAction(activity: ActivitySummary, now: number): ActivityActionView {
  if (activity.status === 3) {
    return { visible: false, disabled: true, label: '已取消', reason: '活动已取消' };
  }
  if (activity.status === 2) {
    return { visible: false, disabled: true, label: '已结束', reason: '活动已结束' };
  }
  if (activity.status === 1 || now >= Date.parse(activity.signupDeadline)) {
    return { visible: true, disabled: true, label: '已截止', reason: '报名已截止' };
  }
  if (isActivityFull(activity)) {
    return { visible: true, disabled: true, label: '已满', reason: '报名人数已满' };
  }
  if (!activity.signupOpen) {
    return { visible: true, disabled: true, label: '已截止', reason: '报名已截止' };
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

export function activityTimeText(activity: Pick<ActivitySummary, 'startAt' | 'endAt'>): string {
  const start = formatDayStartTime(activity.startAt);
  const range = formatTimeRange(activity.startAt, activity.endAt);
  // 复用比赛时段文案，只取结束部分拼到「M月D日 周X HH:mm」之后。
  const suffix = range.includes('–') ? range.slice(range.indexOf('–')) : '';
  return `${start}${suffix}`;
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
    typeText: activity.typeLabel,
    statusText: activity.statusLabel,
    statusTone: activityStatusTone(activity.status),
    timeText: activityTimeText(activity),
    locationText: activity.location,
    countText: activityCountText(activity),
    imageUrl: activity.locationImageUrl || '',
    actionLabel: action.label,
    actionVisible: action.visible,
    actionDisabled: action.disabled,
    actionReason: action.reason,
  };
}
