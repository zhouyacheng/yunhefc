// MP12 账本展示派生：金额正负、流水行、日分组、汇总与统计。与 Web features/ledger/presentation.ts 保持一致。
import type {
  LedgerDailyTotal,
  LedgerEntry,
  LedgerPeriod,
  LedgerStatistics,
  LedgerSummary,
  LedgerTrendGranularity,
} from '../types/api';
import {
  formatDateYmd,
  formatHourMinute,
  formatMonthDayWeekday,
  formatMonthLabel,
} from './datetime';

function groupDigits(value: string): string {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** 金额数值：固定两位小数并千分位，不带方向与货币符号。 */
export function ledgerAmountText(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const [integer = '0', fraction = '00'] = Math.abs(value).toFixed(2).split('.');
  return `${groupDigits(integer)}.${fraction}`;
}

/** 负数按 `−¥123.00` 展示，与 Web 一致。 */
export function ledgerMoneyText(value: number): string {
  return value < 0 ? `−¥${ledgerAmountText(value)}` : `¥${ledgerAmountText(value)}`;
}

const CATEGORY_ICONS: Record<string, string> = {
  队费: 'gold-coin-o',
  场地: 'shop-o',
  装备: 'gift-o',
  活动: 'calendar-o',
  餐饮: 'smile-o',
  赞助: 'award-o',
  其他: 'records',
};

function categoryIcon(name: string): string {
  return CATEGORY_ICONS[name] ?? 'records';
}

export interface LedgerEntryView {
  id: number;
  title: string;
  metaText: string;
  amountSign: string;
  /** 原始数值，供日小计回退求和；展示用 amountText。 */
  amount: number;
  amountText: string;
  tone: 'income' | 'expense';
  dayKey: string;
  icon: string;
}

export interface LedgerSummaryView {
  incomeText: string;
  expenseText: string;
  /** 负号单独渲染以便缩小字号，金额本身不带符号。 */
  balanceSign: string;
  balanceText: string;
  /** 余额不是收支方向：正数为常规墨色，负数标红。 */
  balanceTone: 'default' | 'negative';
}

/** 流水行方向符号，单独渲染以便缩小字号。 */
function ledgerRowAmountSign(entry: Pick<LedgerEntry, 'type'>): string {
  return entry.type === 0 ? '+' : '−';
}

export function decorateLedgerEntry(entry: LedgerEntry): LedgerEntryView {
  const description = entry.description.trim();
  const parts = [formatHourMinute(entry.occurredAt) || '—', entry.categoryName || '—'];
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
    amount: entry.amount,
    amountText: ledgerAmountText(entry.amount),
    tone: entry.type === 0 ? 'income' : 'expense',
    dayKey: formatDateYmd(entry.occurredAt),
    icon: categoryIcon(entry.categoryName),
  };
}

export function ledgerSummaryView(summary: LedgerSummary): LedgerSummaryView {
  return {
    incomeText: ledgerMoneyText(summary.income),
    expenseText: ledgerMoneyText(summary.expense),
    balanceSign: summary.balance < 0 ? '−' : '',
    balanceText: Number.isFinite(summary.balance)
      ? ledgerMoneyText(Math.abs(summary.balance))
      : '—',
    balanceTone: summary.balance < 0 ? 'negative' : 'default',
  };
}

export interface LedgerDayGroupView {
  key: string;
  label: string;
  expenseText: string;
  incomeText: string;
  items: LedgerEntryView[];
}

/**
 * 明细按自然日分组；日小计优先取 LED-13，缺失时回退当前已加载流水之和。
 * 入参已按发生时间倒序，分组保持同样顺序。
 */
export function groupLedgerDays(
  items: readonly LedgerEntryView[],
  totals: readonly LedgerDailyTotal[],
): LedgerDayGroupView[] {
  const totalsByKey = new Map(totals.map((total) => [total.date, total]));
  const groups: LedgerDayGroupView[] = [];
  const byKey = new Map<string, LedgerDayGroupView>();
  const sums = new Map<string, { income: number; expense: number }>();
  for (const item of items) {
    if (!item.dayKey) {
      continue;
    }
    let group = byKey.get(item.dayKey);
    if (!group) {
      group = {
        key: item.dayKey,
        label: formatMonthDayWeekday(`${item.dayKey}T12:00:00+08:00`),
        expenseText: '0.00',
        incomeText: '0.00',
        items: [],
      };
      byKey.set(item.dayKey, group);
      groups.push(group);
    }
    group.items.push(item);
  }
  for (const group of groups) {
    const total = totalsByKey.get(group.key);
    if (total) {
      group.expenseText = ledgerAmountText(total.expense);
      group.incomeText = ledgerAmountText(total.income);
      continue;
    }
    const sum = sums.get(group.key) ?? { income: 0, expense: 0 };
    for (const item of group.items) {
      const amount = item.amount;
      if (item.tone === 'income') {
        sum.income = Math.round((sum.income + amount) * 100) / 100;
      } else {
        sum.expense = Math.round((sum.expense + amount) * 100) / 100;
      }
    }
    sums.set(group.key, sum);
    group.expenseText = ledgerAmountText(sum.expense);
    group.incomeText = ledgerAmountText(sum.income);
  }
  return groups;
}

export type LedgerStatsDirection = 'expense' | 'income';

export function ledgerStatsDirectionLabel(direction: LedgerStatsDirection): string {
  return direction === 'expense' ? '支出' : '收入';
}

const PREVIOUS_NOUN: Record<LedgerPeriod, string> = {
  week: '上周',
  month: '上月',
  year: '去年',
  custom: '上期',
};

const CURRENT_NOUN: Record<LedgerPeriod, string> = {
  week: '本周',
  month: '本月',
  year: '本年',
  custom: '本期',
};

function periodDate(value: string): [number, number, number] {
  const [year = 0, month = 1, day = 1] = value.split('-').map(Number);
  return [year, month, day];
}

/** 统计周期标签：2026年11月 / 11月23日–11月29日 / 2026年 / 11月1日–11月20日。 */
export function ledgerPeriodLabel(stats: Pick<LedgerStatistics, 'period' | 'from' | 'to'>): string {
  const [fromYear, fromMonth, fromDay] = periodDate(stats.from);
  const [toYear, toMonth, toDay] = periodDate(stats.to);
  if (stats.period === 'month') {
    return formatMonthLabel(stats.from.slice(0, 7));
  }
  if (stats.period === 'year') {
    return `${fromYear}年`;
  }
  const head = `${fromMonth}月${fromDay}日`;
  const tail = fromYear === toYear ? `${toMonth}月${toDay}日` : `${toYear}年${toMonth}月${toDay}日`;
  return `${head}–${tail}`;
}

function previousIsEmpty(stats: LedgerStatistics): boolean {
  return stats.previousIncome === 0 && stats.previousExpense === 0;
}

function statsTotal(stats: LedgerStatistics, direction: LedgerStatsDirection): number {
  return direction === 'expense' ? stats.expense : stats.income;
}

function statsCount(stats: LedgerStatistics, direction: LedgerStatsDirection): number {
  return direction === 'expense' ? stats.expenseCount : stats.incomeCount;
}

function statsPreviousTotal(stats: LedgerStatistics, direction: LedgerStatsDirection): number {
  return direction === 'expense' ? stats.previousExpense : stats.previousIncome;
}

/** 笔数与环比：「支出83笔，总计 ¥3,356.03，比上月增加 ¥425.93」。 */
export function ledgerStatsCountText(
  stats: LedgerStatistics,
  direction: LedgerStatsDirection,
): string {
  const label = ledgerStatsDirectionLabel(direction);
  const head = `${label}${statsCount(stats, direction)}笔，总计 ${ledgerMoneyText(
    statsTotal(stats, direction),
  )}`;
  const noun = PREVIOUS_NOUN[stats.period];
  if (previousIsEmpty(stats)) {
    return `${head}，${noun}无数据`;
  }
  const delta = statsTotal(stats, direction) - statsPreviousTotal(stats, direction);
  if (delta === 0) {
    return `${head}，与${noun}持平`;
  }
  return `${head}，比${noun}${delta > 0 ? '增加' : '减少'} ${ledgerMoneyText(Math.abs(delta))}`;
}

/** 结余与环比：「本月结余 −¥3,355.50，相比上月减少 ¥427.11」。 */
export function ledgerStatsBalanceText(stats: LedgerStatistics): string {
  const current = CURRENT_NOUN[stats.period];
  const noun = PREVIOUS_NOUN[stats.period];
  const head = `${current}结余 ${ledgerMoneyText(stats.balance)}`;
  if (previousIsEmpty(stats)) {
    return `${head}，${noun}无数据`;
  }
  const delta = stats.balance - stats.previousBalance;
  if (delta === 0) {
    return `${head}，与${noun}持平`;
  }
  return `${head}，相比${noun}${delta > 0 ? '增加' : '减少'} ${ledgerMoneyText(Math.abs(delta))}`;
}

export interface LedgerTrendBarView {
  start: string;
  label: string;
  value: number;
  valueText: string;
  /** 相对区间最大值的柱高（0–100），非零值至少 2。 */
  percent: number;
  /** 桶较多时只标注首尾与每 5 个桶，避免标签重叠。 */
  labelVisible: boolean;
}

export function ledgerTrendBars(
  stats: Pick<LedgerStatistics, 'trend' | 'trendGranularity'>,
  direction: LedgerStatsDirection,
): LedgerTrendBarView[] {
  const trend = stats.trend ?? [];
  const values = trend.map((point) => (direction === 'expense' ? point.expense : point.income));
  const max = Math.max(0, ...values);
  const count = trend.length;
  return trend.map((point, index) => {
    const value = Math.max(0, values[index] ?? 0);
    const percent = max > 0 ? Math.round((value / max) * 100) : 0;
    return {
      start: point.start,
      label: trendLabel(point.start, stats.trendGranularity),
      value,
      valueText: ledgerMoneyText(value),
      percent: value > 0 ? Math.max(percent, 2) : 0,
      labelVisible: count <= 12 || index === 0 || index === count - 1 || index % 5 === 0,
    };
  });
}

function trendLabel(start: string, granularity: LedgerTrendGranularity): string {
  return granularity === 'month'
    ? `${Number(start.slice(5, 7))}月`
    : `${Number(start.slice(8, 10))}日`;
}

export interface LedgerStatisticsView {
  periodLabel: string;
  totalText: string;
  countLine: string;
  balanceLine: string;
  directionLabel: string;
  bars: LedgerTrendBarView[];
  trendEmpty: boolean;
}

export function ledgerStatisticsView(
  stats: LedgerStatistics,
  direction: LedgerStatsDirection,
): LedgerStatisticsView {
  const bars = ledgerTrendBars(stats, direction);
  return {
    periodLabel: ledgerPeriodLabel(stats),
    totalText: ledgerMoneyText(statsTotal(stats, direction)),
    countLine: ledgerStatsCountText(stats, direction),
    balanceLine: ledgerStatsBalanceText(stats),
    directionLabel: ledgerStatsDirectionLabel(direction),
    bars,
    trendEmpty: bars.every((bar) => bar.value === 0),
  };
}
