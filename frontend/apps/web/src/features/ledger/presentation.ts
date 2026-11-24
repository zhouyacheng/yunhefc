import type { LedgerCategory, LedgerEntry, LedgerType } from '@yunhefc/shared';
import { LEDGER_TYPE_TEXT, formatMoney, ledgerTypeText } from '@yunhefc/shared';

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

export function formatSignedAmount(entry: Pick<LedgerEntry, 'type' | 'amount'>): string {
  return `${entry.type === 0 ? '+' : '−'}${formatMoney(Math.abs(entry.amount))}`;
}
