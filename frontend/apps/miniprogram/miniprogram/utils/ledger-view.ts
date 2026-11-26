// MP12 账本展示派生：金额正负、流水行与汇总卡片。
import type { LedgerEntry, LedgerSummary } from '../types/api';
import { formatDateTime } from './datetime';

export interface LedgerEntryView {
  id: number;
  categoryName: string;
  description: string;
  timeText: string;
  amountSign: string;
  amountText: string;
  tone: 'income' | 'expense';
  metaText: string;
}

export interface LedgerSummaryView {
  incomeText: string;
  expenseText: string;
  balanceText: string;
  tone: 'income' | 'expense';
}

/** 负数按 `−¥123.00` 展示，与 Web 一致。 */
export function ledgerMoneyText(value: number): string {
  return value < 0 ? `−¥${Math.abs(value).toFixed(2)}` : `¥${value.toFixed(2)}`;
}

function groupDigits(value: string): string {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** 流水行方向符号，单独渲染以便缩小字号。 */
function ledgerRowAmountSign(entry: Pick<LedgerEntry, 'type'>): string {
  return entry.type === 0 ? '+' : '−';
}

/** 流水行金额数值：与 Web 一致，固定两位小数并千分位，不带方向与货币符号。 */
function ledgerRowAmountText(entry: Pick<LedgerEntry, 'amount'>): string {
  if (!Number.isFinite(entry.amount)) {
    return '—';
  }
  const [integer = '0', fraction = '00'] = Math.abs(entry.amount).toFixed(2).split('.');
  return `${groupDigits(integer)}.${fraction}`;
}

export function decorateLedgerEntry(entry: LedgerEntry): LedgerEntryView {
  return {
    id: entry.id,
    categoryName: entry.categoryName,
    description: entry.description,
    timeText: formatDateTime(entry.occurredAt),
    amountSign: ledgerRowAmountSign(entry),
    amountText: ledgerRowAmountText(entry),
    tone: entry.type === 0 ? 'income' : 'expense',
    metaText: [entry.playerName, entry.activityTitle].filter(Boolean).join(' · '),
  };
}

export function ledgerSummaryView(summary: LedgerSummary): LedgerSummaryView {
  return {
    incomeText: ledgerMoneyText(summary.income),
    expenseText: ledgerMoneyText(summary.expense),
    balanceText: ledgerMoneyText(summary.balance),
    tone: summary.balance >= 0 ? 'income' : 'expense',
  };
}
