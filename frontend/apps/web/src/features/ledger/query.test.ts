import { describe, expect, it } from 'vitest';

import {
  ledgerDayRange,
  ledgerRangeEnd,
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

  it('自定义日期优先，其次月份，非法值不生效', () => {
    expect(
      ledgerDayRange({
        month: '2026-11',
        startDate: '2026-11-03',
        endDate: '2026-11-09',
      }),
    ).toEqual({ from: '2026-11-03', to: '2026-11-09' });
    expect(ledgerDayRange({ month: '2026-02', startDate: '', endDate: '' })).toEqual({
      from: '2026-02-01',
      to: '2026-02-28',
    });
    expect(ledgerDayRange({ month: '2024-02', startDate: '', endDate: '' })).toEqual({
      from: '2024-02-01',
      to: '2024-02-29',
    });
    expect(ledgerDayRange({ month: '', startDate: '2026-11-20', endDate: '' })).toBeNull();
    expect(
      ledgerDayRange({ month: '', startDate: '2026-11-20', endDate: '2026-11-19' }),
    ).toBeNull();
    expect(ledgerDayRange({ month: '1999-12', startDate: '', endDate: '' })).toBeNull();
  });
});

describe('请求参数', () => {
  it('自定义日期只下发已选筛选', () => {
    expect(
      toLedgerFilter({
        type: 1,
        categoryId: 2,
        month: '',
        startDate: '2026-11-01',
        endDate: '2026-11-30',
      }),
    ).toEqual({
      type: 1,
      categoryId: 2,
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
    });
  });

  it('月份展开为当月首末日', () => {
    expect(
      toLedgerFilter({
        type: null,
        categoryId: null,
        month: '2026-11',
        startDate: '',
        endDate: '',
      }),
    ).toEqual({
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
    });
  });

  it('未筛选时不带多余参数', () => {
    expect(
      toLedgerFilter({ type: null, categoryId: null, month: '', startDate: '', endDate: '' }),
    ).toEqual({});
  });

  it('列表请求追加分页，汇总请求不带分页', () => {
    expect(
      toLedgerRequest({
        type: 1,
        categoryId: null,
        month: '',
        startDate: '',
        endDate: '',
        page: 2,
        pageSize: 20,
      }),
    ).toEqual({ type: 1, page: 2, pageSize: 20 });
  });
});
