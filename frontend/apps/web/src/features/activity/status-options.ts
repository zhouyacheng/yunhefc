import type { ActivityStatus } from '@yunhefc/shared';
import { activityEditable, activityStatusText } from '@yunhefc/shared';

export interface ActivityStatusOption {
  status: ActivityStatus;
  label: string;
  disabled: boolean;
  reason?: string;
}

const ALL_STATUSES: ActivityStatus[] = [0, 1, 2, 3];

/**
 * ACT-13 状态下拉：展示全部状态，但按 ACT-10 合法集禁用。
 * 0→1/2/3，1→2/3；已结束与已取消为终态，报名截止不可恢复为报名中。
 * 权威状态机在后端 ActivityStatusMachine；Web 与小程序各自镜像同一份前端判定。
 */
export function activityStatusOptions(currentStatus: ActivityStatus): ActivityStatusOption[] {
  const editable = activityEditable(currentStatus);
  return ALL_STATUSES.map((status) => {
    const label = activityStatusText(status);
    if (!editable) {
      return { status, label, disabled: true, reason: '已结束或已取消的活动不可修改' };
    }
    if (status === 0 && currentStatus === 1) {
      return { status, label, disabled: true, reason: '报名截止不可恢复为报名中' };
    }
    return { status, label, disabled: false };
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

/** ACT-13 取消原因：非空、≤255 字。 */
export function activityCancelReasonError(reason: string): string | null {
  const trimmed = reason.trim();
  if (!trimmed) {
    return '请输入取消原因';
  }
  if (trimmed.length > 255) {
    return '取消原因不能超过 255 字';
  }
  return null;
}
