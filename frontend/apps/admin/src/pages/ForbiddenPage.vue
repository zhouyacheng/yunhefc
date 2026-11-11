<script setup lang="ts">
import { isAdminSection } from '@yunhefc/permissions';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { SECTION_LABELS } from '../sections';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const rawSection = computed(() => {
  const value = route.query.section;
  return Array.isArray(value) ? value[0] : value;
});

const sectionLabel = computed(() => {
  const section = rawSection.value;
  return typeof section === 'string' && isAdminSection(section)
    ? SECTION_LABELS[section]
    : '该分区';
});
</script>

<template>
  <el-result
    icon="warning"
    title="403 无权限访问"
    :sub-title="`当前账号没有「${sectionLabel}」的后台权限`"
  >
    <template #extra>
      <p class="forbidden-identity">
        当前身份：{{ session.user?.name }}（{{ session.user?.phoneMasked }}）
      </p>
      <el-button type="primary" @click="router.replace(session.homePath)"
        >返回有权限的分区</el-button
      >
    </template>
  </el-result>
</template>
