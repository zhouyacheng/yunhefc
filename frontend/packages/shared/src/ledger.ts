/** M16 球队账本（05 §3.17、§4）。 */

/** 0-收入；1-支出。 */
export type LedgerType = 0 | 1;

/** 分类方向：0-收入；1-支出；null-通用。 */
export type LedgerDirection = LedgerType | null;

export interface LedgerCategory {
  id: number;
  name: string;
  direction: LedgerDirection;
  directionLabel: string | null;
  sortNo: number;
  isActive: boolean;
  entryCount: number;
}

/** LED-01/03/04/05/06。 */
export interface LedgerEntry {
  id: number;
  type: LedgerType;
  typeLabel: string;
  categoryId: number;
  categoryName: string;
  categoryDirection: LedgerDirection;
  amount: number;
  description: string;
  occurredAt: string;
  playerId: number | null;
  playerName: string | null;
  activityId: number | null;
  activityTitle: string | null;
  version: number;
}

/** LED-02/07：余额 = 收入 − 支出，无期初余额。 */
export interface LedgerSummary {
  income: number;
  expense: number;
  balance: number;
}

/** LED-13：按自然日聚合的收支小计；只返回有流水的日期。 */
export interface LedgerDailyTotal {
  /** YYYY-MM-DD（Asia/Shanghai）。 */
  date: string;
  income: number;
  expense: number;
}

export type LedgerPeriod = 'week' | 'month' | 'year' | 'custom';

export type LedgerTrendGranularity = 'day' | 'month';

/** LED-14 趋势桶；start 为桶首日。 */
export interface LedgerTrendPoint {
  start: string;
  income: number;
  expense: number;
}

/** LED-14：周期统计与上一周期对比。 */
export interface LedgerStatistics {
  period: LedgerPeriod;
  from: string;
  to: string;
  previousFrom: string;
  previousTo: string;
  income: number;
  expense: number;
  balance: number;
  incomeCount: number;
  expenseCount: number;
  previousIncome: number;
  previousExpense: number;
  previousBalance: number;
  trendGranularity: LedgerTrendGranularity;
  trend: LedgerTrendPoint[];
}

/** 三端共用的收支方向文案；服务端 fieldLabel 是英文枚举名。 */
export const LEDGER_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '收入',
  1: '支出',
};

export function ledgerTypeText(type: LedgerType): string {
  return LEDGER_TYPE_TEXT[type] ?? '—';
}

export function ledgerDirectionText(direction: LedgerDirection): string {
  return direction === null ? '通用' : ledgerTypeText(direction);
}
