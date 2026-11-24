import type { LedgerCategory, LedgerEntry, LedgerType } from '@yunhefc/shared';

export const LEDGER_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '收入',
  1: '支出',
};

export const LEDGER_TYPE_OPTIONS: ReadonlyArray<{ value: LedgerType; label: string }> = (
  [0, 1] as const
).map((value) => ({ value, label: LEDGER_TYPE_TEXT[value] ?? '' }));

export function ledgerTypeText(type: LedgerType): string {
  return LEDGER_TYPE_TEXT[type] ?? '—';
}

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

function groupDigits(value: string): string {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

export function formatMoney(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const sign = value < 0 ? '−' : '';
  const [integer = '0', fraction = '00'] = Math.abs(value).toFixed(2).split('.');
  return `${sign}¥${groupDigits(integer)}.${fraction}`;
}

export function formatSignedAmount(entry: Pick<LedgerEntry, 'type' | 'amount'>): string {
  return `${entry.type === 0 ? '+' : '−'}${formatMoney(Math.abs(entry.amount))}`;
}
