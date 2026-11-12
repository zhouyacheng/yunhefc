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

describe('parseScheduleQuery', () => {
  it('falls back to defaults for an empty query', () => {
    expect(parseScheduleQuery({})).toEqual({
      type: null,
      venue: '',
      status: null,
      season: null,
      month: null,
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
        season: '26',
        page: '0',
        pageSize: '15',
      }),
    ).toEqual({
      type: null,
      venue: '',
      status: null,
      season: null,
      month: null,
      page: 1,
      pageSize: DEFAULT_PAGE_SIZE,
    });
  });

  it('lets month win over season', () => {
    expect(parseScheduleQuery({ season: '2026', month: '2026-11' })).toMatchObject({
      season: null,
      month: '2026-11',
    });
  });

  it('keeps season when month is illegal', () => {
    expect(parseScheduleQuery({ season: '2025', month: '2026-13' })).toMatchObject({
      season: '2025',
      month: null,
    });
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
      season: '2026',
      page: '2',
      pageSize: '20',
    };
    expect(serializeScheduleQuery(parseScheduleQuery(raw))).toEqual(raw);
  });

  it('omits season when month is present', () => {
    expect(
      serializeScheduleQuery({
        ...resetScheduleQuery(),
        season: '2026',
        month: '2026-11',
      }),
    ).toEqual({ month: '2026-11' });
  });
});

describe('form conversion', () => {
  it('clears season when a month is supplied', () => {
    expect(
      fromScheduleForm({
        type: '1',
        venue: ' 聚龙 ',
        status: '4',
        season: '2025',
        month: '2026-11',
      }),
    ).toEqual({
      type: 1,
      venue: '聚龙',
      status: 4,
      season: null,
      month: '2026-11',
    });
  });

  it('maps a query back to form strings', () => {
    expect(toScheduleForm(parseScheduleQuery({ type: '0', season: '2026', status: '4' }))).toEqual({
      type: '0',
      venue: '',
      status: '4',
      season: '2026',
      month: '',
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
        season: null,
        month: null,
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
      season: null,
      month: null,
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

  it('sends month instead of season and drops blank venue', () => {
    expect(
      toMatchListQuery(
        parseScheduleQuery({
          type: '1',
          venue: '  ',
          status: '4',
          season: '2025',
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
});
