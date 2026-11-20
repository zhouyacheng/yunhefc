// 比赛列表筛选模型：与 Web frontend/apps/web/src/features/schedule/query.ts 口径一致；
// 小程序没有 URL，因此只保留表单/筛选/请求串，不移植 URL 解析与 month=all。
import { currentShanghaiMonth } from './datetime';
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from './presentation';

export const DEFAULT_PAGE_SIZE = 10;

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
const TYPE_CODES = new Set(Object.keys(MATCH_TYPE_TEXT).map(Number));
const STATUS_CODES = new Set(Object.keys(MATCH_STATUS_TEXT).map(Number));

/**
 * 小程序没有 URL 持久化：默认态由 `emptyScheduleFilters` + `toScheduleForm` 表达，
 * 「明确不限月份」由表单空值表达，因此不移植 Web 的 `monthAll` 哨兵。
 */
export interface ScheduleFilters {
  type: number | null;
  venue: string;
  status: number | null;
  /** YYYY-MM；null 表示不按月份筛选。 */
  month: string | null;
  /** YYYY-MM-DD，与 endDate 成对。 */
  startDate: string | null;
  endDate: string | null;
}

/** 表单里的原始字符串；空字符串表示不筛选。 */
export interface ScheduleForm {
  type: string;
  venue: string;
  status: string;
  month: string;
  startDate: string;
  endDate: string;
}

export interface SelectOption {
  value: string;
  label: string;
}

export const TYPE_OPTIONS: readonly SelectOption[] = [
  { value: '', label: '全部类型' },
  ...Object.entries(MATCH_TYPE_TEXT).map(([value, label]) => ({ value, label })),
];

export const STATUS_OPTIONS: readonly SelectOption[] = [
  { value: '', label: '全部状态' },
  ...Object.entries(MATCH_STATUS_TEXT).map(([value, label]) => ({ value, label })),
];

export function emptyScheduleFilters(): ScheduleFilters {
  return {
    type: null,
    venue: '',
    status: null,
    month: null,
    startDate: null,
    endDate: null,
  };
}

/** 严格校验 YYYY-MM-DD，并用 UTC 回读拒绝 2026-02-30 之类的非法日期。 */
function parseDate(value: string): string | null {
  if (!DATE_PATTERN.test(value)) {
    return null;
  }
  const [year = 0, month = 0, day = 0] = value.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
    ? value
    : null;
}

function parseCode(value: string, allowed: ReadonlySet<number>): number | null {
  if (!/^\d+$/.test(value)) {
    return null;
  }
  const code = Number(value);
  return allowed.has(code) ? code : null;
}

function parseMonth(value: string): string | null {
  return MONTH_PATTERN.test(value) ? value : null;
}

/** 有效日期范围优先于月份；两者互斥；venue trim。 */
function normalize(filters: ScheduleFilters): ScheduleFilters {
  const startDate = parseDate(filters.startDate ?? '');
  const endDate = parseDate(filters.endDate ?? '');
  const hasRange = startDate !== null && endDate !== null && startDate <= endDate;
  if (hasRange) {
    return {
      type: filters.type,
      venue: filters.venue.trim(),
      status: filters.status,
      month: null,
      startDate,
      endDate,
    };
  }
  return {
    type: filters.type,
    venue: filters.venue.trim(),
    status: filters.status,
    month: filters.month ? parseMonth(filters.month) : null,
    startDate: null,
    endDate: null,
  };
}

/** 默认态预填当前上海月份，其余为空；首屏由 emptyScheduleFilters 发起查询。 */
export function toScheduleForm(
  filters: ScheduleFilters,
  now: number | Date = Date.now(),
): ScheduleForm {
  return {
    type: filters.type === null ? '' : String(filters.type),
    venue: filters.venue,
    status: filters.status === null ? '' : String(filters.status),
    month: filters.month ?? (filters.startDate !== null ? '' : currentShanghaiMonth(now)),
    startDate: filters.startDate ?? '',
    endDate: filters.endDate ?? '',
  };
}

export function fromScheduleForm(form: ScheduleForm): ScheduleFilters {
  return normalize({
    type: parseCode(form.type, TYPE_CODES),
    venue: form.venue,
    status: parseCode(form.status, STATUS_CODES),
    month: form.month ? parseMonth(form.month) : null,
    startDate: form.startDate.trim() ? form.startDate.trim() : null,
    endDate: form.endDate.trim() ? form.endDate.trim() : null,
  });
}

/** 稳定、已编码的筛选串，作为 PagedFeed 的 key；不含分页。 */
export function scheduleFeedKey(filters: ScheduleFilters): string {
  const normalized = normalize(filters);
  const entries: string[] = [];
  if (normalized.type !== null) {
    entries.push(`type=${normalized.type}`);
  }
  if (normalized.venue) {
    entries.push(`venue=${encodeURIComponent(normalized.venue)}`);
  }
  if (normalized.status !== null) {
    entries.push(`status=${normalized.status}`);
  }
  if (normalized.startDate !== null && normalized.endDate !== null) {
    entries.push(`startDate=${normalized.startDate}`, `endDate=${normalized.endDate}`);
  } else if (normalized.month) {
    entries.push(`month=${normalized.month}`);
  }
  return entries.join('&');
}

export function sameForm(a: ScheduleForm, b: ScheduleForm): boolean {
  return (
    a.type === b.type &&
    a.venue === b.venue &&
    a.status === b.status &&
    a.month === b.month &&
    a.startDate === b.startDate &&
    a.endDate === b.endDate
  );
}
