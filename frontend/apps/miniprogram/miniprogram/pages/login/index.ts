import { errorMessage } from '../../utils/api-errors';
import { PHONE_PATTERN, type ValueEvent } from '../../utils/input';
import { refreshUnreadBadge } from '../../utils/notifications';
import { loginWithPassword, loginWithWechat } from '../../utils/session';

Page({
  data: {
    phone: '',
    password: '',
    submitting: false,
    wechatLoading: false,
    error: '',
  },

  onPhoneInput(event: ValueEvent) {
    this.setData({ phone: event.detail.value });
  },

  onPasswordInput(event: ValueEvent) {
    this.setData({ password: event.detail.value });
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    const phone = this.data.phone.trim();
    const password = this.data.password;
    if (!PHONE_PATTERN.test(phone)) {
      this.setData({ error: '请输入正确的手机号' });
      return;
    }
    if (!password) {
      this.setData({ error: '请输入密码' });
      return;
    }
    this.setData({ submitting: true, error: '' });
    try {
      await loginWithPassword(phone, password);
      this.enterHome();
    } catch (error) {
      this.setData({ error: errorMessage(error, '登录失败，请检查手机号与密码') });
    } finally {
      this.setData({ submitting: false });
    }
  },

  async wechatLogin() {
    if (this.data.wechatLoading || this.data.submitting) {
      return;
    }
    this.setData({ wechatLoading: true, error: '' });
    try {
      const result = await loginWithWechat();
      if (result.needBindPhone) {
        void wx.redirectTo({ url: '/pages/phone-bind/index' });
        return;
      }
      this.enterHome();
    } catch (error) {
      this.setData({ error: errorMessage(error, '微信登录失败，请重试') });
    } finally {
      this.setData({ wechatLoading: false });
    }
  },

  /** 登录成功后的统一收尾。 */
  enterHome() {
    void refreshUnreadBadge();
    void wx.switchTab({ url: '/pages/home/index' });
  },

  skip() {
    void wx.switchTab({ url: '/pages/home/index' });
  },
});
