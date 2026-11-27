import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

import type { AlbumDetail, AlbumPhoto, Page } from '../miniprogram/types/api';
import type { ContentFeed } from '../miniprogram/utils/content-feed';
import type { ContentPhotoRow } from '../miniprogram/utils/content';
import type { PagedFeedState } from '../miniprogram/utils/paged-feed';

const mocks = vi.hoisted(() => ({
  fetchAlbum: vi.fn(),
  listAlbumPhotos: vi.fn(),
  deleteAlbumPhoto: vi.fn(),
  deleteAlbum: vi.fn(),
  updateAlbum: vi.fn(),
  uploadAlbumPhoto: vi.fn(),
  reportContent: vi.fn(),
  showToast: vi.fn(),
  showModal: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => ({
  fetchAlbum: mocks.fetchAlbum,
  listAlbumPhotos: mocks.listAlbumPhotos,
  deleteAlbumPhoto: mocks.deleteAlbumPhoto,
  deleteAlbum: mocks.deleteAlbum,
  updateAlbum: mocks.updateAlbum,
  uploadAlbumPhoto: mocks.uploadAlbumPhoto,
  reportContent: mocks.reportContent,
}));

function pageOf<T>(items: T[], total = items.length): Page<T> {
  return { items, page: 1, pageSize: 30, total, totalPages: total > 0 ? 1 : 0 };
}

function photo(id: number): AlbumPhoto {
  return {
    id,
    thumbnailUrl: `https://cdn.example.com/${id}-t.jpg`,
    originalUrl: `https://cdn.example.com/${id}.jpg`,
    uploadedBy: 7,
    uploaderName: '张三',
    createdAt: '2026-11-27T18:05:00+08:00',
  };
}

const album: AlbumDetail = {
  id: 5,
  title: '第 12 场 照片',
  matchId: 12,
  activityId: null,
  ownerName: '第 12 场',
  coverThumbnailUrl: null,
  coverUrl: null,
  photoCount: 2,
  createdBy: 7,
  createdAt: '2026-11-27T17:00:00+08:00',
};

interface TestPage {
  data: {
    id: number;
    userId: number;
    canDeleteAny: boolean;
    album: AlbumDetail | null;
    photos: PagedFeedState<ContentPhotoRow>;
  };
  photoFeed: ContentFeed<ContentPhotoRow>;
  setData(patch: Record<string, unknown>): void;
  createPhotoFeed(): void;
  removePhoto(photo: AlbumPhoto): Promise<void>;
}

let pageConfig!: Record<string, unknown> & { data: Record<string, unknown> };

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/album-detail/index');
});

beforeEach(() => {
  mocks.listAlbumPhotos.mockReset();
  mocks.deleteAlbumPhoto.mockReset();
  mocks.showModal.mockImplementation(
    (input: { success?: (result: { confirm: boolean }) => void }) =>
      input.success?.({ confirm: true }),
  );
  vi.stubGlobal('wx', {
    showToast: mocks.showToast,
    showModal: mocks.showModal,
    hideShareMenu: vi.fn(),
    navigateTo: vi.fn(),
    redirectTo: vi.fn(),
    previewImage: vi.fn(),
    showActionSheet: vi.fn(),
    stopPullDownRefresh: vi.fn(),
  });
});

afterEach(() => {
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

describe('MP14 相册详情 photo feed', () => {
  it('loads photos at 30 per page and removes one through feed update', async () => {
    mocks.listAlbumPhotos.mockResolvedValue(pageOf([photo(1), photo(2)]));
    mocks.deleteAlbumPhoto.mockResolvedValue({ deleted: true, coverReset: false });
    const page = pageInstance();
    page.setData({ id: 5, userId: 7, album });
    page.createPhotoFeed();

    await page.photoFeed.load();
    expect(mocks.listAlbumPhotos).toHaveBeenCalledWith(5, 1, 30);
    expect(page.data.photos.items.map((item) => item.id)).toEqual([1, 2]);

    const reloadIfEmpty = vi.spyOn(page.photoFeed, 'reloadIfEmpty');
    await page.removePhoto(photo(1));

    expect(mocks.deleteAlbumPhoto).toHaveBeenCalledWith(5, 1);
    expect(reloadIfEmpty).toHaveBeenCalled();
    expect(page.data.photos.items.map((item) => item.id)).toEqual([2]);
    expect(page.data.photos.total).toBe(1);
  });
});
