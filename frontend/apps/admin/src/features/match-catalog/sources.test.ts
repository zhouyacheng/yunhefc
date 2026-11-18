import type { MatchSummary, Page } from '@yunhefc/shared';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  createAdminMatchSource,
  createPublicMatchSource,
  INTERNAL_FETCH_SIZE,
  MATCH_PAGE_SIZE,
} from './sources';
import type { MatchApiClient } from './types';

function match(id: number, season = '2026', type: MatchSummary['type'] = 1): MatchSummary {
  return {
    id,
    title: `比赛${id}`,
    type,
    typeLabel: type === 1 ? '队内赛' : '对外比赛',
    startAt: '2026-11-17T12:00:00Z',
    endAt: '2026-11-17T14:00:00Z',
    venue: '球场',
    season,
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    ourSide: null,
    ourSideLabel: null,
    scoreHome: null,
    scoreAway: null,
    resultText: null,
    status: 2,
    statusLabel: '已锁定',
    totalMatchNo: id,
    seasonMatchNo: id,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
  };
}

function pageResult(
  items: MatchSummary[],
  total: number,
  page = 1,
  pageSize = MATCH_PAGE_SIZE,
): Page<MatchSummary> {
  return {
    items,
    total,
    page,
    pageSize,
    totalPages: Math.max(1, Math.ceil(total / pageSize)),
  };
}

function queryOf(callIndex: number): Record<string, unknown> {
  const call = get.mock.calls[callIndex] as unknown[] | undefined;
  const options = call?.[1] as { query?: Record<string, unknown> } | undefined;
  return options?.query ?? {};
}

const get = vi.fn();
const client = { get } as unknown as MatchApiClient;

beforeEach(() => {
  get.mockReset();
});

describe('public server source', () => {
  it('保留请求参数，并在越界时按最后一页重请求', async () => {
    get
      .mockResolvedValueOnce(pageResult([match(1)], 15, 1, MATCH_PAGE_SIZE))
      .mockResolvedValueOnce(pageResult([match(15)], 15, 2, MATCH_PAGE_SIZE));
    const source = createPublicMatchSource(client);

    const result = await source.list(
      { season: '2026', monthRange: ['2026-01', '2026-03'], status: 1 },
      3,
    );

    expect(get).toHaveBeenCalledTimes(2);
    expect(queryOf(0)).toMatchObject({
      scope: 'all',
      sort: 'startAt:desc',
      page: 3,
      pageSize: MATCH_PAGE_SIZE,
      season: '2026',
      status: 1,
      startDate: '2026-01-01',
      endDate: '2026-03-31',
    });
    expect(queryOf(1)).toMatchObject({ page: 2 });
    expect(result.page).toBe(2);
    expect(result.items.map((item) => item.id)).toEqual([15]);
    expect(result.truncated).toBe(false);
  });

  it('单月范围走 MAT-02 的 month，不拼日期', async () => {
    get.mockResolvedValueOnce(pageResult([match(1)], 1));
    const source = createPublicMatchSource(client);

    await source.list({ season: '2026', monthRange: ['2026-02', '2026-02'], status: '' }, 1);

    expect(queryOf(0)).toMatchObject({ season: '2026', month: '2026-02' });
    expect(queryOf(0)).not.toHaveProperty('startDate');
    expect(queryOf(0)).not.toHaveProperty('endDate');
  });

  it('跨月范围按闰年计算末月最后一天', async () => {
    get.mockResolvedValueOnce(pageResult([match(1)], 1));
    const source = createPublicMatchSource(client);

    await source.list({ season: '2028', monthRange: ['2028-01', '2028-02'], status: '' }, 1);

    expect(queryOf(0)).toMatchObject({
      startDate: '2028-01-01',
      endDate: '2028-02-29',
    });
  });

  it('正常分页不触发越界重取', async () => {
    get.mockResolvedValueOnce(pageResult([match(2)], 25, 2, MATCH_PAGE_SIZE));
    const source = createPublicMatchSource(client);

    const result = await source.list({ season: '2026', monthRange: null, status: '' }, 2);

    expect(get).toHaveBeenCalledTimes(1);
    expect(queryOf(0)).toMatchObject({ page: 2, pageSize: MATCH_PAGE_SIZE });
    expect(result.page).toBe(2);
    expect(result.totalPages).toBe(3);
  });

  it('totalPages=0 时把 page 夹到 1，且不重复请求', async () => {
    get.mockResolvedValueOnce({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 });
    const source = createPublicMatchSource(client);

    const result = await source.list({ season: '2026', monthRange: null, status: '' }, 3);

    expect(get).toHaveBeenCalledTimes(1);
    expect(result.page).toBe(1);
    expect(result.totalPages).toBe(1);
    expect(result.items).toEqual([]);
  });

  it('空筛选不下发 season/status/month', async () => {
    get.mockResolvedValueOnce(pageResult([match(1)], 1));
    const source = createPublicMatchSource(client);

    await source.list({ season: '', monthRange: null, status: '' }, 1);

    expect(queryOf(0)).toMatchObject({ scope: 'all', sort: 'startAt:desc', page: 1 });
    expect(queryOf(0)).not.toHaveProperty('season');
    expect(queryOf(0)).not.toHaveProperty('status');
    expect(queryOf(0)).not.toHaveProperty('month');
    expect(queryOf(0)).not.toHaveProperty('startDate');
  });
});

describe('internalOnly snapshot source', () => {
  const filters = { season: '2026', monthRange: null, status: '' as const };

  function internalItems(): MatchSummary[] {
    return Array.from({ length: 12 }, (_, index) => match(index + 1, '2026', index % 2 ? 2 : 1));
  }

  it('取 100 条、过滤类型并按 10 条切片；翻页复用快照', async () => {
    get.mockResolvedValueOnce(pageResult(internalItems(), 120, 1, INTERNAL_FETCH_SIZE));
    const source = createPublicMatchSource(client, { internalOnly: true });

    const first = await source.list(filters, 1);
    const second = await source.list(filters, 2);
    const clamped = await source.list(filters, 3);

    expect(get).toHaveBeenCalledTimes(1);
    expect(queryOf(0)).toMatchObject({
      page: 1,
      pageSize: INTERNAL_FETCH_SIZE,
      season: '2026',
    });
    expect(first.items).toHaveLength(10);
    expect(first.total).toBe(12);
    expect(first.totalPages).toBe(2);
    expect(first.truncated).toBe(true);
    expect(second.items.map((item) => item.id)).toEqual([11, 12]);
    expect(clamped.page).toBe(2);
    expect(clamped.items.map((item) => item.id)).toEqual([11, 12]);
  });

  it('monthRange 进入快照请求', async () => {
    get.mockResolvedValueOnce(pageResult([], 0, 1, INTERNAL_FETCH_SIZE));
    const source = createPublicMatchSource(client, { internalOnly: true });

    await source.list({ season: '2026', monthRange: ['2026-01', '2026-01'], status: '' }, 1);

    expect(queryOf(0)).toMatchObject({
      pageSize: INTERNAL_FETCH_SIZE,
      month: '2026-01',
    });
  });

  it('force 绕过快照缓存，筛选变化重新取数', async () => {
    get
      .mockResolvedValueOnce(pageResult(internalItems(), 12, 1, INTERNAL_FETCH_SIZE))
      .mockResolvedValueOnce(pageResult([match(99)], 1, 1, INTERNAL_FETCH_SIZE))
      .mockResolvedValueOnce(pageResult([match(98)], 1, 1, INTERNAL_FETCH_SIZE));
    const source = createPublicMatchSource(client, { internalOnly: true });

    await source.list(filters, 1);
    await source.list(filters, 1, { force: true });
    await source.list({ ...filters, status: 2 }, 1);

    expect(get).toHaveBeenCalledTimes(3);
  });

  it('过滤掉对外比赛，byId 也拒绝非队内/训练赛', async () => {
    get
      .mockResolvedValueOnce(
        pageResult([match(1, '2026', 1), match(2, '2026', 0), match(3, '2026', 2)], 3, 1, 100),
      )
      .mockResolvedValueOnce(match(4, '2026', 0))
      .mockResolvedValueOnce(match(5, '2026', 2));
    const source = createPublicMatchSource(client, { internalOnly: true });

    const result = await source.list(filters, 1);

    expect(result.items.map((item) => item.id)).toEqual([1, 3]);
    expect(result.total).toBe(2);
    expect(await source.byId(4)).toBeNull();
    expect((await source.byId(5))?.id).toBe(5);
  });
});

describe('admin source', () => {
  it('列表、详情与赛季走 B 端同一组路径', async () => {
    get
      .mockResolvedValueOnce(pageResult([match(1)], 1))
      .mockResolvedValueOnce(match(7))
      .mockResolvedValueOnce({ items: ['2026', '2025'] });
    const source = createAdminMatchSource(client);

    const result = await source.list({ season: '2026', monthRange: null, status: '' }, 1);
    const detail = await source.byId(7);
    const seasons = await source.seasons();

    expect(queryOf(0)).toMatchObject({ page: 1, pageSize: MATCH_PAGE_SIZE });
    expect(result.pageSize).toBe(MATCH_PAGE_SIZE);
    expect(detail?.id).toBe(7);
    expect(seasons).toEqual(['2026', '2025']);
    expect(get.mock.calls[1]?.[0]).toBe('/matches/7');
    expect(get.mock.calls[2]?.[0]).toBe('/matches/seasons');
  });

  it('单月范围不发 month，换算成 startDate/endDate', async () => {
    get.mockResolvedValueOnce(pageResult([match(1)], 1));
    const source = createAdminMatchSource(client);

    await source.list({ season: '2026', monthRange: ['2026-02', '2026-02'], status: '' }, 1);

    expect(queryOf(0)).toMatchObject({
      startDate: '2026-02-01',
      endDate: '2026-02-28',
    });
    expect(queryOf(0)).not.toHaveProperty('month');
  });

  it('详情与赛季失败时分别返回 null 与 []', async () => {
    get
      .mockRejectedValueOnce(new Error('detail down'))
      .mockRejectedValueOnce(new Error('seasons down'));
    const source = createAdminMatchSource(client);

    expect(await source.byId(7)).toBeNull();
    expect(await source.seasons()).toEqual([]);
  });
});
