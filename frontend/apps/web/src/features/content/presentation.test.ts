import { describe, expect, it } from 'vitest';

import { canDeletePhoto, defaultAlbumTitle } from './presentation';

describe('defaultAlbumTitle', () => {
  it('uses the owner title with a 照片 suffix and caps at 64 characters', () => {
    expect(defaultAlbumTitle('第3场 vs 聚龙FC')).toBe('第3场 vs 聚龙FC 照片');
    expect(defaultAlbumTitle('  ')).toBe('球队相册');
    expect(Array.from(defaultAlbumTitle('x'.repeat(80))).length).toBe(64);
  });
});

describe('canDeletePhoto', () => {
  it('allows the uploader and content admins only', () => {
    expect(canDeletePhoto({ uploadedBy: 2 }, 2, false)).toBe(true);
    expect(canDeletePhoto({ uploadedBy: 2 }, 3, false)).toBe(false);
    expect(canDeletePhoto({ uploadedBy: 2 }, 3, true)).toBe(true);
    expect(canDeletePhoto({ uploadedBy: 2 }, null, false)).toBe(false);
  });
});
