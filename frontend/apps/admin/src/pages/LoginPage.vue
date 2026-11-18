<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import beianUrl from '../assets/beian.png';
import logoUrl from '../assets/logo.png';
import { safeRouteRedirect } from '../router';
import { useSessionStore } from '../stores/session';
import { webUrl } from '../web-url';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const copyrightYears = `2026-${new Date().getFullYear()}`;

const phone = ref('');
const password = ref('');
const submitting = ref(false);
const errorMessage = ref('');
const errorCode = ref<number | null>(null);

const noBackendPermission = computed(() => errorCode.value === ERROR_CODES.FORBIDDEN);
const handoffFailed = computed(() => route.query.handoff === 'failed');

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
      <div class="login-head">
        <span class="login-logo"><img :src="logoUrl" alt="云河FC" /></span>
        <div>
          <h1>云河FC 管理后台</h1>
          <p>使用球队管理员账号登录</p>
        </div>
      </div>

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
          v-if="handoffFailed"
          class="login-alert"
          type="warning"
          title="自动登录已失效，请重新登录"
          :closable="false"
          show-icon
        />
        <el-alert
          v-if="errorMessage"
          class="login-alert"
          type="error"
          :title="errorMessage"
          :closable="false"
          show-icon
        />
        <p v-if="noBackendPermission" class="login-hint">
          请使用具备后台权限的账号<span v-if="webUrl">，或 <a :href="webUrl">前往 C 端登录</a></span
          >。
        </p>

        <el-button class="login-submit" type="primary" native-type="submit" :loading="submitting">
          登录
        </el-button>
      </el-form>
    </div>

    <footer class="admin-footer">
      <p class="admin-footer__copyright">© {{ copyrightYears }} 云河FC All Rights Reserved</p>
      <p class="admin-footer__beian">
        <a
          href="https://beian.mps.gov.cn/#/query/webSearch?code=32028102004219"
          target="_blank"
          rel="noopener noreferrer"
        >
          <img :src="beianUrl" alt="" />
          苏公网安备32028102004219号
        </a>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
          苏ICP备2026090382号
        </a>
      </p>
    </footer>
  </div>
</template>
