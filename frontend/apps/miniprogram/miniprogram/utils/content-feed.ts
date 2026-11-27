// 动态/相册/照片列表的 feed module：在通用「分页 Feed module」上绑定 content 的产品口径。
// 页面只消费 state 快照，不持有 cursor、total 或分页守卫。
import type { Page } from '../types/api';
import { createPagedFeed, type PagedFeedState, type PagedFeedUpdateOptions } from './paged-feed';

/** 通知定位：最近 5 页 ×100（与 Web C14 同口径）。 */
export const CONTENT_LOCATE_PAGE_SIZE = 100;
export const CONTENT_LOCATE_MAX_PAGES = 5;

export interface ContentFeedOptions<T> {
  /** 列表 10、照片 30。 */
  pageSize: number;
  errorText: string;
  fetchPage: (input: { page: number; pageSize: number }) => Promise<Page<T>>;
  onChange: (state: PagedFeedState<T>) => void;
}

export interface ContentFeed<T> {
  readonly state: PagedFeedState<T>;
  load(): Promise<void>;
  loadMore(): Promise<void>;
  update(transform: (items: T[]) => T[], options?: PagedFeedUpdateOptions): void;
  /** 静默定位；命中替换 state，未命中返回 null 且列表不变。 */
  locate(predicate: (item: T) => boolean): Promise<T | null>;
  /** 当前页被删空且不是第一页时回到第一页。 */
  reloadIfEmpty(): Promise<void>;
  dispose(): void;
}

export function createContentFeed<T>(options: ContentFeedOptions<T>): ContentFeed<T> {
  let lastError = '';
  const feed = createPagedFeed<T>({
    pageSize: options.pageSize,
    initialKey: 'content',
    errorText: options.errorText,
    fetchPage: ({ page, pageSize }) => options.fetchPage({ page, pageSize }),
    onChange: (state) => {
      options.onChange(state);
      // 已有内容时的刷新失败只 toast 一次，避免每次 onChange 重复提示。
      if (state.error && state.error !== lastError && state.items.length > 0) {
        wx.showToast({ title: state.error, icon: 'none' });
      }
      lastError = state.error;
    },
  });
  return {
    get state() {
      return feed.state;
    },
    load: feed.load,
    loadMore: feed.loadMore,
    update: (transform, updateOptions) => feed.update(transform, updateOptions),
    locate: (predicate) =>
      feed.locate(predicate, {
        pageSize: CONTENT_LOCATE_PAGE_SIZE,
        maxPages: CONTENT_LOCATE_MAX_PAGES,
      }),
    reloadIfEmpty: async () => {
      if (feed.state.items.length === 0 && feed.state.page > 1) {
        await feed.load();
      }
    },
    dispose: feed.dispose,
  };
}
