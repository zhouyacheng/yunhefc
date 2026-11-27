import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import type { AuthUser, ContentPost, Page } from '../miniprogram/types/api';
import type { ContentFeed } from '../miniprogram/utils/content-feed';
import type { ContentPostRow } from '../miniprogram/utils/content';
import type { PagedFeedState } from '../miniprogram/utils/paged-feed';

const mocks = vi.hoisted(() => ({
  sessionReady: vi.fn(),
  getUser: vi.fn(),
  getPermissions: vi.fn(),
  listPosts: vi.fn(),
  listAlbums: vi.fn(),
  createPost: vi.fn(),
  deletePost: vi.fn(),
  setPostLike: vi.fn(),
  createAlbum: vi.fn(),
  deleteAlbum: vi.fn(),
  listActivities: vi.fn(),
  listMatches: vi.fn(),
  reportContent: vi.fn(),
  hideShareMenu: vi.fn(),
  showToast: vi.fn(),
  showModal: vi.fn(),
  pageScrollTo: vi.fn(),
  navigateTo: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => ({
  createAlbum: mocks.createAlbum,
  createPost: mocks.createPost,
  deleteAlbum: mocks.deleteAlbum,
  deletePost: mocks.deletePost,
  listActivities: mocks.listActivities,
  listAlbums: mocks.listAlbums,
  listMatches: mocks.listMatches,
  listPosts: mocks.listPosts,
  reportContent: mocks.reportContent,
  setPostLike: mocks.setPostLike,
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
  data: {
    activeTab: string;
    focusPostId: number;
    access: string;
    composerText: string;
    timeline: PagedFeedState<ContentPostRow>;
    albums: PagedFeedState<unknown>;
  };
  timelineFeed: ContentFeed<ContentPostRow>;
  setData(patch: Record<string, unknown>): void;
  createFeeds(): void;
  boot(silent: boolean, locateFocus: boolean): Promise<void>;
  publish(): Promise<void>;
  toggleLike(event: { currentTarget: { dataset: { id: number } } }): Promise<void>;
  removePost(event: { currentTarget: { dataset: { id: number } } }): Promise<void>;
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
  mocks.createPost.mockReset();
  mocks.deletePost.mockReset();
  mocks.setPostLike.mockReset();
  mocks.listAlbums.mockResolvedValue(pageOf([]));
  mocks.showModal.mockImplementation(
    (input: { success?: (result: { confirm: boolean }) => void }) =>
      input.success?.({ confirm: true }),
  );
  vi.stubGlobal('wx', {
    hideShareMenu: mocks.hideShareMenu,
    showToast: mocks.showToast,
    showModal: mocks.showModal,
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

describe('MP14 时间线 feed', () => {
  it('通知定位切到 100/页后，上拉加载继续沿用同一口径', async () => {
    mocks.listPosts.mockImplementation(({ page, pageSize }: { page: number; pageSize: number }) =>
      Promise.resolve(
        pageSize === 100
          ? pageOf([post(9)], { page, total: 250, pageSize: 100 })
          : pageOf([post(1), post(2)], { page, total: 30, pageSize: 10 }),
      ),
    );
    const page = pageInstance();
    page.setData({ activeTab: 'timeline', focusPostId: 9 });
    page.createFeeds();

    await page.boot(false, true);

    expect(page.data.timeline.pageSize).toBe(100);
    expect(page.data.timeline.items.map((item) => item.id)).toEqual([9]);
    expect(mocks.listPosts).toHaveBeenLastCalledWith({ page: 1, pageSize: 100 });

    await page.timelineFeed.loadMore();

    expect(mocks.listPosts).toHaveBeenLastCalledWith({ page: 2, pageSize: 100 });
  });

  it('发布/点赞/删除通过 feed update 收口', async () => {
    mocks.listPosts.mockResolvedValue(pageOf([post(1), post(2)], { total: 2 }));
    mocks.createPost.mockResolvedValue(post(99));
    mocks.setPostLike.mockResolvedValue({ liked: true, likeCount: 5 });
    mocks.deletePost.mockResolvedValue({ deleted: true });
    const page = pageInstance();
    page.setData({ activeTab: 'timeline' });
    page.createFeeds();
    await page.boot(false, false);

    page.setData({ composerText: '今天训练很尽兴' });
    await page.publish();
    expect(page.data.timeline.items.map((item) => item.id)).toEqual([99, 1, 2]);
    expect(page.data.timeline.total).toBe(3);

    await page.toggleLike({ currentTarget: { dataset: { id: 1 } } });
    const liked = page.data.timeline.items.find((item) => item.id === 1);
    expect(liked).toMatchObject({ likedByMe: true, likeCount: 5 });

    await page.removePost({ currentTarget: { dataset: { id: 2 } } });
    expect(page.data.timeline.items.map((item) => item.id)).toEqual([99, 1]);
    expect(page.data.timeline.total).toBe(2);
  });
});
