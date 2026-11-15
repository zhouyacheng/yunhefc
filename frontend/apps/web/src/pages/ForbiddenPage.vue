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
    <div class="page-gate">
      <div class="card empty-state">
        <p class="eyebrow">403</p>
        <h1>无权访问</h1>
        <p>该页面需要相应权限；如需访问，请联系管理员为当前账号开通。</p>
        <p class="muted small">当前身份：{{ session.user?.name ?? '未登录' }}</p>
        <div class="empty-state__actions">
          <Button type="primary" block @click="goHome">返回首页</Button>
          <Button block @click="switchAccount">切换账号</Button>
        </div>
      </div>
    </div>
  </section>
</template>
