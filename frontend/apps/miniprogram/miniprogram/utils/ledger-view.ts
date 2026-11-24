// MP12 账本展示派生：金额正负、流水行与汇总卡片。
import type { LedgerEntry, LedgerSummary } from '../types/api';
import { formatDateTime } from './datetime';

export interface LedgerEntryView {
  id: number;
  categoryName: string;
  description: string;
  timeText: string;
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

export function decorateLedgerEntry(entry: LedgerEntry): LedgerEntryView {
  return {
    id: entry.id,
    categoryName: entry.categoryName,
    description: entry.description,
    timeText: formatDateTime(entry.occurredAt),
    amountText: `${entry.type === 0 ? '+' : '−'}${ledgerMoneyText(entry.amount)}`,
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
