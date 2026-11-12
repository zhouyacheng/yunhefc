import { safeRedirectPath } from '@yunhefc/api-client';
import { toBrowserPath as withBase, toRoutePath as withoutBase } from '@yunhefc/shared';
import { createRouter, createWebHistory } from 'vue-router';

import { useSessionStore } from '../stores/session';

const appBase = import.meta.env.BASE_URL;
const loginPath = `${appBase}login`;

export function toRoutePath(browserPath: string): string {
  return withoutBase(browserPath, appBase);
}

export function toBrowserPath(routePath: string): string {
  return withBase(routePath, appBase);
}

export function safeRouteRedirect(value: unknown): string | undefined {
  const raw = Array.isArray(value) ? value[0] : value;
  const safe = safeRedirectPath(typeof raw === 'string' ? raw : undefined, {
    basePath: appBase,
    loginPath,
  });
  return safe ? toRoutePath(safe) : undefined;
}

export const router = createRouter({
  history: createWebHistory(appBase),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../pages/LoginPage.vue'),
    },
    {
      path: '/',
      component: () => import('../components/AppShell.vue'),
      children: [
        { path: '', name: 'home', component: () => import('../pages/HomePage.vue') },
        { path: 'matches', name: 'matches', component: () => import('../pages/SchedulePage.vue') },
        {
          path: 'matches/:id',
          name: 'match-detail',
          component: () => import('../pages/MatchDetailPage.vue'),
        },
        { path: 'team', name: 'team', component: () => import('../pages/TeamPage.vue') },
        {
          path: 'profile',
          name: 'profile',
          component: () => import('../pages/ProfilePage.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'invite/:token',
          name: 'invite',
          component: () => import('../pages/InvitePage.vue'),
        },
        {
          path: '403',
          name: 'forbidden',
          component: () => import('../pages/ForbiddenPage.vue'),
        },
        {
          path: '404',
          name: 'not-found',
          component: () => import('../pages/NotFoundPage.vue'),
        },
        { path: 'account', redirect: '/profile' },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/404' },
  ],
});

router.beforeEach(async (to) => {
  const session = useSessionStore();

  if (to.name === 'login') {
    // 登录页不做会话探测，避免未登录时必然产生的 401。
    if (session.loaded && session.isAuthenticated) {
      return safeRouteRedirect(to.query.redirect) ?? '/matches';
    }
    return true;
  }

  if (to.meta.requiresAuth) {
    await session.ensureLoaded().catch(() => undefined);
    if (!session.isAuthenticated) {
      return { name: 'login', query: { redirect: toBrowserPath(to.fullPath) } };
    }
  }

  return true;
});
