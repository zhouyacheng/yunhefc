import { describe, expect, it } from 'vitest';

import type { LedgerCategory } from '../miniprogram/types/api';
import {
  ALL_CATEGORIES_OPTION,
  categoryOptionsFor,
  DEFAULT_LEDGER_FILTER,
  DEFAULT_LEDGER_KEY,
  ledgerFilterKey,
  ledgerFilterQuery,
  parseLedgerFilterKey,
} from '../miniprogram/utils/ledger-query';

describe('账本筛选编码', () => {
  it('默认 key 可往返', () => {
    expect(DEFAULT_LEDGER_KEY).toBe('|0||');
    expect(parseLedgerFilterKey(DEFAULT_LEDGER_KEY)).toEqual(DEFAULT_LEDGER_FILTER);
    expect(ledgerFilterQuery(DEFAULT_LEDGER_FILTER)).toEqual({
      type: undefined,
      categoryId: undefined,
      from: undefined,
      to: undefined,
    });
  });

  it('key 与请求参数保持同一口径', () => {
    const state = {
      type: 1 as const,
      categoryId: 3,
      fromDate: '2026-11-01',
      toDate: '2026-11-30',
    };
    expect(ledgerFilterKey(state)).toBe('1|3|2026-11-01|2026-11-30');
    expect(parseLedgerFilterKey(ledgerFilterKey(state))).toEqual(state);
    expect(ledgerFilterQuery(state)).toEqual({
      type: 1,
      categoryId: 3,
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
    });
  });

  it('非法或空片段回落到默认值', () => {
    expect(parseLedgerFilterKey('')).toEqual(DEFAULT_LEDGER_FILTER);
    expect(parseLedgerFilterKey('|x||')).toEqual(DEFAULT_LEDGER_FILTER);
  });
});

describe('账本分类下拉', () => {
  const categories: LedgerCategory[] = [
    {
      id: 1,
      name: '队费',
      direction: 0,
      directionLabel: '收入',
      sortNo: 1,
      isActive: true,
      entryCount: 0,
    },
    {
      id: 2,
      name: '球衣',
      direction: 1,
      directionLabel: '支出',
      sortNo: 2,
      isActive: true,
      entryCount: 0,
    },
    {
      id: 3,
      name: '杂项',
      direction: null,
      directionLabel: null,
      sortNo: 3,
      isActive: true,
      entryCount: 0,
    },
  ];

  it('不选方向时展示全部分类', () => {
    expect(categoryOptionsFor(categories, '')).toEqual([
      ALL_CATEGORIES_OPTION,
      { value: 1, label: '队费' },
      { value: 2, label: '球衣' },
      { value: 3, label: '杂项' },
    ]);
  });

  it('按方向收窄但保留通用分类', () => {
    expect(categoryOptionsFor(categories, 1)).toEqual([
      ALL_CATEGORIES_OPTION,
      { value: 2, label: '球衣' },
      { value: 3, label: '杂项' },
    ]);
  });
});
