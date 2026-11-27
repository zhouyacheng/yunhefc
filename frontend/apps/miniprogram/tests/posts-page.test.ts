import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import type { AuthUser, ContentPost, Page } from '../miniprogram/types/api';

const mocks = vi.hoisted(() => ({
  sessionReady: vi.fn(),
  getUser: vi.fn(),
  getPermissions: vi.fn(),
  listPosts: vi.fn(),
  listAlbums: vi.fn(),
  hideShareMenu: vi.fn(),
  showToast: vi.fn(),
  pageScrollTo: vi.fn(),
  navigateTo: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => ({
  createAlbum: vi.fn(),
  createPost: vi.fn(),
  deleteAlbum: vi.fn(),
  deletePost: vi.fn(),
  listActivities: vi.fn(),
  listAlbums: mocks.listAlbums,
  listMatches: vi.fn(),
  listPosts: mocks.listPosts,
  reportContent: vi.fn(),
  setPostLike: vi.fn(),
}));

vi.mock('../miniprogram/utils/session', () => ({
  sessionReady: mocks.sessionReady,
  getUser: mocks.getUser,
  getPermissions: mocks.getPermissions,
}));

const user: AuthUser = {
  id: 7,
  name: '张三',
  phoneMasked: '139****0000',
  status: 1,
  statusLabel: 'ACTIVE',
  roles: ['PLAYER'],
  createdAt: '2026-11-01T10:00:00+08:00',
  wechatBound: true,
};

function pageOf<T>(
  items: T[],
  options: { page?: number; total?: number; pageSize?: number } = {},
): Page<T> {
  const page = options.page ?? 1;
  const pageSize = options.pageSize ?? 10;
  const total = options.total ?? items.length;
  return { items, page, pageSize, total, totalPages: Math.ceil(total / pageSize) };
}

function post(id: number): ContentPost {
  return {
    id,
    author: { id: 7, name: '张三', avatarUrl: null },
    content: `动态 ${id}`,
    album: null,
    likeCount: 0,
    likedByMe: false,
    createdAt: '2026-11-27T09:30:00+08:00',
  };
}

interface TestPage {
  data: Record<string, unknown> & {
    posts: Array<{ id: number }>;
    postPage: number;
    postPageSize: number;
    focusPostId: number;
  };
  setData(patch: Record<string, unknown>): void;
  boot(silent: boolean, locateFocus: boolean): Promise<void>;
  loadTimeline(reset: boolean): Promise<void>;
}

let pageConfig!: Record<string, unknown> & { data: Record<string, unknown> };

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/posts/index');
});

beforeEach(() => {
  vi.useFakeTimers();
  mocks.sessionReady.mockResolvedValue(user);
  mocks.getUser.mockReturnValue(user);
  mocks.getPermissions.mockReturnValue(['content:view:any', 'content:create:own']);
  mocks.listAlbums.mockReset();
  mocks.listPosts.mockReset();
  mocks.listAlbums.mockResolvedValue(pageOf([]));
  vi.stubGlobal('wx', {
    hideShareMenu: mocks.hideShareMenu,
    showToast: mocks.showToast,
    pageScrollTo: mocks.pageScrollTo,
    nextTick: (callback: () => void) => callback(),
    navigateTo: mocks.navigateTo,
  });
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

function pageInstance(): TestPage {
  const config = pageConfig as unknown as Record<string, unknown> & {
    data: Record<string, unknown>;
  };
  const page = Object.create(config) as TestPage;
  page.data = { ...config.data } as TestPage['data'];
  page.setData = (patch) => Object.assign(page.data, patch);
  return page;
}

describe('MP14 时间线分页', () => {
  it('通知定位切到 100/页后，上拉加载继续沿用同一口径', async () => {
    mocks.listPosts.mockImplementation(({ page, pageSize }: { page: number; pageSize: number }) =>
      Promise.resolve(
        pageSize === 100
          ? pageOf([post(9)], { page, total: 250, pageSize: 100 })
          : pageOf([post(1), post(2)], { page, total: 30, pageSize: 10 }),
      ),
    );
    const page = pageInstance();
    page.data.focusPostId = 9;

    await page.boot(false, true);

    expect(page.data.postPageSize).toBe(100);
    expect(page.data.posts.map((item) => item.id)).toEqual([9]);
    expect(mocks.listPosts).toHaveBeenLastCalledWith({ page: 1, pageSize: 100 });

    await page.loadTimeline(false);

    expect(mocks.listPosts).toHaveBeenLastCalledWith({ page: 2, pageSize: 100 });
  });
});
