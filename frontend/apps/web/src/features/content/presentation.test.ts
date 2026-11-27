import { describe, expect, it } from 'vitest';

import { canDeleteOwned, canDeletePhoto, defaultAlbumTitle, toPositiveId } from './presentation';

describe('defaultAlbumTitle', () => {
  it('uses the owner title with a 照片 suffix and caps at 64 characters', () => {
    expect(defaultAlbumTitle('第3场 vs 聚龙FC')).toBe('第3场 vs 聚龙FC 照片');
    expect(defaultAlbumTitle('  ')).toBe('球队相册');
    expect(Array.from(defaultAlbumTitle('x'.repeat(80))).length).toBe(64);
  });
});

describe('content delete rules', () => {
  it('allows the owner and content admins only', () => {
    expect(canDeleteOwned(2, 2, false)).toBe(true);
    expect(canDeleteOwned(2, 3, false)).toBe(false);
    expect(canDeleteOwned(2, 3, true)).toBe(true);
    expect(canDeleteOwned(2, null, false)).toBe(false);
    expect(canDeletePhoto({ uploadedBy: 2 }, 2, false)).toBe(true);
  });
});

describe('toPositiveId', () => {
  it('parses positive integer route/query values', () => {
    expect(toPositiveId('12')).toBe(12);
    expect(toPositiveId(['12'])).toBe(12);
    expect(toPositiveId(0)).toBeNull();
    expect(toPositiveId('-1')).toBeNull();
    expect(toPositiveId('abc')).toBeNull();
    expect(toPositiveId(undefined)).toBeNull();
  });
});
