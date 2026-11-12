<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { formatDateTime, POSITION_TEXT } from '@yunhefc/shared';
import { ActionSheet, Button, CellGroup, Field } from 'vant';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import {
  authErrorMessage,
  isInviteUsable,
  queryString,
  registerInviteToken,
  validateName,
  validatePassword,
  validatePhone,
  validatePosition,
  validateSmsCode,
} from '../features/auth/validation';
import { isPending } from '../features/auth/user-status';
import { safeRouteRedirect } from '../router';
import { useSessionStore } from '../stores/session';

type AuthMode = 'login' | 'register' | 'reset';

interface InviteContext {
  type: number;
  typeLabel?: string;
  team: { id: number; name: string } | null;
  match?: {
    id: number;
    title?: string;
    startAt?: string;
    venue?: string;
  } | null;
  inviter: { id: number; name: string } | null;
  expiresAt: string;
  status: number;
  exhausted?: boolean;
}

interface FieldErrors {
  phone?: string;
  code?: string;
  password?: string;
  name?: string;
  position?: string;
  newPassword?: string;
}

const MODES: Array<{ value: AuthMode; label: string }> = [
  { value: 'login', label: '登录' },
  { value: 'register', label: '注册' },
  { value: 'reset', label: '找回密码' },
];

const POSITION_ACTIONS = [0, 1, 2, 3].map((code) => ({ name: POSITION_TEXT[code] ?? '', code }));

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const mode = computed<AuthMode>(() => {
  const value = queryString(route.query.mode);
  return value === 'register' || value === 'reset' ? value : 'login';
});

const phone = ref('');
const password = ref('');
const code = ref('');
const name = ref('');
const position = ref<number | null>(null);
const newPassword = ref('');

const submitting = ref(false);
const codeSending = ref(false);
const countdown = ref(0);
const errorMessage = ref('');
const successMessage = ref('');
const registeredNotice = ref('');
const smsHint = ref('');
const fieldErrors = ref<FieldErrors>({});
const showPosition = ref(false);

const inviteToken = computed(() => queryString(route.query.inviteToken));
const invite = ref<InviteContext | null>(null);
const inviteLoading = ref(false);
const inviteUnavailable = ref(false);

const positionLabel = computed(() =>
  position.value === null ? '' : (POSITION_TEXT[position.value] ?? ''),
);

let countdownTimer: ReturnType<typeof setInterval> | null = null;
let countdownFor = '';

function smsKey(purpose: 'REGISTER' | 'RESET'): string {
  return `${phone.value.trim()}:${purpose}`;
}

function countdownActive(purpose: 'REGISTER' | 'RESET'): boolean {
  return countdown.value > 0 && countdownFor === smsKey(purpose);
}

function codeButtonText(purpose: 'REGISTER' | 'RESET'): string {
  return countdownActive(purpose) ? `${countdown.value}s 后重发` : '获取验证码';
}

function stopCountdown(): void {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
}

function startCountdown(purpose: 'REGISTER' | 'RESET'): void {
  stopCountdown();
  countdown.value = 60;
  countdownFor = smsKey(purpose);
  countdownTimer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0) {
      countdownFor = '';
      stopCountdown();
    }
  }, 1000);
}

onBeforeUnmount(stopCountdown);

function clearMessages(): void {
  errorMessage.value = '';
  successMessage.value = '';
  registeredNotice.value = '';
  smsHint.value = '';
  fieldErrors.value = {};
}

async function switchMode(next: AuthMode): Promise<void> {
  clearMessages();
  await router.replace({ query: { ...route.query, mode: next } });
}

let inviteRequest = 0;

async function loadInvite(token: string): Promise<void> {
  const request = ++inviteRequest;
  inviteLoading.value = true;
  invite.value = null;
  inviteUnavailable.value = false;
  try {
    const result = await api.get<InviteContext>(`/invites/${encodeURIComponent(token)}`, {
      redirectOnUnauthorized: false,
    });
    if (request !== inviteRequest) {
      return;
    }
    invite.value = result;
    inviteUnavailable.value = !isInviteUsable(result);
  } catch {
    if (request === inviteRequest) {
      inviteUnavailable.value = true;
    }
  } finally {
    if (request === inviteRequest) {
      inviteLoading.value = false;
    }
  }
}

watch(
  inviteToken,
  (token) => {
    if (token) {
      void loadInvite(token);
    } else {
      inviteRequest += 1;
      inviteLoading.value = false;
      invite.value = null;
      inviteUnavailable.value = false;
    }
  },
  { immediate: true },
);

async function sendCode(purpose: 'REGISTER' | 'RESET'): Promise<void> {
  clearMessages();
  const phoneError = validatePhone(phone.value);
  if (phoneError) {
    fieldErrors.value = { phone: phoneError };
    return;
  }
  if (countdownActive(purpose) || codeSending.value) {
    return;
  }

  codeSending.value = true;
  try {
    await api.post('/auth/sms-codes', { phone: phone.value.trim(), purpose });
    startCountdown(purpose);
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

async function submitLogin(): Promise<void> {
  clearMessages();
  const phoneError = validatePhone(phone.value);
  if (phoneError) {
    fieldErrors.value = { phone: phoneError };
    return;
  }
  if (!password.value) {
    fieldErrors.value = { password: '请输入密码' };
    return;
  }

  submitting.value = true;
  try {
    await session.login(phone.value.trim(), password.value);
    await router.replace(safeRouteRedirect(route.query.redirect) ?? '/matches');
  } catch (error) {
    errorMessage.value = isApiError(error)
      ? authErrorMessage(error.code, error.message)
      : '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}

async function submitRegister(): Promise<void> {
  clearMessages();
  const errors: FieldErrors = {
    phone: validatePhone(phone.value),
    code: validateSmsCode(code.value),
    password: validatePassword(password.value),
    name: validateName(name.value),
    position: validatePosition(position.value),
  };
  if (Object.values(errors).some(Boolean)) {
    fieldErrors.value = errors;
    return;
  }

  submitting.value = true;
  try {
    await session.register({
      phone: phone.value.trim(),
      code: code.value.trim(),
      password: password.value,
      name: name.value.trim(),
      position: position.value as number,
      inviteToken: registerInviteToken(inviteToken.value, invite.value, inviteUnavailable.value),
    });
    registeredNotice.value =
      session.user && isPending(session.user.status)
        ? '账号待审核，审核通过后即可参与比赛'
        : '注册成功，已加入球队';
    await new Promise((resolve) => setTimeout(resolve, 1200));
    if (route.name !== 'login' || mode.value !== 'register') {
      return;
    }
    await router.replace(safeRouteRedirect(route.query.redirect) ?? '/');
  } catch (error) {
    if (isApiError(error) && error.code === ERROR_CODES.INVITE_UNAVAILABLE) {
      inviteUnavailable.value = true;
      errorMessage.value = '邀请不可用，已切换为自助注册，请重新提交';
    } else {
      errorMessage.value = isApiError(error)
        ? authErrorMessage(error.code, error.message)
        : '注册失败，请稍后重试';
    }
  } finally {
    submitting.value = false;
  }
}

async function submitReset(): Promise<void> {
  clearMessages();
  const errors: FieldErrors = {
    phone: validatePhone(phone.value),
    code: validateSmsCode(code.value),
    newPassword: validatePassword(newPassword.value),
  };
  if (Object.values(errors).some(Boolean)) {
    fieldErrors.value = errors;
    return;
  }

  submitting.value = true;
  try {
    await api.post('/auth/password/reset', {
      phone: phone.value.trim(),
      code: code.value.trim(),
      newPassword: newPassword.value,
    });
    await switchMode('login');
    successMessage.value = '密码已重置，请使用新密码登录';
  } catch (error) {
    errorMessage.value = isApiError(error)
      ? authErrorMessage(error.code, error.message)
      : '重置失败，请稍后重试';
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
      <h1>云河FC</h1>
      <p class="muted">{{ mode === 'register' ? '加入球队，参与比赛' : '登录后参与比赛' }}</p>
    </div>

    <div class="login-card">
      <nav class="login-modes" role="tablist" aria-label="认证模式">
        <button
          v-for="item in MODES"
          :key="item.value"
          type="button"
          role="tab"
          :aria-selected="mode === item.value"
          :disabled="submitting || codeSending"
          :class="{ 'login-mode--active': mode === item.value }"
          @click="switchMode(item.value)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div v-if="inviteToken" class="login-invite" data-testid="invite-context">
        <p class="login-invite__title">
          {{ invite?.type === 1 ? '比赛邀请' : '入队邀请' }}
        </p>
        <template v-if="invite">
          <p>{{ invite.team?.name ?? '未知球队' }} · 邀请人 {{ invite.inviter?.name ?? '未知' }}</p>
          <p v-if="invite.match">
            {{ invite.match.title }} · {{ formatDateTime(invite.match.startAt) }} ·
            {{ invite.match.venue }}
          </p>
          <p>有效期至 {{ formatDateTime(invite.expiresAt) }}</p>
          <p v-if="inviteUnavailable" class="login-invite__warning">
            该邀请已失效，继续注册将进入待审核
          </p>
        </template>
        <p v-else-if="inviteLoading" class="muted">正在读取邀请…</p>
        <p v-else class="login-invite__warning">邀请不可用，可继续自助注册</p>
      </div>

      <form v-if="mode === 'login'" class="login-form" @submit.prevent="submitLogin">
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
            v-model="password"
            name="password"
            type="password"
            label="密码"
            placeholder="请输入密码"
            autocomplete="current-password"
            :error-message="fieldErrors.password"
          />
        </CellGroup>

        <p v-if="successMessage" class="login-success">{{ successMessage }}</p>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <Button type="primary" block native-type="submit" :loading="submitting">登录</Button>
      </form>

      <form v-else-if="mode === 'register'" class="login-form" @submit.prevent="submitRegister">
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
                :disabled="countdownActive('REGISTER') || codeSending || submitting"
                :loading="codeSending"
                @click="sendCode('REGISTER')"
              >
                {{ codeButtonText('REGISTER') }}
              </Button>
            </template>
          </Field>
          <Field
            v-model="password"
            name="password"
            type="password"
            label="密码"
            placeholder="8–64 位，含字母和数字"
            autocomplete="new-password"
            :error-message="fieldErrors.password"
          />
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
        <p v-if="registeredNotice" class="login-success">{{ registeredNotice }}</p>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <Button type="primary" block native-type="submit" :loading="submitting">注册</Button>
      </form>

      <form v-else class="login-form" @submit.prevent="submitReset">
        <CellGroup inset>
          <Field
            v-model="phone"
            name="phone"
            type="tel"
            label="手机号"
            placeholder="请输入注册手机号"
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
                :disabled="countdownActive('RESET') || codeSending || submitting"
                :loading="codeSending"
                @click="sendCode('RESET')"
              >
                {{ codeButtonText('RESET') }}
              </Button>
            </template>
          </Field>
          <Field
            v-model="newPassword"
            name="new-password"
            type="password"
            label="新密码"
            placeholder="8–64 位，含字母和数字"
            autocomplete="new-password"
            :error-message="fieldErrors.newPassword"
          />
        </CellGroup>

        <p v-if="smsHint" class="login-hint">{{ smsHint }}</p>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <Button type="primary" block native-type="submit" :loading="submitting">重置密码</Button>
      </form>

      <p class="login-footnote muted">换绑手机号请联系管理员</p>
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
