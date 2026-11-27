import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Page } from '../miniprogram/types/api';
import {
  CONTENT_LOCATE_MAX_PAGES,
  CONTENT_LOCATE_PAGE_SIZE,
  createContentFeed,
} from '../miniprogram/utils/content-feed';

afterEach(() => {
  vi.unstubAllGlobals();
});

function page<T>(items: T[], pageNo = 1, totalPages = 1, total = items.length): Page<T> {
  return { items, page: pageNo, pageSize: CONTENT_LOCATE_PAGE_SIZE, totalPages, total };
}

describe('content feed', () => {
  it('locates at 100 per page and gives up after 5 pages', async () => {
    const calls: Array<{ page: number; pageSize: number }> = [];
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async (input) => {
        calls.push(input);
        if (input.pageSize === 10) {
          return page([1], 1, 30, 300);
        }
        return page([input.page * 10], input.page, 30, 300);
      },
      onChange: () => undefined,
    });

    await feed.load();
    const found = await feed.locate((item) => item === 999);

    expect(found).toBeNull();
    expect(calls.filter((call) => call.pageSize === 100)).toHaveLength(CONTENT_LOCATE_MAX_PAGES);
    expect(calls[calls.length - 1]).toEqual({
      page: CONTENT_LOCATE_MAX_PAGES,
      pageSize: CONTENT_LOCATE_PAGE_SIZE,
    });
  });

  it('replaces the snapshot when the post is found', async () => {
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async (input) =>
        input.pageSize === 10 ? page([1, 2], 1, 30, 300) : page([7], 3, 3, 250),
      onChange: () => undefined,
    });

    await feed.load();
    const found = await feed.locate((item) => item === 7);

    expect(found).toBe(7);
    expect(feed.state).toMatchObject({
      items: [7],
      page: 3,
      pageSize: 100,
      totalPages: 3,
      total: 250,
      loaded: true,
    });
  });

  it('uses the content error text on load failure', async () => {
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async () => {
        throw new Error();
      },
      onChange: () => undefined,
    });

    await feed.load();
    expect(feed.state.error).toBe('动态加载失败，请稍后重试');
  });

  it('passes updates through and recomputes totalPages', async () => {
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async () => page([1, 2], 1, 2, 11),
      onChange: () => undefined,
    });
    await feed.load();

    feed.update((items) => [0, ...items], { totalDelta: 1 });
    expect(feed.state).toMatchObject({ items: [0, 1, 2], total: 12, totalPages: 2 });

    feed.update((items) => items, { totalDelta: 9 });
    expect(feed.state).toMatchObject({ total: 21, totalPages: 3 });
  });

  it('reloads the first page when the current page was emptied', async () => {
    const calls: Array<{ page: number; pageSize: number }> = [];
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async (input) => {
        calls.push(input);
        return input.page === 1
          ? page([1, 2, 3, 4, 5, 6, 7, 8, 9, 10], 1, 2, 11)
          : page([11], 2, 2, 11);
      },
      onChange: () => undefined,
    });
    await feed.load();
    await feed.loadMore();
    expect(feed.state).toMatchObject({ page: 2, items: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11] });

    feed.update(() => [], { totalDelta: -11 });
    await feed.reloadIfEmpty();

    expect(calls[calls.length - 1]).toEqual({ page: 1, pageSize: 10 });
    expect(feed.state.page).toBe(1);
    expect(feed.state.items).toHaveLength(10);
  });

  it('toasts a refresh failure once when items are already visible', async () => {
    const showToast = vi.fn();
    vi.stubGlobal('wx', { showToast });
    let fail = false;
    const feed = createContentFeed<number>({
      pageSize: 10,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async () => {
        if (fail) {
          throw new Error('boom');
        }
        return page([1], 1, 1, 1);
      },
      onChange: () => undefined,
    });
    await feed.load();

    fail = true;
    await feed.load();

    expect(showToast).toHaveBeenCalledTimes(1);
    expect(showToast).toHaveBeenCalledWith({ title: 'boom', icon: 'none' });
    expect(feed.state.items).toEqual([1]);
  });
});
