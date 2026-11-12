import { safeRedirectPath } from '@yunhefc/api-client';
import { ADMIN_SECTIONS, type AdminSection } from '@yunhefc/permissions';
import { toBrowserPath as withBase, toRoutePath as withoutBase } from '@yunhefc/shared';
import { createRouter, createWebHistory } from 'vue-router';

import AdminShell from '../components/AdminShell.vue';
import { useSessionStore } from '../stores/session';

const appBase = import.meta.env.BASE_URL;
const loginPath = `${appBase}login`;

function sectionComponent(section: AdminSection) {
  if (section === 'matches') {
    return () => import('../pages/MatchesPage.vue');
  }
  if (section === 'registrations') {
    return () => import('../pages/RegistrationsPage.vue');
  }
  if (section === 'events') {
    return () => import('../pages/EventsPage.vue');
  }
  if (section === 'lineup') {
    return () => import('../pages/LineupPage.vue');
  }
  if (section === 'grouping') {
    return () => import('../pages/GroupingPage.vue');
  }
  if (section === 'players') {
    return () => import('../pages/PlayersPage.vue');
  }
  if (section === 'roles') {
    return () => import('../pages/RolesPage.vue');
  }
  return () => import('../pages/SectionPage.vue');
}

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
      component: AdminShell,
      children: [
        // 根路径由守卫按首个有权限分区跳转，避免无 matches 权限的角色落 403。
        { path: '', name: 'home', component: { render: () => null } },
        ...ADMIN_SECTIONS.map((section) => ({
          path: section,
          name: section,
          component: sectionComponent(section),
          meta: { section },
        })),
        {
          path: 'forbidden',
          name: 'forbidden',
          component: () => import('../pages/ForbiddenPage.vue'),
        },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
});

router.beforeEach(async (to) => {
  const session = useSessionStore();

  if (to.name === 'login') {
    // 登录页不做会话探测，避免未登录时必然产生的 401。
    if (session.loaded && session.isAuthenticated) {
      return safeRouteRedirect(to.query.redirect) ?? session.homePath;
    }
    return true;
  }

  await session.ensureLoaded().catch(() => undefined);
  if (!session.isAuthenticated) {
    return { name: 'login', query: { redirect: toBrowserPath(to.fullPath) } };
  }

  if (to.path === '/') {
    return session.homePath;
  }

  const section = to.meta.section;
  if (section && !session.sections.includes(section)) {
    return { name: 'forbidden', query: { section } };
  }

  return true;
});
