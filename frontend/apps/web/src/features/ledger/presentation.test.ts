import type { LedgerCategory } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  categoryLabelMap,
  categoryOptions,
  entryCategoryLabel,
  formatMoney,
  formatSignedAmount,
  ledgerTypeText,
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

  it('流水金额带方向前缀，负号与余额一致', () => {
    expect(formatSignedAmount({ type: 0, amount: 300 })).toBe('+¥300.00');
    expect(formatSignedAmount({ type: 1, amount: 800 })).toBe('−¥800.00');
  });

  it('方向文案', () => {
    expect(ledgerTypeText(0)).toBe('收入');
    expect(ledgerTypeText(1)).toBe('支出');
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
