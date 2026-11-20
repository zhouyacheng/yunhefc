import { sessionReady } from './utils/session';

App({
  onLaunch() {
    // 启动先静默探测会话；未登录则静默 wx.login。
    // 未绑定时只保留 bindTicket，不强制跳转手机绑定页，公开内容仍可匿名浏览。
    void sessionReady();
  },
});
