<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const token = computed(() => String(route.params.token ?? ''));
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>邀请</h1>
      <p class="muted">邀请详情即将上线</p>
    </header>

    <div class="card">
      <p class="muted">邀请编号：{{ token }}</p>
    </div>
  </section>
</template>
