// 比赛筛选模型：表单无关的筛选值、归一化与请求串编码。
// 传输层（api-client）与表单层（schedule-query）都依赖这里，避免二者反向依赖。
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

export const DEFAULT_PAGE_SIZE = 10;

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

function parseMonth(value: string): string | null {
  return MONTH_PATTERN.test(value) ? value : null;
}

/** 有效日期范围优先于月份；两者互斥；venue trim。 */
export function normalize(filters: ScheduleFilters): ScheduleFilters {
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

/** 稳定、已编码的筛选串，不含分页；同一串同时用作 PagedFeed 的 key。 */
export function filterQueryString(filters: ScheduleFilters): string {
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
