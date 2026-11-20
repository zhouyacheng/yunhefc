import type { Page } from '../types/api';

export interface PagedFeedState<T> {
  key: string;
  items: T[];
  page: number;
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
}

export interface PagedFeed<T> {
  readonly state: PagedFeedState<T>;
  /** 从第 1 页重载当前 key；保留旧 items，失败时保留并写 error。 */
  load(): Promise<void>;
  /** 加载下一页；loading/loadingMore/末页/空页时 no-op。 */
  loadMore(): Promise<void>;
  /** key 变化时清空并加载；同 key no-op。 */
  setKey(key: string): void;
  /** 清空并回到 initialKey，不请求；迟到响应丢弃。 */
  reset(): void;
}

export function emptyPagedFeedState<T>(key: string): PagedFeedState<T> {
  return {
    key,
    items: [],
    page: 1,
    totalPages: 0,
    total: 0,
    loading: false,
    loadingMore: false,
    loaded: false,
    error: '',
  };
}

function errorMessage(error: unknown): string {
  return (error as Error).message || '加载失败，请稍后重试';
}

/** 分页列表 module：页面只消费 state 快照，代次与分页守卫留在 implementation。 */
export function createPagedFeed<T>(options: PagedFeedOptions<T>): PagedFeed<T> {
  const { pageSize, initialKey, fetchPage, onChange } = options;
  let state = emptyPagedFeedState<T>(initialKey);
  let generation = 0;

  function setState(next: PagedFeedState<T>): void {
    state = next;
    onChange(state);
  }

  function patch(partial: Partial<PagedFeedState<T>>): void {
    setState({ ...state, ...partial });
  }

  async function load(): Promise<void> {
    const seq = ++generation;
    const key = state.key;
    patch({ loading: true, loadingMore: false, error: '' });
    try {
      const result = await fetchPage({ key, page: 1, pageSize });
      if (seq !== generation) {
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
      if (seq !== generation) {
        return;
      }
      patch({ loading: false, loaded: true, error: errorMessage(error) });
    }
  }

  function setKey(key: string): void {
    if (key === state.key) {
      return;
    }
    generation += 1;
    setState(emptyPagedFeedState<T>(key));
    void load();
  }

  async function loadMore(): Promise<void> {
    if (
      state.loading ||
      state.loadingMore ||
      state.totalPages === 0 ||
      state.page >= state.totalPages
    ) {
      return;
    }
    const seq = generation;
    const key = state.key;
    const nextPage = state.page + 1;
    patch({ loadingMore: true, error: '' });
    try {
      const result = await fetchPage({ key, page: nextPage, pageSize });
      if (seq !== generation) {
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
      if (seq !== generation) {
        return;
      }
      patch({ loadingMore: false, error: errorMessage(error) });
    }
  }

  function reset(): void {
    generation += 1;
    setState(emptyPagedFeedState<T>(initialKey));
  }

  return {
    get state() {
      return state;
    },
    load,
    loadMore,
    setKey,
    reset,
  };
}
