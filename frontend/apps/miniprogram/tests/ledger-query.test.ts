import { describe, expect, it } from 'vitest';

import type { LedgerCategory } from '../miniprogram/types/api';
import {
  ALL_CATEGORIES_OPTION,
  categoryOptionsFor,
  DEFAULT_LEDGER_FILTER,
  DEFAULT_LEDGER_KEY,
  ledgerFilterKey,
  ledgerFilterQuery,
  ledgerIsFiltered,
  ledgerStatisticsQuery,
  ledgerSummaryQuery,
  parseLedgerFilterKey,
  shiftStatsAnchor,
} from '../miniprogram/utils/ledger-query';

describe('账本筛选编码', () => {
  it('默认 key 可往返', () => {
    expect(DEFAULT_LEDGER_KEY).toBe('|0||||');
    expect(parseLedgerFilterKey(DEFAULT_LEDGER_KEY)).toEqual(DEFAULT_LEDGER_FILTER);
    expect(ledgerFilterQuery(DEFAULT_LEDGER_FILTER)).toEqual({
      type: undefined,
      categoryId: undefined,
      from: undefined,
      to: undefined,
    });
  });

  it('自定义日期与月份互斥，均换算为 ISO 区间', () => {
    const state = {
      type: 1 as const,
      categoryId: 3,
      month: '',
      startDate: '2026-11-01',
      endDate: '2026-11-30',
      keyword: '',
    };
    expect(ledgerFilterKey(state)).toBe('1|3||2026-11-01|2026-11-30|');
    expect(parseLedgerFilterKey(ledgerFilterKey(state))).toEqual(state);
    expect(ledgerFilterQuery(state)).toEqual({
      type: 1,
      categoryId: 3,
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
    });
  });

  it('月份展开为当月首末日', () => {
    expect(
      ledgerFilterQuery({
        type: '',
        categoryId: 0,
        month: '2024-02',
        startDate: '',
        endDate: '',
        keyword: '',
      }),
    ).toEqual({
      type: undefined,
      categoryId: undefined,
      from: '2024-02-01T00:00:00+08:00',
      to: '2024-02-29T23:59:59+08:00',
    });
  });

  it('关键字编码进 key，只进列表与日小计', () => {
    const state = {
      type: '' as const,
      categoryId: 0,
      month: '2026-11',
      startDate: '',
      endDate: '',
      keyword: '场地|A',
    };
    expect(ledgerFilterKey(state)).toContain('%7C');
    expect(parseLedgerFilterKey(ledgerFilterKey(state))).toEqual(state);
    expect(ledgerFilterQuery(state)).toMatchObject({ keyword: '场地|A' });
    expect(ledgerSummaryQuery(state)).not.toHaveProperty('keyword');
  });

  it('筛选标记忽略月份与关键字', () => {
    const base = {
      type: '' as const,
      categoryId: 0,
      month: '2026-11',
      startDate: '',
      endDate: '',
      keyword: '场地',
    };
    expect(ledgerIsFiltered(base)).toBe(false);
    expect(ledgerIsFiltered({ ...base, type: 1 as const })).toBe(true);
    expect(ledgerIsFiltered({ ...base, categoryId: 3 })).toBe(true);
    expect(ledgerIsFiltered({ ...base, startDate: '2026-11-01', endDate: '2026-11-02' })).toBe(
      true,
    );
  });

  it('非法或空片段回落到默认值', () => {
    expect(parseLedgerFilterKey('')).toEqual(DEFAULT_LEDGER_FILTER);
    expect(parseLedgerFilterKey('|x||')).toEqual(DEFAULT_LEDGER_FILTER);
  });

  it('统计周期切换不因月末日溢出', () => {
    expect(shiftStatsAnchor('2026-01-29', 'month', 1)).toBe('2026-02-15');
    expect(shiftStatsAnchor('2026-03-31', 'month', -1)).toBe('2026-02-15');
    expect(shiftStatsAnchor('2026-11-15', 'year', 1)).toBe('2027-11-15');
    expect(shiftStatsAnchor('2026-11-15', 'week', 1)).toBe('2026-11-22');
    expect(shiftStatsAnchor('2026-11-15', 'custom', 1)).toBe('2026-11-15');
  });

  it('统计请求按周期选择 anchor 或 from/to', () => {
    expect(
      ledgerStatisticsQuery({
        period: 'month',
        anchor: '2026-11-26',
        from: '',
        to: '',
      }),
    ).toEqual({ period: 'month', anchor: '2026-11-26' });
    expect(
      ledgerStatisticsQuery({
        period: 'custom',
        anchor: '2026-11-26',
        from: '2026-11-01',
        to: '2026-11-20',
      }),
    ).toEqual({ period: 'custom', from: '2026-11-01', to: '2026-11-20' });
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
