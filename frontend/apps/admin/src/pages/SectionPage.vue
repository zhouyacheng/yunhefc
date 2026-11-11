<script setup lang="ts">
import type { AdminSection } from '@yunhefc/permissions';
import { computed } from 'vue';
import { useRoute } from 'vue-router';

import { SECTION_LABELS } from '../sections';

const route = useRoute();
const section = computed(() => route.meta.section as AdminSection | undefined);
</script>

<template>
  <el-empty
    :description="
      section ? `${SECTION_LABELS[section]}页面将在后续模块交付` : '页面将在后续模块交付'
    "
  />
</template>
