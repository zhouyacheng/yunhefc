import { describe, expect, it } from 'vitest';

import {
  AVATAR_JPEG_QUALITY,
  AVATAR_MAX_BYTES,
  AVATAR_MAX_EDGE,
  AVATAR_SIZE_ERROR,
  AVATAR_TYPE_ERROR,
  avatarDimensions,
  avatarPlaceholder,
  isDisplayableAvatarUrl,
  validateAvatarFile,
} from './avatar';

describe('validateAvatarFile', () => {
  it('accepts JPEG, PNG and WebP up to 2MiB', () => {
    for (const type of ['image/jpeg', 'image/png', 'image/webp']) {
      expect(validateAvatarFile({ type, size: AVATAR_MAX_BYTES })).toBeNull();
    }
  });

  it('rejects unsupported formats with the agreed message', () => {
    expect(validateAvatarFile({ type: 'image/gif', size: 1024 })).toBe(AVATAR_TYPE_ERROR);
    expect(validateAvatarFile({ type: '', size: 1024 })).toBe(AVATAR_TYPE_ERROR);
    expect(AVATAR_TYPE_ERROR).toBe('仅支持 JPG/PNG/WebP');
  });

  it('rejects files above 2MiB with the agreed message', () => {
    expect(validateAvatarFile({ type: 'image/png', size: AVATAR_MAX_BYTES + 1 })).toBe(
      AVATAR_SIZE_ERROR,
    );
    expect(AVATAR_SIZE_ERROR).toBe('图片不能超过 2MB，请重新选择');
  });
});

describe('avatarDimensions', () => {
  it('keeps images within the 1080px long edge', () => {
    expect(AVATAR_MAX_EDGE).toBe(1080);
    expect(avatarDimensions(800, 600)).toEqual({ width: 800, height: 600 });
    expect(avatarDimensions(1080, 1920)).toEqual({ width: 608, height: 1080 });
    expect(avatarDimensions(1920, 1080)).toEqual({ width: 1080, height: 608 });
    expect(avatarDimensions(2400, 2400)).toEqual({ width: 1080, height: 1080 });
  });

  it('never rounds a scaled edge down to zero', () => {
    expect(avatarDimensions(2160, 1)).toEqual({ width: 1080, height: 1 });
    expect(avatarDimensions(0, 0)).toEqual({ width: 0, height: 0 });
  });
});

describe('avatar compression output', () => {
  it('uses JPEG quality 0.85', () => {
    expect(AVATAR_JPEG_QUALITY).toBe(0.85);
  });
});

describe('avatar placeholders', () => {
  it('falls back to the first character of the name', () => {
    expect(avatarPlaceholder('测试后卫01')).toBe('测');
    expect(avatarPlaceholder('  Alex ')).toBe('A');
    expect(avatarPlaceholder('')).toBe('?');
    expect(avatarPlaceholder(null)).toBe('?');
  });

  it('only displays absolute http(s) URLs', () => {
    expect(isDisplayableAvatarUrl('https://oss.example.com/a.jpg')).toBe(true);
    expect(isDisplayableAvatarUrl('http://oss.example.com/a.jpg')).toBe(true);
    expect(isDisplayableAvatarUrl('avatar/1/a.jpg')).toBe(false);
    expect(isDisplayableAvatarUrl(null)).toBe(false);
  });
});
