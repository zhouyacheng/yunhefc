import { describe, expect, it } from 'vitest';

import {
  STATUS_OPTIONS,
  TYPE_OPTIONS,
  emptyScheduleFilters,
  formatCalendarYmd,
  fromScheduleForm,
  sameForm,
  scheduleFeedKey,
  scheduleRequestPath,
  toScheduleForm,
  ymdToLocalDate,
  type ScheduleForm,
} from '../miniprogram/utils/schedule-query';

/** 2026-11-15 12:00 Asia/Shanghai。 */
const NOW = Date.UTC(2026, 10, 15, 4);

function form(overrides: Partial<ScheduleForm> = {}): ScheduleForm {
  return {
    type: '',
    venue: '',
    status: '',
    month: '',
    startDate: '',
    endDate: '',
    ...overrides,
  };
}

describe('schedule defaults', () => {
  it('starts without any filter and without a month param', () => {
    expect(scheduleFeedKey(emptyScheduleFilters())).toBe('');
    expect(scheduleRequestPath('', 1)).toBe(
      '/matches?scope=all&sort=startAt:desc&page=1&pageSize=10',
    );
  });

  it('prefills the current Shanghai month in the form only', () => {
    const defaults = toScheduleForm(emptyScheduleFilters(), NOW);
    expect(defaults).toEqual(form({ month: '2026-11' }));
    expect(scheduleFeedKey(emptyScheduleFilters())).toBe('');
  });

  it('lets the prefilled month participate after any filter change', () => {
    const prefilled = toScheduleForm(emptyScheduleFilters(), NOW);
    expect(prefilled.month).toBe('2026-11');
    expect(scheduleFeedKey(emptyScheduleFilters())).toBe('');

    const changed = fromScheduleForm({ ...prefilled, type: '0' });
    expect(scheduleFeedKey(changed)).toBe('type=0&month=2026-11');
  });

  it('treats type 0 and status 0 as valid filter values', () => {
    expect(scheduleFeedKey(fromScheduleForm(form({ type: '0', status: '0' })))).toBe(
      'type=0&status=0',
    );
  });
});

describe('time filters', () => {
  it('lets a valid range win over the month and clears the month', () => {
    const filters = fromScheduleForm(
      form({ month: '2026-11', startDate: '2026-11-01', endDate: '2026-11-30' }),
    );
    expect(filters.month).toBeNull();
    expect(scheduleFeedKey(filters)).toBe('startDate=2026-11-01&endDate=2026-11-30');
  });

  it('drops the time filter when the month is cleared', () => {
    expect(scheduleFeedKey(fromScheduleForm(form({ type: '1', month: '' })))).toBe('type=1');
  });

  it('does not restore the month when the range is cleared', () => {
    expect(scheduleFeedKey(fromScheduleForm(form({ month: '', startDate: '', endDate: '' })))).toBe(
      '',
    );
  });

  it('ignores impossible or reversed dates and falls back to the month', () => {
    expect(
      scheduleFeedKey(
        fromScheduleForm(
          form({ month: '2026-11', startDate: '2026-02-30', endDate: '2026-03-01' }),
        ),
      ),
    ).toBe('month=2026-11');
    expect(
      scheduleFeedKey(
        fromScheduleForm(
          form({ month: '2026-11', startDate: '2026-11-30', endDate: '2026-11-01' }),
        ),
      ),
    ).toBe('month=2026-11');
    expect(scheduleFeedKey(fromScheduleForm(form({ startDate: '2026-11-01', endDate: '' })))).toBe(
      '',
    );
  });

  it('rejects malformed months and enum codes', () => {
    expect(scheduleFeedKey(fromScheduleForm(form({ month: '2026-13' })))).toBe('');
    expect(scheduleFeedKey(fromScheduleForm(form({ type: '9', status: 'x' })))).toBe('');
  });
});

describe('venue and request path', () => {
  it('trims and percent-encodes the venue exactly once', () => {
    const key = scheduleFeedKey(fromScheduleForm(form({ venue: '  聚龙 ' })));
    expect(key).toBe('venue=%E8%81%9A%E9%BE%99');
    expect(scheduleRequestPath(key, 2)).toBe(
      '/matches?scope=all&sort=startAt:desc&page=2&pageSize=10&venue=%E8%81%9A%E9%BE%99',
    );
  });

  it('always sends scope, sort, page and pageSize', () => {
    expect(scheduleRequestPath('status=4', 3, 20)).toBe(
      '/matches?scope=all&sort=startAt:desc&page=3&pageSize=20&status=4',
    );
    expect(scheduleRequestPath('', 0)).toContain('page=1');
  });
});

describe('calendar helpers', () => {
  it('formats local calendar days without shifting the day', () => {
    expect(formatCalendarYmd(new Date(2026, 11, 3))).toBe('2026-12-03');
    expect(formatCalendarYmd(new Date(2026, 0, 1))).toBe('2026-01-01');
  });

  it('parses YYYY-MM-DD into a local date', () => {
    const date = ymdToLocalDate('2026-12-03');
    expect(date?.getFullYear()).toBe(2026);
    expect(date?.getMonth()).toBe(11);
    expect(date?.getDate()).toBe(3);
    expect(ymdToLocalDate('2026-02-30')).toBeNull();
    expect(ymdToLocalDate('')).toBeNull();
  });
});

describe('form comparison and options', () => {
  it('compares every form field', () => {
    expect(sameForm(form(), form())).toBe(true);
    expect(sameForm(form(), form({ venue: '聚龙' }))).toBe(false);
    expect(sameForm(form({ month: '2026-11' }), form({ month: '2026-12' }))).toBe(false);
  });

  it('builds picker options with the "all" entry first', () => {
    expect(TYPE_OPTIONS[0]).toEqual({ value: '', label: '全部类型' });
    expect(TYPE_OPTIONS.map((option) => option.label)).toEqual([
      '全部类型',
      '对外比赛',
      '队内赛',
      '训练赛',
      '友谊赛',
    ]);
    expect(STATUS_OPTIONS[0]).toEqual({ value: '', label: '全部状态' });
    expect(STATUS_OPTIONS).toHaveLength(8);
  });
});
