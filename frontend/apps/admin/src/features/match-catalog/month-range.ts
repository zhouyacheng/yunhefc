const SEASON_PATTERN = /^\d{4}$/;
const MONTH_VALUE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

export interface MonthParts {
  start: number;
  end: number;
}

/** 从 [YYYY-MM, YYYY-MM] 解析月号；非法输入返回 null。 */
export function parseMonthRange(range: [string, string] | null): MonthParts | null {
  const startValue = range?.[0];
  const endValue = range?.[1];
  if (
    !startValue ||
    !endValue ||
    !MONTH_VALUE_PATTERN.test(startValue) ||
    !MONTH_VALUE_PATTERN.test(endValue)
  ) {
    return null;
  }
  const start = Number(startValue.slice(5));
  const end = Number(endValue.slice(5));
  return { start: Math.min(start, end), end: Math.max(start, end) };
}

/** 用赛季年份与两个 1–12 月号构建范围；反向区间自动升序。 */
export function buildMonthRange(
  season: string,
  first: number,
  second: number,
): [string, string] | null {
  if (!SEASON_PATTERN.test(season)) {
    return null;
  }
  if (
    !Number.isInteger(first) ||
    !Number.isInteger(second) ||
    first < 1 ||
    first > 12 ||
    second < 1 ||
    second > 12
  ) {
    return null;
  }
  const start = Math.min(first, second);
  const end = Math.max(first, second);
  const pad = (month: number): string => String(month).padStart(2, '0');
  return [`${season}-${pad(start)}`, `${season}-${pad(end)}`];
}

/** 只保留月号、替换年份；空赛季或非法范围返回 null。 */
export function remapMonthRange(
  range: [string, string] | null,
  season: string,
): [string, string] | null {
  const parts = parseMonthRange(range);
  if (!parts) {
    return null;
  }
  return buildMonthRange(season, parts.start, parts.end);
}

export function monthText(month: number): string {
  return Number.isInteger(month) && month >= 1 && month <= 12 ? `${month}月` : '';
}

/** 空范围 -> ''；单月 -> '3月'；跨月 -> '1月 至 3月'。 */
export function formatMonthRange(range: [string, string] | null): string {
  const parts = parseMonthRange(range);
  if (!parts) {
    return '';
  }
  return parts.start === parts.end
    ? monthText(parts.start)
    : `${monthText(parts.start)} 至 ${monthText(parts.end)}`;
}

export function isSingleMonth(range: [string, string] | null): boolean {
  const parts = parseMonthRange(range);
  return parts !== null && parts.start === parts.end;
}
