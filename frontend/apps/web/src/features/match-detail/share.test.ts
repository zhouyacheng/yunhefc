import { describe, expect, it } from 'vitest';

import { WECHAT_SHARE_HINT, isWechatBrowser, shareMode, stableMatchUrl } from './share';

describe('stableMatchUrl', () => {
  it('builds a root-based stable URL', () => {
    expect(stableMatchUrl(1005, 'http://localhost:5273')).toBe(
      'http://localhost:5273/matches/1005',
    );
  });

  it('normalizes a trailing slash on the origin', () => {
    expect(stableMatchUrl(1005, 'https://www.example.com/')).toBe(
      'https://www.example.com/matches/1005',
    );
  });

  it('keeps a non-root app base path', () => {
    expect(stableMatchUrl(7, 'https://example.com', '/app/')).toBe(
      'https://example.com/app/matches/7',
    );
    expect(stableMatchUrl(7, 'https://example.com', '/app')).toBe(
      'https://example.com/app/matches/7',
    );
  });
});

describe('isWechatBrowser', () => {
  it('detects WeChat user agents case-insensitively', () => {
    expect(
      isWechatBrowser('Mozilla/5.0 (iPhone) AppleWebKit/605.1.15 Mobile MicroMessenger/8.0.0'),
    ).toBe(true);
    expect(isWechatBrowser('micromessenger/7.0')).toBe(true);
  });

  it('rejects other user agents', () => {
    expect(isWechatBrowser('Mozilla/5.0 Chrome/120')).toBe(false);
    expect(isWechatBrowser(null)).toBe(false);
    expect(isWechatBrowser(undefined)).toBe(false);
  });

  it('exposes a top-right share hint', () => {
    expect(WECHAT_SHARE_HINT).toContain('右上角');
  });
});

describe('shareMode', () => {
  it('prefers the WeChat hint even when native sharing exists', () => {
    expect(shareMode({ isWechat: true, canNativeShare: true })).toBe('wechat');
  });

  it('uses native sharing when available outside WeChat', () => {
    expect(shareMode({ isWechat: false, canNativeShare: true })).toBe('native');
  });

  it('falls back to the clipboard', () => {
    expect(shareMode({ isWechat: false, canNativeShare: false })).toBe('clipboard');
  });
});
