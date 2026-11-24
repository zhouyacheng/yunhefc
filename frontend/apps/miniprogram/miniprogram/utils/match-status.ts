// 移植自 Web features/match-detail/status-options.ts 与 shared match.ts 的状态规则。
import { MATCH_STATUS_TEXT } from './presentation';
import { SHANGHAI_OFFSET_MS } from './shanghai';

export interface StatusOption {
  status: number;
  label: string;
  disabled: boolean;
  reason?: string;
}

const ALL_STATUSES = [0, 1, 2, 3, 4, 5, 6];

function matchPostponable(status: number | null | undefined): boolean {
  return status === 0 || status === 1 || status === 2 || status === 3;
}

/** 终态不可取消；已延期可取消。 */
function matchCancellable(status: number | null | undefined): boolean {
  return status === 0 || status === 1 || status === 2 || status === 3 || status === 5;
}

/**
 * MAT-12 状态下拉：展示全部状态，但按后端合法集禁用。
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

export type StatusChoice =
  | { kind: 'ignore' }
  | { kind: 'postpone' }
  | { kind: 'cancel' }
  | { kind: 'change'; status: number };

/** 状态项点击的领域结果：忽略、打开延期/取消表单，或直接切换状态。 */
export function statusChoice(
  options: readonly StatusOption[],
  currentStatus: number,
  index: number,
  cancelStatus = 6,
): StatusChoice {
  const option = options[index];
  if (!option || option.disabled || option.status === currentStatus) {
    return { kind: 'ignore' };
  }
  if (option.status === 5) {
    return { kind: 'postpone' };
  }
  if (option.status === cancelStatus) {
    return { kind: 'cancel' };
  }
  return { kind: 'change', status: option.status };
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** ISO 时刻 → 上海墙钟值（YYYY-MM-DDTHH:mm）。 */
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

/** ISO → [日期, 时间] 墙钟字段；缺失/非法时回退到 fallbackIso（默认当前时间）。 */
export function shanghaiLocalFields(value: string, fallbackIso?: string): [string, string] {
  const local =
    isoToShanghaiLocal(value) || isoToShanghaiLocal(fallbackIso ?? new Date().toISOString());
  const [date, time] = local.split('T');
  return [date, time ?? '00:00'];
}

export interface StatusSheetInitialInput {
  initialStartAt: string;
  initialEndAt: string;
  options: readonly StatusOption[];
  currentStatus: number;
  now?: string;
}

export interface StatusSheetInitial {
  mode: 'list';
  currentLabel: string;
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
}

/** 打开弹层时的初始视图：模式、当前状态文案与延期表单的日期时间。 */
export function statusSheetInitial(input: StatusSheetInitialInput): StatusSheetInitial {
  const [startDate, startTime] = shanghaiLocalFields(input.initialStartAt, input.now);
  const [endDate, endTime] = shanghaiLocalFields(input.initialEndAt, input.now);
  return {
    mode: 'list',
    currentLabel: statusCurrentLabel(input.options, input.currentStatus) ?? '—',
    startDate,
    startTime,
    endDate,
    endTime,
  };
}

/** 当前状态文案；选项未命中时返回 null。 */
export function statusCurrentLabel(
  options: readonly StatusOption[],
  currentStatus: number,
): string | null {
  return options.find((option) => option.status === currentStatus)?.label ?? null;
}

/** 上海墙钟值 → ISO；非法日期（如 2 月 30 日）返回 null。 */
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

const MIN_MATCH_DURATION_MS = 2 * 60 * 60 * 1000;
const MAX_MATCH_DURATION_MS = 4 * 60 * 60 * 1000;
const CANCEL_LOCK_SECONDS = 60 * 60;

/** MAT-04/05/07/11：开始/结束相差 2–4 小时，开赛晚于当天 01:00（Asia/Shanghai）。 */
function matchScheduleError(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string | null {
  if (!startAt || !endAt) {
    return '请选择开始与结束时间';
  }
  const start = Date.parse(startAt);
  const end = Date.parse(endAt);
  if (Number.isNaN(start) || Number.isNaN(end)) {
    return '时间格式不正确';
  }
  const duration = end - start;
  if (duration < MIN_MATCH_DURATION_MS || duration > MAX_MATCH_DURATION_MS) {
    return '开始/结束时间相差必须为 2–4 小时';
  }
  const local = new Date(start + SHANGHAI_OFFSET_MS);
  const seconds = local.getUTCHours() * 3600 + local.getUTCMinutes() * 60 + local.getUTCSeconds();
  return seconds <= CANCEL_LOCK_SECONDS ? '开赛时间必须晚于当天 01:00' : null;
}

export type PostponeSubmission =
  { ok: true; startAt: string; endAt: string } | { ok: false; error: string };

function postponeInstants(startLocal: string, endLocal: string): PostponeSubmission {
  const startAt = shanghaiLocalToIso(startLocal);
  const endAt = shanghaiLocalToIso(endLocal);
  if (!startAt || !endAt) {
    return { ok: false, error: '请选择开始与结束时间' };
  }
  const error = matchScheduleError(startAt, endAt);
  return error ? { ok: false, error } : { ok: true, startAt, endAt };
}

/** 延期弹窗校验：2–4 小时、开赛晚于当天 01:00（Asia/Shanghai）。 */
export function postponeFormError(startLocal: string, endLocal: string): string | null {
  const result = postponeInstants(startLocal, endLocal);
  return result.ok ? null : result.error;
}

export interface PostponeFields {
  startDate: string;
  startTime: string;
  endDate: string;
  endTime: string;
}

/** 延期表单的日期时间字段 → 提交时刻；校验失败返回文案。 */
export function buildPostponeSubmission(fields: PostponeFields): PostponeSubmission {
  return postponeInstants(
    `${fields.startDate}T${fields.startTime}`,
    `${fields.endDate}T${fields.endTime}`,
  );
}

/** 取消原因校验：非空、不超过 maxLength 字（比赛 200，活动 255）。 */
export function cancelReasonError(reason: string, maxLength = 200): string | null {
  const trimmed = reason.trim();
  if (!trimmed) {
    return '请输入取消原因';
  }
  if (trimmed.length > maxLength) {
    return `取消原因不能超过 ${maxLength} 字`;
  }
  return null;
}

export type CancelSubmission = { ok: true; reason: string } | { ok: false; error: string };

/** 取消表单 → 提交原因；校验失败返回文案。 */
export function buildCancelSubmission(reason: string, maxLength = 200): CancelSubmission {
  const message = cancelReasonError(reason, maxLength);
  if (message) {
    return { ok: false, error: message };
  }
  return { ok: true, reason: reason.trim() };
}
