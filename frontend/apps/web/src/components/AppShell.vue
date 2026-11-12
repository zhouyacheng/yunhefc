<script setup lang="ts">
import { Tabbar, TabbarItem } from 'vant';
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useNotificationPolling } from '../composables/useNotificationPolling';
import { useSessionStore } from '../stores/session';
import { hasSessionHint } from '../stores/session-hint';

type TabName = 'home' | 'matches' | 'team' | 'profile';

interface TabEntry {
  name: TabName;
  path: string;
  icon: string;
  label: string;
  exact?: boolean;
  badge?: boolean;
}

const TABS: readonly TabEntry[] = [
  { name: 'home', path: '/', icon: 'home-o', label: '首页', exact: true },
  { name: 'matches', path: '/matches', icon: 'calendar-o', label: '赛程' },
  { name: 'team', path: '/team', icon: 'friends-o', label: '球队' },
  { name: 'profile', path: '/profile', icon: 'user-o', label: '我的', badge: true },
];

const session = useSessionStore();
const route = useRoute();
const router = useRouter();

useNotificationPolling();

const unreadBadge = computed<string | number | undefined>(() => {
  const count = session.unreadCount;
  if (count <= 0) {
    return undefined;
  }
  return count > 99 ? '99+' : count;
});

// Vant route 模式按 $route.matched 判断激活，首页子路由 '' 的父记录 '/' 会匹配所有页面；
// 改用受控模式：首页精确匹配，其余按 path 前缀。
const activeTab = computed<string>(
  () =>
    TABS.find((tab) => (tab.exact ? route.path === tab.path : route.path.startsWith(tab.path)))
      ?.name ?? '',
);

function onTabChange(name: string | number): void {
  const tab = TABS.find((item) => item.name === name);
  if (tab && tab.path !== route.path) {
    void router.replace(tab.path);
  }
}

onMounted(() => {
  // 访客不探测 /c/me；只有本地会话提示或受保护路由才恢复登录态。
  if (hasSessionHint() || route.meta.requiresAuth) {
    void session.ensureLoaded().catch(() => undefined);
  }
});
</script>

<template>
  <div class="c-shell">
    <header class="c-header">
      <RouterLink class="c-brand" to="/">云河FC</RouterLink>
      <RouterLink v-if="session.isAuthenticated" class="c-user" to="/profile">
        {{ session.user?.name }}
      </RouterLink>
      <RouterLink v-else class="c-user" to="/login">登录</RouterLink>
    </header>

    <main class="c-main">
      <RouterView />
    </main>

    <Tabbar :model-value="activeTab" fixed placeholder @change="onTabChange">
      <TabbarItem
        v-for="tab in TABS"
        :key="tab.name"
        :name="tab.name"
        :icon="tab.icon"
        :badge="tab.badge ? unreadBadge : undefined"
      >
        {{ tab.label }}
      </TabbarItem>
    </Tabbar>
  </div>
</template>
