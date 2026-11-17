<script setup lang="ts">
import { ADMIN_SECTIONS } from '@yunhefc/permissions';
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import logoUrl from '../assets/logo.png';
import { SECTION_LABELS } from '../sections';
import { useSessionStore } from '../stores/session';
import { webUrl } from '../web-url';

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

const copyrightYears = `2026-${new Date().getFullYear()}`;

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
    <el-aside class="admin-sidebar">
      <div class="admin-brand">
        <img class="admin-brand-logo" :src="logoUrl" alt="云河FC" />
        <div class="admin-brand-text">
          <strong>云河FC</strong>
          <span>管理后台</span>
        </div>
      </div>
      <el-menu :default-active="route.path" router class="admin-menu">
        <el-menu-item v-for="item in navItems" :key="item.section" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>
      <div v-if="webUrl" class="admin-side-foot">
        <el-button tag="a" :href="webUrl">返回前台</el-button>
      </div>
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

      <el-footer class="admin-footer">
        <p class="admin-footer__copyright">© {{ copyrightYears }} 云河FC All Rights Reserved</p>
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
          苏ICP备2026090382号
        </a>
      </el-footer>
    </el-container>
  </el-container>
</template>
