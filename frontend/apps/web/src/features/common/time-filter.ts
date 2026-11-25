/**
 * 列表页共用的时间筛选：月份（YYYY-MM）与自定义日期范围（YYYY-MM-DD，成对）互斥。
 * 解析、归一化与请求参数编码集中在此，赛程与活动列表共用。
 * 原生小程序无法消费 workspace 包，同一口径在小程序 utils/time-filter.ts 另行维护。
 */
export interface TimeFilter {
  /** YYYY-MM；null 表示不按月份筛选。 */
  month: string | null;
  /** YYYY-MM-DD，与 endDate 成对；与 month 互斥。 */
  startDate: string | null;
  endDate: string | null;
}

/** 表单里的原始字符串；空字符串表示不筛选。 */
export interface TimeForm {
  month: string;
  startDate: string;
  endDate: string;
}

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
const SHANGHAI_MONTH = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
});

/** 与后端 DateRanges 一致的时间边界。 */
export const TIME_FILTER_MIN_DATE = new Date(2000, 0, 1);
export const TIME_FILTER_MAX_DATE = new Date(2100, 11, 31);

export function parseMonth(value: string): string | null {
  if (!MONTH_PATTERN.test(value)) {
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
export function parseDate(value: string): string | null {
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

/** 严格解析为本地日历日（用于日历默认值）；非法返回 null。 */
export function toLocalDate(value: string): Date | null {
  const text = parseDate(value);
  if (text === null) {
    return null;
  }
  const [year = 0, month = 1, day = 1] = text.split('-').map(Number);
  return new Date(year, month - 1, day);
}

/** Calendar 返回本地日历日，按本地 getter 格式化，避免 toISOString 在 UTC+8 少一天。 */
export function formatLocalDate(date: Date): string {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

/** 当前年月（Asia/Shanghai），避免浏览器本地时区在月末预填错月。 */
export function currentShanghaiMonth(now = new Date()): string {
  const parts = SHANGHAI_MONTH.formatToParts(now);
  const year = parts.find((part) => part.type === 'year')?.value ?? '';
  const month = parts.find((part) => part.type === 'month')?.value ?? '';
  return `${year}-${month}`;
}

/** 有效日期范围优先；其次月份；非法或缺失一律归一为空。 */
export function normalizeTimeFilter(filters: TimeFilter): TimeFilter {
  const startDate = parseDate(filters.startDate ?? '');
  const endDate = parseDate(filters.endDate ?? '');
  if (startDate !== null && endDate !== null && startDate <= endDate) {
    return { month: null, startDate, endDate };
  }
  return {
    month: filters.month ? parseMonth(filters.month) : null,
    startDate: null,
    endDate: null,
  };
}

/** 请求/URL 的时间参数：范围优先下发 startDate+endDate，否则月份；两者都没有时不带参数。 */
export function timeParamEntries(filters: TimeFilter): Array<[string, string]> {
  const normalized = normalizeTimeFilter(filters);
  if (normalized.startDate !== null && normalized.endDate !== null) {
    return [
      ['startDate', normalized.startDate],
      ['endDate', normalized.endDate],
    ];
  }
  return normalized.month ? [['month', normalized.month]] : [];
}
