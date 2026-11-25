// 比赛筛选模型：表单无关的筛选值、归一化与请求串编码。
// 传输层（api-client）与表单层（schedule-query）都依赖这里，避免二者反向依赖。
import { normalizeTimeRange, timeRangeEntries } from './time-filter';
import type { NullableMonthRange } from './time-filter';

export const DEFAULT_PAGE_SIZE = 10;

export interface ScheduleFilters extends NullableMonthRange {
  type: number | null;
  venue: string;
  status: number | null;
}

/** 有效日期范围优先于月份；两者互斥；venue trim。 */
export function normalize(filters: ScheduleFilters): ScheduleFilters {
  return {
    ...filters,
    venue: filters.venue.trim(),
    ...normalizeTimeRange(filters),
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
  entries.push(...timeRangeEntries(normalized));
  return entries.join('&');
}
