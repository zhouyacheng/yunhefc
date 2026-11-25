import type { ActivityStatus } from '@yunhefc/shared';
import { activityStatusText } from '@yunhefc/shared';

export interface ActivityStatusOption {
  status: ActivityStatus;
  label: string;
  disabled: boolean;
  reason?: string;
}

const ALL_STATUSES: ActivityStatus[] = [0, 1, 2, 3];

/** 合法目标集：0↔1、0/1→2/3、2→0/1、3→0/1。 */
const ALLOWED_TARGETS: Readonly<Record<ActivityStatus, readonly ActivityStatus[]>> = {
  0: [1, 2, 3],
  1: [0, 2, 3],
  2: [0, 1],
  3: [0, 1],
};

/**
 * ACT-13 状态下拉：展示全部状态，但按 ACT-10 合法集禁用。
 * 报名中与报名截止可互转（P-111）；已结束可重新开放（P-110）、已取消可恢复（P-114），
 * 目标均仅限报名中/报名截止。权威状态机在后端 ActivityStatusMachine；Web 与小程序各自镜像同一份前端判定。
 */
export function activityStatusOptions(currentStatus: ActivityStatus): ActivityStatusOption[] {
  return ALL_STATUSES.map((status) => {
    const label = activityStatusText(status);
    if (status === currentStatus || ALLOWED_TARGETS[currentStatus].includes(status)) {
      return { status, label, disabled: false };
    }
    return { status, label, disabled: true, reason: disabledReason(currentStatus, status) };
  });
}

function disabledReason(currentStatus: ActivityStatus, targetStatus: ActivityStatus): string {
  if (currentStatus === 2 && targetStatus === 3) {
    return '已结束的活动不可取消';
  }
  if (currentStatus === 3 && targetStatus === 2) {
    return '已取消的活动不可结束';
  }
  return '当前状态不允许该操作';
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
