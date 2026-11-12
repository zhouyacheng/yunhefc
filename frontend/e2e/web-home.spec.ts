import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C01 首页：访客看到 Hero 并引导登录', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const meRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/c/me') {
      meRequests.push(request.url());
    }
  });

  await page.goto('/');

  await expect(page.getByRole('heading', { name: 'B3首页联调赛' })).toBeVisible();
  await expect(page.getByRole('button', { name: '登录后报名' })).toBeVisible();
  await expect(page.locator('.home-section .match-card')).toHaveCount(3);
  await expect(page.locator('.home-section .match-card-link').first()).toHaveAttribute(
    'href',
    '/matches/1005',
  );
  await expect(page.locator('.van-tabbar-item--active')).toContainText('首页');

  await page.getByRole('button', { name: '登录后报名' }).click();
  await expect(page).toHaveURL(/\/login\?redirect=/);
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/');

  expect(meRequests).toEqual([]);
  expect(errors).toEqual([]);
});

test('C01 首页：登录球员报名后 CTA 与队列更新', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAs(page);

  await expect(page.locator('.van-tabbar-item--active')).toContainText('赛程');

  await page.locator('.van-tabbar-item').filter({ hasText: '首页' }).click();
  await expect(page).toHaveURL(/\/$/);
  await expect(page.getByRole('heading', { name: 'B3首页联调赛' })).toBeVisible();
  await expect(page.locator('.van-tabbar-item--active')).toContainText('首页');

  const formalQueue = page.locator('.hero-queue__item').first();
  await expect(formalQueue).toContainText('0 / 30');

  // 报名成功后的静默刷新失败一次，验证「重试刷新」入口。
  let failRefresh = true;
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches' && url.searchParams.get('scope') === 'upcoming',
    async (route) => {
      if (failRefresh) {
        failRefresh = false;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 50001, message: '刷新失败', data: null }),
        });
        return;
      }
      await route.continue();
    },
  );

  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/registrations' &&
      response.request().method() === 'POST',
  );
  await page.getByRole('button', { name: '报名', exact: true }).click();
  expect((await responsePromise).ok()).toBe(true);

  await expect(page.getByText('刷新失败')).toBeVisible();
  await page.getByRole('button', { name: '重试刷新' }).click();

  await expect(page.getByRole('button', { name: '查看详情' })).toBeVisible();
  await expect(page.locator('.hint').filter({ hasText: '已报名' })).toBeVisible();
  await expect(formalQueue).toContainText('1 / 30');
  await expect(page.getByRole('button', { name: '重试刷新' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C01 首页：加载失败可重试', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  let failed = false;

  await page.route(
    (url) => url.pathname === '/api/v1/c/matches' && url.searchParams.get('scope') === 'upcoming',
    async (route) => {
      if (!failed) {
        failed = true;
        // 用 200 + 业务错误 envelope：让 api-client 抛 ApiError，同时不产生浏览器资源错误日志。
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 50001, message: '服务暂不可用', data: null }),
        });
        return;
      }
      await route.continue();
    },
  );

  await page.goto('/');
  await expect(page.getByText('服务暂不可用')).toBeVisible();

  await page.getByRole('button', { name: '重试' }).click();
  await expect(page.getByRole('heading', { name: 'B3首页联调赛' })).toBeVisible();

  expect(errors).toEqual([]);
});

test('C01 首页：无待进行比赛显示空态', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.route(
    (url) => url.pathname === '/api/v1/c/matches',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'success',
          data: { items: [], total: 0, page: 1, pageSize: 3, totalPages: 0 },
          requestId: 'e2e-empty-home',
        }),
      });
    },
  );

  await page.goto('/');
  await expect(page.getByText('暂无待进行比赛')).toBeVisible();
  await expect(page.getByRole('button', { name: '去赛程' })).toBeVisible();

  expect(errors).toEqual([]);
});
