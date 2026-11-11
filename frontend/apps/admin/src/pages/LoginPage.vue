<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { computed, ref } from 'vue';
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
const errorCode = ref<number | null>(null);

const noBackendPermission = computed(() => errorCode.value === ERROR_CODES.FORBIDDEN);

async function submit(): Promise<void> {
  errorMessage.value = '';
  errorCode.value = null;

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
    await router.replace(safeRouteRedirect(route.query.redirect) ?? session.homePath);
  } catch (error) {
    if (isApiError(error)) {
      errorCode.value = error.code;
      errorMessage.value = error.message;
    } else {
      errorMessage.value = '登录失败，请稍后重试';
    }
  } finally {
    submitting.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <div class="login-card">
      <h1>云河FC 管理后台</h1>
      <p>使用球队管理员账号登录</p>

      <el-form label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号">
          <el-input v-model="phone" placeholder="请输入手机号" autocomplete="tel" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input
            v-model="password"
            type="password"
            placeholder="请输入密码"
            autocomplete="current-password"
            show-password
          />
        </el-form-item>

        <el-alert
          v-if="errorMessage"
          class="login-alert"
          type="error"
          :title="errorMessage"
          :closable="false"
          show-icon
        />
        <p v-if="noBackendPermission" class="login-hint">
          请使用具备后台权限的账号，或前往 C 端登录。
        </p>

        <el-button class="login-submit" type="primary" native-type="submit" :loading="submitting">
          登录
        </el-button>
      </el-form>
    </div>
  </div>
</template>
