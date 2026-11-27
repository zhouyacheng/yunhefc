import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  sessionReady: vi.fn(),
  getUser: vi.fn(),
  getPlayer: vi.fn(),
  getPermissions: vi.fn(),
  listRecentMatches: vi.fn(),
  fetchMatchDetail: vi.fn(),
  issueAdminTicket: vi.fn(),
  signupMatch: vi.fn(),
  adminEntryUrl: vi.fn(),
  invalidate: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => ({
  fetchMatchDetail: mocks.fetchMatchDetail,
  issueAdminTicket: mocks.issueAdminTicket,
  listRecentMatches: mocks.listRecentMatches,
  signupMatch: mocks.signupMatch,
}));

vi.mock('../miniprogram/utils/session', () => ({
  sessionReady: mocks.sessionReady,
  getUser: mocks.getUser,
  getPlayer: mocks.getPlayer,
  getPermissions: mocks.getPermissions,
}));

vi.mock('../miniprogram/utils/admin-entry', () => ({ adminEntryUrl: mocks.adminEntryUrl }));
vi.mock('../miniprogram/utils/invalidation', () => ({ invalidate: mocks.invalidate }));

interface HomePage {
  data: {
    quickEntries: Array<{ icon: string; title: string; url: string }>;
    canEnterAdmin: boolean;
  };
  setData(patch: Record<string, unknown>): void;
  bootstrap(): Promise<void>;
}

let pageConfig!: Record<string, unknown> & { data: Record<string, unknown> };

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/home/index');
});

beforeEach(() => {
  mocks.sessionReady.mockResolvedValue(undefined);
  mocks.getUser.mockReturnValue(null);
  mocks.getPlayer.mockReturnValue(null);
  mocks.getPermissions.mockReturnValue([]);
  mocks.listRecentMatches.mockResolvedValue({
    items: [],
    total: 0,
    page: 1,
    pageSize: 10,
    totalPages: 0,
  });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

function pageInstance(): HomePage {
  const config = pageConfig as unknown as Record<string, unknown> & {
    data: Record<string, unknown>;
  };
  const page = Object.create(config) as HomePage;
  page.data = { ...config.data } as HomePage['data'];
  page.setData = (patch) => Object.assign(page.data, patch);
  return page;
}

describe('MP06 首页入口', () => {
  it('只保留活动入口，不再提供账本与动态入口', async () => {
    const page = pageInstance();
    await page.bootstrap();

    expect(page.data.quickEntries).toEqual([
      { icon: '🎉', title: '活动', url: '/pages/activity/index' },
    ]);
  });
});
