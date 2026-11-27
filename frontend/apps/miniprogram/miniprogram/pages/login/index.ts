import {
  AGREEMENT_REQUIRED_MESSAGE,
  ensureAgreement,
  hasAgreedToAgreements,
  recordAgreementConsent,
} from '../../utils/agreement';
import { errorMessage } from '../../utils/api-errors';
import { PHONE_PATTERN, type ValueEvent } from '../../utils/input';
import { inviteBindPath, inviteTokenFromQuery, navigateAfterAuth } from '../../utils/invite';
import { refreshUnreadBadge } from '../../utils/notifications';
import { loginWithPassword, loginWithWechat } from '../../utils/session';

interface FieldEvent {
  currentTarget: { dataset: { field?: string } };
}

Page({
  data: {
    phone: '',
    password: '',
    focus: '',
    submitting: false,
    wechatLoading: false,
    error: '',
    /** 是否已勾选《用户服务协议》与《隐私政策》。 */
    agreed: false,
    /** MP13 邀请落地回跳；只接受 token，不提供通用 redirect。 */
    inviteToken: '',
  },

  onLoad(query: Record<string, string | undefined>) {
    const inviteToken = inviteTokenFromQuery(query);
    if (inviteToken) {
      this.setData({ inviteToken });
    }
    // 已同意过当前版本的用户默认勾选；协议版本更新后重新要求勾选。
    this.setData({ agreed: hasAgreedToAgreements() });
  },

  onPhoneInput(event: ValueEvent) {
    this.setData({ phone: event.detail.value });
  },

  onPasswordInput(event: ValueEvent) {
    this.setData({ password: event.detail.value });
  },

  onFieldFocus(event: FieldEvent) {
    this.setData({ focus: String(event.currentTarget.dataset.field ?? '') });
  },

  onFieldBlur() {
    this.setData({ focus: '' });
  },

  onAgreementChange(event: { detail: { checked: boolean } }) {
    this.setData({ agreed: event.detail.checked, error: '' });
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    if (!ensureAgreement(this.data.agreed, () => this.setData({ error: AGREEMENT_REQUIRED_MESSAGE }))) {
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
    recordAgreementConsent();
    try {
      await loginWithPassword(phone, password);
      this.finishLogin();
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
    if (!ensureAgreement(this.data.agreed, () => this.setData({ error: AGREEMENT_REQUIRED_MESSAGE }))) {
      return;
    }
    this.setData({ wechatLoading: true, error: '' });
    recordAgreementConsent();
    try {
      const result = await loginWithWechat();
      if (result.needBindPhone) {
        void wx.redirectTo({ url: inviteBindPath(this.data.inviteToken) });
        return;
      }
      this.finishLogin();
    } catch (error) {
      this.setData({ error: errorMessage(error, '微信登录失败，请重试') });
    } finally {
      this.setData({ wechatLoading: false });
    }
  },

  /** 登录成功后的统一收尾：邀请落地回跳优先，否则回首页。 */
  finishLogin() {
    void refreshUnreadBadge();
    navigateAfterAuth(this.data.inviteToken);
  },

  skip() {
    void wx.switchTab({ url: '/pages/home/index' });
  },
});
