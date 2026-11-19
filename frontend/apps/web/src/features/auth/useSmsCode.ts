import { isApiError } from '@yunhefc/api-client';
import { onBeforeUnmount, ref } from 'vue';

import { api } from '../../api/client';
import { authErrorMessage, validatePhone } from './validation';

export interface SmsSendResult {
  ok: boolean;
  phoneError?: string;
  errorMessage?: string;
}

/** BIND 短信发送与 60 秒倒计时；微信注册页与个人中心补绑手机号共用。 */
export function useSmsCode() {
  const countdown = ref(0);
  const sending = ref(false);
  const hint = ref('');
  let timer: ReturnType<typeof setInterval> | null = null;

  function stopCountdown(): void {
    if (timer) {
      clearInterval(timer);
      timer = null;
    }
  }

  function startCountdown(): void {
    stopCountdown();
    countdown.value = 60;
    timer = setInterval(() => {
      countdown.value -= 1;
      if (countdown.value <= 0) {
        stopCountdown();
      }
    }, 1000);
  }

  async function send(phone: string): Promise<SmsSendResult> {
    hint.value = '';
    const phoneError = validatePhone(phone);
    if (phoneError) {
      return { ok: false, phoneError };
    }
    if (countdown.value > 0 || sending.value) {
      return { ok: false };
    }

    sending.value = true;
    try {
      await api.post('/auth/sms-codes', { phone: phone.trim(), purpose: 'BIND' });
      startCountdown();
      hint.value = import.meta.env.DEV
        ? `开发环境验证码：${import.meta.env.VITE_SMS_MOCK_CODE ?? '123456'}，60 秒内有效`
        : '验证码已发送，请留意短信';
      return { ok: true };
    } catch (error) {
      return {
        ok: false,
        errorMessage: isApiError(error)
          ? authErrorMessage(error.code, error.message)
          : '验证码发送失败，请稍后重试',
      };
    } finally {
      sending.value = false;
    }
  }

  onBeforeUnmount(stopCountdown);

  return { countdown, sending, hint, send, stop: stopCountdown };
}
