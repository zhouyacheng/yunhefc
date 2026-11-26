// 账本筛选的唯一编码口径：页面只维护 filter state，key 与请求参数的互转集中在此。
import type { LedgerCategory, LedgerPeriod, LedgerType } from '../types/api';
import { monthDayBounds, normalizeTimeRange, type MonthRange } from './time-filter';
import type { LedgerFilter, LedgerStatisticsQuery } from './api-client';

export interface LedgerCategoryOption {
  value: number;
  label: string;
}

export const ALL_CATEGORIES_OPTION: LedgerCategoryOption = { value: 0, label: '全部分类' };

/** 分类下拉：按方向收窄；通用分类（direction=null）始终保留。 */
export function categoryOptionsFor(
  categories: readonly LedgerCategory[],
  type: LedgerType | '',
): LedgerCategoryOption[] {
  const matched = categories.filter(
    (category) => type === '' || category.direction === null || category.direction === type,
  );
  return [ALL_CATEGORIES_OPTION, ...matched.map(({ id, name }) => ({ value: id, label: name }))];
}

/** 时间条件与活动列表同口径：月份（YYYY-MM）与自定义日期范围互斥，空串表示不筛选。 */
export interface LedgerFilterState extends MonthRange {
  type: LedgerType | '';
  categoryId: number;
  keyword: string;
}

export const DEFAULT_LEDGER_FILTER: LedgerFilterState = {
  type: '',
  categoryId: 0,
  month: '',
  startDate: '',
  endDate: '',
  keyword: '',
};

/** key = `type|categoryId|month|startDate|endDate|keyword`，关键字转义后拼接。 */
export function ledgerFilterKey(state: LedgerFilterState): string {
  return [
    state.type,
    state.categoryId,
    state.month,
    state.startDate,
    state.endDate,
    encodeURIComponent(state.keyword),
  ].join('|');
}

export const DEFAULT_LEDGER_KEY = ledgerFilterKey(DEFAULT_LEDGER_FILTER);

export function parseLedgerFilterKey(key: string): LedgerFilterState {
  const [type, categoryId, month, startDate, endDate, keyword = ''] = key.split('|');
  let decodedKeyword: string;
  try {
    decodedKeyword = decodeURIComponent(keyword);
  } catch {
    decodedKeyword = keyword;
  }
  return {
    type: type === '' ? '' : (Number(type) as LedgerType),
    categoryId: Number(categoryId) || 0,
    month: month || '',
    startDate: startDate || '',
    endDate: endDate || '',
    keyword: decodedKeyword,
  };
}

/** 时间条件按 Asia/Shanghai 自然日换算为 ISO-8601 `+08:00`（api-rules §4.6）。 */
export function ledgerFilterQuery(
  state: LedgerFilterState,
  options: { includeKeyword?: boolean } = {},
): LedgerFilter {
  const range = normalizeTimeRange(state);
  const bounds =
    range.startDate && range.endDate
      ? { startDate: range.startDate, endDate: range.endDate }
      : monthDayBounds(range.month ?? '');
  const query: LedgerFilter = {
    type: state.type === '' ? undefined : state.type,
    categoryId: state.categoryId || undefined,
    from: bounds ? `${bounds.startDate}T00:00:00+08:00` : undefined,
    to: bounds ? `${bounds.endDate}T23:59:59+08:00` : undefined,
  };
  if (options.includeKeyword !== false && state.keyword) {
    query.keyword = state.keyword;
  }
  return query;
}

/** LED-02 汇总不接受关键字，避免把摘要搜索混进汇总口径。 */
export function ledgerSummaryQuery(state: LedgerFilterState): LedgerFilter {
  return ledgerFilterQuery(state, { includeKeyword: false });
}

/** 汇总是否处于额外筛选；月份是页面基准、关键字只作用于明细，均不算「筛选合计」。 */
export function ledgerIsFiltered(state: LedgerFilterState): boolean {
  return (
    state.type !== '' || state.categoryId !== 0 || (state.startDate !== '' && state.endDate !== '')
  );
}

/**
 * LED-14 周期前后切换：周按 7 天；月/年固定取 15 日，避免 29–31 日跨月溢出，
 * 同时保证切到周时落在目标月内。自定义周期不可前后切换，原样返回。
 */
export function shiftStatsAnchor(anchor: string, period: LedgerPeriod, delta: number): string {
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

/** LED-14 请求参数：week/month/year 用 anchor，custom 用 from/to。 */
export function ledgerStatisticsQuery(params: {
  period: LedgerPeriod;
  anchor: string;
  from: string;
  to: string;
}): LedgerStatisticsQuery {
  if (params.period === 'custom') {
    return { period: 'custom', from: params.from, to: params.to };
  }
  return { period: params.period, anchor: params.anchor };
}
