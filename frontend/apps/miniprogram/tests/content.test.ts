import { describe, expect, it } from 'vitest';

import type { AlbumPhoto, AlbumSummary, AuthUser, ContentPost } from '../miniprogram/types/api';
import {
  applyPostLike,
  buildAlbumCardView,
  buildContentPhotoRow,
  buildContentPostRow,
  contentAccessState,
  contentAlbumTitleError,
  contentIdFromQuery,
  contentOwnerText,
  contentPostTextError,
  contentVisible,
  defaultAlbumTitle,
} from '../miniprogram/utils/content';

const user: AuthUser = {
  id: 7,
  name: '账号名',
  phoneMasked: '139****0000',
  status: 1,
  statusLabel: 'ACTIVE',
  roles: ['PLAYER'],
  createdAt: '2026-11-01T10:00:00+08:00',
  wechatBound: true,
};

function album(overrides: Partial<AlbumSummary> = {}): AlbumSummary {
  return {
    id: 3,
    title: '训练照片',
    matchId: null,
    activityId: null,
    ownerName: null,
    coverThumbnailUrl: 'https://cdn.example.com/cover.jpg',
    photoCount: 12,
    createdBy: 7,
    createdAt: '2026-11-26T20:00:00+08:00',
    ...overrides,
  };
}

function post(overrides: Partial<ContentPost> = {}): ContentPost {
  return {
    id: 9,
    author: { id: 7, name: '张三', avatarUrl: null },
    content: '今天训练很尽兴',
    album: null,
    likeCount: 2,
    likedByMe: false,
    createdAt: '2026-11-27T09:30:00+08:00',
    ...overrides,
  };
}

describe('content access', () => {
  it('仅登录且非待审核、持 content:view:any 时可见', () => {
    expect(contentAccessState(null, ['content:view:any'])).toBe('guest');
    expect(contentAccessState({ ...user, status: 0 }, ['content:view:any'])).toBe('pending');
    expect(contentAccessState(user, [])).toBe('blocked');
    expect(contentAccessState(user, ['content:view:any'])).toBe('ok');
    expect(contentVisible(user, ['content:view:any'])).toBe(true);
    expect(contentVisible(user, [])).toBe(false);
  });
});

describe('content view mapping', () => {
  it('maps album owner, cover and photo count', () => {
    expect(buildAlbumCardView(album())).toEqual({
      id: 3,
      title: '训练照片',
      ownerText: '球队通用相册',
      photoCountText: '12 张',
      timeText: '2026/11/26 20:00',
      coverUrl: 'https://cdn.example.com/cover.jpg',
    });
    expect(contentOwnerText(album({ matchId: 5, ownerName: '第 12 场' }))).toBe('第 12 场');
    expect(contentOwnerText(album({ activityId: 6, ownerName: null }))).toBe('归属已删除');
    expect(buildAlbumCardView(album({ coverThumbnailUrl: 'oss://key' })).coverUrl).toBe('');
  });

  it('maps post author, album and delete permission', () => {
    const row = buildContentPostRow(post({ album: album({ matchId: 5, ownerName: '第 12 场' }) }), {
      userId: 7,
      canDeleteAny: false,
    });
    expect(row).toMatchObject({
      id: 9,
      authorName: '张三',
      authorInitial: '张',
      avatarUrl: '',
      timeText: '2026/11/27 09:30',
      likeCount: 2,
      likedByMe: false,
      canDelete: true,
    });
    expect(row.album).toMatchObject({ id: 3, ownerText: '第 12 场', photoCountText: '12 张' });

    expect(buildContentPostRow(post(), { userId: 8, canDeleteAny: false }).canDelete).toBe(false);
    expect(buildContentPostRow(post(), { userId: 8, canDeleteAny: true }).canDelete).toBe(true);
    expect(buildContentPostRow(post(), { userId: null, canDeleteAny: false }).canDelete).toBe(
      false,
    );
  });

  it('maps photo uploader time and delete permission', () => {
    const photo: AlbumPhoto = {
      id: 88,
      thumbnailUrl: 'https://cdn.example.com/t.jpg',
      originalUrl: 'https://cdn.example.com/o.jpg',
      uploadedBy: 7,
      uploaderName: '张三',
      createdAt: '2026-11-27T18:05:00+08:00',
    };
    expect(buildContentPhotoRow(photo, { userId: 7, canDeleteAny: false })).toMatchObject({
      id: 88,
      timeText: '2026/11/27 18:05',
      canDelete: true,
    });
    expect(buildContentPhotoRow(photo, { userId: 8, canDeleteAny: false }).canDelete).toBe(false);
    expect(buildContentPhotoRow(photo, { userId: 8, canDeleteAny: true }).canDelete).toBe(true);
  });

  it('applies server like state over the local row', () => {
    const row = buildContentPostRow(post(), { userId: 7, canDeleteAny: false });
    expect(applyPostLike(row, { liked: true, likeCount: 3 })).toMatchObject({
      likedByMe: true,
      likeCount: 3,
    });
    expect(applyPostLike(row, { liked: false, likeCount: 1 })).toMatchObject({
      likedByMe: false,
      likeCount: 1,
    });
  });
});

describe('content validation', () => {
  it('validates post text and album title', () => {
    expect(contentPostTextError('  ')).toBe('请输入动态内容');
    expect(contentPostTextError(' 训练 ')).toBeNull();
    expect(contentPostTextError('x'.repeat(2001))).toBe('动态内容不能超过 2000 字');
    expect(contentAlbumTitleError('')).toBe('请输入相册名称');
    expect(contentAlbumTitleError('x'.repeat(65))).toBe('相册名称不能超过 64 字');
    expect(contentAlbumTitleError('球队相册')).toBeNull();
  });

  it('builds default album titles and parses ids', () => {
    expect(defaultAlbumTitle(' 第 12 场 ')).toBe('第 12 场 照片');
    expect(defaultAlbumTitle('')).toBe('球队相册');
    expect(Array.from(defaultAlbumTitle('x'.repeat(80))).length).toBe(64);
    expect(contentIdFromQuery('12')).toBe(12);
    expect(contentIdFromQuery('0')).toBeNull();
    expect(contentIdFromQuery(undefined)).toBeNull();
  });
});
