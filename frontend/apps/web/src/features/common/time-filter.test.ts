import { describe, expect, it } from 'vitest';

import {
  formatLocalDate,
  normalizeTimeFilter,
  parseDate,
  parseMonth,
  timeParamEntries,
  toLocalDate,
} from './time-filter';

describe('parseMonth', () => {
  it('接受 2000-01 至 2100-12', () => {
    expect(parseMonth('2000-01')).toBe('2000-01');
    expect(parseMonth('2100-12')).toBe('2100-12');
    expect(parseMonth('2026-11')).toBe('2026-11');
  });

  it('拒绝越界与非法格式', () => {
    expect(parseMonth('1999-12')).toBeNull();
    expect(parseMonth('2101-01')).toBeNull();
    expect(parseMonth('2026-13')).toBeNull();
    expect(parseMonth('2026-1')).toBeNull();
  });
});

describe('parseDate', () => {
  it('拒绝不存在的日期', () => {
    expect(parseDate('2026-02-30')).toBeNull();
    expect(parseDate('2026-11-01')).toBe('2026-11-01');
  });
});

describe('normalizeTimeFilter', () => {
  it('有效范围优先于月份', () => {
    expect(
      normalizeTimeFilter({ month: '2026-11', startDate: '2026-11-01', endDate: '2026-11-10' }),
    ).toEqual({ month: null, startDate: '2026-11-01', endDate: '2026-11-10' });
  });

  it('非法时间回落为空', () => {
    expect(normalizeTimeFilter({ month: '1800-01', startDate: null, endDate: null })).toEqual({
      month: null,
      startDate: null,
      endDate: null,
    });
    expect(
      normalizeTimeFilter({ month: null, startDate: '2026-11-10', endDate: '2026-11-01' }),
    ).toEqual({ month: null, startDate: null, endDate: null });
  });
});

describe('timeParamEntries', () => {
  it('范围优先，其次月份，均无则空', () => {
    expect(
      timeParamEntries({ month: null, startDate: '2026-11-01', endDate: '2026-11-10' }),
    ).toEqual([
      ['startDate', '2026-11-01'],
      ['endDate', '2026-11-10'],
    ]);
    expect(timeParamEntries({ month: '2026-11', startDate: null, endDate: null })).toEqual([
      ['month', '2026-11'],
    ]);
    expect(timeParamEntries({ month: null, startDate: null, endDate: null })).toEqual([]);
  });
});

describe('toLocalDate/formatLocalDate', () => {
  it('严格解析并往返格式化', () => {
    const date = toLocalDate('2026-11-01');
    expect(date).not.toBeNull();
    expect(formatLocalDate(date as Date)).toBe('2026-11-01');
    expect(toLocalDate('2026-02-30')).toBeNull();
  });
});
