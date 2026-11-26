import { describe, expect, it } from 'vitest';

import {
  CONTENT_ALBUM_FULL_ERROR,
  CONTENT_ALBUM_MAX_PHOTOS,
  CONTENT_ALBUM_TITLE_MAX_LENGTH,
  CONTENT_ALBUM_TITLE_REQUIRED,
  CONTENT_PHOTO_JPEG_QUALITY,
  CONTENT_PHOTO_MAX_BYTES,
  CONTENT_PHOTO_MAX_EDGE,
  CONTENT_PHOTO_SIZE_ERROR,
  CONTENT_PHOTO_TYPE_ERROR,
  CONTENT_POST_MAX_LENGTH,
  contentAlbumTitleError,
  contentOwnerText,
  contentPhotoDimensions,
  contentPhotoSizeError,
  contentPostTextError,
  validateContentPhoto,
} from './content';

describe('content photo rules', () => {
  it('accepts JPEG, PNG and WebP up to 5MiB', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      expect(validateContentPhoto({ type, size: CONTENT_PHOTO_MAX_BYTES })).toBeNull();
    }
  });

  it('rejects unsupported formats and oversize files', () => {
    expect(validateContentPhoto({ type: 'image/gif', size: 1024 })).toBe(CONTENT_PHOTO_TYPE_ERROR);
    expect(validateContentPhoto({ type: 'image/png', size: CONTENT_PHOTO_MAX_BYTES + 1 })).toBe(
      CONTENT_PHOTO_SIZE_ERROR,
    );
    expect(CONTENT_PHOTO_SIZE_ERROR).toBe('图片不能超过 5MB');
    expect(contentPhotoSizeError(CONTENT_PHOTO_MAX_BYTES)).toBeNull();
    expect(contentPhotoSizeError(CONTENT_PHOTO_MAX_BYTES + 1)).toBe(CONTENT_PHOTO_SIZE_ERROR);
    expect(CONTENT_ALBUM_MAX_PHOTOS).toBe(200);
    expect(CONTENT_ALBUM_FULL_ERROR).toContain('200');
  });

  it('compresses to a 2048px long edge at JPEG q80', () => {
    expect(CONTENT_PHOTO_MAX_EDGE).toBe(2048);
    expect(CONTENT_PHOTO_JPEG_QUALITY).toBe(0.8);
    expect(contentPhotoDimensions(1024, 768)).toEqual({ width: 1024, height: 768 });
    expect(contentPhotoDimensions(4096, 2048)).toEqual({ width: 2048, height: 1024 });
    expect(contentPhotoDimensions(2048, 4096)).toEqual({ width: 1024, height: 2048 });
  });
});

describe('content text rules', () => {
  it('requires 1-2000 characters after trimming', () => {
    expect(CONTENT_POST_MAX_LENGTH).toBe(2000);
    expect(contentPostTextError('  ')).toBe('请输入动态内容');
    expect(contentPostTextError(' 今天赢球了\n明天继续 ')).toBeNull();
    expect(contentPostTextError('x'.repeat(2000))).toBeNull();
    expect(contentPostTextError('x'.repeat(2001))).toContain('2000');
  });

  it('requires a 1-64 character album title after trimming', () => {
    expect(CONTENT_ALBUM_TITLE_MAX_LENGTH).toBe(64);
    expect(contentAlbumTitleError(' ')).toBe(CONTENT_ALBUM_TITLE_REQUIRED);
    expect(contentAlbumTitleError(' 周末比赛 ')).toBeNull();
    expect(contentAlbumTitleError('x'.repeat(65))).toContain('64');
  });
});

describe('content owner text', () => {
  it('falls back to the team album label when unowned', () => {
    expect(contentOwnerText({ matchId: null, activityId: null, ownerName: null })).toBe(
      '球队通用相册',
    );
  });

  it('uses the owner title and handles deleted owners', () => {
    expect(contentOwnerText({ matchId: 1, activityId: null, ownerName: '第3场 vs 对手' })).toBe(
      '第3场 vs 对手',
    );
    expect(contentOwnerText({ matchId: null, activityId: 5, ownerName: null })).toBe('归属已删除');
  });
});
