<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { POSITION_TEXT } from '@yunhefc/shared';
import { ActionSheet, Button, CellGroup, Field } from 'vant';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import logoUrl from '../assets/logo.png';
import SiteFooter from '../components/SiteFooter.vue';
import {
  authErrorMessage,
  inviteTokenFromRedirect,
  queryString,
  validateName,
  validatePhone,
  validatePosition,
  validateSmsCode,
} from '../features/auth/validation';
import { useSmsCode } from '../features/auth/useSmsCode';
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
  [ERROR_CODES.PHONE_EXISTS]: '该手机号已有账号，请退出后用手机号登录，再到个人中心绑定微信',
  [ERROR_CODES.INVITE_UNAVAILABLE]: '邀请已失效，请重新打开邀请链接',
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
/** 邀请注册时 redirect 为 /invite/<token>，从中还原 inviteToken 以在 AUTH-09 消费。 */
const inviteToken = computed(() => inviteTokenFromRedirect(redirectPath.value));
const loginTarget = computed(() => ({
  name: 'login',
  query: redirectPath.value ? { redirect: toBrowserPath(redirectPath.value) } : {},
}));

const phone = ref('');
const code = ref('');
const name = ref('');
const position = ref<number | null>(null);
/** true 时走「绑定已有手机号账号」，false 时为无手机号微信注册。 */
const useExistingAccount = ref(false);

const submitting = ref(false);
const { countdown, sending: codeSending, hint: smsHint, send: sendSms } = useSmsCode();
const retrying = ref(false);
const errorMessage = ref('');
const registeredNotice = ref('');
const retryMessage = ref('');
const fieldErrors = ref<FieldErrors>({});
const showPosition = ref(false);

const positionLabel = computed(() =>
  position.value === null ? '' : (POSITION_TEXT[position.value] ?? ''),
);

async function sendCode(): Promise<void> {
  errorMessage.value = '';
  const result = await sendSms(phone.value);
  if (result.phoneError) {
    fieldErrors.value = { ...fieldErrors.value, phone: result.phoneError };
  } else if (!result.ok && result.errorMessage) {
    errorMessage.value = result.errorMessage;
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

async function submitWechat(): Promise<void> {
  errorMessage.value = '';
  const errors: FieldErrors = useExistingAccount.value
    ? { phone: validatePhone(phone.value), code: validateSmsCode(code.value) }
    : { name: validateName(name.value), position: validatePosition(position.value) };
  if (Object.values(errors).some(Boolean)) {
    fieldErrors.value = errors;
    return;
  }

  submitting.value = true;
  try {
    await session.registerViaWechat(
      useExistingAccount.value
        ? {
            phone: phone.value.trim(),
            code: code.value.trim(),
            inviteToken: inviteToken.value,
          }
        : {
            name: name.value.trim(),
            position: position.value ?? undefined,
            inviteToken: inviteToken.value,
          },
    );
    markSessionHint();
    registeredNotice.value = session.user?.status === 0
      ? '账号待审核，审核通过后可报名'
      : '注册成功，已加入球队';
    // 与手机号注册一致：先展示结果提示，再按 redirect 跳转。
    await new Promise((resolve) => setTimeout(resolve, 1200));
    await router.replace(redirectPath.value ?? '/');
  } catch (error) {
    errorMessage.value = registerErrorMessage(error);
  } finally {
    submitting.value = false;
  }
}

function registerErrorMessage(error: unknown): string {
  if (useExistingAccount.value
      && isApiError(error)
      && error.code === ERROR_CODES.VALIDATION_FAILED) {
    return '该手机号未注册或验证码有误；若尚未注册，请返回微信注册';
  }
  return isApiError(error)
    ? authErrorMessage(error.code, error.message)
    : '注册失败，请稍后重试';
}

function toggleExistingAccount(): void {
  useExistingAccount.value = !useExistingAccount.value;
  errorMessage.value = '';
  fieldErrors.value = {};
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
    <div class="login-card">
      <div class="login-brand">
        <img class="login-logo" :src="logoUrl" alt="" />
        <p class="login-eyebrow">YUNHE FC</p>
        <h1>{{ bindMode ? '微信注册' : '微信授权未完成' }}</h1>
        <p class="muted">
          {{ bindMode ? '填写姓名与主位置即可完成注册，手机号可稍后在个人中心补绑' : errorText }}
        </p>
      </div>

      <form v-if="bindMode" class="login-form" @submit.prevent="submitWechat">
        <CellGroup inset>
          <template v-if="!useExistingAccount">
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
          </template>
          <template v-else>
            <Field
              v-model="phone"
              name="phone"
              type="tel"
              label="手机号"
              placeholder="请输入已注册手机号"
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
          </template>
        </CellGroup>

        <p v-if="smsHint" class="login-hint">{{ smsHint }}</p>
        <p v-if="registeredNotice" class="login-success">{{ registeredNotice }}</p>
        <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

        <Button type="primary" block native-type="submit" :loading="submitting">
          {{ useExistingAccount ? '绑定并登录' : '完成注册' }}
        </Button>
        <Button block plain type="default" @click="toggleExistingAccount">
          {{ useExistingAccount ? '返回微信注册' : '已注册过？绑定已有手机号账号' }}
        </Button>
        <p v-if="!useExistingAccount" class="login-hint">
          微信注册账号需管理员审核后激活；带入队邀请注册可直接加入球队。
        </p>
      </form>

      <template v-else>
        <p v-if="retryMessage" class="login-error">{{ retryMessage }}</p>
        <Button type="primary" block :loading="retrying" @click="retry">重新授权</Button>
      </template>

      <RouterLink v-if="!bindMode" class="wechat-alt" :to="loginTarget">
        使用手机号登录
      </RouterLink>
    </div>

    <SiteFooter />

    <ActionSheet
      v-model:show="showPosition"
      title="选择主位置"
      :actions="POSITION_ACTIONS"
      cancel-text="取消"
      @select="selectPosition"
    />
  </div>
</template>
