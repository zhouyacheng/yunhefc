<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import type { WechatBindPhoneResponse } from '@yunhefc/shared';
import { POSITION_TEXT } from '@yunhefc/shared';
import { ActionSheet, Button, CellGroup, Field } from 'vant';
import { computed, onBeforeUnmount, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import {
  authErrorMessage,
  queryString,
  validateName,
  validatePhone,
  validatePosition,
  validateSmsCode,
} from '../features/auth/validation';
import { requestWechatAuthorize } from '../features/auth/wechat';
import { safeRouteRedirect, toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';
import { markSessionHint } from '../stores/session-hint';

interface FieldErrors {
  phone?: string;
  code?: string;
  name?: string;
  position?: string;
}

const ERROR_TEXT: Readonly<Record<number, string>> = {
  [ERROR_CODES.VALIDATION_FAILED]: '微信授权已失效，请重新授权',
  [ERROR_CODES.ACCOUNT_DISABLED]: '账号已停用，请联系管理员',
  [ERROR_CODES.BUSINESS_RULE_VIOLATION]: '该微信已绑定其他账号，请联系管理员解绑后再试',
  [ERROR_CODES.INTERNAL_ERROR]: '微信授权暂时不可用，请稍后重试',
};

const POSITION_ACTIONS = [0, 1, 2, 3].map((code) => ({ name: POSITION_TEXT[code] ?? '', code }));

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const bindMode = computed(() => queryString(route.query.bind) === '1');
const errorCode = computed(() => {
  const raw = queryString(route.query.error);
  const value = raw ? Number(raw) : Number.NaN;
  return Number.isFinite(value) ? value : undefined;
});
const errorText = computed(
  () =>
    (errorCode.value !== undefined ? ERROR_TEXT[errorCode.value] : undefined) ??
    '微信授权未完成，请重试或使用手机号登录',
);
const redirectPath = computed(() => safeRouteRedirect(route.query.redirect));
const loginTarget = computed(() => ({
  name: 'login',
  query: redirectPath.value ? { redirect: toBrowserPath(redirectPath.value) } : {},
}));

const phone = ref('');
const code = ref('');
const name = ref('');
const position = ref<number | null>(null);

const submitting = ref(false);
const codeSending = ref(false);
const retrying = ref(false);
const countdown = ref(0);
const errorMessage = ref('');
const retryMessage = ref('');
const smsHint = ref('');
const fieldErrors = ref<FieldErrors>({});
const showPosition = ref(false);

const positionLabel = computed(() =>
  position.value === null ? '' : (POSITION_TEXT[position.value] ?? ''),
);

let countdownTimer: ReturnType<typeof setInterval> | null = null;

function stopCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown(): void {
  stopCountdown();
  countdown.value = 60;
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      stopCountdown();
    }
  }, 1000);
}

onBeforeUnmount(stopCountdown);

async function sendCode(): Promise<void> {
  errorMessage.value = '';
  smsHint.value = '';
  const phoneError = validatePhone(phone.value);
  if (phoneError) {
    fieldErrors.value = { ...fieldErrors.value, phone: phoneError };
    return;
  }
  if (countdown.value > 0 || codeSending.value) {
    return;
  }

  codeSending.value = true;
  try {
    await api.post('/auth/sms-codes', { phone: phone.value.trim(), purpose: 'BIND' });
    startCountdown();
    smsHint.value = import.meta.env.DEV
      ? `开发环境验证码：${import.meta.env.VITE_SMS_MOCK_CODE ?? '123456'}，60 秒内有效`
      : '验证码已发送，请留意短信';
  } catch (error) {
    errorMessage.value = isApiError(error)
      ? authErrorMessage(error.code, error.message)
      : '验证码发送失败，请稍后重试';
  } finally {
    codeSending.value = false;
  }
}

/** 回调失败页重试：重新发起 AUTH-07，不刷新已失效的回调 URL。 */
async function retry(): Promise<void> {
  retryMessage.value = '';
  retrying.value = true;
  try {
    const authorizeUrl = await requestWechatAuthorize(redirectPath.value ?? '/');
    // 成功后后端 302 直达站内页面，先写会话提示，落地页才能恢复登录态。
    markSessionHint();
    window.location.assign(authorizeUrl);
  } catch (error) {
    retryMessage.value = isApiError(error)
      ? authErrorMessage(error.code, error.message)
      : '无法发起微信授权，请稍后重试';
    retrying.value = false;
  }
}

async function submitBind(): Promise<void> {
  errorMessage.value = '';
  const errors: FieldErrors = {
    phone: validatePhone(phone.value),
    code: validateSmsCode(code.value),
    name: validateName(name.value),
    position: validatePosition(position.value),
  };
  if (Object.values(errors).some(Boolean)) {
    fieldErrors.value = errors;
    return;
  }

  submitting.value = true;
  try {
    const result = await api.post<WechatBindPhoneResponse>('/auth/wechat/bind-phone', {
      phone: phone.value.trim(),
      code: code.value.trim(),
      name: name.value.trim(),
      position: position.value,
    });
    api.setCsrfToken(result.csrfToken);
    markSessionHint();
    // 补取 AUTH-10：权限、角色与未读角标。
    await session.refresh().catch(() => undefined);
    await router.replace(redirectPath.value ?? '/');
  } catch (error) {
    errorMessage.value = isApiError(error)
      ? authErrorMessage(error.code, error.message)
      : '绑定失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

function selectPosition(action: { code?: number }): void {
  if (typeof action.code === 'number') {
    position.value = action.code;
    fieldErrors.value = { ...fieldErrors.value, position: undefined };
  }
  showPosition.value = false;
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <p class="login-eyebrow">YUNHE FC</p>
      <h1>{{ bindMode ? '绑定手机号' : '微信授权未完成' }}</h1>
      <p class="muted">
        {{ bindMode ? '首次微信登录需绑定手机号，已注册手机号将直接登录' : errorText }}
      </p>
    </div>

    <div class="login-card">
      <form v-if="bindMode" class="login-form" @submit.prevent="submitBind">
        <CellGroup inset>
          <Field
            v-model="phone"
            name="phone"
            type="tel"
            label="手机号"
            placeholder="请输入手机号"
            autocomplete="tel"
            maxlength="11"
            :error-message="fieldErrors.phone"
          />
          <Field
            v-model="code"
            name="code"
            type="digit"
            label="验证码"
            placeholder="6 位数字验证码"
            autocomplete="one-time-code"
            maxlength="6"
            :error-message="fieldErrors.code"
          >
            <template #button>
              <Button
                size="small"
                type="primary"
                plain
                :disabled="countdown > 0 || codeSending || submitting"
                :loading="codeSending"
                @click="sendCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </Button>
            </template>
          </Field>
          <Field
            v-model="name"
            name="name"
            label="姓名"
            placeholder="请输入姓名"
            maxlength="32"
            :error-message="fieldErrors.name"
          />
          <Field
            :model-value="positionLabel"
            name="position"
            label="主位置"
            placeholder="请选择主位置"
            readonly
            is-link
            :error-message="fieldErrors.position"
            @click="showPosition = true"
          />
        </CellGroup>

        <p v-if="smsHint" class="login-hint">{{ smsHint }}</p>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <Button type="primary" block native-type="submit" :loading="submitting">绑定并登录</Button>
      </form>

      <template v-else>
        <p v-if="retryMessage" class="login-error">{{ retryMessage }}</p>
        <Button type="primary" block :loading="retrying" @click="retry">重新授权</Button>
      </template>

      <RouterLink v-if="!bindMode" class="wechat-alt" :to="loginTarget">
        使用手机号登录
      </RouterLink>
    </div>

    <ActionSheet
      v-model:show="showPosition"
      title="选择主位置"
      :actions="POSITION_ACTIONS"
      cancel-text="取消"
      @select="selectPosition"
    />
  </div>
</template>

<style scoped>
.wechat-alt {
  display: block;
  margin-top: 16px;
  color: #cbd5e1;
  font-size: 14px;
  text-align: center;
  text-decoration: underline;
}
</style>
