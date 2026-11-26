// MP12 账本展示派生：金额正负、流水行与汇总卡片。与 Web features/ledger/presentation.ts 保持一致。
import type { LedgerEntry, LedgerSummary } from '../types/api';
import { formatYearDate } from './datetime';

export interface LedgerEntryView {
  id: number;
  title: string;
  metaText: string;
  amountSign: string;
  amountText: string;
  tone: 'income' | 'expense';
}

export interface LedgerSummaryView {
  incomeText: string;
  expenseText: string;
  balanceText: string;
  /** 余额不是收支方向：正数为常规墨色，负数标红。 */
  balanceTone: 'default' | 'negative';
  /** 支出占收入的进度（0–100）；无支出时为 null，隐藏条形。 */
  ratioPercent: number | null;
  ratioLabel: string;
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
  const description = entry.description.trim();
  const parts = description ? [entry.categoryName] : [];
  parts.push(formatYearDate(entry.occurredAt));
  if (entry.playerName) {
    parts.push(`归属 ${entry.playerName}`);
  }
  if (entry.activityTitle) {
    parts.push(entry.activityTitle);
  }
  return {
    id: entry.id,
    // 摘要可为空（P-136）：回退分类名，避免行首留白。
    title: description || entry.categoryName || '—',
    metaText: parts.join(' · '),
    amountSign: ledgerRowAmountSign(entry),
    amountText: ledgerRowAmountText(entry),
    tone: entry.type === 0 ? 'income' : 'expense',
  };
}

/** 汇总卡的收支比：以收入为 100%，超过收入时条形填满并改用超出文案。 */
export function ledgerFlow(
  summary: Pick<LedgerSummary, 'income' | 'expense'>,
): Pick<LedgerSummaryView, 'ratioPercent' | 'ratioLabel'> {
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

export function ledgerSummaryView(summary: LedgerSummary): LedgerSummaryView {
  return {
    incomeText: ledgerMoneyText(summary.income),
    expenseText: ledgerMoneyText(summary.expense),
    balanceText: ledgerMoneyText(summary.balance),
    balanceTone: summary.balance < 0 ? 'negative' : 'default',
    ...ledgerFlow(summary),
  };
}
