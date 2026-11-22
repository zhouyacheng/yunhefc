import { describe, expect, it } from 'vitest';

import {
  DEFAULT_PAGE_SIZE,
  filterQueryString,
  normalize,
  type ScheduleFilters,
} from '../miniprogram/utils/match-query';

function filters(overrides: Partial<ScheduleFilters> = {}): ScheduleFilters {
  return {
    type: null,
    venue: '',
    status: null,
    month: null,
    startDate: null,
    endDate: null,
    ...overrides,
  };
}

describe('normalize', () => {
  it('lets a valid range win over the month and clears the month', () => {
    expect(
      normalize(filters({ month: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30' })),
    ).toEqual(filters({ month: null, startDate: '2026-11-01', endDate: '2026-11-30' }));
  });

  it('falls back to the month when the range is impossible, reversed or half-filled', () => {
    const month = filters({ month: '2026-11' });
    expect(
      normalize(filters({ month: '2026-11', startDate: '2026-02-30', endDate: '2026-03-01' })),
    ).toEqual(month);
    expect(
      normalize(filters({ month: '2026-11', startDate: '2026-11-30', endDate: '2026-11-01' })),
    ).toEqual(month);
    expect(normalize(filters({ startDate: '2026-11-01' }))).toEqual(filters());
  });

  it('trims the venue', () => {
    expect(normalize(filters({ venue: '  聚龙 ' })).venue).toBe('聚龙');
  });

  it('drops a malformed month', () => {
    expect(normalize(filters({ month: '2026-13' }))).toEqual(filters());
  });
});

describe('filterQueryString', () => {
  it('produces an empty string for empty filters', () => {
    expect(filterQueryString(filters())).toBe('');
  });

  it('keeps a stable order: type, venue, status, then range', () => {
    expect(
      filterQueryString(
        filters({
          type: 0,
          venue: ' 聚龙 ',
          status: 0,
          startDate: '2026-11-01',
          endDate: '2026-11-30',
        }),
      ),
    ).toBe('type=0&venue=%E8%81%9A%E9%BE%99&status=0&startDate=2026-11-01&endDate=2026-11-30');
  });

  it('uses the month only when there is no valid range', () => {
    expect(filterQueryString(filters({ month: '2026-11' }))).toBe('month=2026-11');
    expect(
      filterQueryString(
        filters({ month: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30' }),
      ),
    ).toBe('startDate=2026-11-01&endDate=2026-11-30');
  });

  it('normalizes before encoding', () => {
    expect(filterQueryString(filters({ type: 1, venue: '   ', month: '' }))).toBe('type=1');
  });
});

describe('defaults', () => {
  it('exposes the list page size', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(10);
  });
});
