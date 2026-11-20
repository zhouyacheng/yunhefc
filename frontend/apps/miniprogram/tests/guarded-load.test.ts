import { describe, expect, it } from 'vitest';

import { createGuardedLoads, type GuardedLoadSpec } from '../miniprogram/utils/guarded-load';

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function recorder() {
  const patches: Array<Record<string, unknown>> = [];
  return {
    patches,
    setData: (patch: Record<string, unknown>) => {
      patches.push(patch);
    },
  };
}

function spec<T>(
  overrides: Partial<GuardedLoadSpec<T>> & Pick<GuardedLoadSpec<T>, 'fetch' | 'map'>,
): GuardedLoadSpec<T> {
  return { key: 'roster', errorText: '加载失败，请稍后重试', ...overrides };
}

describe('guarded load', () => {
  it('sets loading, applies the mapping and resets loading', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);

    await loads.run(spec({ fetch: async () => 7, map: (value) => ({ roster: value }) }));

    expect(calls.patches).toEqual([
      { rosterLoading: true, rosterError: '' },
      { roster: 7 },
      { rosterLoading: false },
    ]);
  });

  it('merges the begin patch and clears previous errors', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);

    await loads.run(
      spec({
        key: 'lineup',
        begin: { lineupSide: 1 },
        fetch: async () => 'board',
        map: (value) => ({ lineup: value }),
      }),
    );

    expect(calls.patches[0]).toEqual({ lineupSide: 1, lineupLoading: true, lineupError: '' });
  });

  it('writes the fallback patch plus the error message and resets loading', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);

    await loads.run(
      spec({
        key: 'grouping',
        fallback: { grouping: null, groupingBoard: null },
        errorText: '分组加载失败，请稍后重试',
        fetch: async () => {
          throw new Error('网络异常');
        },
        map: () => ({ grouping: 'never' }),
      }),
    );

    expect(calls.patches).toEqual([
      { groupingLoading: true, groupingError: '' },
      { grouping: null, groupingBoard: null, groupingError: '网络异常' },
      { groupingLoading: false },
    ]);
  });

  it('falls back to the configured text for non-Error rejections', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);

    await loads.run(
      spec({
        key: 'events',
        errorText: '事件加载失败，请稍后重试',
        fetch: () => Promise.reject('boom'),
        map: () => ({ events: 'never' }),
      }),
    );

    expect(calls.patches[1]).toEqual({ eventsError: '事件加载失败，请稍后重试' });
  });

  it('drops a late response when a newer run supersedes it', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);
    const first = deferred<number>();
    const second = deferred<number>();

    const firstRun = loads.run(
      spec({ key: 'lineup', fetch: () => first.promise, map: (value) => ({ lineup: value }) }),
    );
    const secondRun = loads.run(
      spec({ key: 'lineup', fetch: () => second.promise, map: (value) => ({ lineup: value }) }),
    );

    second.resolve(2);
    await secondRun;
    first.resolve(1);
    await firstRun;

    expect(calls.patches).toEqual([
      { lineupLoading: true, lineupError: '' },
      { lineupLoading: true, lineupError: '' },
      { lineup: 2 },
      { lineupLoading: false },
    ]);
  });

  it('keeps the newest lineup side when responses arrive out of order', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);
    const home = deferred<string>();
    const away = deferred<string>();

    const homeRun = loads.run(
      spec({
        key: 'lineup',
        begin: { lineupSide: 0 },
        fallback: { lineup: null },
        fetch: () => home.promise,
        map: (value) => ({ lineup: value }),
      }),
    );
    const awayRun = loads.run(
      spec({
        key: 'lineup',
        begin: { lineupSide: 1 },
        fallback: { lineup: null },
        fetch: () => away.promise,
        map: (value) => ({ lineup: value }),
      }),
    );

    away.resolve('away');
    await awayRun;
    home.resolve('home');
    await homeRun;

    expect(calls.patches).toEqual([
      { lineupSide: 0, lineupLoading: true, lineupError: '' },
      { lineupSide: 1, lineupLoading: true, lineupError: '' },
      { lineup: 'away' },
      { lineupLoading: false },
    ]);
  });

  it('applies each key fallback independently', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);

    await loads.run(
      spec({
        key: 'roster',
        fallback: { roster: 'empty' },
        fetch: () => Promise.reject(new Error('名单加载失败')),
        map: () => ({ roster: 'never' }),
      }),
    );
    await loads.run(
      spec({
        key: 'lineup',
        fallback: { lineup: null },
        fetch: () => Promise.reject(new Error('阵容加载失败')),
        map: () => ({ lineup: 'never' }),
      }),
    );

    expect(calls.patches).toContainEqual({ roster: 'empty', rosterError: '名单加载失败' });
    expect(calls.patches).toContainEqual({ lineup: null, lineupError: '阵容加载失败' });
  });

  it('keeps loading true until the newest run settles', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);
    const first = deferred<number>();
    const second = deferred<number>();

    const firstRun = loads.run(
      spec({ key: 'lineup', fetch: () => first.promise, map: (value) => ({ lineup: value }) }),
    );
    const secondRun = loads.run(
      spec({ key: 'lineup', fetch: () => second.promise, map: (value) => ({ lineup: value }) }),
    );

    first.resolve(1);
    await firstRun;
    expect(calls.patches).not.toContainEqual({ lineupLoading: false });

    second.resolve(2);
    await secondRun;
    expect(calls.patches[calls.patches.length - 1]).toEqual({ lineupLoading: false });
  });

  it('drops in-flight responses and errors after invalidateAll', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);
    const pending = deferred<number>();

    const running = loads.run(
      spec({
        key: 'roster',
        fallback: { roster: [] },
        fetch: () => pending.promise,
        map: (value) => ({ roster: value }),
      }),
    );
    loads.invalidateAll();
    pending.reject(new Error('网络异常'));
    await running;

    expect(calls.patches).toEqual([{ rosterLoading: true, rosterError: '' }]);
  });

  it('runs after only for the current response', async () => {
    const calls = recorder();
    const loads = createGuardedLoads(calls.setData);
    const events: string[] = [];
    const pending = deferred<number>();

    const stale = loads.run(
      spec({
        key: 'events',
        fetch: () => pending.promise,
        map: () => ({}),
        after: () => events.push('stale'),
      }),
    );
    const fresh = loads.run(
      spec({
        key: 'events',
        fetch: async () => 2,
        map: () => ({}),
        after: () => events.push('fresh'),
      }),
    );
    await fresh;
    pending.resolve(1);
    await stale;

    expect(events).toEqual(['fresh']);
  });
});
