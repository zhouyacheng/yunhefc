import type { MatchStatus } from '@yunhefc/shared';
import {
  MATCH_STATUS_TEXT,
  matchCancellable,
  matchPostponable,
  matchScheduleError,
} from '@yunhefc/shared';

export interface StatusOption {
  status: MatchStatus;
  label: string;
  disabled: boolean;
  reason: string;
}

const ALL_STATUSES: MatchStatus[] = [0, 1, 2, 3, 4, 5, 6];

/**
 * MAT-12 下拉：展示全部状态，但按后端合法集禁用。
 * 0–4 恒可（含从终态恢复）；5 仅 0–3 且有编辑权限；6 在 4/6 禁用。
 */
export function statusOptions(input: {
  currentStatus: number;
  canEditAny: boolean;
}): StatusOption[] {
  return ALL_STATUSES.map((status) => {
    const label = MATCH_STATUS_TEXT[status];
    if (status === 5) {
      if (!input.canEditAny) {
        return { status, label, disabled: true, reason: '需要编辑比赛权限' };
      }
      if (!matchPostponable(input.currentStatus)) {
        return { status, label, disabled: true, reason: '当前状态不可延期' };
      }
      return { status, label, disabled: false, reason: '' };
    }
    if (status === 6) {
      if (!matchCancellable(input.currentStatus)) {
        return { status, label, disabled: true, reason: '已结束或已取消的比赛不可取消' };
      }
      return { status, label, disabled: false, reason: '' };
    }
    return { status, label, disabled: false, reason: '' };
  });
}

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** ISO 时刻 → datetime-local 的上海墙钟值（YYYY-MM-DDTHH:mm）。 */
export function isoToShanghaiLocal(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const instant = Date.parse(value);
  if (Number.isNaN(instant)) {
    return '';
  }
  const local = new Date(instant + SHANGHAI_OFFSET_MS);
  return `${local.getUTCFullYear()}-${pad(local.getUTCMonth() + 1)}-${pad(local.getUTCDate())}T${pad(local.getUTCHours())}:${pad(local.getUTCMinutes())}`;
}

/**
 * datetime-local 的上海墙钟值 → ISO。按 Asia/Shanghai 解释，不依赖浏览器时区；
 * 非法日期（如 2 月 30 日）返回 null。
 */
export function shanghaiLocalToIso(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value);
  if (!match) {
    return null;
  }
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  const hour = Number(match[4]);
  const minute = Number(match[5]);
  const second = Number(match[6] ?? 0);
  if (month < 1 || month > 12 || day < 1 || day > 31 || hour > 23 || minute > 59 || second > 59) {
    return null;
  }
  const instant = Date.UTC(year, month - 1, day, hour - 8, minute, second);
  const check = new Date(instant + SHANGHAI_OFFSET_MS);
  if (
    check.getUTCFullYear() !== year ||
    check.getUTCMonth() !== month - 1 ||
    check.getUTCDate() !== day ||
    check.getUTCHours() !== hour ||
    check.getUTCMinutes() !== minute
  ) {
    return null;
  }
  return new Date(instant).toISOString();
}

/** 延期弹窗校验：2–4 小时、开赛晚于当天 10:00（Asia/Shanghai）。 */
export function postponeFormError(startLocal: string, endLocal: string): string | null {
  const startAt = shanghaiLocalToIso(startLocal);
  const endAt = shanghaiLocalToIso(endLocal);
  if (!startAt || !endAt) {
    return '请选择开始与结束时间';
  }
  return matchScheduleError(startAt, endAt);
}

/** 取消原因校验：非空、≤200 字。 */
export function cancelReasonError(reason: string): string | null {
  const trimmed = reason.trim();
  if (!trimmed) {
    return '请输入取消原因';
  }
  if (trimmed.length > 200) {
    return '取消原因不能超过 200 字';
  }
  return null;
}
