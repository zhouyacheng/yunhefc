import type { LedgerCategory, LedgerEntry, LedgerStatistics } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  amountSign,
  categoryIcon,
  categoryLabelMap,
  categoryOptions,
  entryCategoryLabel,
  entryMetaText,
  entryTitle,
  formatEntryAmount,
  formatMoney,
  groupEntriesByDay,
  ledgerMonthLabel,
  ledgerPeriodLabel,
  ledgerTrendBars,
  statsBalanceText,
  statsCountText,
} from './presentation';

const CATEGORIES: LedgerCategory[] = [
  {
    id: 1,
    name: '队费',
    direction: 0,
    directionLabel: null,
    sortNo: 1,
    isActive: true,
    entryCount: 3,
  },
  {
    id: 2,
    name: '场地',
    direction: 1,
    directionLabel: null,
    sortNo: 2,
    isActive: true,
    entryCount: 4,
  },
  {
    id: 3,
    name: '其他',
    direction: null,
    directionLabel: null,
    sortNo: 3,
    isActive: true,
    entryCount: 1,
  },
];

function entry(overrides: Partial<LedgerEntry>): LedgerEntry {
  return {
    id: 1,
    type: 1,
    typeLabel: 'EXPENSE',
    categoryId: 2,
    categoryName: '场地',
    categoryDirection: 1,
    amount: 800,
    description: '11 月场地费',
    occurredAt: '2026-11-20T20:10:00+08:00',
    playerId: null,
    playerName: null,
    activityId: null,
    activityTitle: null,
    version: 0,
    ...overrides,
  };
}

function statistics(overrides: Partial<LedgerStatistics>): LedgerStatistics {
  return {
    period: 'month',
    from: '2026-11-01',
    to: '2026-11-30',
    previousFrom: '2026-10-01',
    previousTo: '2026-10-31',
    income: 500,
    expense: 1140,
    balance: -640,
    incomeCount: 2,
    expenseCount: 3,
    previousIncome: 0,
    previousExpense: 50,
    previousBalance: -50,
    trendGranularity: 'day',
    trend: [],
    ...overrides,
  };
}

describe('金额与方向格式化', () => {
  it('金额固定两位小数并千分位', () => {
    expect(formatMoney(0)).toBe('¥0.00');
    expect(formatMoney(800)).toBe('¥800.00');
    expect(formatMoney(1234.5)).toBe('¥1,234.50');
    expect(formatMoney(-1234.5)).toBe('−¥1,234.50');
    expect(formatMoney(Number.NaN)).toBe('—');
  });

  it('流水金额拆分方向符号与数值，均不带货币符号', () => {
    expect(amountSign({ type: 0 })).toBe('+');
    expect(amountSign({ type: 1 })).toBe('−');
    expect(formatEntryAmount({ amount: 300 })).toBe('300.00');
    expect(formatEntryAmount({ amount: -800 })).toBe('800.00');
  });
});

describe('分类选项与图标', () => {
  it('按方向过滤并保留通用分类', () => {
    expect(categoryOptions(CATEGORIES, 0).map((item) => item.name)).toEqual(['队费', '其他']);
    expect(categoryOptions(CATEGORIES, 1).map((item) => item.name)).toEqual(['场地', '其他']);
  });

  it('未选方向时展示全部分类', () => {
    expect(categoryOptions(CATEGORIES, null)).toHaveLength(3);
  });

  it('分类标签映射回退接口返回名', () => {
    const labels = categoryLabelMap(CATEGORIES);
    expect(entryCategoryLabel({ categoryId: 2, categoryName: '旧名字' }, labels)).toBe('场地');
    expect(entryCategoryLabel({ categoryId: 99, categoryName: '历史分类' }, labels)).toBe(
      '历史分类',
    );
  });

  it('内置分类有图标，自定义分类回退票据图标', () => {
    expect(categoryIcon('场地')).toBe('shop-o');
    expect(categoryIcon('自定义')).toBe('records');
  });
});

describe('流水行展示', () => {
  const base = {
    description: '11 月场地费（4 场）',
    categoryId: 2,
    categoryName: '场地',
    occurredAt: '2026-11-20T20:10:00+08:00',
    playerName: null,
    activityTitle: null,
  };

  it('摘要主导，元信息按时刻/分类/归属拼接', () => {
    const labels = categoryLabelMap(CATEGORIES);
    expect(entryTitle(base, labels)).toBe('11 月场地费（4 场）');
    expect(entryMetaText(base, labels)).toBe('20:10 · 场地');
    expect(entryMetaText({ ...base, playerName: '张伟', activityTitle: '队内活动' }, labels)).toBe(
      '20:10 · 场地 · 归属 张伟 · 队内活动',
    );
  });

  it('空摘要回退分类名', () => {
    const labels = categoryLabelMap(CATEGORIES);
    expect(entryTitle({ ...base, description: '  ' }, labels)).toBe('场地');
  });
});

describe('按日分组', () => {
  it('按日期分组并优先使用服务端日小计', () => {
    const groups = groupEntriesByDay(
      [
        entry({ id: 1, occurredAt: '2026-11-25T18:20:00+08:00', amount: 20.7, type: 1 }),
        entry({ id: 2, occurredAt: '2026-11-25T09:00:00+08:00', amount: 0.02, type: 0 }),
        entry({ id: 3, occurredAt: '2026-11-20T20:10:00+08:00', amount: 800, type: 1 }),
      ],
      [
        { date: '2026-11-25', income: 0.02, expense: 43.2 },
        { date: '2026-11-20', income: 0, expense: 800 },
      ],
    );

    expect(groups).toHaveLength(2);
    expect(groups[0]?.label).toBe('11月25日 星期三');
    expect(groups[0]?.expense).toBe(43.2);
    expect(groups[0]?.income).toBe(0.02);
    expect(groups[0]?.entries.map((item) => item.id)).toEqual([1, 2]);
    expect(groups[1]?.key).toBe('2026-11-20');
  });

  it('日小计缺失时回退已加载流水之和', () => {
    const groups = groupEntriesByDay(
      [
        entry({ id: 1, occurredAt: '2026-11-25T18:20:00+08:00', amount: 20.7, type: 1 }),
        entry({ id: 2, occurredAt: '2026-11-25T09:00:00+08:00', amount: 5.6, type: 1 }),
      ],
      [],
    );

    expect(groups[0]?.expense).toBe(26.3);
    expect(groups[0]?.income).toBe(0);
  });
});

describe('统计文案', () => {
  it('月份标题与周期标签', () => {
    expect(ledgerMonthLabel('2026-11')).toBe('2026年11月');
    expect(ledgerPeriodLabel(statistics({ period: 'month' }))).toBe('2026年11月');
    expect(ledgerPeriodLabel(statistics({ period: 'year' }))).toBe('2026年');
    expect(
      ledgerPeriodLabel(statistics({ period: 'week', from: '2026-11-23', to: '2026-11-29' })),
    ).toBe('11月23日–11月29日');
  });

  it('笔数与环比区分增加、减少与持平', () => {
    expect(statsCountText(statistics({ previousExpense: 714.07 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，比上月增加 ¥425.93',
    );
    expect(statsCountText(statistics({ previousExpense: 2000 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，比上月减少 ¥860.00',
    );
    expect(statsCountText(statistics({ previousExpense: 1140 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，与上月持平',
    );
    expect(statsCountText(statistics({ previousIncome: 0, previousExpense: 0 }), 'income')).toBe(
      '收入2笔，总计 ¥500.00，上月无数据',
    );
  });

  it('结余与环比文案', () => {
    expect(statsBalanceText(statistics({ previousExpense: 0, previousIncome: 0 }))).toBe(
      '本月结余 −¥640.00，上月无数据',
    );
    expect(statsBalanceText(statistics({ previousExpense: 50, previousIncome: 0 }))).toBe(
      '本月结余 −¥640.00，相比上月减少 ¥590.00',
    );
  });

  it('趋势柱按最大值归一，非零值保留最小高度', () => {
    const bars = ledgerTrendBars(
      [
        { start: '2026-11-01', income: 0, expense: 0 },
        { start: '2026-11-02', income: 0, expense: 100 },
        { start: '2026-11-03', income: 0, expense: 50 },
      ],
      'day',
      'expense',
    );

    expect(bars.map((bar) => bar.percent)).toEqual([0, 100, 50]);
    expect(bars[0]?.label).toBe('1日');
    expect(bars[2]?.value).toBe(50);
  });

  it('桶较多时只标注首尾与每五个，避免标签重叠', () => {
    const bars = ledgerTrendBars(
      Array.from({ length: 13 }, (_, index) => ({
        start: `2026-11-${String(index + 1).padStart(2, '0')}`,
        income: 0,
        expense: index === 0 ? 10 : 0,
      })),
      'day',
      'expense',
    );

    expect(bars.filter((bar) => bar.labelVisible).map((bar) => bar.start.slice(8))).toEqual([
      '01',
      '06',
      '11',
      '13',
    ]);
  });
});
