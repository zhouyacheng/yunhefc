import { describe, expect, it } from 'vitest';

import type { LedgerEntry } from '../miniprogram/types/api';
import {
  decorateLedgerEntry,
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
    expect(decorateLedgerEntry(entry()).amountText).toBe('+¥120.00');
    expect(decorateLedgerEntry(entry({ type: 1, typeLabel: '支出' })).amountText).toBe('−¥120.00');
    expect(ledgerSummaryView({ income: 100, expense: 250, balance: -150 })).toEqual({
      incomeText: '¥100.00',
      expenseText: '¥250.00',
      balanceText: '−¥150.00',
      tone: 'expense',
    });
  });
});

describe('流水行', () => {
  it('拼接时间与归属信息，缺省不留下分隔符', () => {
    expect(decorateLedgerEntry(entry())).toMatchObject({
      id: 1,
      categoryName: '队费',
      description: '11 月队费',
      timeText: '2026/11/20 20:00',
      tone: 'income',
      metaText: '张三 · 年终聚餐',
    });
    expect(decorateLedgerEntry(entry({ playerName: null, activityTitle: null })).metaText).toBe('');
  });
});
