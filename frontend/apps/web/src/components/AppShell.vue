<script setup lang="ts">
import { Tabbar, TabbarItem } from 'vant';
import { computed, onMounted } from 'vue';
import { useRoute } from 'vue-router';

import { useNotificationPolling } from '../composables/useNotificationPolling';
import { useSessionStore } from '../stores/session';
import { hasSessionHint } from '../stores/session-hint';

const session = useSessionStore();
const route = useRoute();

useNotificationPolling();

const unreadBadge = computed<string | number | undefined>(() => {
  const count = session.unreadCount;
  if (count <= 0) {
    return undefined;
  }
  return count > 99 ? '99+' : count;
});

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

    <Tabbar route fixed placeholder>
      <TabbarItem replace to="/" icon="home-o">首页</TabbarItem>
      <TabbarItem replace to="/matches" icon="calendar-o">赛程</TabbarItem>
      <TabbarItem replace to="/team" icon="friends-o">球队</TabbarItem>
      <TabbarItem replace to="/profile" icon="user-o" :badge="unreadBadge">我的</TabbarItem>
    </Tabbar>
  </div>
</template>
