import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  fetchLeaderboards: vi.fn(),
  listMatchSeasons: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => mocks);

const captured = vi.hoisted(() => ({ page: null as unknown }));

interface PageInstance {
  data: {
    boardKey: string;
    windows: Array<{ key: string; label: string }>;
    windowKey: string;
    mainLabel: string;
    summary: string;
  };
  setData: (patch: Record<string, unknown>) => void;
  onLoad: (query: Record<string, string | undefined>) => void;
  loadWindows: () => Promise<void>;
  loadBoards: () => Promise<void>;
}

beforeAll(async () => {
  (globalThis as unknown as { Page: (config: unknown) => void }).Page = (config) => {
    captured.page = config;
  };
  await import('../miniprogram/pages/leaderboard/index');
});

function pageInstance(): PageInstance {
  const config = captured.page as Record<string, unknown> & { data: Record<string, unknown> };
  const page = Object.create(config) as PageInstance;
  page.data = { ...config.data } as PageInstance['data'];
  page.setData = (patch) => Object.assign(page.data, patch);
  return page;
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.setSystemTime(new Date('2026-06-01T00:00:00Z'));
  mocks.fetchLeaderboards.mockReset();
  mocks.listMatchSeasons.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('MP09 排行榜页', () => {
  it('默认出场榜，赛季窗口只含四位赛季且不含生涯累计', async () => {
    mocks.listMatchSeasons.mockResolvedValue(['2025', 'all', '202', '2026']);
    const page = pageInstance();

    page.onLoad({});
    await page.loadWindows();

    expect(page.data.boardKey).toBe('appearances');
    expect(page.data.windows.map((window) => window.key)).toEqual(['2026', '2025']);
    expect(page.data.windows.some((window) => window.label.includes('生涯'))).toBe(false);
  });

  it('始终按赛季口径请求，并沿用入口赛季', async () => {
    mocks.listMatchSeasons.mockResolvedValue(['2025', '2026']);
    mocks.fetchLeaderboards.mockResolvedValue({ season: '2025', scope: 'season', items: [] });
    const page = pageInstance();

    page.onLoad({ season: '2025' });
    await page.loadWindows();
    await page.loadBoards();

    expect(mocks.fetchLeaderboards).toHaveBeenCalledWith({ season: '2025', scope: 'season' });
    expect(page.data.mainLabel).toBe('2025 赛季');
    expect(page.data.summary).toContain('2025 赛季');
  });

  it('无入口赛季时按当前年份请求', async () => {
    mocks.listMatchSeasons.mockResolvedValue([]);
    mocks.fetchLeaderboards.mockResolvedValue({ season: '2026', scope: 'season', items: [] });
    const page = pageInstance();

    page.onLoad({});
    await page.loadWindows();
    await page.loadBoards();

    expect(mocks.fetchLeaderboards).toHaveBeenCalledWith({ season: '2026', scope: 'season' });
    expect(page.data.mainLabel).toBe('当前赛季');
  });
});
