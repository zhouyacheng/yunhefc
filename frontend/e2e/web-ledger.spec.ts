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

const DAILY_TOTALS = {
  items: [
    { date: '2099-11-20', income: 0, expense: 800 },
    { date: '2099-11-18', income: 300, expense: 0 },
  ],
};

const TREND = Array.from({ length: 30 }, (_, index) => ({
  start: `2026-11-${String(index + 1).padStart(2, '0')}`,
  income: index === 17 ? 300 : 0,
  expense: index === 19 ? 800 : 0,
}));

const STATISTICS = {
  period: 'month',
  from: '2026-11-01',
  to: '2026-11-30',
  previousFrom: '2026-10-01',
  previousTo: '2026-10-31',
  income: 300,
  expense: 800,
  balance: -500,
  incomeCount: 1,
  expenseCount: 1,
  previousIncome: 200,
  previousExpense: 700,
  previousBalance: -500,
  trendGranularity: 'day',
  trend: TREND,
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

async function mockLedger(page: Page) {
  const entryQueries: URLSearchParams[] = [];
  const summaryQueries: URLSearchParams[] = [];
  const statsQueries: URLSearchParams[] = [];

  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/categories',
    (route) => fulfill(route, { items: CATEGORIES }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/entries',
    async (route) => {
      const params = new URL(route.request().url()).searchParams;
      entryQueries.push(params);
      const expenseOnly = params.get('type') === '1' || params.has('keyword');
      await fulfill(route, pageOf(expenseOnly ? [EXPENSE_ENTRY] : [EXPENSE_ENTRY, INCOME_ENTRY]));
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/summary',
    async (route) => {
      const params = new URL(route.request().url()).searchParams;
      summaryQueries.push(params);
      const expenseOnly = params.get('type') === '1';
      await fulfill(
        route,
        expenseOnly
          ? { income: 0, expense: 800, balance: -800 }
          : { income: 300, expense: 800, balance: -500 },
      );
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/daily-totals',
    async (route) => {
      const params = new URL(route.request().url()).searchParams;
      const expenseOnly = params.get('type') === '1' || params.has('keyword');
      await fulfill(
        route,
        expenseOnly ? { items: [{ date: '2099-11-20', income: 0, expense: 800 }] } : DAILY_TOTALS,
      );
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/ledger/statistics',
    async (route) => {
      const params = new URL(route.request().url()).searchParams;
      statsQueries.push(params);
      if (params.get('period') === 'custom') {
        await fulfill(route, {
          ...STATISTICS,
          period: 'custom',
          from: '2026-11-01',
          to: '2026-11-20',
          previousFrom: '2026-10-12',
          previousTo: '2026-10-31',
          trend: TREND.slice(0, 20),
        });
        return;
      }
      await fulfill(route, STATISTICS);
    },
  );

  return { entryQueries, summaryQueries, statsQueries };
}

test('C13 账本：月份汇总、按日明细、搜索与统计', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page, ['ledger:view:any']);
  const { entryQueries, summaryQueries, statsQueries } = await mockLedger(page);

  await page.goto('/ledger');

  await expect(page.getByRole('heading', { name: '账本' })).toBeVisible();
  const summary = page.getByTestId('ledger-summary');
  await expect(summary.locator('.ledger-figure__value--expense')).toHaveText('¥800.00');
  await expect(summary.locator('.ledger-figure__value--income')).toHaveText('¥300.00');
  await expect(summary.locator('.ledger-hero__balance b')).toHaveText('−¥500.00');
  await expect(summary.locator('.ledger-hero__scope')).toHaveCount(0);
  await expect(summary.getByRole('button', { name: '选择月份' })).toHaveText(/^\d{4}年\d{1,2}月/);

  // 明细按日分组，组头展示当日收支小计。
  await expect(page.getByTestId('ledger-day')).toHaveCount(2);
  await expect(page.getByTestId('ledger-day').first()).toContainText('11月20日');
  await expect(page.getByTestId('ledger-day').first()).toContainText('支 800.00');
  await expect(page.getByTestId('ledger-row')).toHaveCount(2);
  await expect(page.getByTestId('ledger-row').first()).toContainText('−800.00');
  await expect(page.getByTestId('ledger-row').first()).toContainText('20:10 · 场地');
  await expect(page.getByTestId('ledger-row').nth(1)).toContainText('+300.00');
  await expect(page.getByTestId('ledger-row').nth(1)).toContainText('归属 张伟');
  expect(entryQueries.at(-1)?.get('from')).toMatch(/T00:00:00\+08:00$/);

  // 方向筛选：列表、汇总与日小计同步，并标注「筛选合计」。
  await page.getByLabel('收支方向').selectOption('1');
  await expect(page.getByTestId('ledger-row')).toHaveCount(1);
  await expect(page.getByTestId('ledger-row')).toContainText('−800.00');
  await expect(summary.locator('.ledger-figure__value--income')).toHaveText('¥0.00');
  await expect(summary.locator('.ledger-hero__scope')).toHaveText('筛选合计');

  // 方向为支出时，分类下拉只保留支出与通用分类。
  const categorySelect = page.getByLabel('账本分类');
  await expect(categorySelect.locator('option')).toHaveCount(3);
  await expect(categorySelect).toContainText('场地');
  await expect(categorySelect).toContainText('其他');
  await expect(categorySelect).not.toContainText('队费');

  await page.getByRole('button', { name: '重置' }).click();
  await expect(page.getByTestId('ledger-row')).toHaveCount(2);
  await expect(summary.locator('.ledger-hero__scope')).toHaveCount(0);

  // 月份切换：确认当月后按当月首末日下发 from/to。
  await summary.getByRole('button', { name: '选择月份' }).click();
  await page.locator('.van-picker__confirm').click();
  const monthLabel = (await summary.getByRole('button', { name: '选择月份' }).textContent()) ?? '';
  const matched = monthLabel.match(/(\d{4})年(\d{1,2})月/);
  expect(matched).not.toBeNull();
  const year = Number(matched?.[1]);
  const month = Number(matched?.[2]);
  const lastDay = String(new Date(Date.UTC(year, month, 0)).getUTCDate()).padStart(2, '0');
  const monthKey = `${year}-${String(month).padStart(2, '0')}`;
  await expect.poll(() => summaryQueries.at(-1)?.get('from')).toBe(`${monthKey}-01T00:00:00+08:00`);
  await expect
    .poll(() => summaryQueries.at(-1)?.get('to'))
    .toBe(`${monthKey}-${lastDay}T23:59:59+08:00`);

  // 上一个月：标题回退一个月且继续按整月查询。
  await summary.getByRole('button', { name: '上一个月' }).click();
  await expect(summary.getByRole('button', { name: '选择月份' })).not.toHaveText(monthLabel);
  await expect.poll(() => summaryQueries.at(-1)?.has('from')).toBe(true);

  // 自定义时间与月份互斥：选范围后标题显示区间，月份箭头禁用。
  await page.getByRole('button', { name: '选择自定义时间' }).click();
  const days = page.locator('.van-calendar__day:not(.van-calendar__day--disabled)');
  await days.first().click();
  await days.first().click();
  await page.locator('.van-calendar__confirm').click();
  const latest = () => summaryQueries.at(-1) as URLSearchParams;
  await expect.poll(() => latest().get('from')).toMatch(/^\d{4}-\d{2}-\d{2}T00:00:00\+08:00$/);
  expect(latest().get('to')).toBe(latest().get('from')?.replace('T00:00:00', 'T23:59:59'));
  await expect(summary.getByRole('button', { name: '选择月份' })).toHaveText(/\d{1,2}\/\d{1,2}–/);
  await expect(summary.getByRole('button', { name: '上一个月' })).toBeDisabled();

  await page.getByRole('button', { name: '重置' }).click();
  await expect(summary.getByRole('button', { name: '选择月份' })).toHaveText(/年\d{1,2}月/);

  // 搜索只匹配摘要：列表与日小计带 keyword，汇总不带。
  await expect(page.getByLabel('搜索流水')).toHaveAttribute('maxlength', '64');
  await page.getByLabel('搜索流水').fill('场地');
  await expect.poll(() => entryQueries.at(-1)?.get('keyword')).toBe('场地');
  await expect(page.getByTestId('ledger-row')).toHaveCount(1);
  await expect(page.getByTestId('ledger-day')).toHaveCount(1);
  expect(summaryQueries.at(-1)?.has('keyword')).toBe(false);

  // 统计 Tab：默认按月、支出口径，并跟随页面当前月份取数。
  const currentLabel =
    (await summary.getByRole('button', { name: '选择月份' }).textContent()) ?? '';
  const current = currentLabel.match(/(\d{4})年(\d{1,2})月/);
  expect(current).not.toBeNull();
  const currentAnchor = `${current?.[1]}-${String(Number(current?.[2])).padStart(2, '0')}-15`;
  await page.getByRole('tab', { name: '统计' }).click();
  await expect.poll(() => statsQueries.at(-1)?.get('period')).toBe('month');
  await expect.poll(() => statsQueries.at(-1)?.get('anchor')).toBe(currentAnchor);
  const stats = page.getByTestId('ledger-stats');
  await expect(stats.locator('.stats-total')).toHaveText('¥800.00');
  await expect(stats.locator('.stats-line').first()).toHaveText(
    '支出1笔，总计 ¥800.00，比上月增加 ¥100.00',
  );
  await expect(stats.locator('.stats-line').nth(1)).toHaveText('本月结余 −¥500.00，与上月持平');
  await expect(page.getByTestId('ledger-trend').locator('.trend__col')).toHaveCount(30);

  await stats.getByRole('button', { name: '收入', exact: true }).click();
  await expect(stats.locator('.stats-total')).toHaveText('¥300.00');
  await expect(stats.locator('.stats-line').first()).toContainText('收入1笔');

  // 自定义统计时间：打开日历确认后按 from/to 查询。
  await page.getByRole('tab', { name: '自定义时间' }).click();
  const statsDays = page.locator('.van-calendar__day:not(.van-calendar__day--disabled)');
  await statsDays.first().click();
  await statsDays.first().click();
  await page.locator('.van-calendar__confirm').click();
  await expect.poll(() => statsQueries.at(-1)?.get('period')).toBe('custom');
  expect(statsQueries.at(-1)?.get('from')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  expect(statsQueries.at(-1)?.get('to')).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  await expect(page.getByTestId('ledger-trend').locator('.trend__col')).toHaveCount(20);

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
