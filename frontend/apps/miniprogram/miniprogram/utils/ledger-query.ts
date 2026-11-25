// 账本筛选的唯一编码口径：页面只维护 filter state，key 与请求参数的互转集中在此。
import type { LedgerCategory, LedgerType } from '../types/api';
import { monthDayBounds, normalizeTimeRange, type MonthRange } from './time-filter';
import type { LedgerFilter } from './api-client';

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
}

export const DEFAULT_LEDGER_FILTER: LedgerFilterState = {
  type: '',
  categoryId: 0,
  month: '',
  startDate: '',
  endDate: '',
};

/** key = `type|categoryId|month|startDate|endDate`。 */
export function ledgerFilterKey(state: LedgerFilterState): string {
  return [state.type, state.categoryId, state.month, state.startDate, state.endDate].join('|');
}

export const DEFAULT_LEDGER_KEY = ledgerFilterKey(DEFAULT_LEDGER_FILTER);

export function parseLedgerFilterKey(key: string): LedgerFilterState {
  const [type, categoryId, month, startDate, endDate] = key.split('|');
  return {
    type: type === '' ? '' : (Number(type) as LedgerType),
    categoryId: Number(categoryId) || 0,
    month: month || '',
    startDate: startDate || '',
    endDate: endDate || '',
  };
}

/** 时间条件按 Asia/Shanghai 自然日换算为 ISO-8601 `+08:00`（api-rules §4.6）。 */
export function ledgerFilterQuery(state: LedgerFilterState): LedgerFilter {
  const range = normalizeTimeRange(state);
  const bounds =
    range.startDate && range.endDate
      ? { startDate: range.startDate, endDate: range.endDate }
      : monthDayBounds(range.month ?? '');
  return {
    type: state.type === '' ? undefined : state.type,
    categoryId: state.categoryId || undefined,
    from: bounds ? `${bounds.startDate}T00:00:00+08:00` : undefined,
    to: bounds ? `${bounds.endDate}T23:59:59+08:00` : undefined,
  };
}
