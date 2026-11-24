// ACT-13/ACT-10 状态流转：展示全部状态，但按后端合法集禁用。
// 权威状态机在后端 ActivityStatusMachine；Web 与小程序各自镜像同一份前端判定（小程序不引 workspace 包）。
import type { ActivityStatus } from '../types/api';
import { activityEditable, activityStatusText } from './activity-view';

export interface ActivityStatusOption {
  status: ActivityStatus;
  label: string;
  disabled: boolean;
  reason?: string;
}

const ALL_STATUSES: ActivityStatus[] = [0, 1, 2, 3];

/**
 * 0→1/2/3，1→2/3；已结束与已取消为终态，报名截止不可恢复为报名中。
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
