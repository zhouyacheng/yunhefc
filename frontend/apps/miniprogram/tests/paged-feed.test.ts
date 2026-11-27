import { describe, expect, it } from 'vitest';

import type { Page } from '../miniprogram/types/api';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeedFetchInput,
} from '../miniprogram/utils/paged-feed';

function page<T>(items: T[], pageNo = 1, totalPages = 1, total = items.length): Page<T> {
  return { items, page: pageNo, pageSize: 10, totalPages, total };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
}

describe('paged feed', () => {
  it('loads the first page and exposes the snapshot', async () => {
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'upcoming',
      fetchPage: async () => page([1, 2]),
      onChange: () => undefined,
    });

    await feed.load();

    expect(feed.state).toMatchObject({
      key: 'upcoming',
      items: [1, 2],
      page: 1,
      totalPages: 1,
      total: 2,
      loading: false,
      loaded: true,
      error: '',
    });
  });

  it('keeps items while reloading and after a failed reload', async () => {
    let fail = false;
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'active',
      fetchPage: async () => {
        if (fail) {
          throw new Error('网络异常');
        }
        return page([1]);
      },
      onChange: () => undefined,
    });
    await feed.load();
    expect(feed.state.items).toEqual([1]);

    fail = true;
    const pending = feed.load();
    expect(feed.state.loading).toBe(true);
    expect(feed.state.items).toEqual([1]);
    await pending;
    expect(feed.state.items).toEqual([1]);
    expect(feed.state.loading).toBe(false);
    expect(feed.state.error).toBe('网络异常');
  });

  it('does not reload for the same key', async () => {
    let calls = 0;
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async () => {
        calls += 1;
        return page([1]);
      },
      onChange: () => undefined,
    });
    await feed.load();
    feed.setKey('all');
    expect(calls).toBe(1);
  });

  it('clears a changed key and discards the stale response', async () => {
    const calls: Array<{
      input: PagedFeedFetchInput;
      result: ReturnType<typeof deferred<Page<number>>>;
    }> = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'upcoming',
      fetchPage: (input) => {
        const result = deferred<Page<number>>();
        calls.push({ input, result });
        return result.promise;
      },
      onChange: () => undefined,
    });

    const first = feed.load();
    expect(calls[0].input).toEqual({ key: 'upcoming', page: 1, pageSize: 10 });

    feed.setKey('history');
    expect(feed.state.key).toBe('history');
    expect(feed.state.items).toEqual([]);
    expect(calls[1].input).toEqual({ key: 'history', page: 1, pageSize: 10 });

    calls[1].result.resolve(page([3], 1, 1, 1));
    await flush();
    calls[0].result.resolve(page([1], 1, 1, 1));
    await first;

    expect(feed.state.key).toBe('history');
    expect(feed.state.items).toEqual([3]);
  });

  it('appends pages and guards load-more edges', async () => {
    const calls: PagedFeedFetchInput[] = [];
    const pending: Array<ReturnType<typeof deferred<Page<number>>>> = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'upcoming',
      fetchPage: (input) => {
        calls.push(input);
        if (input.page === 1) {
          return Promise.resolve(page([1, 2], 1, 2, 4));
        }
        const next = deferred<Page<number>>();
        pending.push(next);
        return next.promise;
      },
      onChange: () => undefined,
    });

    await feed.load();
    const more = feed.loadMore();
    expect(feed.state.loadingMore).toBe(true);
    void feed.loadMore();
    expect(calls.filter((call) => call.page === 2)).toHaveLength(1);

    pending[0].resolve(page([3, 4], 2, 2, 4));
    await more;
    expect(feed.state.items).toEqual([1, 2, 3, 4]);

    const count = calls.length;
    await feed.loadMore();
    expect(calls).toHaveLength(count);
  });

  it('reports an empty first page and no-ops load-more', async () => {
    let calls = 0;
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'history',
      fetchPage: async () => {
        calls += 1;
        return page([], 1, 0, 0);
      },
      onChange: () => undefined,
    });
    await feed.load();
    await feed.loadMore();
    expect(calls).toBe(1);
    expect(feed.state).toMatchObject({ items: [], totalPages: 0, loaded: true, error: '' });
  });

  it('resets to the initial key without fetching and drops late responses', async () => {
    const calls: Array<ReturnType<typeof deferred<Page<number>>>> = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: () => {
        const result = deferred<Page<number>>();
        calls.push(result);
        return result.promise;
      },
      onChange: () => undefined,
    });
    const first = feed.load();
    feed.setKey('unread');
    feed.reset();

    expect(feed.state).toEqual(emptyPagedFeedState('all'));
    calls[1].resolve(page([2]));
    calls[0].resolve(page([1]));
    await first;
    expect(feed.state).toEqual(emptyPagedFeedState('all'));
  });

  it('notifies onChange for mutations, not at creation', async () => {
    const states: string[] = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async () => page([1]),
      onChange: (state) =>
        states.push(`${state.loading ? 'loading' : 'idle'}:${state.items.length}`),
    });
    expect(states).toEqual([]);
    await feed.load();
    expect(states[0]).toBe('loading:0');
    expect(states[states.length - 1]).toBe('idle:1');
  });

  it('locates across pages at the requested page size and keeps it for load-more', async () => {
    const calls: PagedFeedFetchInput[] = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async (input) => {
        calls.push(input);
        if (input.pageSize === 100) {
          return page([7, 8], input.page, 2, 150);
        }
        return page([1, 2], input.page, 15, 150);
      },
      onChange: () => undefined,
    });

    await feed.load();
    const found = await feed.locate((item) => item === 7, { pageSize: 100, maxPages: 5 });

    expect(found).toBe(7);
    expect(feed.state).toMatchObject({ items: [7, 8], page: 1, pageSize: 100, total: 150 });
    expect(calls[calls.length - 1]).toEqual({ key: 'all', page: 1, pageSize: 100 });

    await feed.loadMore();
    expect(calls[calls.length - 1]).toEqual({ key: 'all', page: 2, pageSize: 100 });
  });

  it('leaves the snapshot untouched when locate misses', async () => {
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async (input) =>
        input.pageSize === 100 ? page([9], input.page, 2, 100) : page([1], 1, 1, 1),
      onChange: () => undefined,
    });
    await feed.load();

    const found = await feed.locate((item) => item === 99, { pageSize: 100, maxPages: 5 });

    expect(found).toBeNull();
    expect(feed.state).toMatchObject({ items: [1], page: 1, pageSize: 10, total: 1 });
  });

  it('updates items, recomputes totalPages and clears errors', async () => {
    let fail = true;
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async () => {
        if (fail) {
          throw new Error('网络异常');
        }
        return page([1, 2], 1, 2, 11);
      },
      onChange: () => undefined,
    });
    await feed.load();
    expect(feed.state.error).toBe('网络异常');

    feed.update((items) => [0, ...items], { totalDelta: 1 });
    expect(feed.state).toMatchObject({
      items: [0],
      total: 1,
      totalPages: 1,
      loaded: true,
      error: '',
    });

    fail = false;
    await feed.load();
    feed.update((items) => items.filter((item) => item !== 2), { totalDelta: -1 });
    expect(feed.state).toMatchObject({ items: [1], total: 10, totalPages: 1 });

    feed.update((items) => items, { totalDelta: -20 });
    expect(feed.state).toMatchObject({ items: [1], total: 0, totalPages: 0 });
  });

  it('uses the injected error text', async () => {
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      errorText: '自定义失败',
      fetchPage: async () => {
        throw new Error();
      },
      onChange: () => undefined,
    });

    await feed.load();
    expect(feed.state.error).toBe('自定义失败');
  });

  it('mutes fetching, updates and locate after dispose', async () => {
    let calls = 0;
    const states: number[] = [];
    const feed = createPagedFeed<number>({
      pageSize: 10,
      initialKey: 'all',
      fetchPage: async () => {
        calls += 1;
        return page([1], 1, 2, 20);
      },
      onChange: (state) => states.push(state.items.length),
    });
    await feed.load();
    const before = states.length;

    feed.dispose();
    await feed.load();
    await feed.loadMore();
    feed.update((items) => [...items, 2]);
    const found = await feed.locate((item) => item === 2, { pageSize: 100, maxPages: 5 });

    expect(calls).toBe(1);
    expect(found).toBeNull();
    expect(states).toHaveLength(before);
    expect(feed.state.items).toEqual([1]);
  });
});
