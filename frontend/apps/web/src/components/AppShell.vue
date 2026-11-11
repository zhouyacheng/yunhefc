<script setup lang="ts">
import { Tabbar, TabbarItem } from 'vant';

import { useSessionStore } from '../stores/session';

const session = useSessionStore();
</script>

<template>
  <div class="c-shell">
    <header class="c-header">
      <RouterLink class="c-brand" to="/matches">云河FC</RouterLink>
      <RouterLink v-if="session.isAuthenticated" class="c-user" to="/account">
        {{ session.user?.name }}
      </RouterLink>
      <RouterLink v-else class="c-user" to="/login">登录</RouterLink>
    </header>

    <main class="c-main">
      <RouterView />
    </main>

    <Tabbar route fixed placeholder>
      <TabbarItem replace to="/matches" icon="calendar-o">赛程</TabbarItem>
      <TabbarItem replace to="/account" icon="user-o">我的</TabbarItem>
    </Tabbar>
  </div>
</template>
