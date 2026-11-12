<script setup lang="ts">
import { Tabbar, TabbarItem } from 'vant';
import { computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useNotificationPolling } from '../composables/useNotificationPolling';
import { useSessionStore } from '../stores/session';
import { hasSessionHint } from '../stores/session-hint';

const TAB_PATHS = {
  home: '/',
  matches: '/matches',
  team: '/team',
  profile: '/profile',
} as const;

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
// 改用受控模式按 path 前缀精确匹配。
const activeTab = computed<string>(() => {
  if (route.path === '/') {
    return 'home';
  }
  if (route.path.startsWith('/matches')) {
    return 'matches';
  }
  if (route.path.startsWith('/team')) {
    return 'team';
  }
  if (route.path.startsWith('/profile')) {
    return 'profile';
  }
  return '';
});

function onTabChange(name: string | number): void {
  const path = TAB_PATHS[name as keyof typeof TAB_PATHS];
  if (path && path !== route.path) {
    void router.replace(path);
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
      <TabbarItem name="home" icon="home-o">首页</TabbarItem>
      <TabbarItem name="matches" icon="calendar-o">赛程</TabbarItem>
      <TabbarItem name="team" icon="friends-o">球队</TabbarItem>
      <TabbarItem name="profile" icon="user-o" :badge="unreadBadge">我的</TabbarItem>
    </Tabbar>
  </div>
</template>
