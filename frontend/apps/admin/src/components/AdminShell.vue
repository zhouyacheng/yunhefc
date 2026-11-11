<script setup lang="ts">
import { ADMIN_SECTIONS } from '@yunhefc/permissions';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { SECTION_LABELS } from '../sections';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const navItems = computed(() =>
  ADMIN_SECTIONS.filter((section) => session.sections.includes(section)).map((section) => ({
    section,
    label: SECTION_LABELS[section],
    path: `/${section}`,
  })),
);

const currentLabel = computed(() => {
  const section = route.meta.section;
  return section ? SECTION_LABELS[section] : '无权限';
});

async function onCommand(command: string): Promise<void> {
  if (command !== 'logout') {
    return;
  }
  await session.logout();
  await router.replace('/login');
}
</script>

<template>
  <el-container class="admin-shell">
    <el-aside width="220px" class="admin-sidebar">
      <div class="admin-brand">
        云河FC
        <span>管理后台</span>
      </div>
      <el-menu :default-active="route.path" router class="admin-menu">
        <el-menu-item v-for="item in navItems" :key="item.section" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="admin-header">
        <h1>{{ currentLabel }}</h1>
        <el-dropdown @command="onCommand">
          <span class="admin-user">{{ session.user?.name }} ▾</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>
