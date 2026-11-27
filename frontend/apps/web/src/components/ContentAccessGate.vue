<script setup lang="ts">
import { Button, Empty, Loading } from 'vant';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import type { ContentAccess } from '../features/content/useContentAccess';
import { toBrowserPath } from '../router';

/** C14/相册详情的访问态占位：加载、失败重试、未登录引导、待审核/外挂不可见。 */
const props = defineProps<{
  state: ContentAccess;
  /** 用于「登录后查看…」「暂不能查看…」文案，如「动态与相册」「相册」。 */
  noun: string;
  testIdPrefix: string;
}>();
const emit = defineEmits<{ retry: [] }>();

const route = useRoute();
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: toBrowserPath(route.fullPath) },
}));
</script>

<template>
  <div v-if="props.state === 'loading'" class="state">
    <Loading size="24">加载中…</Loading>
  </div>

  <section
    v-else-if="props.state === 'error'"
    class="state card"
    :data-testid="`${props.testIdPrefix}-error`"
  >
    <p class="muted">内容加载失败，请稍后重试。</p>
    <Button size="small" type="primary" @click="emit('retry')">重试</Button>
  </section>

  <section
    v-else-if="props.state === 'guest'"
    class="state card"
    :data-testid="`${props.testIdPrefix}-guest`"
  >
    <Empty :description="`登录后查看${props.noun}`" />
    <p class="muted small">动态与相册仅对球队成员开放。</p>
    <RouterLink :to="loginTarget">
      <Button type="primary" size="small">去登录</Button>
    </RouterLink>
  </section>

  <section
    v-else-if="props.state === 'pending'"
    class="state card"
    :data-testid="`${props.testIdPrefix}-pending`"
  >
    <Empty :description="`账号待审核，暂不能查看${props.noun}`" />
    <p class="muted small">通过审核后即可查看{{ props.noun }}。</p>
  </section>

  <section
    v-else-if="props.state === 'blocked'"
    class="state card"
    :data-testid="`${props.testIdPrefix}-blocked`"
  >
    <Empty description="动态与相册仅正式球员与管理员可见" />
  </section>
</template>
