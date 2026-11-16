import { describe, expect, it } from 'vitest';

import {
  DEFAULT_PAGE_SIZE,
  fromScheduleForm,
  parseScheduleQuery,
  resetScheduleQuery,
  serializeScheduleQuery,
  toMatchListQuery,
  toScheduleForm,
  withPage,
  withPageSize,
  withScheduleFilters,
} from './query';

const noTimeFilters = { month: null, monthAll: false, startDate: null, endDate: null };

describe('parseScheduleQuery', () => {
  it('falls back to defaults for an empty query', () => {
    expect(parseScheduleQuery({})).toEqual({
      type: null,
      venue: '',
      status: null,
      ...noTimeFilters,
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
    });
  });

  it('ignores illegal type, status, month and pageSize', () => {
    expect(
      parseScheduleQuery({
        type: '9',
        status: '-1',
        month: '2026-13',
        page: '0',
        pageSize: '15',
      }),
    ).toEqual({
      type: null,
      venue: '',
      status: null,
      ...noTimeFilters,
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
    });
  });

  it('parses a month filter', () => {
    expect(parseScheduleQuery({ month: '2026-11' })).toMatchObject({
      month: '2026-11',
      monthAll: false,
      startDate: null,
      endDate: null,
    });
  });

  it('treats month=all as explicit no-time-filter', () => {
    expect(parseScheduleQuery({ month: 'all' })).toMatchObject({
      month: null,
      monthAll: true,
    });
  });

  it('lets a valid range win over month', () => {
    expect(
      parseScheduleQuery({
        month: '2026-11',
        startDate: '2026-11-01',
        endDate: '2026-11-15',
      }),
    ).toMatchObject({
      month: null,
      monthAll: true,
      startDate: '2026-11-01',
      endDate: '2026-11-15',
    });
  });

  it('ignores one-sided and reversed ranges', () => {
    expect(parseScheduleQuery({ startDate: '2026-11-01', month: '2026-11' })).toMatchObject({
      month: '2026-11',
      monthAll: false,
      startDate: null,
      endDate: null,
    });
    expect(
      parseScheduleQuery({
        month: '2026-11',
        startDate: '2026-11-15',
        endDate: '2026-11-01',
      }),
    ).toMatchObject({ month: '2026-11', monthAll: false, startDate: null, endDate: null });
  });

  it('rejects impossible calendar dates', () => {
    expect(parseScheduleQuery({ startDate: '2026-02-30', endDate: '2026-03-01' })).toMatchObject(
      noTimeFilters,
    );
  });

  it('takes the first value when a param repeats', () => {
    expect(parseScheduleQuery({ status: ['4', '0'] })).toMatchObject({ status: 4 });
  });

  it('keeps code 0 as a real filter value', () => {
    expect(parseScheduleQuery({ type: '0', status: '0' })).toMatchObject({ type: 0, status: 0 });
  });
});

describe('serializeScheduleQuery', () => {
  it('writes nothing for defaults', () => {
    expect(serializeScheduleQuery(resetScheduleQuery())).toEqual({});
  });

  it('round-trips a full query', () => {
    const raw = {
      type: '1',
      venue: '聚龙',
      status: '4',
      month: '2026-11',
      page: '2',
      pageSize: '20',
    };
    expect(serializeScheduleQuery(parseScheduleQuery(raw))).toEqual(raw);
  });

  it('writes month=all for explicit no-time-filter', () => {
    expect(
      serializeScheduleQuery({
        ...resetScheduleQuery(),
        monthAll: true,
      }),
    ).toEqual({ month: 'all' });
  });

  it('writes a range without month=all', () => {
    expect(
      serializeScheduleQuery({
        ...resetScheduleQuery(),
        monthAll: true,
        startDate: '2026-11-01',
        endDate: '2026-11-15',
      }),
    ).toEqual({ startDate: '2026-11-01', endDate: '2026-11-15' });
  });
});

describe('form conversion', () => {
  it('prefills the current Shanghai month for the default state', () => {
    expect(toScheduleForm(parseScheduleQuery({}), new Date('2026-10-31T16:30:00Z'))).toMatchObject({
      month: '2026-11',
      startDate: '',
      endDate: '',
    });
    expect(toScheduleForm(parseScheduleQuery({}), new Date('2026-10-31T15:30:00Z'))).toMatchObject({
      month: '2026-10',
    });
  });

  it('leaves month blank for explicit all and for ranges', () => {
    expect(toScheduleForm(parseScheduleQuery({ month: 'all' })).month).toBe('');
    expect(
      toScheduleForm(parseScheduleQuery({ startDate: '2026-11-01', endDate: '2026-11-15' })),
    ).toMatchObject({ month: '', startDate: '2026-11-01', endDate: '2026-11-15' });
  });

  it('applies a month when no range is supplied', () => {
    expect(
      fromScheduleForm({
        type: '1',
        venue: ' 聚龙 ',
        status: '4',
        month: '2026-11',
        startDate: '',
        endDate: '',
      }),
    ).toEqual({
      type: 1,
      venue: '聚龙',
      status: 4,
      month: '2026-11',
      monthAll: false,
      startDate: null,
      endDate: null,
    });
  });

  it('lets a valid range win over a month', () => {
    expect(
      fromScheduleForm({
        type: '',
        venue: '',
        status: '',
        month: '2026-11',
        startDate: '2026-11-01',
        endDate: '2026-11-15',
      }),
    ).toMatchObject({
      month: null,
      monthAll: true,
      startDate: '2026-11-01',
      endDate: '2026-11-15',
    });
  });

  it('marks a cleared month without a range as explicit all', () => {
    expect(
      fromScheduleForm({
        type: '',
        venue: '',
        status: '',
        month: '',
        startDate: '',
        endDate: '',
      }),
    ).toMatchObject({ month: null, monthAll: true, startDate: null, endDate: null });
  });

  it('maps a query back to form strings', () => {
    expect(toScheduleForm(parseScheduleQuery({ type: '0', status: '4' }))).toMatchObject({
      type: '0',
      venue: '',
      status: '4',
    });
  });
});

describe('query transitions', () => {
  const paged = { ...parseScheduleQuery({}), page: 3 };

  it('returns to page 1 when filters change', () => {
    expect(
      withScheduleFilters(paged, {
        type: null,
        venue: '聚龙',
        status: null,
        ...noTimeFilters,
      }),
    ).toMatchObject({ venue: '聚龙', page: 1, pageSize: DEFAULT_PAGE_SIZE });
  });

  it('returns to page 1 when pageSize changes', () => {
    expect(withPageSize(paged, 50)).toMatchObject({ page: 1, pageSize: 50 });
  });

  it('resets filters and page but keeps the requested pageSize', () => {
    expect(resetScheduleQuery(20)).toEqual({
      type: null,
      venue: '',
      status: null,
      ...noTimeFilters,
      page: 1,
      pageSize: 20,
    });
  });

  it('changes only the page', () => {
    expect(withPage(paged, 2)).toMatchObject({ page: 2, pageSize: DEFAULT_PAGE_SIZE });
  });
});

describe('toMatchListQuery', () => {
  it('fixes scope and sort and skips empty filters', () => {
    expect(toMatchListQuery(parseScheduleQuery({}))).toEqual({
      scope: 'all',
      sort: 'startAt:desc',
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
    });
  });

  it('sends month and drops blank venue', () => {
    expect(
      toMatchListQuery(
        parseScheduleQuery({
          type: '1',
          venue: '  ',
          status: '4',
          month: '2026-11',
        }),
      ),
    ).toEqual({
      scope: 'all',
      sort: 'startAt:desc',
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
      type: 1,
      status: 4,
      month: '2026-11',
    });
  });

  it('never sends month=all', () => {
    expect(toMatchListQuery(parseScheduleQuery({ month: 'all' }))).not.toHaveProperty('month');
  });

  it('sends a date range instead of month', () => {
    expect(
      toMatchListQuery(
        parseScheduleQuery({
          month: '2026-11',
          startDate: '2026-11-01',
          endDate: '2026-11-15',
        }),
      ),
    ).toEqual({
      scope: 'all',
      sort: 'startAt:desc',
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
      startDate: '2026-11-01',
      endDate: '2026-11-15',
    });
  });
});
