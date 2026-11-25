// ACT-13/ACT-10 状态流转：展示全部状态，但按后端合法集禁用。
// 权威状态机在后端 ActivityStatusMachine；Web 与小程序各自镜像同一份前端判定（小程序不引 workspace 包）。
import type { ActivityStatus } from '../types/api';
import { activityStatusText } from './activity-view';

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

function disabledReason(currentStatus: ActivityStatus, targetStatus: ActivityStatus): string {
  if (currentStatus === 2 && targetStatus === 3) {
    return '已结束的活动不可取消';
  }
  if (currentStatus === 3 && targetStatus === 2) {
    return '已取消的活动不可结束';
  }
  return '当前状态不允许该操作';
}

/**
 * 报名中与报名截止可互转（P-111）；已结束可重新开放（P-110）、已取消可恢复（P-114），
 * 目标均仅限报名中/报名截止。
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
