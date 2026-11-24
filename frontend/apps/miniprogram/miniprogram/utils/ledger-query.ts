// 账本筛选的唯一编码口径：页面只维护 filter state，key 与请求参数的互转集中在此。
import type { LedgerCategory, LedgerType } from '../types/api';
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

export interface LedgerFilterState {
  type: LedgerType | '';
  categoryId: number;
  /** 自然日 `YYYY-MM-DD`；空串表示不限。 */
  fromDate: string;
  toDate: string;
}

export const DEFAULT_LEDGER_FILTER: LedgerFilterState = {
  type: '',
  categoryId: 0,
  fromDate: '',
  toDate: '',
};

/** key = `type|categoryId|fromDate|toDate`。 */
export function ledgerFilterKey(state: LedgerFilterState): string {
  return [state.type, state.categoryId, state.fromDate, state.toDate].join('|');
}

export const DEFAULT_LEDGER_KEY = ledgerFilterKey(DEFAULT_LEDGER_FILTER);

export function parseLedgerFilterKey(key: string): LedgerFilterState {
  const [type, categoryId, fromDate, toDate] = key.split('|');
  return {
    type: type === '' ? '' : (Number(type) as LedgerType),
    categoryId: Number(categoryId) || 0,
    fromDate: fromDate || '',
    toDate: toDate || '',
  };
}

/** 区间按 Asia/Shanghai 自然日换算为 ISO-8601 `+08:00`（api-rules §4.6）。 */
export function ledgerFilterQuery(state: LedgerFilterState): LedgerFilter {
  return {
    type: state.type === '' ? undefined : state.type,
    categoryId: state.categoryId || undefined,
    from: state.fromDate ? `${state.fromDate}T00:00:00+08:00` : undefined,
    to: state.toDate ? `${state.toDate}T23:59:59+08:00` : undefined,
  };
}
