// 移植自 frontend/packages/shared/src/match.ts；仅用于按钮可用性提示，服务端强校验。
import { SHANGHAI_OFFSET_MS } from './shanghai';

const DAY_MS = 24 * 60 * 60 * 1000;
const CHECK_IN_OPENS_BEFORE_MS = 4 * 60 * 60 * 1000;
const EVENT_EDIT_WINDOW_MS = 24 * 60 * 60 * 1000;

export type CheckInWindowState = 'open' | 'before' | 'after';

/** 签到窗口：开赛前 4 小时至比赛当天 23:59（Asia/Shanghai）。 */
export function checkInWindowState(startAt: string, now: number = Date.now()): CheckInWindowState {
  const start = Date.parse(startAt);
  if (Number.isNaN(start)) {
    return 'after';
  }
  if (now < start - CHECK_IN_OPENS_BEFORE_MS) {
    return 'before';
  }
  const localDayStart =
    Math.floor((start + SHANGHAI_OFFSET_MS) / DAY_MS) * DAY_MS - SHANGHAI_OFFSET_MS;
  return now < localDayStart + DAY_MS ? 'open' : 'after';
}

/** 事件录入仅进行中/已结束。 */
export function eventEntryOpen(status: number | null | undefined): boolean {
  return status === 3 || status === 4;
}

/** 球员赛后 24 小时编辑窗口；管理员不受限。 */
export function eventEditWindowOpen(endAt: string, now: number = Date.now()): boolean {
  const end = Date.parse(endAt);
  return !Number.isNaN(end) && now <= end + EVENT_EDIT_WINDOW_MS;
}
