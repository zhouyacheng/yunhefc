// Web 端账本展示派生；小程序 utils/ledger-view.ts 与 prototype 的账本页保持同口径。
import type { LedgerCategory, LedgerEntry, LedgerSummary, LedgerType } from '@yunhefc/shared';
import { LEDGER_TYPE_TEXT, formatAmount, formatMoney, formatYearDate } from '@yunhefc/shared';

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

/** 摘要可为空（P-136），空摘要回退分类名，避免行首留白。 */
export function entryTitle(
  entry: Pick<LedgerEntry, 'description' | 'categoryId' | 'categoryName'>,
  labels: Map<number, string>,
): string {
  return entry.description.trim() || entryCategoryLabel(entry, labels);
}

/** 流水行元信息：分类 · 日期 · 归属球员/活动；摘要回退分类时不重复分类。 */
export function entryMetaText(
  entry: Pick<
    LedgerEntry,
    'description' | 'categoryId' | 'categoryName' | 'occurredAt' | 'playerName' | 'activityTitle'
  >,
  labels: Map<number, string>,
): string {
  const parts = entry.description.trim() ? [entryCategoryLabel(entry, labels)] : [];
  parts.push(formatYearDate(entry.occurredAt));
  if (entry.playerName) {
    parts.push(`归属 ${entry.playerName}`);
  }
  if (entry.activityTitle) {
    parts.push(entry.activityTitle);
  }
  return parts.join(' · ');
}

export interface LedgerFlowView {
  /** 支出占收入的进度（0–100）；无支出时为 null，隐藏条形与文案。 */
  ratioPercent: number | null;
  /** 比例说明；无支出时为空。 */
  ratioLabel: string;
}

/** 汇总卡的收支比：以收入为 100%，超过收入时条形填满并改用超出文案。 */
export function ledgerFlow(summary: Pick<LedgerSummary, 'income' | 'expense'>): LedgerFlowView {
  const { income, expense } = summary;
  if (expense <= 0) {
    return { ratioPercent: null, ratioLabel: '' };
  }
  if (income <= 0) {
    return { ratioPercent: 100, ratioLabel: '暂无收入' };
  }
  const ratio = Math.round((expense / income) * 100);
  return {
    ratioPercent: Math.min(100, ratio),
    ratioLabel: ratio > 100 ? `支出超过收入 ${ratio - 100}%` : `支出占收入 ${ratio}%`,
  };
}
