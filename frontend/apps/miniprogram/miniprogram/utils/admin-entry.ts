import { ADMIN_URL } from './config';

/**
 * C→B 免登录交接（P-42、P-81）：票据放 URL fragment，不进入服务端日志。
 * 个人主体小程序不支持 web-view，复制链接后由用户在浏览器打开。
 */
export function adminEntryUrl(ticket: string | null | undefined): string {
  return ticket ? `${ADMIN_URL}#ticket=${encodeURIComponent(ticket)}` : ADMIN_URL;
}
