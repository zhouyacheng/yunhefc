import { expect, test, type Page, type Route } from '@playwright/test';

import { collectConsoleErrors } from './support';

const CATEGORIES = [
  {
    id: 1,
    name: '队费',
    direction: 0,
    directionLabel: null,
    sortNo: 1,
    isActive: true,
    entryCount: 1,
  },
  {
    id: 2,
    name: '场地',
    direction: 1,
    directionLabel: null,
    sortNo: 2,
    isActive: true,
    entryCount: 1,
  },
  {
    id: 3,
    name: '其他',
    direction: null,
    directionLabel: null,
    sortNo: 3,
    isActive: true,
    entryCount: 0,
  },
];

const INCOME_ENTRY = {
  id: 501,
  type: 0,
  typeLabel: 'INCOME',
  categoryId: 1,
  categoryName: '队费',
  categoryDirection: 0,
  amount: 300,
  description: '11 月队费',
  occurredAt: '2099-11-18T21:00:00+08:00',
  playerId: 2,
  playerName: '张伟',
  activityId: null,
  activityTitle: null,
  version: 0,
};

const EXPENSE_ENTRY = {
  id: 502,
  type: 1,
  typeLabel: 'EXPENSE',
  categoryId: 2,
  categoryName: '场地',
  categoryDirection: 1,
  amount: 800,
  description: '11 月场地费（4 场）',
  occurredAt: '2099-11-20T20:10:00+08:00',
  playerId: null,
  playerName: null,
  activityId: null,
  activityTitle: null,
  version: 0,
};

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

function meWith(permissions: string[]) {
  return {
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
    permissions,
    unreadCount: 0,
  };
}

async function mockSession(page: Page, permissions: string[]): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('yunhefc.c.sessionHint', '1');
  });
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    (route) => fulfill(route, meWith(permissions)),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/notifications/unread-count',
    (route) => fulfill(route, { count: 0 }),
  );
}

test('C13 账本：汇总、流水与方向筛选', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page, ['ledger:view:any']);

  const summaryQueries: URLSearchParams[] = [];
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/categories',
    (route) => fulfill(route, { items: CATEGORIES }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/entries',
    async (route) => {
      const type = new URL(route.request().url()).searchParams.get('type');
      await fulfill(route, pageOf(type === '1' ? [EXPENSE_ENTRY] : [INCOME_ENTRY, EXPENSE_ENTRY]));
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/summary',
    async (route) => {
      const url = new URL(route.request().url());
      summaryQueries.push(url.searchParams);
      const expenseOnly = url.searchParams.get('type') === '1';
      await fulfill(
        route,
        expenseOnly
          ? { income: 0, expense: 800, balance: -800 }
          : { income: 300, expense: 800, balance: -500 },
      );
    },
  );

  await page.goto('/ledger');

  await expect(page.getByRole('heading', { name: '账本' })).toBeVisible();
  await expect(page.locator('.ledger-summary__value--income')).toHaveText('¥300.00');
  await expect(page.locator('.ledger-summary__value--expense')).toHaveText('¥800.00');
  await expect(page.locator('.ledger-summary__value').nth(2)).toHaveText('−¥500.00');

  await expect(page.getByTestId('ledger-row')).toHaveCount(2);
  await expect(page.getByTestId('ledger-row').first()).toContainText('+300.00');
  await expect(page.getByTestId('ledger-row').first()).toContainText('归属 张伟');
  await expect(page.getByTestId('ledger-row').nth(1)).toContainText('−800.00');

  await page.getByLabel('收支方向').selectOption('1');
  await expect(page.getByTestId('ledger-row')).toHaveCount(1);
  await expect(page.getByTestId('ledger-row')).toContainText('−800.00');
  await expect(page.locator('.ledger-summary__value--income')).toHaveText('¥0.00');
  await expect(page.locator('.ledger-summary__value').nth(2)).toHaveText('−¥800.00');

  // 方向为支出时，分类下拉只保留支出与通用分类。
  const categorySelect = page.getByLabel('账本分类');
  await expect(categorySelect.locator('option')).toHaveCount(3);
  await expect(categorySelect).toContainText('场地');
  await expect(categorySelect).toContainText('其他');
  await expect(categorySelect).not.toContainText('队费');

  // 月份筛选：确认当月后按当月首末日下发 from/to。
  await page.getByRole('button', { name: '选择月份' }).click();
  await page.getByRole('button', { name: '确认', exact: true }).click();
  const monthText = (await page.getByRole('button', { name: '选择月份' }).textContent()) ?? '';
  expect(monthText.trim()).toMatch(/^\d{4}-\d{2}$/);
  const [year, month] = monthText.trim().split('-').map(Number);
  const lastDay = String(new Date(Date.UTC(year ?? 0, month ?? 1, 0)).getUTCDate()).padStart(
    2,
    '0',
  );
  const monthStart = `${monthText.trim()}-01T00:00:00+08:00`;
  const monthEnd = `${monthText.trim()}-${lastDay}T23:59:59+08:00`;
  await expect.poll(() => summaryQueries.at(-1)?.get('from')).toBe(monthStart);
  await expect.poll(() => summaryQueries.at(-1)?.get('to')).toBe(monthEnd);
  await expect(page.getByRole('button', { name: '清除月份', exact: true })).toBeVisible();

  // 清除月份后不再下发时间条件。
  await page.getByRole('button', { name: '清除月份', exact: true }).click();
  await expect.poll(() => summaryQueries.at(-1)?.has('from')).toBe(false);

  // 自定义时间与月份互斥：选范围后清空月份，首末日边界都要正确。
  await page.getByRole('button', { name: '选择自定义时间' }).click();
  const days = page.locator('.van-calendar__day:not(.van-calendar__day--disabled)');
  await days.first().click();
  await days.first().click();
  await page.locator('.van-calendar__confirm').click();
  await expect
    .poll(() => summaryQueries.at(-1)?.get('from'))
    .toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00\+08:00$/);
  const latest = summaryQueries.at(-1) as URLSearchParams;
  expect(latest.get('to')).toBe(latest.get('from')?.replace('T00:00:00', 'T23:59:59'));
  await expect(page.getByRole('button', { name: '清除自定义时间', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('全部月份');

  expect(errors).toEqual([]);
});

test('C13 账本：无权限账号不请求账本并提示', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page, ['activity:signup:own']);

  const ledgerRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname.startsWith('/api/v1/c/ledger')) {
      ledgerRequests.push(request.url());
    }
  });

  await page.goto('/ledger');
  await expect(page.getByText('无权限查看账本')).toBeVisible();
  expect(ledgerRequests).toEqual([]);
  expect(errors).toEqual([]);
});
