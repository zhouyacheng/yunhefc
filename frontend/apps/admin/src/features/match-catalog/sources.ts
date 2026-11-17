import type { QueryParams } from '@yunhefc/api-client';
import type { MatchSummary, Page } from '@yunhefc/shared';
import { isInternalMatchType } from '@yunhefc/shared';

import type { MatchApiClient, MatchCatalogPage, MatchCatalogSource, MatchFilters } from './types';

export const MATCH_PAGE_SIZE = 10;
export const INTERNAL_FETCH_SIZE = 100;

function buildListQuery(filters: MatchFilters, page: number, pageSize: number): QueryParams {
  const query: QueryParams = {
    scope: 'all',
    sort: 'startAt:desc',
    page,
    pageSize,
  };
  if (filters.season !== '') {
    query.season = filters.season;
  }
  if (filters.status !== '') {
    query.status = filters.status;
  }
  if (filters.dateRange?.[0] && filters.dateRange[1]) {
    query.startDate = filters.dateRange[0];
    query.endDate = filters.dateRange[1];
  }
  return query;
}

async function fetchPage(
  client: MatchApiClient,
  filters: MatchFilters,
  page: number,
  pageSize: number,
): Promise<Page<MatchSummary>> {
  return client.get<Page<MatchSummary>>('/matches', {
    query: buildListQuery(filters, page, pageSize),
  });
}

async function fetchDetail(
  client: MatchApiClient,
  id: number,
  internalOnly: boolean,
): Promise<MatchSummary | null> {
  try {
    const detail = await client.get<MatchSummary>(`/matches/${id}`);
    if (internalOnly && !isInternalMatchType(detail.type)) {
      return null;
    }
    return detail;
  } catch {
    return null;
  }
}

async function fetchSeasons(client: MatchApiClient): Promise<string[]> {
  try {
    const result = await client.get<{ items: string[] }>('/matches/seasons');
    return result.items;
  } catch {
    return [];
  }
}

/** 服务端分页：public 非 internalOnly 与 admin 共用。 */
function createServerSource(client: MatchApiClient): MatchCatalogSource {
  return {
    async list(filters, page): Promise<MatchCatalogPage> {
      let requested = Math.max(1, Math.trunc(page));
      let result = await fetchPage(client, filters, requested, MATCH_PAGE_SIZE);
      const lastPage = Math.max(1, result.totalPages);
      if (requested > lastPage) {
        requested = lastPage;
        if (result.totalPages > 0) {
          result = await fetchPage(client, filters, lastPage, MATCH_PAGE_SIZE);
        }
      }
      return {
        items: result.items,
        total: result.total,
        page: requested,
        pageSize: MATCH_PAGE_SIZE,
        totalPages: Math.max(1, result.totalPages),
        truncated: false,
      };
    },
    byId: (id) => fetchDetail(client, id, false),
    seasons: () => fetchSeasons(client),
  };
}

/**
 * internalOnly 快照分页：MAT-02 的 type 是单值，无法一次请求两种类型。
 * 取 100 条后过滤队内赛/训练赛，再按 10 条切片；翻页复用快照。
 */
function createInternalSnapshotSource(client: MatchApiClient): MatchCatalogSource {
  let cacheKey = '';
  let snapshot: MatchSummary[] = [];
  let truncated = false;

  async function ensureSnapshot(filters: MatchFilters, force: boolean): Promise<void> {
    const key = JSON.stringify(filters);
    if (!force && key === cacheKey) {
      return;
    }
    const result = await fetchPage(client, filters, 1, INTERNAL_FETCH_SIZE);
    snapshot = result.items.filter((match) => isInternalMatchType(match.type));
    truncated = result.total > result.items.length;
    cacheKey = key;
  }

  return {
    async list(filters, page, options): Promise<MatchCatalogPage> {
      await ensureSnapshot(filters, options?.force === true);
      const total = snapshot.length;
      const totalPages = Math.max(1, Math.ceil(total / MATCH_PAGE_SIZE));
      const current = Math.min(Math.max(1, Math.trunc(page)), totalPages);
      const start = (current - 1) * MATCH_PAGE_SIZE;
      return {
        items: snapshot.slice(start, start + MATCH_PAGE_SIZE),
        total,
        page: current,
        pageSize: MATCH_PAGE_SIZE,
        totalPages,
        truncated,
      };
    },
    byId: (id) => fetchDetail(client, id, true),
    seasons: () => fetchSeasons(client),
  };
}

export function createPublicMatchSource(
  client: MatchApiClient,
  options: { internalOnly?: boolean } = {},
): MatchCatalogSource {
  return options.internalOnly ? createInternalSnapshotSource(client) : createServerSource(client);
}

export function createAdminMatchSource(client: MatchApiClient): MatchCatalogSource {
  return createServerSource(client);
}
