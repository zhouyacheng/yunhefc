<script setup lang="ts">
import { Button } from 'vant';
import { useRouter } from 'vue-router';

import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();

function goHome(): void {
  void router.push('/');
}

async function switchAccount(): Promise<void> {
  if (session.isAuthenticated) {
    await session.logout();
  }
  await router.replace('/login');
}
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>无权访问</h1>
      <p class="muted">该页面需要相应权限；如需访问，请联系管理员为当前账号开通。</p>
    </header>

    <div class="card">
      <p class="muted">当前身份：{{ session.user?.name ?? '未登录' }}</p>
      <Button type="primary" block @click="goHome">返回首页</Button>
      <Button block style="margin-top: 12px" @click="switchAccount">切换账号</Button>
    </div>
  </section>
</template>
