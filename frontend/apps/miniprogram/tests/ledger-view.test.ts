import { describe, expect, it } from 'vitest';

import type { LedgerEntry, LedgerStatistics } from '../miniprogram/types/api';
import {
  decorateLedgerEntry,
  groupLedgerDays,
  ledgerMoneyText,
  ledgerPeriodLabel,
  ledgerStatisticsView,
  ledgerStatsBalanceText,
  ledgerStatsCountText,
  ledgerSummaryView,
  ledgerTrendBars,
} from '../miniprogram/utils/ledger-view';

function entry(patch: Partial<LedgerEntry> = {}): LedgerEntry {
  return {
    id: 1,
    type: 0,
    typeLabel: '收入',
    categoryId: 3,
    categoryName: '队费',
    categoryDirection: 0,
    amount: 120,
    description: '11 月队费',
    occurredAt: '2026-11-20T20:00:00+08:00',
    playerId: 2,
    playerName: '张三',
    activityId: 9,
    activityTitle: '年终聚餐',
    version: 1,
    ...patch,
  };
}

function statistics(patch: Partial<LedgerStatistics> = {}): LedgerStatistics {
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
    ...patch,
  };
}

describe('金额文案', () => {
  it('收入/支出与负余额使用统一符号', () => {
    expect(ledgerMoneyText(120)).toBe('¥120.00');
    expect(ledgerMoneyText(-123)).toBe('−¥123.00');
    expect(decorateLedgerEntry(entry())).toMatchObject({ amountSign: '+', amountText: '120.00' });
    expect(decorateLedgerEntry(entry({ type: 1, typeLabel: '支出' }))).toMatchObject({
      amountSign: '−',
      amountText: '120.00',
    });
    expect(decorateLedgerEntry(entry({ amount: 1234.5 })).amountText).toBe('1,234.50');
    expect(ledgerSummaryView({ income: 100, expense: 250, balance: -150 })).toEqual({
      incomeText: '¥100.00',
      expenseText: '¥250.00',
      balanceText: '−¥150.00',
      balanceTone: 'negative',
    });
  });
});

describe('流水行', () => {
  it('摘要主导，元信息按时分/分类/归属拼接', () => {
    expect(decorateLedgerEntry(entry())).toMatchObject({
      id: 1,
      title: '11 月队费',
      metaText: '20:00 · 队费 · 归属 张三 · 年终聚餐',
      tone: 'income',
      dayKey: '2026-11-20',
      icon: 'gold-coin-o',
    });
    expect(decorateLedgerEntry(entry({ playerName: null, activityTitle: null })).metaText).toBe(
      '20:00 · 队费',
    );
  });

  it('空摘要回退分类名，自定义分类回退票据图标', () => {
    const view = decorateLedgerEntry(
      entry({
        description: '  ',
        categoryName: '杂项',
        playerName: null,
        activityTitle: null,
      }),
    );
    expect(view.title).toBe('杂项');
    expect(view.icon).toBe('records');
  });
});

describe('按日分组', () => {
  it('按日期分组并优先使用服务端日小计', () => {
    const groups = groupLedgerDays(
      [
        decorateLedgerEntry(
          entry({ id: 1, type: 1, amount: 20.7, occurredAt: '2026-11-25T18:20:00+08:00' }),
        ),
        decorateLedgerEntry(
          entry({ id: 2, type: 0, amount: 0.02, occurredAt: '2026-11-25T09:00:00+08:00' }),
        ),
      ],
      [{ date: '2026-11-25', income: 0.02, expense: 43.2 }],
    );

    expect(groups).toHaveLength(1);
    expect(groups[0]?.label).toBe('11月25日 星期三');
    expect(groups[0]?.expenseText).toBe('43.20');
    expect(groups[0]?.incomeText).toBe('0.02');
    expect(groups[0]?.items.map((item) => item.id)).toEqual([1, 2]);
  });

  it('日小计缺失时回退已加载流水之和', () => {
    const groups = groupLedgerDays(
      [
        decorateLedgerEntry(
          entry({ id: 1, type: 1, amount: 20.7, occurredAt: '2026-11-25T18:20:00+08:00' }),
        ),
        decorateLedgerEntry(
          entry({ id: 2, type: 1, amount: 5.6, occurredAt: '2026-11-25T09:00:00+08:00' }),
        ),
      ],
      [],
    );

    expect(groups[0]?.expenseText).toBe('26.30');
    expect(groups[0]?.incomeText).toBe('0.00');
  });
});

describe('统计文案', () => {
  it('周期标签区分月/年/周/自定义', () => {
    expect(ledgerPeriodLabel(statistics())).toBe('2026年11月');
    expect(ledgerPeriodLabel(statistics({ period: 'year' }))).toBe('2026年');
    expect(
      ledgerPeriodLabel(statistics({ period: 'week', from: '2026-11-23', to: '2026-11-29' })),
    ).toBe('11月23日–11月29日');
  });

  it('笔数与环比区分增加、减少、持平与无数据', () => {
    expect(ledgerStatsCountText(statistics({ previousExpense: 714.07 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，比上月增加 ¥425.93',
    );
    expect(ledgerStatsCountText(statistics({ previousExpense: 2000 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，比上月减少 ¥860.00',
    );
    expect(ledgerStatsCountText(statistics({ previousExpense: 1140 }), 'expense')).toBe(
      '支出3笔，总计 ¥1,140.00，与上月持平',
    );
    expect(
      ledgerStatsCountText(statistics({ previousIncome: 0, previousExpense: 0 }), 'income'),
    ).toBe('收入2笔，总计 ¥500.00，上月无数据');
  });

  it('结余与环比文案', () => {
    expect(ledgerStatsBalanceText(statistics({ previousExpense: 0, previousIncome: 0 }))).toBe(
      '本月结余 −¥640.00，上月无数据',
    );
    expect(ledgerStatsBalanceText(statistics())).toBe('本月结余 −¥640.00，相比上月减少 ¥590.00');
  });

  it('趋势柱按最大值归一，桶多时只标注首尾与每五个', () => {
    const bars = ledgerTrendBars(
      statistics({
        trend: Array.from({ length: 30 }, (_, index) => ({
          start: `2026-11-${String(index + 1).padStart(2, '0')}`,
          income: 0,
          expense: index === 0 ? 100 : index === 5 ? 50 : 0,
        })),
      }),
      'expense',
    );

    expect(bars[0]?.percent).toBe(100);
    expect(bars[5]?.percent).toBe(50);
    expect(bars[1]?.percent).toBe(0);
    expect(bars[0]?.labelVisible).toBe(true);
    expect(bars[5]?.labelVisible).toBe(true);
    expect(bars[6]?.labelVisible).toBe(false);
    expect(bars[29]?.labelVisible).toBe(true);
  });

  it('统计视图汇总方向、总额与趋势空态', () => {
    const view = ledgerStatisticsView(statistics({ trend: [] }), 'income');

    expect(view.directionLabel).toBe('收入');
    expect(view.totalText).toBe('¥500.00');
    expect(view.trendEmpty).toBe(true);
    expect(view.countLine).toContain('收入2笔');
  });
});
