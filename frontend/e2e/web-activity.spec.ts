import { expect, test, type Page, type Route } from '@playwright/test';

import { collectConsoleErrors } from './support';

const OPEN_ACTIVITY = {
  id: 9001,
  title: '赛季收官聚餐',
  type: 0,
  typeLabel: 'DINNER',
  startAt: '2099-12-06T18:30:00+08:00',
  endAt: '2099-12-06T21:00:00+08:00',
  signupDeadline: '2099-12-04T20:00:00+08:00',
  capacity: 20,
  familyAllowed: true,
  costPerPerson: 120,
  location: '老地方火锅（体育中心店）',
  locationImageUrls: [],
  description: '赛季结束一起聚个餐。',
  status: 0,
  statusLabel: 'SIGNUP_OPEN',
  signupCount: 12,
  signupOpen: true,
  version: 0,
  mySignup: null,
};

const FULL_ACTIVITY = {
  ...OPEN_ACTIVITY,
  id: 9002,
  title: '满员活动',
  capacity: 10,
  signupCount: 10,
};

const CLOSED_ACTIVITY = {
  ...OPEN_ACTIVITY,
  id: 9003,
  title: '已截止活动',
  status: 1,
  statusLabel: 'SIGNUP_CLOSED',
  signupOpen: false,
};

const CANCELLED_ACTIVITY = {
  ...OPEN_ACTIVITY,
  id: 9004,
  title: '已取消活动',
  status: 3,
  statusLabel: 'CANCELLED',
  signupOpen: false,
  signupCount: 0,
};

const LIST_ITEMS = [OPEN_ACTIVITY, FULL_ACTIVITY, CLOSED_ACTIVITY, CANCELLED_ACTIVITY];

function envelope(data: unknown): unknown {
  return { code: 0, message: 'success', data, requestId: 'e2e' };
}

async function fulfill(route: Route, data: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(envelope(data)),
  });
}

function pageOf<T>(items: T[]): unknown {
  return { items, total: items.length, page: 1, pageSize: 10, totalPages: 1 };
}

const ME = {
  user: {
    id: 2,
    name: '张伟',
    phoneMasked: '139****0002',
    status: 1,
    statusLabel: 'ACTIVE',
    roles: ['PLAYER'],
    createdAt: '2026-01-01T00:00:00+08:00',
    wechatBound: true,
  },
  player: {
    id: 2,
    name: '张伟',
    jersey: 7,
    type: 1,
    typeLabel: 'FORMAL',
    position: 2,
    positionLabel: 'MID',
    region: '上海',
    avatarUrl: null,
    skill: 3.5,
  },
  roles: ['PLAYER'],
  permissions: ['activity:signup:own', 'activity:view:any'],
  unreadCount: 0,
};

async function mockSession(page: Page, me: typeof ME = ME): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('yunhefc.c.sessionHint', '1');
  });
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    (route) => fulfill(route, me),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/notifications/unread-count',
    (route) => fulfill(route, { count: 0 }),
  );
}

test('C11 活动列表：筛选、最近活动与状态矩阵', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities',
    async (route) => {
      const scope = new URL(route.request().url()).searchParams.get('scope');
      await fulfill(route, pageOf(scope === 'latest' ? [OPEN_ACTIVITY] : LIST_ITEMS));
    },
  );

  await page.goto('/activities');

  const cards = page.getByTestId('activity-card');
  await expect(cards).toHaveCount(4);
  await expect(cards.filter({ hasText: '赛季收官聚餐' })).toContainText(
    '已报 12 / 20 人（不含家属）',
  );
  await expect(cards.filter({ hasText: '赛季收官聚餐' })).toContainText('报名');
  await expect(cards.filter({ hasText: '满员活动' })).toContainText('已满');
  await expect(cards.filter({ hasText: '已截止活动' })).toContainText('已截止');
  const cancelled = cards.filter({ hasText: '已取消活动' });
  await expect(cancelled).toContainText('已取消');
  await expect(cancelled.locator('.activity-cta')).toHaveCount(0);

  await page.getByLabel('活动范围').selectOption('latest');
  await expect(cards).toHaveCount(1);
  await expect(page.locator('.page-footer')).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C11 活动列表：加载失败可重试', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  let calls = 0;

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities',
    async (route) => {
      calls += 1;
      if (calls === 1) {
        // 业务错误仍走 200 envelope，避免浏览器把 HTTP 错误记成资源加载 console error。
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 50001, message: '活动加载失败', data: null }),
        });
        return;
      }
      await fulfill(route, pageOf([OPEN_ACTIVITY]));
    },
  );

  await page.goto('/activities');
  await expect(page.getByText('活动加载失败')).toBeVisible();

  await page.getByRole('button', { name: '重试' }).click();
  await expect(page.getByTestId('activity-card')).toHaveCount(1);
  expect(errors).toEqual([]);
});

test('C12 活动详情：报名（含家属）与取消全流程', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);

  let signedUp = false;
  const postBodies: Array<Record<string, unknown>> = [];
  const postKeys: Array<string | undefined> = [];
  let deleteCalls = 0;

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001',
    async (route) => {
      await fulfill(route, {
        ...OPEN_ACTIVITY,
        mySignup: signedUp
          ? {
              id: 55,
              activityId: 9001,
              playerId: 2,
              playerName: '张伟',
              playerNo: 7,
              avatarUrl: null,
              withFamily: true,
              familyNote: '带 1 位家属',
              status: 0,
              statusLabel: 'CONFIRMED',
              signedUpAt: '2099-11-20T10:00:00+08:00',
            }
          : null,
      });
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/auth/csrf',
    (route) => fulfill(route, { token: 'csrf-token' }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001/signups',
    async (route) => {
      postBodies.push(JSON.parse(route.request().postData() ?? '{}'));
      postKeys.push(route.request().headers()['idempotency-key']);
      signedUp = true;
      await fulfill(route, { signup: { id: 55 } });
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001/signups/me',
    async (route) => {
      deleteCalls += 1;
      signedUp = false;
      await fulfill(route, { cancelled: true });
    },
  );

  await page.goto('/activities/9001');

  await expect(page.getByRole('heading', { name: '赛季收官聚餐' })).toBeVisible();
  await expect(page.getByText('已报 12 / 20 人（不含家属）')).toBeVisible();
  await expect(page.getByText('人均 ¥120.00')).toBeVisible();

  await page.getByTestId('activity-signup').click();
  await expect(page.getByRole('heading', { name: '报名活动' })).toBeVisible();
  await page.getByText('携带家属', { exact: true }).click();
  await page.getByPlaceholder('如：带 1 位家属，儿童餐').fill('带 1 位家属');
  await page.getByRole('button', { name: '确认报名' }).click();

  await expect(page.getByTestId('activity-message')).toHaveText('报名成功');
  await expect(page.getByTestId('activity-cancel')).toBeVisible();
  await expect(page.getByText('携带家属（带 1 位家属）')).toBeVisible();
  expect(postBodies).toEqual([{ withFamily: true, familyNote: '带 1 位家属' }]);
  expect(postKeys[0]).toBeTruthy();

  await page.getByTestId('activity-cancel').click();
  await expect(page.getByTestId('activity-message')).toHaveText('已取消报名');
  await expect(page.getByTestId('activity-signup')).toBeVisible();
  await expect(page.getByTestId('activity-status-select')).toHaveCount(0);
  expect(deleteCalls).toBe(1);

  await page.getByRole('button', { name: '复制链接' }).click();
  await expect(page.getByText('链接已复制')).toBeVisible();

  expect(errors).toEqual([]);
});

test('C12 活动详情：不允许携带家属时隐藏家属选项', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const noFamily = { ...OPEN_ACTIVITY, id: 9005, familyAllowed: false, mySignup: null };

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9005',
    (route) => fulfill(route, noFamily),
  );

  await page.goto('/activities/9005');
  await page.getByTestId('activity-signup').click();
  await expect(page.getByRole('heading', { name: '报名活动' })).toBeVisible();
  await expect(page.getByText('携带家属', { exact: true })).toHaveCount(0);
  await expect(page.getByPlaceholder('如：带 1 位家属，儿童餐')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('C12 活动详情：已取消活动对访客不展示报名按钮', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9004',
    (route) => fulfill(route, { ...CANCELLED_ACTIVITY, mySignup: null }),
  );

  await page.goto('/activities/9004');
  await expect(page.getByTestId('activity-closed-banner')).toContainText('活动已取消');
  await expect(page.getByTestId('activity-signup')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('C12 活动详情：访客在已截止活动看到禁用文案', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9003',
    (route) => fulfill(route, { ...CLOSED_ACTIVITY, mySignup: null }),
  );

  await page.goto('/activities/9003');
  const signup = page.getByTestId('activity-signup');
  await expect(signup).toHaveText('已截止');
  await expect(signup).toBeDisabled();
  expect(errors).toEqual([]);
});

test('C12 活动详情：展示场地环境图片，无图时不展示该区块', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const image = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
  const withImages = { ...OPEN_ACTIVITY, locationImageUrls: [image, image, image] };

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001',
    (route) => fulfill(route, withImages),
  );
  await page.goto('/activities/9001');
  await expect(page.getByRole('heading', { name: '场地环境' })).toBeVisible();
  await expect(page.locator('img[alt="场地环境图片"]')).toHaveCount(3);

  // 桌面端鼠标拖拽切图：Vant Swipe 本身只处理触摸事件，页面补了鼠标位移切换。
  const swipe = page.locator('.van-swipe');
  await swipe.scrollIntoViewIfNeeded();
  const indicators = page.locator('.van-swipe__indicator');
  await expect(indicators.nth(0)).toHaveClass(/van-swipe__indicator--active/);
  const box = (await swipe.boundingBox())!;
  const centerY = box.y + box.height / 2;
  await page.mouse.move(box.x + box.width * 0.7, centerY);
  await page.mouse.down();
  await page.mouse.move(box.x + box.width * 0.3, centerY, { steps: 5 });
  await page.mouse.up();
  await expect(indicators.nth(1)).toHaveClass(/van-swipe__indicator--active/);

  // 左右箭头可点击切换，且可循环。
  await page.getByRole('button', { name: '下一张' }).click();
  await expect(indicators.nth(2)).toHaveClass(/van-swipe__indicator--active/);
  await page.getByRole('button', { name: '上一张' }).click();
  await expect(indicators.nth(1)).toHaveClass(/van-swipe__indicator--active/);

  // 连续两次点击不能被 Vant 的 change 回写吞掉（乐观索引）。
  await page.evaluate(() => {
    const next = document.querySelector<HTMLElement>('[aria-label="下一张"]');
    next?.click();
    next?.click();
  });
  await expect(indicators.nth(0)).toHaveClass(/van-swipe__indicator--active/);

  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001',
    (route) => fulfill(route, OPEN_ACTIVITY),
  );
  await page.goto('/activities/9001');
  await expect(page.getByRole('heading', { name: '活动详情' })).toBeVisible();
  await expect(page.getByRole('heading', { name: '场地环境' })).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('C12 管理员：状态下拉、直接流转与取消原因', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page, {
    ...ME,
    permissions: [...ME.permissions, 'activity:manage:any'],
  });

  let version = 0;
  let status = 0;
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001',
    (route) =>
      fulfill(route, {
        ...OPEN_ACTIVITY,
        status,
        signupOpen: status === 0,
        version,
        mySignup: null,
      }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/auth/csrf',
    (route) => fulfill(route, { token: 'csrf-token' }),
  );
  const bodies: Array<Record<string, unknown>> = [];
  const keys: Array<string | undefined> = [];
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001/status',
    async (route) => {
      const body = JSON.parse(route.request().postData() ?? '{}') as Record<string, unknown>;
      bodies.push(body);
      keys.push(route.request().headers()['idempotency-key']);
      version += 1;
      status = Number(body.status);
      await fulfill(route, { ...OPEN_ACTIVITY, status, version });
    },
  );

  await page.goto('/activities/9001');
  const select = page.getByTestId('activity-status-select');
  await expect(select).toBeVisible();
  await expect(select.locator('option')).toHaveCount(4);

  await select.selectOption('1');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名截止');
  expect(bodies[0]).toEqual({ status: 1, version: 0 });
  expect(keys[0]).toBeTruthy();

  // 报名截止可恢复为报名中（P-111）。
  await select.selectOption('0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');
  expect(bodies[1]).toEqual({ status: 0, version: 1 });

  await select.selectOption('2');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('已结束');
  expect(bodies[2]).toEqual({ status: 2, version: 2 });

  // 已结束可重新开放为报名中/报名截止，但不可取消（P-110）。
  await expect(select).toBeVisible();
  await expect(select.locator('option[value="3"]')).toBeDisabled();
  await select.selectOption('0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');
  expect(bodies[3]).toEqual({ status: 0, version: 3 });

  await select.selectOption('3');
  await expect(page.getByRole('heading', { name: '取消活动' })).toBeVisible();
  await page.getByRole('button', { name: '确认取消' }).click();
  await expect(page.getByTestId('activity-cancel-error')).toHaveText('请输入取消原因');
  await page.getByPlaceholder('请填写取消原因（最多 255 字）').fill('场地停业');
  await page.getByRole('button', { name: '确认取消' }).click();

  await expect(page.locator('.match-hero__top .van-tag')).toContainText('已取消');
  expect(bodies[4]).toEqual({ status: 3, version: 4, reason: '场地停业' });

  // 已取消可恢复为报名中/报名截止，但不可结束（P-114）。
  await expect(select).toBeVisible();
  await expect(select.locator('option[value="2"]')).toBeDisabled();
  await select.selectOption('0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');
  expect(bodies[5]).toEqual({ status: 0, version: 5 });
  expect(errors).toEqual([]);
});
