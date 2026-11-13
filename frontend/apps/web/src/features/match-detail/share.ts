export type ShareMode = 'wechat' | 'native' | 'clipboard';

export const WECHAT_SHARE_HINT = '请点击右上角 ··· 选择「发送给朋友」分享本场比赛';

/** 分享稳定 URL：`{origin}{basePath}matches/{id}`。 */
export function stableMatchUrl(matchId: number, origin: string, basePath = '/'): string {
  const normalizedOrigin = origin.replace(/\/+$/, '');
  const normalizedBase =
    basePath === '/' || basePath === '' ? '/' : `/${basePath.replace(/^\/+|\/+$/g, '')}/`;
  return `${normalizedOrigin}${normalizedBase}matches/${matchId}`;
}

export function isWechatBrowser(userAgent: string | null | undefined): boolean {
  return /micromessenger/i.test(userAgent ?? '');
}

/** 微信内置浏览器优先提示右上角；否则原生分享；最后复制。 */
export function shareMode(input: { isWechat: boolean; canNativeShare: boolean }): ShareMode {
  if (input.isWechat) {
    return 'wechat';
  }
  return input.canNativeShare ? 'native' : 'clipboard';
}
