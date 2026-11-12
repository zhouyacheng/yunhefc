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
    <header class="page-head">
      <h1>页面不存在</h1>
      <p class="muted">你访问的页面或资源不存在。</p>
    </header>

    <div class="card">
      <Button type="primary" block @click="goHome">返回首页</Button>
      <Button block style="margin-top: 12px" @click="goBack">返回上一页</Button>
    </div>
  </section>
</template>
