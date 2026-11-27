import type { Page } from '../types/api';
import { errorMessage } from './api-errors';

export interface PagedFeedState<T> {
  key: string;
  items: T[];
  page: number;
  pageSize: number;
  totalPages: number;
  total: number;
  loading: boolean;
  loadingMore: boolean;
  loaded: boolean;
  error: string;
}

export interface PagedFeedFetchInput {
  key: string;
  page: number;
  pageSize: number;
}

export interface PagedFeedOptions<T> {
  pageSize: number;
  initialKey: string;
  fetchPage: (input: PagedFeedFetchInput) => Promise<Page<T>>;
  onChange: (state: PagedFeedState<T>) => void;
  /** 加载失败文案；缺省为通用提示。 */
  errorText?: string;
}

export interface PagedFeedUpdateOptions {
  /** total 只允许增量；totalPages 由 module 按 total/pageSize 重算。 */
  totalDelta?: number;
}

export interface PagedFeedLocateOptions {
  pageSize: number;
  maxPages: number;
}

export interface PagedFeed<T> {
  readonly state: PagedFeedState<T>;
  /** 从第 1 页重载当前 key；保留旧 items，失败时保留并写 error。 */
  load(): Promise<void>;
  /** 加载下一页；loading/loadingMore/末页/空页时 no-op。 */
  loadMore(): Promise<void>;
  /** key 变化时清空并加载；同 key no-op。 */
  setKey(key: string): void;
  /** 清空并回到 initialKey，保留当前 pageSize；迟到响应丢弃。 */
  reset(): void;
  /** 以 transform 换新 items；page/pageSize/totalPages 仍归 module。 */
  update(transform: (items: T[]) => T[], options?: PagedFeedUpdateOptions): void;
  /** 静默跨页查找；命中时整体替换 state，未命中返回 null 且 items/cursor 不变。 */
  locate(predicate: (item: T) => boolean, options: PagedFeedLocateOptions): Promise<T | null>;
  /** 作废代次并静默后续 onChange；之后所有操作 no-op。 */
  dispose(): void;
}

const DEFAULT_ERROR_TEXT = '加载失败，请稍后重试';
const DEFAULT_PAGE_SIZE = 10;

function totalPagesOf(total: number, pageSize: number): number {
  return total === 0 ? 0 : Math.ceil(total / pageSize);
}

export function emptyPagedFeedState<T>(
  key: string,
  pageSize: number = DEFAULT_PAGE_SIZE,
): PagedFeedState<T> {
  return {
    key,
    items: [],
    page: 1,
    pageSize,
    totalPages: 0,
    total: 0,
    loading: false,
    loadingMore: false,
    loaded: false,
    error: '',
  };
}

/** 分页列表 module：页面只消费 state 快照，代次与分页守卫留在 implementation。 */
export function createPagedFeed<T>(options: PagedFeedOptions<T>): PagedFeed<T> {
  const {
    pageSize: initialPageSize,
    initialKey,
    fetchPage,
    onChange,
    errorText = DEFAULT_ERROR_TEXT,
  } = options;
  let state = emptyPagedFeedState<T>(initialKey, initialPageSize);
  let generation = 0;
  let disposed = false;

  function setState(next: PagedFeedState<T>): void {
    state = next;
    if (!disposed) {
      onChange(state);
    }
  }

  function patch(partial: Partial<PagedFeedState<T>>): void {
    setState({ ...state, ...partial });
  }

  async function load(): Promise<void> {
    if (disposed) {
      return;
    }
    const seq = ++generation;
    const key = state.key;
    const pageSize = state.pageSize;
    patch({ loading: true, loadingMore: false, error: '' });
    try {
      const result = await fetchPage({ key, page: 1, pageSize });
      if (seq !== generation || disposed) {
        return;
      }
      setState({
        ...state,
        items: result.items,
        page: result.page,
        totalPages: result.totalPages,
        total: result.total,
        loading: false,
        loadingMore: false,
        loaded: true,
        error: '',
      });
    } catch (error) {
      if (seq !== generation || disposed) {
        return;
      }
      patch({ loading: false, loaded: true, error: errorMessage(error, errorText) });
    }
  }

  function setKey(key: string): void {
    if (disposed || key === state.key) {
      return;
    }
    generation += 1;
    setState(emptyPagedFeedState<T>(key, state.pageSize));
    void load();
  }

  async function loadMore(): Promise<void> {
    if (
      disposed ||
      state.loading ||
      state.loadingMore ||
      state.totalPages === 0 ||
      state.page >= state.totalPages
    ) {
      return;
    }
    const seq = generation;
    const key = state.key;
    const pageSize = state.pageSize;
    const nextPage = state.page + 1;
    patch({ loadingMore: true, error: '' });
    try {
      const result = await fetchPage({ key, page: nextPage, pageSize });
      if (seq !== generation || disposed) {
        return;
      }
      setState({
        ...state,
        items: state.items.concat(result.items),
        page: result.page,
        totalPages: result.totalPages,
        total: result.total,
        loadingMore: false,
        loaded: true,
        error: '',
      });
    } catch (error) {
      if (seq !== generation || disposed) {
        return;
      }
      patch({ loadingMore: false, error: errorMessage(error, errorText) });
    }
  }

  function reset(): void {
    if (disposed) {
      return;
    }
    generation += 1;
    setState(emptyPagedFeedState<T>(initialKey, state.pageSize));
  }

  function update(transform: (items: T[]) => T[], updateOptions?: PagedFeedUpdateOptions): void {
    if (disposed) {
      return;
    }
    const items = transform(state.items).slice();
    const total = Math.max(0, state.total + (updateOptions?.totalDelta ?? 0));
    setState({
      ...state,
      items,
      total,
      totalPages: totalPagesOf(total, state.pageSize),
      loaded: true,
      error: '',
    });
  }

  async function locate(
    predicate: (item: T) => boolean,
    locateOptions: PagedFeedLocateOptions,
  ): Promise<T | null> {
    if (disposed || locateOptions.maxPages < 1) {
      return null;
    }
    // 作废在途 load/loadMore：定位命中后它们不能让旧页覆盖新 state。
    const seq = ++generation;
    const key = state.key;
    try {
      for (let page = 1; page <= locateOptions.maxPages; page += 1) {
        const result = await fetchPage({ key, page, pageSize: locateOptions.pageSize });
        if (seq !== generation || disposed) {
          return null;
        }
        const found = result.items.find(predicate);
        if (found) {
          setState({
            ...state,
            items: result.items,
            page: result.page,
            pageSize: locateOptions.pageSize,
            totalPages: result.totalPages,
            total: result.total,
            loading: false,
            loadingMore: false,
            loaded: true,
            error: '',
          });
          return found;
        }
        if (page >= result.totalPages) {
          break;
        }
      }
    } catch {
      if (seq !== generation || disposed) {
        return null;
      }
    }
    // locate 作废了在途 load/loadMore；若它把 loading 留在 true，这里只清瞬时标志。
    if (seq === generation && !disposed && (state.loading || state.loadingMore)) {
      patch({ loading: false, loadingMore: false });
    }
    return null;
  }

  function dispose(): void {
    disposed = true;
    generation += 1;
  }

  return {
    get state() {
      return state;
    },
    load,
    loadMore,
    setKey,
    reset,
    update,
    locate,
    dispose,
  };
}
