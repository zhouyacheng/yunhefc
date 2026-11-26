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

/** 关键字非空时追加到请求；列表与日小计共用。 */
function withKeyword(params: QueryParams, keyword: string | undefined): QueryParams {
  const trimmed = keyword?.trim();
  if (trimmed) {
    params.keyword = trimmed;
  }
  return params;
}

/** LED-01 列表请求：筛选 + 关键字 + 分页。 */
export function toLedgerRequest(query: LedgerQuery & { keyword?: string }): QueryParams {
  return withKeyword(
    {
      ...toLedgerFilter(query),
      page: query.page,
      pageSize: query.pageSize,
    },
    query.keyword,
  );
}

/** LED-13 日小计请求：与列表同口径，并带关键字。 */
export function toDailyTotalRequest(query: LedgerFilters, keyword: string): QueryParams {
  return withKeyword(toLedgerFilter(query), keyword);
}

/** 月份前后切换；越界返回原值。 */
export function shiftMonth(month: string, delta: number): string {
  const [year = 0, monthOfYear = 1] = month.split('-').map(Number);
  const shifted = new Date(Date.UTC(year, monthOfYear - 1 + delta, 1));
  const nextYear = shifted.getUTCFullYear();
  if (nextYear < 2000 || nextYear > 2100) {
    return month;
  }
  return `${nextYear}-${String(shifted.getUTCMonth() + 1).padStart(2, '0')}`;
}

export type LedgerStatsPeriod = 'week' | 'month' | 'year' | 'custom';

/**
 * LED-14 周期前后切换：周按 7 天；月/年固定取 15 日，避免 29–31 日跨月溢出，
 * 同时保证切到周时落在目标月内。自定义周期不可前后切换，原样返回。
 */
export function shiftStatsAnchor(anchor: string, period: LedgerStatsPeriod, delta: number): string {
  if (period === 'custom') {
    return anchor;
  }
  const [year = 0, month = 1, day = 1] = anchor.split('-').map(Number);
  let date: Date;
  if (period === 'week') {
    date = new Date(Date.UTC(year, month - 1, day));
    date.setUTCDate(date.getUTCDate() + delta * 7);
  } else if (period === 'month') {
    date = new Date(Date.UTC(year, month - 1 + delta, 15));
  } else {
    date = new Date(Date.UTC(year + delta, month - 1, 15));
  }
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}-${String(
    date.getUTCDate(),
  ).padStart(2, '0')}`;
}

/** LED-14 请求：week/month/year 用 anchor，custom 用 from/to。 */
export function toStatisticsRequest(query: {
  period: LedgerStatsPeriod;
  anchor: string;
  from: string;
  to: string;
}): QueryParams {
  if (query.period === 'custom') {
    return { period: 'custom', from: query.from, to: query.to };
  }
  return { period: query.period, anchor: query.anchor };
}
