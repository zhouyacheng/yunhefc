<script setup lang="ts">
import { Button } from 'vant';
import { useRouter } from 'vue-router';

const router = useRouter();

function goBack(): void {
  router.back();
}

function goHome(): void {
  void router.push('/');
}
</script>

<template>
  <section class="page">
    <div class="page-gate">
      <div class="card empty-state">
        <p class="eyebrow">404</p>
        <h1>页面不存在</h1>
        <p>你访问的页面或资源不存在。</p>
        <div class="empty-state__actions">
          <Button type="primary" block @click="goHome">返回首页</Button>
          <Button block @click="goBack">返回上一页</Button>
        </div>
      </div>
    </div>
  </section>
</template>
