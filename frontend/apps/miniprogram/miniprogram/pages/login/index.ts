import { refreshUnreadBadge } from '../../utils/notifications';
import { login } from '../../utils/session';

Page({
  data: {
    status: 'loading' as 'loading' | 'error',
    message: '',
  },

  onLoad() {
    void this.start();
  },

  async start() {
    this.setData({ status: 'loading', message: '' });
    try {
      const result = await login();
      if (result.needBindPhone) {
        void wx.redirectTo({ url: '/pages/phone-bind/index' });
        return;
      }
      void refreshUnreadBadge();
      void wx.switchTab({ url: '/pages/home/index' });
    } catch (error) {
      this.setData({
        status: 'error',
        message: (error as Error).message || '微信登录失败，请重试',
      });
    }
  },

  retry() {
    void this.start();
  },

  skip() {
    void wx.switchTab({ url: '/pages/home/index' });
  },
});
