import { ApiError } from '../../utils/request';
import { bindPhone, getBindTicket, sendSmsCode } from '../../utils/session';

const POSITION_LABELS = ['门将', '后卫', '中场', '前锋'];
const PHONE = /^1\d{10}$/;

// 原生 input 的 bindinput 与 picker 的 bindchange 都把值放在 detail.value 上。
interface ValueEvent {
  detail: { value: string };
}

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
  },

  onLoad() {
    if (!getBindTicket()) {
      this.setData({ error: '登录已过期，请返回重新登录' });
    }
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

  async sendCode() {
    if (this.data.sending || this.data.countdown > 0) {
      return;
    }
    if (!PHONE.test(this.data.phone)) {
      this.setData({ error: '请输入正确的手机号' });
      return;
    }
    this.setData({ sending: true, error: '' });
    try {
      await sendSmsCode(this.data.phone);
      this.setData({ countdown: 60 });
      startTimer(this);
    } catch (error) {
      this.setData({ error: (error as Error).message || '验证码发送失败' });
    } finally {
      this.setData({ sending: false });
    }
  },

  async submit() {
    if (this.data.submitting) {
      return;
    }
    const { phone, code, name, positionIndex } = this.data;
    if (!PHONE.test(phone)) {
      this.setData({ error: '请输入正确的手机号' });
      return;
    }
    if (!/^\d{6}$/.test(code)) {
      this.setData({ error: '请输入 6 位验证码' });
      return;
    }
    // 姓名/位置只在后端判定为新手机号时必填；已有账号会忽略这两个字段。
    this.setData({ submitting: true, error: '' });
    try {
      await bindPhone({ phone, code, name: name.trim(), position: positionIndex });
      void wx.switchTab({ url: '/pages/home/index' });
    } catch (error) {
      if (error instanceof ApiError && error.code === 40101) {
        this.setData({ error: '登录已过期，正在重新登录…' });
        void wx.redirectTo({ url: '/pages/login/index' });
        return;
      }
      const message =
        error instanceof ApiError && error.code === 40001
          ? '新手机号需要填写姓名并选择位置'
          : (error as Error).message || '绑定失败，请稍后重试';
      this.setData({ error: message });
    } finally {
      this.setData({ submitting: false });
    }
  },

  skip() {
    void wx.switchTab({ url: '/pages/home/index' });
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
