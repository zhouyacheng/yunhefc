import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { AlbumDetail, Page } from '../miniprogram/types/api';

const mocks = vi.hoisted(() => ({
  listAlbums: vi.fn(),
  createAlbum: vi.fn(),
  navigateTo: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => ({
  listAlbums: mocks.listAlbums,
  createAlbum: mocks.createAlbum,
}));

const { openRelatedAlbum } = await import('../miniprogram/utils/content-entry');

function page<T>(items: T[], total = items.length): Page<T> {
  return { items, total, page: 1, pageSize: 10, totalPages: total > 0 ? 1 : 0 };
}

const album: AlbumDetail = {
  id: 31,
  title: '第 12 场 照片',
  matchId: 12,
  activityId: null,
  ownerName: '第 12 场',
  coverThumbnailUrl: null,
  coverUrl: null,
  photoCount: 0,
  createdBy: 7,
  createdAt: '2026-11-27T10:00:00+08:00',
};

beforeEach(() => {
  mocks.listAlbums.mockReset();
  mocks.createAlbum.mockReset();
  mocks.navigateTo.mockReset();
  mocks.navigateTo.mockImplementation((input: { success?: () => void }) => input.success?.());
  vi.stubGlobal('wx', { navigateTo: mocks.navigateTo });
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('content photo entry', () => {
  it('opens the filtered album list when the owner already has albums', async () => {
    mocks.listAlbums.mockResolvedValue(page([album]));
    await openRelatedAlbum({ matchId: 12, title: '第 12 场' });

    expect(mocks.listAlbums).toHaveBeenCalledWith({ matchId: 12, page: 1, pageSize: 10 });
    expect(mocks.createAlbum).not.toHaveBeenCalled();
    expect(mocks.navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/pages/posts/index?tab=albums&matchId=12' }),
    );
  });

  it('creates a default album for the activity when none exists', async () => {
    mocks.listAlbums.mockResolvedValue(page([]));
    mocks.createAlbum.mockResolvedValue({ ...album, id: 41, activityId: 21, matchId: null });

    await openRelatedAlbum({ activityId: 21, title: '年终聚餐' });

    expect(mocks.listAlbums).toHaveBeenCalledWith({ activityId: 21, page: 1, pageSize: 10 });
    expect(mocks.createAlbum).toHaveBeenCalledWith({
      title: '年终聚餐 照片',
      activityId: 21,
    });
    expect(mocks.navigateTo).toHaveBeenCalledWith(
      expect.objectContaining({ url: '/pages/album-detail/index?id=41' }),
    );
  });
});
