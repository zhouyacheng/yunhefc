import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import { ACTIVITY_SHARE_COVER_FALLBACK } from '../miniprogram/utils/activity-share-cover';

const mocks = vi.hoisted(() => ({ generate: vi.fn() }));

vi.mock('../miniprogram/utils/share-cover', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../miniprogram/utils/share-cover')>();
  return { ...actual, generateShareCover: mocks.generate };
});

const BASE_ACTIVITY = {
  id: 1,
  title: '周末聚餐',
  startAt: '2026-11-28T18:00:00+08:00',
  endAt: '2026-11-28T21:00:00+08:00',
  location: '聚龙足球场',
  signupCount: 12,
  capacity: 50,
  signupDeadline: '2026-11-27T20:00:00+08:00',
  costPerPerson: 50,
  signupOpen: true,
};

interface TestPage {
  data: Record<string, unknown>;
  shareCoverSeq: number;
  setData(patch: Record<string, unknown>): void;
  prepareShareCover(): Promise<void>;
}

let pageConfig!: {
  data: Record<string, unknown>;
  prepareShareCover: (this: TestPage) => Promise<void>;
};

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/activity-detail/index');
});

afterAll(() => {
  vi.unstubAllGlobals();
});

beforeEach(() => {
  mocks.generate.mockReset();
});

function makePage(overrides: Record<string, unknown> = {}): TestPage {
  return {
    data: {
      ...pageConfig.data,
      activity: BASE_ACTIVITY,
      shareCoverPath: ACTIVITY_SHARE_COVER_FALLBACK,
      shareCoverReady: false,
      ...overrides,
    },
    shareCoverSeq: 0,
    setData(patch) {
      Object.assign(this.data, patch);
    },
    prepareShareCover: pageConfig.prepareShareCover,
  };
}

function deferred<T>(): { promise: Promise<T>; resolve: (value: T) => void } {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => {
    resolve = done;
  });
  return { promise, resolve };
}

describe('MP11 share cover state', () => {
  it('starts with the placeholder state until the cover is ready', () => {
    expect(pageConfig.data.shareCoverReady).toBe(false);
    expect(pageConfig.data.shareCoverPath).toBe(ACTIVITY_SHARE_COVER_FALLBACK);
  });

  it('marks the cover ready after generation', async () => {
    mocks.generate.mockResolvedValueOnce('/tmp/new.png');
    const page = makePage();

    await page.prepareShareCover();

    expect(mocks.generate).toHaveBeenCalledWith(
      '#activity-share-cover',
      expect.any(Function),
      ACTIVITY_SHARE_COVER_FALLBACK,
    );
    expect(page.data.shareCoverPath).toBe('/tmp/new.png');
    expect(page.data.shareCoverReady).toBe(true);
  });

  it('keeps the previous cover usable while regenerating', async () => {
    const pending = deferred<string>();
    mocks.generate.mockReturnValueOnce(pending.promise);
    const page = makePage({ shareCoverPath: '/tmp/old.png', shareCoverReady: true });

    const generation = page.prepareShareCover();
    expect(page.data.shareCoverPath).toBe('/tmp/old.png');
    expect(page.data.shareCoverReady).toBe(true);

    pending.resolve('/tmp/new.png');
    await generation;

    expect(page.data.shareCoverPath).toBe('/tmp/new.png');
    expect(page.data.shareCoverReady).toBe(true);
  });

  it('discards a stale generation result', async () => {
    const first = deferred<string>();
    const second = deferred<string>();
    mocks.generate.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
    const page = makePage();

    const stale = page.prepareShareCover();
    const latest = page.prepareShareCover();
    second.resolve('/tmp/latest.png');
    await latest;
    first.resolve('/tmp/stale.png');
    await stale;

    expect(page.data.shareCoverPath).toBe('/tmp/latest.png');
  });

  it('marks the cover ready with the static fallback when generation falls back', async () => {
    mocks.generate.mockResolvedValueOnce(ACTIVITY_SHARE_COVER_FALLBACK);
    const page = makePage();

    await page.prepareShareCover();

    expect(page.data.shareCoverPath).toBe(ACTIVITY_SHARE_COVER_FALLBACK);
    expect(page.data.shareCoverReady).toBe(true);
  });
});
