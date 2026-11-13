import type { WechatAuthorizeResponse } from '@yunhefc/shared';

import { api } from '../../api/client';

/** 微信内置浏览器检测；微信入口仅在微信内展示。 */
export function isWechatBrowser(userAgent: string | null | undefined): boolean {
  return /MicroMessenger/i.test(userAgent ?? '');
}

/** AUTH-07：发起微信授权并返回微信侧跳转地址。 */
export async function requestWechatAuthorize(redirect: string): Promise<string> {
  const result = await api.get<WechatAuthorizeResponse>('/auth/wechat/authorize', {
    query: { redirect },
  });
  return result.authorizeUrl;
}
