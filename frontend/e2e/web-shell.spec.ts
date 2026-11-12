import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C 端壳层：访客打开赛程不请求 /c/me', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const meRequests: string[] = [];
  const unauthorized: string[] = [];

  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/c/me') {
      meRequests.push(request.url());
    }
  });
  page.on('response', (response) => {
    if (new URL(response.url()).pathname === '/api/v1/c/me' && response.status() === 401) {
      unauthorized.push(response.url());
    }
  });

  await page.goto('/matches');
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(meRequests).toEqual([]);
  expect(unauthorized).toEqual([]);
  expect(errors).toEqual([]);
});

test('C 端壳层：未知路由进 404，403 独立可访问', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/unknown');
  await expect(page).toHaveURL(/\/404$/);
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();

  await page.goto('/403');
  await expect(page).toHaveURL(/\/403$/);
  await expect(page.getByRole('heading', { name: '无权访问' })).toBeVisible();

  expect(errors).toEqual([]);
});

test('C 端壳层：不存在的比赛进入 404', async ({ page }) => {
  await page.goto('/matches/999999');

  await expect(page).toHaveURL(/\/404$/);
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();
});

test('C 端壳层：/account 未登录跳登录、登录后重定向 /profile', async ({ page }) => {
  // 受保护路由的访客探测会产生预期的 /c/me 401 网络日志，本用例只断言页面结果。
  await page.goto('/account');
  await expect(page).toHaveURL(/\/login\?redirect=/);
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/profile');

  await loginAs(page);
  await page.goto('/account');
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: '我的', exact: true })).toBeVisible();
});

test('C 端壳层：登录后「我的」显示未读角标', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page);
  const response = await page.request.get('/api/v1/c/notifications/unread-count');
  expect(response.ok()).toBe(true);
  const body = (await response.json()) as { data: { count: number } };
  expect(body.data.count).toBeGreaterThan(0);

  const badge = page.locator('.van-tabbar-item').filter({ hasText: '我的' }).locator('.van-badge');
  await expect(badge).toHaveText(body.data.count > 99 ? '99+' : String(body.data.count));

  expect(errors).toEqual([]);
});
