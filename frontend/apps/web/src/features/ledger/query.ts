import type { QueryParams } from '@yunhefc/api-client';
import type { LedgerType } from '@yunhefc/shared';

import { monthDayBounds, normalizeTimeFilter, type TimeForm } from '../common/time-filter';

export const DEFAULT_LEDGER_PAGE_SIZE = 10;
export const LEDGER_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

/** 时间条件与活动列表同口径：月份与自定义日期范围互斥，均未选时不筛选。 */
export interface LedgerFilters extends TimeForm {
  type: LedgerType | null;
  categoryId: number | null;
}

export interface LedgerQuery extends LedgerFilters {
  page: number;
  pageSize: number;
}

const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;

export function parseLedgerType(value: string): LedgerType | null {
  return value === '0' ? 0 : value === '1' ? 1 : null;
}

export function parseLedgerDate(value: string): string | null {
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

/** 后端 from/to 接收 ISO-8601；按 Asia/Shanghai 自然日展开为当天边界。 */
export function ledgerRangeStart(date: string): string | null {
  return parseLedgerDate(date) ? `${date}T00:00:00+08:00` : null;
}

export function ledgerRangeEnd(date: string): string | null {
  return parseLedgerDate(date) ? `${date}T23:59:59+08:00` : null;
}

/** 生效的时间区间：自定义日期优先，其次月份；倒置或非法一律不生效。 */
export function ledgerDayRange(
  filters: Pick<LedgerFilters, 'month' | 'startDate' | 'endDate'>,
): { from: string; to: string } | null {
  const normalized = normalizeTimeFilter(filters);
  if (normalized.startDate !== null && normalized.endDate !== null) {
    return { from: normalized.startDate, to: normalized.endDate };
  }
  const bounds = monthDayBounds(normalized.month ?? '');
  return bounds ? { from: bounds.startDate, to: bounds.endDate } : null;
}

/** LED-01~03/07 共用的筛选参数；不含分页。 */
export function toLedgerFilter(query: LedgerFilters): QueryParams {
  const params: QueryParams = {};
  if (query.type !== null) {
    params.type = query.type;
  }
  if (query.categoryId !== null) {
    params.categoryId = query.categoryId;
  }
  const range = ledgerDayRange(query);
  if (range) {
    const from = ledgerRangeStart(range.from);
    const to = ledgerRangeEnd(range.to);
    if (from) {
      params.from = from;
    }
    if (to) {
      params.to = to;
    }
  }
  return params;
}

/** LED-01 列表请求：筛选 + 分页。 */
export function toLedgerRequest(query: LedgerQuery): QueryParams {
  return {
    ...toLedgerFilter(query),
    page: query.page,
    pageSize: query.pageSize,
  };
}
