import type { LedgerCategory } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  amountSign,
  categoryLabelMap,
  categoryOptions,
  entryCategoryLabel,
  entryMetaText,
  entryTitle,
  formatEntryAmount,
  formatMoney,
  ledgerFlow,
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

describe('分类选项', () => {
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

  it('摘要主导，元信息按分类/时间/归属拼接', () => {
    const labels = categoryLabelMap(CATEGORIES);
    expect(entryTitle(base, labels)).toBe('11 月场地费（4 场）');
    expect(entryMetaText(base, labels)).toBe('场地 · 2026/11/20');
    expect(entryMetaText({ ...base, playerName: '张伟', activityTitle: '队内活动' }, labels)).toBe(
      '场地 · 2026/11/20 · 归属 张伟 · 队内活动',
    );
  });

  it('空摘要回退分类名且不重复分类', () => {
    const labels = categoryLabelMap(CATEGORIES);
    const empty = { ...base, description: '  ' };
    expect(entryTitle(empty, labels)).toBe('场地');
    expect(entryMetaText(empty, labels)).toBe('2026/11/20');
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

  it('支出超过收入时填满并说明超出比例', () => {
    expect(ledgerFlow({ income: 800, expense: 1200 })).toEqual({
      ratioPercent: 100,
      ratioLabel: '支出超过收入 50%',
    });
  });

  it('无支出隐藏条形与文案，无收入时不给比例', () => {
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
