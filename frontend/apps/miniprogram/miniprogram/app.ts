import { refreshUnreadBadge } from './utils/notifications';
import { sessionReady } from './utils/session';

const BADGE_INTERVAL_MS = 60_000;

let badgeTimer: ReturnType<typeof setInterval> | undefined;

function startBadgePolling(): void {
  if (badgeTimer === undefined) {
    badgeTimer = setInterval(() => {
      void refreshUnreadBadge();
    }, BADGE_INTERVAL_MS);
  }
}

function stopBadgePolling(): void {
  if (badgeTimer !== undefined) {
    clearInterval(badgeTimer);
    badgeTimer = undefined;
  }
}

App({
  onLaunch() {
    // 启动先静默探测会话；未登录则静默 wx.login。
    // 未绑定时只保留 bindTicket，不强制跳转手机绑定页，公开内容仍可匿名浏览。
    void sessionReady().then(() => refreshUnreadBadge());
    startBadgePolling();
  },

  onShow() {
    startBadgePolling();
  },

  onHide() {
    stopBadgePolling();
  },
});
