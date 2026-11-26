import { describe, expect, it } from 'vitest';

import type { LedgerEntry } from '../miniprogram/types/api';
import {
  decorateLedgerEntry,
  ledgerFlow,
  ledgerMoneyText,
  ledgerSummaryView,
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
      ratioPercent: 100,
      ratioLabel: '支出超过收入 150%',
    });
  });
});

describe('流水行', () => {
  it('摘要主导，元信息按分类/时间/归属拼接', () => {
    expect(decorateLedgerEntry(entry())).toMatchObject({
      id: 1,
      title: '11 月队费',
      metaText: '队费 · 2026/11/20 · 归属 张三 · 年终聚餐',
      tone: 'income',
    });
    expect(decorateLedgerEntry(entry({ playerName: null, activityTitle: null })).metaText).toBe(
      '队费 · 2026/11/20',
    );
  });

  it('空摘要回退分类名且不重复分类', () => {
    const view = decorateLedgerEntry(
      entry({ description: '  ', playerName: null, activityTitle: null }),
    );
    expect(view.title).toBe('队费');
    expect(view.metaText).toBe('2026/11/20');
  });
});

describe('汇总收支比', () => {
  it('按收入计算支出占比', () => {
    expect(ledgerFlow({ income: 53500, expense: 44930 })).toEqual({
      ratioPercent: 84,
      ratioLabel: '支出占收入 84%',
    });
    expect(ledgerFlow({ income: 1000, expense: 1000 })).toEqual({
      ratioPercent: 100,
      ratioLabel: '支出占收入 100%',
    });
  });

  it('无支出隐藏条形，无收入时不给比例', () => {
    expect(ledgerFlow({ income: 1000, expense: 0 })).toEqual({
      ratioPercent: null,
      ratioLabel: '',
    });
    expect(ledgerFlow({ income: 0, expense: 0 })).toEqual({
      ratioPercent: null,
      ratioLabel: '',
    });
    expect(ledgerFlow({ income: 0, expense: 800 })).toEqual({
      ratioPercent: 100,
      ratioLabel: '暂无收入',
    });
  });
});
