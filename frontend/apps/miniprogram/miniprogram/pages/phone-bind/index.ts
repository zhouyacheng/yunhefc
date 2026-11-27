import {
  applyAgreementChange,
  hasAgreedToAgreements,
  recordAgreementConsent,
  requireAgreement,
  type AgreementChangeEvent,
} from '../../utils/agreement';
import { errorMessage } from '../../utils/api-errors';
import { PHONE_PATTERN, type ValueEvent } from '../../utils/input';
import { inviteLoginPath, inviteTokenFromQuery, navigateAfterAuth } from '../../utils/invite';
import { refreshUnreadBadge } from '../../utils/notifications';
import { ApiError } from '../../utils/request';
import { bindPhone, clearBindTicket, getBindTicket, sendSmsCode } from '../../utils/session';

const POSITION_LABELS = ['门将', '后卫', '中场', '前锋'];

let timer: ReturnType<typeof setInterval> | undefined;

Page({
  data: {
    phone: '',
    code: '',
    name: '',
    positionLabels: POSITION_LABELS,
    positionIndex: 2,
    countdown: 0,
    sending: false,
    submitting: false,
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
    if (!getBindTicket()) {
      this.setData({ error: '登录已过期，请返回重新登录' });
    }
    // 已同意过当前版本的用户默认勾选；协议版本更新后重新要求勾选。
    this.setData({ agreed: hasAgreedToAgreements() });
  },

  onUnload() {
    clearTimer();
  },

  onPhoneInput(event: ValueEvent) {
    this.setData({ phone: event.detail.value });
  },

  onCodeInput(event: ValueEvent) {
    this.setData({ code: event.detail.value });
  },

  onNameInput(event: ValueEvent) {
    this.setData({ name: event.detail.value });
  },

  onPositionChange(event: ValueEvent) {
    this.setData({ positionIndex: Number(event.detail.value) });
  },

  onAgreementChange(event: AgreementChangeEvent) {
    applyAgreementChange(this, event);
  },

  async sendCode() {
    if (this.data.sending || this.data.countdown > 0) {
      return;
    }
    if (!requireAgreement(this)) {
      return;
    }
    if (!PHONE_PATTERN.test(this.data.phone)) {
      this.setData({ error: '请输入正确的手机号' });
      return;
    }
    this.setData({ sending: true, error: '' });
    recordAgreementConsent();
    try {
      await sendSmsCode(this.data.phone);
      this.setData({ countdown: 60 });
      startTimer(this);
    } catch (error) {
      this.setData({ error: errorMessage(error, '验证码发送失败') });
    } finally {
      this.setData({ sending: false });
    }
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    if (!requireAgreement(this)) {
      return;
    }
    const { phone, code, name, positionIndex } = this.data;
    if (!PHONE_PATTERN.test(phone)) {
      this.setData({ error: '请输入正确的手机号' });
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      this.setData({ error: '请输入 6 位验证码' });
      return;
    }
    // 姓名/位置只在后端判定为新手机号时必填；已有账号会忽略这两个字段。
    this.setData({ submitting: true, error: '' });
    recordAgreementConsent();
    try {
      await bindPhone({ phone, code, name: name.trim(), position: positionIndex });
      void refreshUnreadBadge();
      this.finishBind();
    } catch (error) {
      if (error instanceof ApiError && error.code === 40101) {
        this.setData({ error: '登录已过期，正在重新登录…' });
        void wx.redirectTo({ url: inviteLoginPath(this.data.inviteToken) });
        return;
      }
      const message =
        error instanceof ApiError && error.code === 40001
          ? '新手机号需要填写姓名并选择位置'
          : errorMessage(error, '绑定失败，请稍后重试');
      this.setData({ error: message });
    } finally {
      this.setData({ submitting: false });
    }
  },

  skip() {
    void wx.switchTab({ url: '/pages/home/index' });
  },

  /** 绑定成功后：邀请落地回跳优先，否则回首页。 */
  finishBind() {
    navigateAfterAuth(this.data.inviteToken);
  },

  usePassword() {
    clearBindTicket();
    void wx.redirectTo({ url: inviteLoginPath(this.data.inviteToken) });
  },
});

interface CountdownPage {
  data: { countdown: number };
  setData: (data: { countdown: number }) => void;
}

function startTimer(page: CountdownPage) {
  clearTimer();
  timer = setInterval(() => {
    const next = page.data.countdown - 1;
    if (next <= 0) {
      clearTimer();
      page.setData({ countdown: 0 });
      return;
    }
    page.setData({ countdown: next });
  }, 1000);
}

function clearTimer() {
  if (timer !== undefined) {
    clearInterval(timer);
    timer = undefined;
  }
}
