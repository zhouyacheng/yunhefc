import type { ActivityStatus } from '@yunhefc/shared';
import {
  activityStatusCanTransit,
  activityStatusDisabledReason,
  activityStatusText,
} from '@yunhefc/shared';

export interface ActivityStatusOption {
  status: ActivityStatus;
  label: string;
  disabled: boolean;
  reason?: string;
}

const ALL_STATUSES: ActivityStatus[] = [0, 1, 2, 3];

/**
 * ACT-13 状态下拉：展示全部状态，但按 ACT-10 合法集禁用。
 * 报名中与报名截止可互转（P-111）；已结束可重新开放（P-110）、已取消可恢复（P-114），
 * 目标均仅限报名中/报名截止。权威状态机在后端 ActivityStatusMachine；Web 与小程序各自镜像同一份前端判定。
 */
export function activityStatusOptions(currentStatus: ActivityStatus): ActivityStatusOption[] {
  return ALL_STATUSES.map((status) => {
    const label = activityStatusText(status);
    if (status === currentStatus || activityStatusCanTransit(currentStatus, status)) {
      return { status, label, disabled: false };
    }
    return {
      status,
      label,
      disabled: true,
      reason: activityStatusDisabledReason(currentStatus, status),
    };
  });
}

export type ActivityStatusChoice =
  { kind: 'ignore' } | { kind: 'cancel' } | { kind: 'change'; status: ActivityStatus };

/** 下拉选择的领域结果：忽略非法/当前值、打开取消原因表单，或直接切换状态。 */
export function activityStatusChoice(
  options: readonly ActivityStatusOption[],
  currentStatus: ActivityStatus,
  targetStatus: number,
): ActivityStatusChoice {
  const option = options.find((item) => item.status === targetStatus);
  if (!option || option.disabled || option.status === currentStatus) {
    return { kind: 'ignore' };
  }
  if (option.status === 3) {
    return { kind: 'cancel' };
  }
  return { kind: 'change', status: option.status };
}

/** ACT-13 取消原因：可选、≤255 字。 */
export function activityCancelReasonError(reason: string): string | null {
  if (reason.trim().length > 255) {
    return '取消原因不能超过 255 字';
  }
  return null;
}
