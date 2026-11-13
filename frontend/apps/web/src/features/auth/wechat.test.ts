import { describe, expect, it } from 'vitest';

import { isWechatBrowser } from './wechat';

describe('isWechatBrowser', () => {
  it('识别微信内置浏览器 UA', () => {
    expect(
      isWechatBrowser(
        'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 MicroMessenger/8.0.50',
      ),
    ).toBe(true);
  });

  it('普通浏览器与空 UA 返回 false', () => {
    expect(isWechatBrowser('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) Chrome/120.0')).toBe(
      false,
    );
    expect(isWechatBrowser(undefined)).toBe(false);
    expect(isWechatBrowser(null)).toBe(false);
  });
});
