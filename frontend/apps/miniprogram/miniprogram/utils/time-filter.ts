// 列表页共用的时间筛选：月份（YYYY-MM）与自定义日期范围（YYYY-MM-DD，成对）互斥。
// 赛程与活动列表共用解析、归一化、参数编码与表单草稿转换，避免两套口径漂移。
// 原生小程序无法消费 workspace 包，本文件与 Web features/common/time-filter.ts 保持同口径。
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

/** 与后端 DateRanges 一致的时间边界；用于原生 picker 的 start/end。 */
export const TIME_FILTER_MIN = '2000-01-01';
export const TIME_FILTER_MAX = '2100-12-31';

/** 表单/生效态均可使用：空字符串表示不筛选。 */
export interface MonthRange {
  month: string;
  startDate: string;
  endDate: string;
}

/** 赛程筛选沿用 null 表示不筛选。 */
export interface NullableMonthRange {
  month: string | null;
  startDate: string | null;
  endDate: string | null;
}

function parseMonth(value: string | null): string | null {
  if (!value || !MONTH_PATTERN.test(value)) {
    return null;
  }
  const year = Number(value.slice(0, 4));
  return year >= 2000 && year <= 2100 ? value : null;
}

/** 月份换算为自然日区间（含首末日）；非法月份返回 null。 */
export function monthDayBounds(month: string): { startDate: string; endDate: string } | null {
  const parsed = parseMonth(month);
  if (parsed === null) {
    return null;
  }
  const [year = 0, monthOfYear = 1] = parsed.split('-').map(Number);
  // 下个月 0 日即本月最后一天，避免闰年与月份天数表。
  const lastDay = new Date(Date.UTC(year, monthOfYear, 0)).getUTCDate();
  return {
    startDate: `${parsed}-01`,
    endDate: `${parsed}-${String(lastDay).padStart(2, '0')}`,
  };
}

/** 严格校验 YYYY-MM-DD，并用 UTC 回读拒绝 2026-02-30 之类的非法日期。 */
function parseDate(value: string | null): string | null {
  if (!value || !DATE_PATTERN.test(value)) {
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

/** 有效日期范围优先于月份；两者互斥，非法值回落为空。 */
export function normalizeTimeRange(range: NullableMonthRange): NullableMonthRange {
  const startDate = parseDate(range.startDate);
  const endDate = parseDate(range.endDate);
  if (startDate !== null && endDate !== null && startDate <= endDate) {
    return { month: null, startDate, endDate };
  }
  return { month: parseMonth(range.month), startDate: null, endDate: null };
}

/** ACT-01/MAT-02 时间参数：范围优先，其次月份，均无则不下发。 */
export function timeRangeParams(range: NullableMonthRange): {
  month?: string;
  startDate?: string;
  endDate?: string;
} {
  const normalized = normalizeTimeRange(range);
  if (normalized.startDate && normalized.endDate) {
    return { startDate: normalized.startDate, endDate: normalized.endDate };
  }
  return normalized.month ? { month: normalized.month } : {};
}

/** 稳定、已编码的时间筛选串，用于 PagedFeed key。 */
export function timeRangeEntries(range: NullableMonthRange): string[] {
  return Object.entries(timeRangeParams(range)).map(([key, value]) => `${key}=${value}`);
}

/** 选择月份时清空日期范围。 */
export function patchMonth(month: string): MonthRange {
  return { month, startDate: '', endDate: '' };
}

/** 选择日期范围时清空月份。 */
export function patchRange(startDate: string, endDate: string): MonthRange {
  return { month: '', startDate, endDate };
}

/** 日期范围确认前的校验；通过返回 null。 */
export function rangeError(startDate: string, endDate: string): string | null {
  if (!startDate || !endDate) {
    return '请选择开始和结束日期';
  }
  if (startDate > endDate) {
    return '开始日期不能晚于结束日期';
  }
  return null;
}
