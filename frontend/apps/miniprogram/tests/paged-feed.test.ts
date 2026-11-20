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
    const calls: Array<{ input: PagedFeedFetchInput; result: ReturnType<typeof deferred<Page<number>>> }> = [];
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
      onChange: (state) => states.push(`${state.loading ? 'loading' : 'idle'}:${state.items.length}`),
    });
    expect(states).toEqual([]);
    await feed.load();
    expect(states[0]).toBe('loading:0');
    expect(states[states.length - 1]).toBe('idle:1');
  });
});
