import { describe, expect, it } from 'vitest';

import {
  ledgerRangeEnd,
  ledgerRangeError,
  ledgerRangeStart,
  parseLedgerDate,
  parseLedgerType,
  toLedgerFilter,
  toLedgerRequest,
} from './query';

describe('筛选解析', () => {
  it('解析方向与自然日', () => {
    expect(parseLedgerType('0')).toBe(0);
    expect(parseLedgerType('1')).toBe(1);
    expect(parseLedgerType('')).toBeNull();
    expect(parseLedgerDate('2026-11-20')).toBe('2026-11-20');
    expect(parseLedgerDate('2026-02-30')).toBeNull();
    expect(parseLedgerDate('2026/11/20')).toBeNull();
  });

  it('自然日展开为当天起止 ISO 时间', () => {
    expect(ledgerRangeStart('2026-11-20')).toBe('2026-11-20T00:00:00+08:00');
    expect(ledgerRangeEnd('2026-11-20')).toBe('2026-11-20T23:59:59+08:00');
    expect(ledgerRangeStart('')).toBeNull();
    expect(ledgerRangeStart('2026-13-01')).toBeNull();
  });

  it('区间倒置给出提示', () => {
    expect(ledgerRangeError({ from: '2026-11-20', to: '2026-11-19' })).toBe(
      '开始日期不能晚于结束日期',
    );
    expect(ledgerRangeError({ from: '2026-11-19', to: '2026-11-20' })).toBe('');
  });
});

describe('请求参数', () => {
  it('只下发已选筛选', () => {
    expect(
      toLedgerFilter({
        type: 1,
        categoryId: 2,
        from: '2026-11-01',
        to: '2026-11-30',
      }),
    ).toEqual({
      type: 1,
      categoryId: 2,
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
    });
  });

  it('未筛选时不带多余参数', () => {
    expect(toLedgerFilter({ type: null, categoryId: null, from: '', to: '' })).toEqual({});
  });

  it('列表请求追加分页，汇总请求不带分页', () => {
    expect(
      toLedgerRequest({
        type: 1,
        categoryId: null,
        from: '',
        to: '',
        page: 2,
        pageSize: 20,
      }),
    ).toEqual({ type: 1, page: 2, pageSize: 20 });
  });
});
