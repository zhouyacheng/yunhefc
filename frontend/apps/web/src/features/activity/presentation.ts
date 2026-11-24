import type { ActivitySignupStatus, ActivityStatus, ActivityType } from '@yunhefc/shared';

export const ACTIVITY_SIGNUP_OPEN: ActivityStatus = 0;
export const ACTIVITY_SIGNUP_CLOSED: ActivityStatus = 1;
export const ACTIVITY_FINISHED: ActivityStatus = 2;
export const ACTIVITY_CANCELLED: ActivityStatus = 3;

export const ACTIVITY_SIGNUP_ACTIVE: ActivitySignupStatus = 0;

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

export type ActivityStatusTone = 'open' | 'closed' | 'finished' | 'cancelled';

export interface ActivityGateInput {
  status: ActivityStatus;
  signupDeadline: string;
  capacity: number | null;
  signupCount: number;
  signupOpen: boolean;
}

export interface ActivityCta {
  /** 已取消/已结束只显示徽标，不渲染按钮。 */
  visible: boolean;
  label: string;
  disabled: boolean;
}

export type ActivitySignupAction = 'login' | 'signup' | 'none';

export interface ActivitySignupState {
  visible: boolean;
  label: string;
  action: ActivitySignupAction;
  disabled: boolean;
  reason: string;
}

export interface ActivityCancelState {
  visible: boolean;
  label: string;
  disabled: boolean;
  reason: string;
}

export function activityTypeText(type: ActivityType): string {
  return ACTIVITY_TYPE_TEXT[type] ?? '其他';
}

export function activityStatusText(status: ActivityStatus): string {
  return ACTIVITY_STATUS_TEXT[status] ?? '—';
}

export function activityStatusTone(status: ActivityStatus): ActivityStatusTone {
  if (status === ACTIVITY_SIGNUP_OPEN) {
    return 'open';
  }
  if (status === ACTIVITY_SIGNUP_CLOSED) {
    return 'closed';
  }
  return status === ACTIVITY_FINISHED ? 'finished' : 'cancelled';
}

function withinDeadline(input: ActivityGateInput, now: number): boolean {
  const deadline = Date.parse(input.signupDeadline);
  return !Number.isNaN(deadline) && now < deadline;
}

/** C11/C12 共用按钮矩阵：已取消 → 已结束 → 已截止 → 已满 → 报名。 */
export function activityCta(input: ActivityGateInput, now = Date.now()): ActivityCta {
  if (input.status === ACTIVITY_CANCELLED) {
    return { visible: false, label: '已取消', disabled: true };
  }
  if (input.status === ACTIVITY_FINISHED) {
    return { visible: false, label: '已结束', disabled: true };
  }
  if (input.status !== ACTIVITY_SIGNUP_OPEN || !withinDeadline(input, now)) {
    return { visible: true, label: '已截止', disabled: true };
  }
  if (input.capacity !== null && input.signupCount >= input.capacity) {
    return { visible: true, label: '已满', disabled: true };
  }
  // signupOpen 是后端对同一组条件（状态/截止/满员）的派生值，不一致时保守禁用。
  return { visible: true, label: '报名', disabled: !input.signupOpen };
}

/** C12 报名按钮；先按活动状态收敛，再区分未登录与待审核。 */
export function activitySignupState(
  input: ActivityGateInput & {
    authenticated: boolean;
    pending: boolean;
    mySignupStatus: ActivitySignupStatus | null;
    now?: number;
  },
): ActivitySignupState {
  if (input.mySignupStatus === ACTIVITY_SIGNUP_ACTIVE) {
    return { visible: false, label: '', action: 'none', disabled: true, reason: '' };
  }
  const cta = activityCta(input, input.now);
  if (!cta.visible) {
    return { visible: false, label: cta.label, action: 'none', disabled: true, reason: '' };
  }
  if (cta.disabled) {
    return {
      visible: true,
      label: cta.label,
      action: 'none',
      disabled: true,
      reason: cta.label === '已满' ? '名额已满' : '',
    };
  }
  if (!input.authenticated) {
    return { visible: true, label: '登录后报名', action: 'login', disabled: false, reason: '' };
  }
  if (input.pending) {
    return {
      visible: false,
      label: '',
      action: 'none',
      disabled: true,
      reason: '账号待审核，通过后可报名',
    };
  }
  return { visible: true, label: cta.label, action: 'signup', disabled: false, reason: '' };
}

/** C12 取消按钮；仅报名中且未过截止可自行取消。 */
export function activityCancelState(
  input: ActivityGateInput & {
    mySignupStatus: ActivitySignupStatus | null;
    now?: number;
  },
): ActivityCancelState {
  if (input.mySignupStatus !== ACTIVITY_SIGNUP_ACTIVE) {
    return { visible: false, label: '', disabled: true, reason: '' };
  }
  const label = '取消报名';
  const now = input.now ?? Date.now();
  if (input.status === ACTIVITY_CANCELLED) {
    return { visible: true, label, disabled: true, reason: '活动已取消' };
  }
  if (input.status === ACTIVITY_FINISHED) {
    return { visible: true, label, disabled: true, reason: '活动已结束' };
  }
  if (input.status !== ACTIVITY_SIGNUP_OPEN || !withinDeadline(input, now)) {
    return { visible: true, label, disabled: true, reason: '报名已截止，无法取消' };
  }
  return { visible: true, label, disabled: false, reason: '' };
}

/** 只计报名人数，不含家属；无上限只显示已报人数。 */
export function activitySignupText(signupCount: number, capacity: number | null): string {
  return capacity === null
    ? `已报 ${signupCount} 人（不含家属）`
    : `已报 ${signupCount} / ${capacity} 人（不含家属）`;
}

export function formatActivityCost(value: number | null): string {
  return value === null ? '—' : `¥${value.toFixed(2)}`;
}

export function activityFamilyText(signup: {
  withFamily: boolean;
  familyNote: string | null;
}): string {
  if (!signup.withFamily) {
    return '不携带家属';
  }
  const note = signup.familyNote?.trim();
  return note ? `携带家属（${note}）` : '携带家属';
}
