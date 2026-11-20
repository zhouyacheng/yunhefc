import type { NotificationUnreadCount } from '../types/api';
import { request } from './request';
import { getUser } from './session';

/** tabBar 顺序：首页 0、比赛 1、球队 2、我的 3。 */
const PROFILE_TAB_INDEX = 3;

export function clearUnreadBadge(): void {
  wx.removeTabBarBadge({ index: PROFILE_TAB_INDEX, fail: () => undefined });
}

/** 刷新「我的」角标；未登录或请求失败时保持无角标。 */
export async function refreshUnreadBadge(): Promise<number> {
  if (!getUser()) {
    clearUnreadBadge();
    return 0;
  }
  try {
    const { count } = await request<NotificationUnreadCount>('GET', '/notifications/unread-count');
    if (count > 0) {
      wx.setTabBarBadge({
        index: PROFILE_TAB_INDEX,
        text: count > 99 ? '99+' : String(count),
        fail: () => undefined,
      });
    } else {
      clearUnreadBadge();
    }
    return count;
  } catch {
    return 0;
  }
}
