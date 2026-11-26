import type { LedgerCategory } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  LEDGER_AMOUNT_MAX,
  categoriesForType,
  ledgerDirectionText,
  ledgerTypeText,
  validateLedgerAmount,
  validateLedgerDescription,
} from './form';

function category(overrides: Partial<LedgerCategory>): LedgerCategory {
  return {
    id: 1,
    name: '场地',
    direction: 1,
    directionLabel: 'EXPENSE',
    sortNo: 0,
    isActive: true,
    entryCount: 0,
    ...overrides,
  };
}

describe('validateLedgerAmount', () => {
  it('接受正数且最多两位小数', () => {
    expect(validateLedgerAmount('88.50')).toBeNull();
    expect(validateLedgerAmount('0.01')).toBeNull();
    expect(validateLedgerAmount(String(LEDGER_AMOUNT_MAX))).toBeNull();
  });

  it('拒绝空值、0、负数与三位小数', () => {
    expect(validateLedgerAmount('')).toBe('请输入金额');
    expect(validateLedgerAmount('0')).toBe('金额必须大于 0');
    expect(validateLedgerAmount('0.00')).toBe('金额必须大于 0');
    expect(validateLedgerAmount('-1')).toBe('金额必须大于 0');
    expect(validateLedgerAmount('88.555')).toBe('金额最多两位小数');
    expect(validateLedgerAmount(String(LEDGER_AMOUNT_MAX + 0.01))).toBe(
      '金额不能超过 99,999,999.99',
    );
  });
});

describe('validateLedgerDescription', () => {
  it('允许空白摘要', () => {
    expect(validateLedgerDescription('')).toBeNull();
    expect(validateLedgerDescription('   ')).toBeNull();
  });

  it('限制 255 字', () => {
    expect(validateLedgerDescription('字'.repeat(255))).toBeNull();
    expect(validateLedgerDescription('字'.repeat(256))).toBe('摘要不能超过 255 字');
  });
});

describe('categoriesForType', () => {
  it('只保留启用且方向匹配或通用的分类', () => {
    const categories = [
      category({ id: 1, name: '队费', direction: 0 }),
      category({ id: 2, name: '场地', direction: 1 }),
      category({ id: 3, name: '其他', direction: null }),
      category({ id: 4, name: '停用', direction: 1, isActive: false }),
    ];

    expect(categoriesForType(categories, 0).map((item) => item.id)).toEqual([1, 3]);
    expect(categoriesForType(categories, 1).map((item) => item.id)).toEqual([2, 3]);
  });
});

describe('账本文案与金额', () => {
  it('映射收入/支出/通用', () => {
    expect(ledgerTypeText(0)).toBe('收入');
    expect(ledgerTypeText(1)).toBe('支出');
    expect(ledgerDirectionText(0)).toBe('收入');
    expect(ledgerDirectionText(1)).toBe('支出');
    expect(ledgerDirectionText(null)).toBe('通用');
  });
});
