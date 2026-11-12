<script setup lang="ts">
import { Button } from 'vant';
import { useRouter } from 'vue-router';

import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();

async function logout(): Promise<void> {
  await session.logout();
  await router.replace('/login');
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>我的</h1>
      <p class="muted">账号与当前会话</p>
    </header>

    <div v-if="session.user" class="card account-card">
      <p class="account-name">{{ session.user.name }}</p>
      <p class="muted">{{ session.user.phoneMasked }}</p>
      <Button type="danger" block @click="logout">退出登录</Button>
    </div>
  </section>
</template>
