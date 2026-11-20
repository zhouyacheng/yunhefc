// MP04 写操作：报名/取消/签到、比赛状态修改与事件录入。
// 只接收领域输入，返回页面指令（patch/toast/error/reload）；setData、弹层与忙闲标记仍由页面负责。
import type { Attendance, EventCreate, MatchEvent } from '../../types/api';
import { errorMessage, shouldRefreshAfterConflict } from '../../utils/api-errors';
import {
  cancelMyRegistration,
  changeMatchStatus,
  checkInMatch,
  createMatchEvent,
  signupMatch,
  updateMatchEvent,
} from '../../utils/api-client';
import { invalidate } from '../../utils/invalidation';
import { MATCH_STATUS_TEXT, attendanceText } from '../../utils/presentation';
import { REGISTRATION_STATUS, promotionText } from '../../utils/registration';

/** 写操作成功后要写入页面的字段；页面其余 data 字段不由写操作触碰。 */
export type WritePatch = {
  actionMessage?: string;
  attendance?: Attendance;
  attendanceText?: string;
  statusOpen?: boolean;
  eventFormOpen?: boolean;
};

/**
 * 写操作结果。成功时页面重载详情；失败时仅当服务端数据已变化才重载，
 * 事件更新冲突还要在重载后按 id 重取版本。
 */
export type WriteOutcome =
  | {
      ok: true;
      patch: WritePatch;
      /** 轻提示；空串表示不提示。 */
      toast: string;
    }
  | {
      ok: false;
      /** 失败文案。 */
      error: string;
      /** 服务端数据已变化，需要静默重载。 */
      reload: boolean;
      /** 重载后要重取版本的事件 id；无则 null。 */
      refreshEventId: number | null;
    };

function succeeded(patch: WritePatch = {}, toast = ''): WriteOutcome {
  return { ok: true, patch, toast };
}

function failed(
  error: unknown,
  fallback: string,
  refreshEventId: number | null = null,
): WriteOutcome {
  const reload = shouldRefreshAfterConflict(error);
  return {
    ok: false,
    error: errorMessage(error, fallback),
    reload,
    refreshEventId: reload ? refreshEventId : null,
  };
}

export async function signup(matchId: number): Promise<WriteOutcome> {
  try {
    const registration = await signupMatch(matchId);
    invalidate('my-registrations');
    return succeeded(
      {},
      registration.status === REGISTRATION_STATUS.WAITLISTED ? '已加入候补' : '报名成功',
    );
  } catch (error) {
    return failed(error, '报名失败，请稍后重试');
  }
}

export interface CancelInput {
  matchId: number;
  /** 取消前的报名状态，用于区分“取消报名/取消候补”。 */
  cancelledStatus: number | null;
}

export async function cancel(input: CancelInput): Promise<WriteOutcome> {
  try {
    const result = await cancelMyRegistration(input.matchId);
    invalidate('my-registrations');
    return succeeded({ actionMessage: promotionText(result, null, input.cancelledStatus) });
  } catch (error) {
    return failed(error, '取消报名失败，请稍后重试');
  }
}

export async function checkIn(matchId: number): Promise<WriteOutcome> {
  try {
    const attendance = await checkInMatch(matchId);
    return succeeded({ attendance, attendanceText: attendanceText(attendance) }, '签到成功');
  } catch (error) {
    return failed(error, '签到失败，请稍后重试');
  }
}

export interface StatusChange {
  matchId: number;
  /** 详情当前版本，作为乐观锁。 */
  version: number;
  status: number;
  reason?: string;
  startAt?: string;
  endAt?: string;
}

export async function changeStatus(input: StatusChange): Promise<WriteOutcome> {
  const { matchId, version, status, reason, startAt, endAt } = input;
  try {
    await changeMatchStatus(matchId, {
      status,
      version,
      manualStatus: true,
      reason,
      startAt,
      endAt,
    });
    return succeeded({ statusOpen: false }, `状态已改为${MATCH_STATUS_TEXT[status] ?? ''}`);
  } catch (error) {
    return failed(error, '状态修改失败，请稍后重试');
  }
}

export interface EventSave {
  matchId: number;
  payload: EventCreate;
  /** 编辑时为事件 id，新建为 null。 */
  eventId: number | null;
  /** 编辑时为事件版本，新建为 null。 */
  version: number | null;
}

export async function saveEvent(input: EventSave): Promise<WriteOutcome> {
  const { matchId, payload, eventId, version } = input;
  try {
    if (eventId !== null && version !== null) {
      await updateMatchEvent(eventId, { ...payload, version });
    } else {
      await createMatchEvent(matchId, payload);
    }
    return succeeded({ eventFormOpen: false }, '事件已保存');
  } catch (error) {
    return failed(error, '事件保存失败，请稍后重试', eventId);
  }
}

/** 冲突重载后按 id 取事件的最新版本；事件已被删除时返回 null。 */
export function refreshedEventVersion(
  events: readonly MatchEvent[],
  eventId: number,
): number | null {
  return events.find((item) => item.id === eventId)?.version ?? null;
}
