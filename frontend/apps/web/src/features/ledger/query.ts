import type { QueryParams } from '@yunhefc/api-client';
import type { LedgerType } from '@yunhefc/shared';

export const DEFAULT_LEDGER_PAGE_SIZE = 10;
export const LEDGER_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

export interface LedgerFilters {
  type: LedgerType | null;
  categoryId: number | null;
  /** YYYY-MM-DD，含当天 00:00:00。 */
  from: string;
  /** YYYY-MM-DD，含当天 23:59:59。 */
  to: string;
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

export function ledgerRangeError(filters: Pick<LedgerFilters, 'from' | 'to'>): string {
  if (filters.from && filters.to && filters.from > filters.to) {
    return '开始日期不能晚于结束日期';
  }
  return '';
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
  const from = ledgerRangeStart(query.from);
  const to = ledgerRangeEnd(query.to);
  if (from) {
    params.from = from;
  }
  if (to) {
    params.to = to;
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
