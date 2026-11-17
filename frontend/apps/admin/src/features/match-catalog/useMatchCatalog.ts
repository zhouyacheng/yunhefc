import { isApiError } from '@yunhefc/api-client';
import type { MatchStatus, MatchSummary } from '@yunhefc/shared';
import { getCurrentScope, onScopeDispose, ref, type Ref } from 'vue';

import { currentShanghaiSeason } from './selector';
import type { MatchCatalogPage, MatchCatalogSource, MatchFilters } from './types';

export interface MatchCatalog {
  season: Ref<string>;
  monthRange: Ref<[string, string] | null>;
  status: Ref<MatchStatus | ''>;
  page: Ref<number>;
  items: Ref<MatchSummary[]>;
  total: Ref<number>;
  totalPages: Ref<number>;
  truncated: Ref<boolean>;
  loading: Ref<boolean>;
  error: Ref<string | null>;
  seasons: Ref<string[]>;

  setFilters(next?: Partial<MatchFilters>): Promise<void>;
  setPage(nextPage: number): Promise<void>;
  reload(): Promise<void>;
  byId(id: number): Promise<MatchSummary | null>;
  updateItem(match: MatchSummary): void;
}

export interface UseMatchCatalogOptions {
  initialSeason?: string;
  /** 成功写入 state 后调用；用于 MatchPicker 的选中同步与自动选中。 */
  onLoaded?: (result: MatchCatalogPage) => void;
  /** 每次失败调用一次；用于 emit('load-error')。 */
  onError?: (message: string) => void;
}

const DEFAULT_ERROR = '比赛列表加载失败，请稍后重试';

export function useMatchCatalog(
  source: MatchCatalogSource,
  options: UseMatchCatalogOptions = {},
): MatchCatalog {
  const season = ref(options.initialSeason ?? currentShanghaiSeason());
  const monthRange = ref<[string, string] | null>(null);
  const status = ref<MatchStatus | ''>('');
  const page = ref(1);
  const items = ref<MatchSummary[]>([]);
  const total = ref(0);
  const totalPages = ref(1);
  const truncated = ref(false);
  const loading = ref(true);
  const error = ref<string | null>(null);
  const seasons = ref<string[]>([]);
  let requestSeq = 0;
  let disposed = false;

  function snapshot(): MatchFilters {
    return { season: season.value, monthRange: monthRange.value, status: status.value };
  }

  async function load(force = false): Promise<MatchCatalogPage | null> {
    const seq = ++requestSeq;
    loading.value = true;
    error.value = null;
    try {
      const result = await source.list(snapshot(), page.value, force ? { force: true } : undefined);
      if (disposed || seq !== requestSeq) {
        return null;
      }
      items.value = result.items;
      total.value = result.total;
      totalPages.value = Math.max(1, result.totalPages);
      page.value = result.page;
      truncated.value = result.truncated;
      options.onLoaded?.(result);
      return result;
    } catch (caught) {
      if (disposed || seq !== requestSeq) {
        return null;
      }
      const message = isApiError(caught) ? caught.message : DEFAULT_ERROR;
      error.value = message;
      options.onError?.(message);
      return null;
    } finally {
      if (!disposed && seq === requestSeq) {
        loading.value = false;
      }
    }
  }

  async function setFilters(next: Partial<MatchFilters> = {}): Promise<void> {
    if ('season' in next && next.season !== undefined) {
      season.value = next.season;
    }
    if ('monthRange' in next) {
      monthRange.value = next.monthRange ?? null;
    }
    if ('status' in next && next.status !== undefined) {
      status.value = next.status;
    }
    page.value = 1;
    await load();
  }

  async function setPage(nextPage: number): Promise<void> {
    const next = Math.min(Math.max(1, Math.trunc(nextPage)), totalPages.value);
    if (next === page.value) {
      return;
    }
    page.value = next;
    await load();
  }

  async function reload(): Promise<void> {
    await load(true);
  }

  async function byId(id: number): Promise<MatchSummary | null> {
    return source.byId(id);
  }

  function updateItem(match: MatchSummary): void {
    const index = items.value.findIndex((item) => item.id === match.id);
    if (index >= 0) {
      items.value[index] = match;
    }
  }

  void source
    .seasons()
    .then((values) => {
      if (!disposed) {
        seasons.value = values;
      }
    })
    .catch(() => {
      // source.seasons 已兜底为 []；这里只防御未预期的实现。
    });

  if (getCurrentScope()) {
    onScopeDispose(() => {
      disposed = true;
      requestSeq += 1;
    });
  }

  return {
    season,
    monthRange,
    status,
    page,
    items,
    total,
    totalPages,
    truncated,
    loading,
    error,
    seasons,
    setFilters,
    setPage,
    reload,
    byId,
    updateItem,
  };
}
