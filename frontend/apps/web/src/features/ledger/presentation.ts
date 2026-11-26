// Web 端账本展示派生；小程序 utils/ledger-view.ts 与 prototype 的账本页保持同口径。
import type {
  LedgerCategory,
  LedgerDailyTotal,
  LedgerEntry,
  LedgerPeriod,
  LedgerStatistics,
  LedgerTrendGranularity,
  LedgerTrendPoint,
  LedgerType,
} from '@yunhefc/shared';
import {
  LEDGER_TYPE_TEXT,
  formatAmount,
  formatDayKey,
  formatMoney,
  formatMonthDayWeekday,
  formatTimeOfDay,
} from '@yunhefc/shared';

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

const CATEGORY_ICONS: Readonly<Record<string, string>> = {
  队费: 'gold-coin-o',
  场地: 'shop-o',
  装备: 'gift-o',
  活动: 'calendar-o',
  餐饮: 'smile-o',
  赞助: 'award-o',
  其他: 'records',
};

/** 分类图标：内置分类用固定图标，自定义分类回退通用票据图标。 */
export function categoryIcon(name: string): string {
  return CATEGORY_ICONS[name] ?? 'records';
}

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

/** 流水行元信息：时分 · 分类 · 归属球员/活动；日期由分组头承载。 */
export function entryMetaText(
  entry: Pick<
    LedgerEntry,
    'categoryId' | 'categoryName' | 'occurredAt' | 'playerName' | 'activityTitle'
  >,
  labels: Map<number, string>,
): string {
  const parts = [formatTimeOfDay(entry.occurredAt), entryCategoryLabel(entry, labels)];
  if (entry.playerName) {
    parts.push(`归属 ${entry.playerName}`);
  }
  if (entry.activityTitle) {
    parts.push(entry.activityTitle);
  }
  return parts.join(' · ');
}

export interface LedgerDayGroup {
  /** YYYY-MM-DD（Asia/Shanghai）。 */
  key: string;
  label: string;
  income: number;
  expense: number;
  entries: LedgerEntry[];
}

/**
 * 明细按自然日分组；日小计优先取 LED-13，缺失时回退当前已加载流水之和。
 * 入参已按发生时间倒序，分组保持同样顺序。
 */
export function groupEntriesByDay(
  entries: LedgerEntry[],
  totals: LedgerDailyTotal[],
): LedgerDayGroup[] {
  const totalsByKey = new Map(totals.map((total) => [total.date, total]));
  const groups = new Map<string, LedgerDayGroup>();
  for (const entry of entries) {
    const key = formatDayKey(entry.occurredAt);
    if (!key) {
      continue;
    }
    let group = groups.get(key);
    if (!group) {
      group = {
        key,
        label: formatMonthDayWeekday(entry.occurredAt),
        income: 0,
        expense: 0,
        entries: [],
      };
      groups.set(key, group);
    }
    group.entries.push(entry);
  }
  for (const group of groups.values()) {
    const total = totalsByKey.get(group.key);
    if (total) {
      group.income = total.income;
      group.expense = total.expense;
      continue;
    }
    for (const entry of group.entries) {
      // 金额按分累加，避免浮点尾差。
      if (entry.type === 0) {
        group.income = Math.round((group.income + entry.amount) * 100) / 100;
      } else {
        group.expense = Math.round((group.expense + entry.amount) * 100) / 100;
      }
    }
  }
  return [...groups.values()];
}

/** 明细月份标题：2026年11月。 */
export function ledgerMonthLabel(month: string): string {
  const [year = '', monthOfYear = ''] = month.split('-');
  return year && monthOfYear ? `${year}年${Number(monthOfYear)}月` : '—';
}

/** 统计口径：支出或收入。 */
export type LedgerStatsDirection = 'expense' | 'income';

export function statsDirectionLabel(direction: LedgerStatsDirection): string {
  return direction === 'expense' ? '支出' : '收入';
}

export function statsTotal(
  stats: Pick<LedgerStatistics, 'income' | 'expense'>,
  direction: LedgerStatsDirection,
): number {
  return direction === 'expense' ? stats.expense : stats.income;
}

export function statsCount(
  stats: Pick<LedgerStatistics, 'incomeCount' | 'expenseCount'>,
  direction: LedgerStatsDirection,
): number {
  return direction === 'expense' ? stats.expenseCount : stats.incomeCount;
}

export function statsPreviousTotal(
  stats: Pick<LedgerStatistics, 'previousIncome' | 'previousExpense'>,
  direction: LedgerStatsDirection,
): number {
  return direction === 'expense' ? stats.previousExpense : stats.previousIncome;
}

const PREVIOUS_NOUN: Readonly<Record<LedgerPeriod, string>> = {
  week: '上周',
  month: '上月',
  year: '去年',
  custom: '上期',
};

const CURRENT_NOUN: Readonly<Record<LedgerPeriod, string>> = {
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
    return `${fromYear}年${fromMonth}月`;
  }
  if (stats.period === 'year') {
    return `${fromYear}年`;
  }
  const head = `${fromMonth}月${fromDay}日`;
  const tail = fromYear === toYear ? `${toMonth}月${toDay}日` : `${toYear}年${toMonth}月${toDay}日`;
  return `${head}–${tail}`;
}

/** 上一周期无任何流水时，环比文案改为「上月无数据」之类的提示。 */
function previousIsEmpty(stats: LedgerStatistics): boolean {
  return stats.previousIncome === 0 && stats.previousExpense === 0;
}

/** 笔数与环比：「支出83笔，总计 ¥3,356.03，比上月增加 ¥425.93」。 */
export function statsCountText(stats: LedgerStatistics, direction: LedgerStatsDirection): string {
  const label = statsDirectionLabel(direction);
  const head = `${label}${statsCount(stats, direction)}笔，总计 ${formatMoney(
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
  return `${head}，比${noun}${delta > 0 ? '增加' : '减少'} ${formatMoney(Math.abs(delta))}`;
}

/** 结余与环比：「本月结余 −¥3,355.50，相比上月减少 ¥427.11」。 */
export function statsBalanceText(stats: LedgerStatistics): string {
  const current = CURRENT_NOUN[stats.period];
  const noun = PREVIOUS_NOUN[stats.period];
  const head = `${current}结余 ${formatMoney(stats.balance)}`;
  if (previousIsEmpty(stats)) {
    return `${head}，${noun}无数据`;
  }
  const delta = stats.balance - stats.previousBalance;
  if (delta === 0) {
    return `${head}，与${noun}持平`;
  }
  return `${head}，相比${noun}${delta > 0 ? '增加' : '减少'} ${formatMoney(Math.abs(delta))}`;
}

export interface LedgerTrendBar {
  start: string;
  label: string;
  value: number;
  /** 相对区间最大值的柱高（0–100），非零值至少 2 以便可见。 */
  percent: number;
  /** 桶较多时只标注首尾与每 5 个桶，避免标签重叠（与小程序同口径）。 */
  labelVisible: boolean;
}

/** 趋势柱：按所选方向取值，按区间最大值归一；非零值给最小高度。 */
export function ledgerTrendBars(
  trend: LedgerTrendPoint[],
  granularity: LedgerTrendGranularity,
  direction: LedgerStatsDirection,
): LedgerTrendBar[] {
  const values = trend.map((point) => (direction === 'expense' ? point.expense : point.income));
  const max = Math.max(0, ...values);
  const count = trend.length;
  return trend.map((point, index) => {
    const value = Math.max(0, values[index] ?? 0);
    const percent = max > 0 ? Math.round((value / max) * 100) : 0;
    return {
      start: point.start,
      label:
        granularity === 'month'
          ? `${Number(point.start.slice(5, 7))}月`
          : `${Number(point.start.slice(8, 10))}日`,
      value,
      percent: value > 0 ? Math.max(percent, 2) : 0,
      labelVisible: count <= 12 || index === 0 || index === count - 1 || index % 5 === 0,
    };
  });
}
