import { ApiError } from '@yunhefc/api-client';
import type { MatchSummary } from '@yunhefc/shared';
import { effectScope } from 'vue';
import { describe, expect, it, vi } from 'vitest';

import { currentShanghaiSeason } from './selector';
import type { MatchCatalogPage, MatchCatalogSource, MatchFilters } from './types';
import { useMatchCatalog, type UseMatchCatalogOptions } from './useMatchCatalog';

function match(id: number, title = `比赛${id}`): MatchSummary {
  return {
    id,
    title,
    type: 1,
    typeLabel: '队内赛',
    startAt: '2026-11-17T12:00:00Z',
    endAt: '2026-11-17T14:00:00Z',
    venue: '球场',
    season: '2026',
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
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
  };
}

function pageResult(
  items: MatchSummary[],
  total = items.length,
  page = 1,
  pageSize = 10,
): MatchCatalogPage {
  return {
    items,
    total,
    page,
    pageSize,
    totalPages: Math.max(1, Math.ceil(total / pageSize)),
    truncated: false,
  };
}

interface FakeSource {
  source: MatchCatalogSource;
  list: ReturnType<typeof vi.fn>;
  byId: ReturnType<typeof vi.fn>;
  seasons: ReturnType<typeof vi.fn>;
}

function createFakeSource(): FakeSource {
  const list = vi.fn().mockResolvedValue(pageResult([match(1)]));
  const byId = vi.fn().mockResolvedValue(null);
  const seasons = vi.fn().mockResolvedValue(['2026', '2025']);
  return {
    source: { list, byId, seasons } as unknown as MatchCatalogSource,
    list,
    byId,
    seasons,
  };
}

function setup(fake: FakeSource, options?: UseMatchCatalogOptions) {
  const scope = effectScope();
  const catalog = scope.run(() => useMatchCatalog(fake.source, options))!;
  return { scope, catalog };
}

function callAt(
  list: ReturnType<typeof vi.fn>,
  index: number,
): {
  filters: MatchFilters;
  page: number;
  options?: { force?: boolean };
} {
  const call = list.mock.calls[index] as
    [MatchFilters, number, { force?: boolean } | undefined] | undefined;
  return {
    filters: call?.[0] ?? { season: '', monthRange: null, status: '' },
    page: call?.[1] ?? 0,
    options: call?.[2],
  };
}

describe('useMatchCatalog', () => {
  it('初始赛季取当前上海赛季，并静默加载赛季选项', async () => {
    const fake = createFakeSource();
    const { scope, catalog } = setup(fake);

    expect(catalog.season.value).toBe(currentShanghaiSeason());
    expect(catalog.loading.value).toBe(true);
    await vi.waitFor(() => expect(catalog.seasons.value).toEqual(['2026', '2025']));
    scope.stop();
  });

  it('initialSeason 覆盖默认赛季并随筛选下发', async () => {
    const fake = createFakeSource();
    const { scope, catalog } = setup(fake, { initialSeason: '2025' });

    expect(catalog.season.value).toBe('2025');
    await catalog.setFilters();
    expect(callAt(fake.list, 0).filters.season).toBe('2025');
    scope.stop();
  });

  it('加载成功后写入 page/items/total/totalPages 并调用 onLoaded', async () => {
    const fake = createFakeSource();
    fake.list.mockResolvedValueOnce(pageResult([match(1)], 25, 1, 10));
    const onLoaded = vi.fn();
    const { scope, catalog } = setup(fake, { onLoaded });

    await catalog.setFilters();

    expect(callAt(fake.list, 0)).toMatchObject({ page: 1 });
    expect(catalog.items.value.map((item) => item.id)).toEqual([1]);
    expect(catalog.total.value).toBe(25);
    expect(catalog.totalPages.value).toBe(3);
    expect(catalog.page.value).toBe(1);
    expect(catalog.loading.value).toBe(false);
    expect(onLoaded).toHaveBeenCalledTimes(1);
    scope.stop();
  });

  it('setFilters 回到第 1 页，并能清空 monthRange/status', async () => {
    const fake = createFakeSource();
    fake.list.mockImplementation(async (_filters: MatchFilters, page: number) =>
      pageResult([match(page)], 25, page, 10),
    );
    const { scope, catalog } = setup(fake);

    await catalog.setFilters();
    await catalog.setPage(2);
    expect(callAt(fake.list, 1).page).toBe(2);

    await catalog.setFilters({ status: 1 });
    expect(callAt(fake.list, 2)).toMatchObject({
      page: 1,
      filters: { status: 1 },
    });

    await catalog.setFilters({ monthRange: ['2026-01', '2026-02'], status: '' });
    expect(callAt(fake.list, 3).filters).toMatchObject({
      monthRange: ['2026-01', '2026-02'],
      status: '',
    });

    await catalog.setFilters({ monthRange: null, status: '' });
    expect(callAt(fake.list, 4).filters).toMatchObject({ monthRange: null, status: '' });
    scope.stop();
  });

  it('setPage 夹到 [1,totalPages]，页码未变不请求', async () => {
    const fake = createFakeSource();
    fake.list.mockImplementation(async (_filters: MatchFilters, page: number) =>
      pageResult([match(page)], 25, page, 10),
    );
    const { scope, catalog } = setup(fake);

    await catalog.setFilters();
    const callsAfterLoad = fake.list.mock.calls.length;

    await catalog.setPage(5);
    expect(catalog.page.value).toBe(3);
    expect(callAt(fake.list, callsAfterLoad).page).toBe(3);

    await catalog.setPage(3);
    expect(fake.list).toHaveBeenCalledTimes(callsAfterLoad + 1);
    scope.stop();
  });

  it('reload 以 force=true 调用 source', async () => {
    const fake = createFakeSource();
    const { scope, catalog } = setup(fake);

    await catalog.reload();

    expect(callAt(fake.list, 0).options).toEqual({ force: true });
    scope.stop();
  });

  it('竞态：只采用最后一次响应', async () => {
    const fake = createFakeSource();
    fake.list.mockResolvedValueOnce(pageResult([match(1)], 25, 1, 10));
    const { scope, catalog } = setup(fake);
    await catalog.setFilters();

    let resolveSecond!: (value: MatchCatalogPage) => void;
    let resolveThird!: (value: MatchCatalogPage) => void;
    fake.list
      .mockImplementationOnce(
        () =>
          new Promise<MatchCatalogPage>((resolve) => {
            resolveSecond = resolve;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise<MatchCatalogPage>((resolve) => {
            resolveThird = resolve;
          }),
      );

    const second = catalog.setPage(2);
    const third = catalog.setPage(3);
    resolveThird(pageResult([match(3)], 25, 3, 10));
    await third;
    expect(catalog.items.value.map((item) => item.id)).toEqual([3]);

    resolveSecond(pageResult([match(2)], 25, 2, 10));
    await second;
    expect(catalog.items.value.map((item) => item.id)).toEqual([3]);
    scope.stop();
  });

  it('失败时写 error、调用 onError，不调用 onLoaded', async () => {
    const fake = createFakeSource();
    fake.list.mockRejectedValueOnce(
      new ApiError({ status: 500, code: 50001, message: '比赛列表炸了' }),
    );
    const onLoaded = vi.fn();
    const onError = vi.fn();
    const { scope, catalog } = setup(fake, { onLoaded, onError });

    await catalog.setFilters();

    expect(catalog.error.value).toBe('比赛列表炸了');
    expect(onError).toHaveBeenCalledWith('比赛列表炸了');
    expect(onLoaded).not.toHaveBeenCalled();
    expect(catalog.loading.value).toBe(false);
    scope.stop();
  });

  it('updateItem 只替换当前页同 id 对象', async () => {
    const fake = createFakeSource();
    fake.list.mockResolvedValueOnce(pageResult([match(1)], 1, 1, 10));
    const { scope, catalog } = setup(fake);
    await catalog.setFilters();

    catalog.updateItem({ ...match(1), title: '已更新' });

    expect(catalog.items.value[0]?.title).toBe('已更新');
    scope.stop();
  });

  it('byId 直接委托 source', async () => {
    const fake = createFakeSource();
    fake.byId.mockResolvedValue(match(7));
    const { scope, catalog } = setup(fake);

    expect((await catalog.byId(7))?.id).toBe(7);
    scope.stop();
  });

  it('scope dispose 后晚到结果不写入', async () => {
    const fake = createFakeSource();
    let resolveLoad!: (value: MatchCatalogPage) => void;
    fake.list.mockImplementationOnce(
      () =>
        new Promise<MatchCatalogPage>((resolve) => {
          resolveLoad = resolve;
        }),
    );
    const { scope, catalog } = setup(fake);

    const pending = catalog.setFilters();
    scope.stop();
    resolveLoad(pageResult([match(9)], 1, 1, 10));
    await pending;

    expect(catalog.items.value).toEqual([]);
  });
});
