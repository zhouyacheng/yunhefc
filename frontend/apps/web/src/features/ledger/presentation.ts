import type { LedgerCategory, LedgerEntry, LedgerType } from '@yunhefc/shared';
import { LEDGER_TYPE_TEXT, formatAmount, formatMoney, ledgerTypeText } from '@yunhefc/shared';

export { LEDGER_TYPE_TEXT, ledgerTypeText };

export const LEDGER_TYPE_OPTIONS: ReadonlyArray<{ value: LedgerType; label: string }> = (
  [0, 1] as const
).map((value) => ({ value, label: LEDGER_TYPE_TEXT[value] ?? '' }));

/** LED-08 分类选项按方向过滤，通用分类始终保留。 */
export function categoryOptions(
  categories: LedgerCategory[],
  type: LedgerType | null,
): LedgerCategory[] {
  if (type === null) {
    return categories;
  }
  return categories.filter(
    (category) => category.direction === null || category.direction === type,
  );
}

export function categoryLabelMap(categories: LedgerCategory[]): Map<number, string> {
  return new Map(categories.map((category) => [category.id, category.name]));
}

/** 历史流水保留原分类名；分类列表缺失时回退接口返回的 categoryName。 */
export function entryCategoryLabel(
  entry: Pick<LedgerEntry, 'categoryId' | 'categoryName'>,
  labels: Map<number, string>,
): string {
  return labels.get(entry.categoryId) ?? entry.categoryName ?? '—';
}

export { formatMoney };

/** 流水行方向符号，单独渲染以便缩小字号。 */
export function amountSign(entry: Pick<LedgerEntry, 'type'>): string {
  return entry.type === 0 ? '+' : '−';
}

/** 流水行金额数值，不带方向与货币符号。 */
export function formatEntryAmount(entry: Pick<LedgerEntry, 'amount'>): string {
  return formatAmount(entry.amount);
}
