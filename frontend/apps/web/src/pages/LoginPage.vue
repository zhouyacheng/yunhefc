<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { Button, CellGroup, Field } from 'vant';
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { safeRouteRedirect } from '../router';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const phone = ref('');
const password = ref('');
const submitting = ref(false);
const errorMessage = ref('');

function loginErrorMessage(code: number, message: string): string {
  switch (code) {
    case ERROR_CODES.INVALID_CREDENTIALS:
    case ERROR_CODES.ACCOUNT_LOCKED:
    case ERROR_CODES.ACCOUNT_DISABLED:
    case ERROR_CODES.RATE_LIMITED:
      return message;
    default:
      return message || '登录失败，请稍后重试';
  }
}

async function submit(): Promise<void> {
  errorMessage.value = '';

  if (!/^1\d{10}$/.test(phone.value)) {
    errorMessage.value = '请输入正确的手机号';
    return;
  }
  if (!password.value) {
    errorMessage.value = '请输入密码';
    return;
  }

  submitting.value = true;
  try {
    await session.login(phone.value, password.value);
    await router.replace(safeRouteRedirect(route.query.redirect) ?? '/matches');
  } catch (error) {
    errorMessage.value = isApiError(error)
      ? loginErrorMessage(error.code, error.message)
      : '登录失败，请稍后重试';
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-brand">
      <p class="login-eyebrow">YUNHE FC</p>
      <h1>云河FC</h1>
      <p class="muted">登录后参与比赛</p>
    </div>

    <form class="login-card" @submit.prevent="submit">
      <CellGroup inset>
        <Field
          v-model="phone"
          name="phone"
          type="tel"
          label="手机号"
          placeholder="请输入手机号"
          autocomplete="tel"
          maxlength="11"
        />
        <Field
          v-model="password"
          name="password"
          type="password"
          label="密码"
          placeholder="请输入密码"
          autocomplete="current-password"
        />
      </CellGroup>

      <p v-if="errorMessage" class="login-error">{{ errorMessage }}</p>

      <Button type="primary" block native-type="submit" :loading="submitting">登录</Button>
    </form>
  </div>
</template>
