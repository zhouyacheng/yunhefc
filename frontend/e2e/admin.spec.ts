import { Buffer } from 'node:buffer';

import { expect, test, type Locator, type Page, type Response } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** 可被浏览器解码并重新压缩的 2×2 PNG。 */
const VALID_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAAqADAAQAAAABAAAAAgAAAADtGLyqAAAAFElEQVQIHWP4z8DAAMIM////ZwAAHu8E/CH5t8cAAAAASUVORK5CYII=',
  'base64',
);

async function loginAdmin(
  page: Page,
  phone = '13910000001',
  landing: RegExp = /\/admin\/matches$/,
): Promise<void> {
  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(landing);
}

/** 比赛下拉新增赛季/状态筛选后，隐藏 popper 里也有选项；只匹配可见下拉。 */
async function pickMatch(page: Page, ...texts: string[]): Promise<void> {
  await page.locator('.match-picker').click();
  let option = page.locator('.el-select__popper:visible .el-select-dropdown__item');
  for (const text of texts) {
    option = option.filter({ hasText: text });
  }
  await option.first().click();
}

const shanghaiInputFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** 表单统一按上海墙钟填写，与页面的 Asia/Shanghai 展示一致。 */
function shanghaiDateTimeInput(date: Date): string {
  return shanghaiInputFormatter.format(date);
}

/** 未来某天上海时间 20:00（UTC 12:00），避免受浏览器时区影响。 */
function futureInstant(daysAhead: number): Date {
  const date = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000);
  date.setUTCHours(12, 0, 0, 0);
  return date;
}

async function fillDialogDateTime(dialog: Locator, label: string, date: Date): Promise<void> {
  const input = dialog.locator('.el-form-item').filter({ hasText: label }).locator('input').first();
  await input.click();
  await input.fill(shanghaiDateTimeInput(date));
  await input.press('Enter');
  await input.blur();
  await expect(input).toHaveValue(shanghaiDateTimeInput(date));
}

function matchRow(page: Page, title: string): Locator {
  return page.locator('.el-table__row').filter({ hasText: title });
}

interface MatchAdminItem {
  id: number;
  seasonMatchNo: number;
  round: number | null;
}

/** 从管理端列表接口取系统分配的赛季场次编号，作为页面展示的独立事实源。 */
async function matchAdminItemFor(
  page: Page,
  title: string,
  season: string,
): Promise<MatchAdminItem> {
  const response = await page.request.get('/api/v1/b/matches', {
    params: { season, pageSize: 50 },
  });
  expect(response.ok()).toBe(true);
  const body = await response.json();
  const item = (body.data.items as Array<MatchAdminItem & { title: string }>).find(
    (match) => match.title === title,
  );
  expect(item).toBeTruthy();
  return item!;
}

async function chooseStatus(page: Page, row: Locator, label: string): Promise<void> {
  await row.getByRole('button', { name: '状态' }).click();
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: label }).first().click();
}

async function chooseRowAction(page: Page, row: Locator, label: string): Promise<void> {
  await row.getByRole('button', { name: '更多' }).click();
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: label }).first().click();
}

async function openRowMenu(page: Page, row: Locator): Promise<void> {
  await row.getByRole('button', { name: '更多' }).click();
  await expect(page.locator('.el-dropdown-menu__item:visible').first()).toBeVisible();
}

/** 下拉菜单不会响应 Escape，需点击表格外区域关闭后再操作下一行。 */
async function closeRowMenu(page: Page): Promise<void> {
  await page.locator('.el-table__header').click({ position: { x: 10, y: 10 } });
  await expect(page.locator('.el-dropdown-menu:visible')).toHaveCount(0);
}

/** B06 保存可能依次弹出“调整已保存分组”和“保存分组”确认。 */
async function confirmGroupingDialogs(page: Page): Promise<void> {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const box = page.locator('.el-message-box');
    const visible = await box
      .waitFor({ state: 'visible', timeout: 3000 })
      .then(() => true)
      .catch(() => false);
    if (!visible) {
      return;
    }
    // 弹窗关闭动画会让按钮瞬时脱离 DOM，强制点击避免 Playwright 重试到超时。
    await box
      .getByRole('button', { name: /继续调整|仍然保存|确定/ })
      .first()
      .click({ force: true });
    await box.waitFor({ state: 'hidden' }).catch(() => undefined);
  }
}

test('B 端：管理员登录后看到比赛列表', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/\/admin\/matches$/);
  await expect(page.getByText('橙队VS兰队').first()).toBeVisible();
  await expect(page.getByText('已锁定').first()).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B03 选择比赛并标记考勤', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  // 1001 与 1003 同名同状态，下拉又排除当前选中；用深链明确落到 1001。
  await page.goto('/admin/registrations?match=1001');
  await expect(page).toHaveURL(/\/admin\/registrations\?match=1001$/);
  await expect(page.getByText('正式球员').first()).toBeVisible();
  await expect(page.locator('.match-picker')).toContainText('橙队VS兰队');

  const row = page.locator('.roster-row').filter({ hasText: '测试门将01' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '正常' }).click();
  await expect(row.locator('.attendance-state')).toContainText('正常');
  await expect(row.locator('.attendance-state')).toContainText('管理员');

  expect(errors).toEqual([]);
});

test('B 端：比赛选择器默认当前赛季、下拉分页与深链刷新', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);

  const currentYear = String(new Date(Date.now() + 8 * 60 * 60 * 1000).getUTCFullYear());
  const listRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/api/v1/c/matches?') && request.url().includes('pageSize=10'),
  );
  await page.goto('/admin/registrations');
  const requestUrl = new URL((await listRequest).url());
  expect(requestUrl.searchParams.get('season')).toBe(currentYear);
  expect(requestUrl.searchParams.get('page')).toBe('1');

  await expect(page.locator('.season-filter')).toContainText(`${currentYear} 赛季`);
  await expect(page.locator('.month-filter')).toBeVisible();

  // 自定义月份面板：没有年份，只有 12 个月；1月–3月换算为日期范围。
  const monthRequest = page.waitForRequest(
    (request) =>
      request.url().includes('/api/v1/c/matches?') &&
      request.url().includes(`startDate=${currentYear}-01-01`) &&
      request.url().includes(`endDate=${currentYear}-03-31`),
  );
  await page.locator('.month-filter').click();
  const monthPopper = page.locator('.month-range-popper:visible');
  await expect(monthPopper).toBeVisible();
  await expect(monthPopper.getByRole('button')).toHaveCount(12);
  await expect(monthPopper).not.toContainText('年');
  await monthPopper.getByRole('button', { name: '1月', exact: true }).click();
  await monthPopper.getByRole('button', { name: '3月', exact: true }).click();
  await monthRequest;
  await expect(page.locator('.month-filter')).toContainText('1月 至 3月');

  // 全部赛季：清空并禁用月份筛选。
  await page.locator('.season-filter').click();
  await page
    .locator('.el-select__popper:visible .el-select-dropdown__item')
    .filter({ hasText: '全部赛季' })
    .click();
  await expect(page.locator('.month-filter')).toHaveClass(/is-disabled/);
  await expect(page.locator('.month-filter')).toContainText('全部赛季不筛选月份');

  // 切回当前赛季：恢复可用且月份为空。
  await page.locator('.season-filter').click();
  await page
    .locator('.el-select__popper:visible .el-select-dropdown__item')
    .filter({ hasText: `${currentYear} 赛季` })
    .click();
  await expect(page.locator('.month-filter')).not.toHaveClass(/is-disabled/);
  await expect(page.locator('.month-filter')).toContainText('开始月份 至 结束月份');

  // 深链：显式落到 1001（同名同状态的 1003 也会命中文字筛选），刷新后仍停在该场。
  await page.goto('/admin/registrations?match=1001');
  await expect(page).toHaveURL(/[?&]match=1001/);

  await page.reload();
  await expect(page.locator('.roster-row').filter({ hasText: '测试门将01' })).toBeVisible();

  await page.locator('.match-picker').click();
  await expect(page.locator('.el-select__popper:visible .picker-footer')).toContainText(
    /共 \d+ 场/,
  );
  await expect(page.locator('.el-select__popper:visible .picker-footer')).toContainText(
    /\d+ \/ \d+/,
  );

  expect(errors).toEqual([]);
});

test('B 端：比赛选择器下拉翻页并保留当前选中', async ({ page }) => {
  await loginAdmin(page);

  const totalMatches = 25;
  const mockMatch = (id: number) => ({
    id,
    title: `E2E翻页赛${id}`,
    type: 1,
    typeLabel: '队内赛',
    startAt: new Date(
      Date.UTC(2026, 10, 20, 12, 0, 0) - (id - 9000) * 60 * 60 * 1000,
    ).toISOString(),
    endAt: new Date(Date.UTC(2026, 10, 20, 14, 0, 0) - (id - 9000) * 60 * 60 * 1000).toISOString(),
    venue: 'E2E球场',
    season: '2026',
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    status: 2,
    statusLabel: '已锁定',
    totalMatchNo: id,
    seasonMatchNo: id,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
  });

  await page.route('**/api/v1/c/matches?**', async (route) => {
    const url = new URL(route.request().url());
    const pageNo = Number(url.searchParams.get('page') ?? '1');
    const pageSize = Number(url.searchParams.get('pageSize') ?? '10');
    const all = Array.from({ length: totalMatches }, (_, index) => mockMatch(9000 + index));
    const start = (pageNo - 1) * pageSize;
    await route.fulfill({
      json: {
        code: 0,
        message: 'ok',
        requestId: 'e2e-picker',
        data: {
          items: all.slice(start, start + pageSize),
          total: totalMatches,
          page: pageNo,
          pageSize,
          totalPages: Math.ceil(totalMatches / pageSize),
        },
      },
    });
  });

  await page.goto('/admin/registrations');
  await expect(page.locator('.match-picker')).toContainText('E2E翻页赛9000');

  await page.locator('.match-picker').click();
  const popper = page.locator('.el-select__popper:visible');
  await expect(popper.locator('.picker-footer')).toContainText('共 25 场');
  await expect(popper.locator('.picker-footer')).toContainText('1 / 3');

  await popper.getByRole('button', { name: '下一页' }).click();
  await expect(popper.locator('.picker-footer')).toContainText('2 / 3');
  await expect(
    popper.locator('.el-select-dropdown__item').filter({ hasText: 'E2E翻页赛9010' }),
  ).toBeVisible();
  // 当前选中项不出现在下拉选项；触发框仍显示它。
  await expect(
    popper.locator('.el-select-dropdown__item').filter({ hasText: 'E2E翻页赛9000' }),
  ).toHaveCount(0);
  await expect(page.locator('.match-picker')).toContainText('E2E翻页赛9000');

  await popper.locator('.el-select-dropdown__item').filter({ hasText: 'E2E翻页赛9010' }).click();
  await expect(page.locator('.match-picker')).toContainText('E2E翻页赛9010');
  await expect(page).toHaveURL(/[?&]match=9010/);
});

test('B 端：比赛选择器筛选后按结果校正选中', async ({ page }) => {
  await loginAdmin(page);

  const currentYear = String(new Date(Date.now() + 8 * 60 * 60 * 1000).getUTCFullYear());
  const mockMatch = (id: number, title: string, startAt: string) => ({
    id,
    title,
    type: 1,
    typeLabel: '队内赛',
    startAt,
    endAt: new Date(Date.parse(startAt) + 2 * 60 * 60 * 1000).toISOString(),
    venue: 'E2E球场',
    season: currentYear,
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    status: 2,
    statusLabel: '已锁定',
    totalMatchNo: id,
    seasonMatchNo: id,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
  });
  const november = mockMatch(9100, 'E2E筛选赛甲', `${currentYear}-11-20T12:00:00Z`);
  const march = mockMatch(9101, 'E2E筛选赛乙', `${currentYear}-03-10T12:00:00Z`);

  let lastMonth: string | null = null;
  await page.route('**/api/v1/c/matches?**', async (route) => {
    const url = new URL(route.request().url());
    const month = url.searchParams.get('month');
    const startDate = url.searchParams.get('startDate');
    lastMonth = month;
    const all = [november, march];
    let items = all;
    if (month === `${currentYear}-03` || startDate === `${currentYear}-03-01`) {
      items = [march];
    } else if (month === `${currentYear}-04` || startDate === `${currentYear}-04-01`) {
      items = [];
    }
    await route.fulfill({
      json: {
        code: 0,
        message: 'ok',
        requestId: 'e2e-picker-reconcile',
        data: {
          items,
          total: items.length,
          page: 1,
          pageSize: 10,
          totalPages: 1,
        },
      },
    });
  });

  await page.goto('/admin/registrations');
  // 默认选中新查询结果第 1 场。
  await expect(page.locator('.match-picker')).toContainText('E2E筛选赛甲');

  // public source 单月发 month=YYYY-MM；同月需点两次才提交范围。
  await page.locator('.month-filter').click();
  const monthPopper = page.locator('.month-range-popper:visible');
  await expect(monthPopper).toBeVisible();
  await monthPopper.getByRole('button', { name: '3月', exact: true }).click();
  await monthPopper.getByRole('button', { name: '3月', exact: true }).click();
  await expect.poll(() => lastMonth).toBe(`${currentYear}-03`);
  // 原选中不在 3 月结果内：自动改选第 1 场并写回深链。
  await expect(page.locator('.match-picker')).toContainText('E2E筛选赛乙');
  await expect.poll(() => new URL(page.url()).searchParams.get('match')).toBe('9101');

  await page.locator('.month-filter').click();
  await expect(monthPopper).toBeVisible();
  await monthPopper.getByRole('button', { name: '4月', exact: true }).click();
  await monthPopper.getByRole('button', { name: '4月', exact: true }).click();
  await expect.poll(() => lastMonth).toBe(`${currentYear}-04`);
  // 结果为空：清空选中与深链，展示空态。
  await expect(page.locator('.match-picker')).not.toContainText('E2E筛选赛乙');
  await expect.poll(() => new URL(page.url()).searchParams.has('match')).toBe(false);
  await expect(page.getByText('当前筛选下暂无比赛，试试调整赛季/日期/状态')).toBeVisible();
});

test('B 端：B06 生成分组并展示 A/B', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  // 1001 场为已锁定队内赛，种子数据已生成 10 A / 9 B；下拉排除当前选中，改用深链。
  await page.goto('/admin/grouping?match=1001');
  await expect(page).toHaveURL(/\/admin\/grouping\?match=1001$/);

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('测试门将01').first()).toBeVisible();
  await expect(page.getByText('分差')).toBeVisible();

  await page.getByRole('button', { name: /生成分组|重新生成/ }).click();
  await page
    .locator('.el-message-box')
    .getByRole('button', { name: /重新生成|确定/ })
    .click({ timeout: 2000 })
    .catch(() => undefined);
  // 等生成响应并被页面消化，避免列快照读在响应落地前后。
  await expect(page.getByText(/分组已(重新)?生成/)).toBeVisible();

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('测试门将01').first()).toBeVisible();

  // 有分组时报名区按 A/B 两列展示，列内成员与分组调整区一致。
  const rosterGrid = page.locator('.roster-section .roster-grid');
  await expect(rosterGrid.locator('.position-title')).toHaveText(['A 队 · 橙队', 'B 队 · 兰队']);
  for (const index of [0, 1]) {
    const rosterLabels = await rosterGrid
      .locator('> div')
      .nth(index)
      .locator('.roster-row > span:first-child')
      .allInnerTexts();
    const groupingNames = await page
      .locator('.group-admin-column')
      .nth(index)
      .locator('.group-player strong')
      .allInnerTexts();
    expect(rosterLabels).toHaveLength(groupingNames.length);
    for (const name of groupingNames) {
      expect(rosterLabels.filter((label) => label.startsWith(name))).toHaveLength(1);
    }
  }

  // 报名区每行展示星级图标与两位小数分值。
  const skillValues = await rosterGrid.locator('.roster-row .skill-value').allInnerTexts();
  expect(skillValues.length).toBeGreaterThan(0);
  for (const value of skillValues) {
    expect(value).toMatch(/^[1-5]\.\d{2}$/);
  }
  await expect(rosterGrid.locator('.roster-row').first().locator('.skill-star')).toHaveCount(5);
  await expect(
    page.locator('.group-admin-column .group-player').first().locator('.skill-star'),
  ).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('B 端：B06 调整 → 保存 → 清除 → 重新生成', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  // 先等初始分组请求落定：生成前的空态会先于响应渲染，count() 不自动等待会误判。
  const groupingLoaded = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/b/matches/1001/grouping') &&
      response.request().method() === 'GET',
  );
  await page.goto('/admin/grouping?match=1001');
  await groupingLoaded;
  await expect(page.getByRole('button', { name: '清除分组' })).toBeVisible();

  // 从空分组开始，保证后续断言不依赖上一轮遗留状态。
  await page.getByRole('button', { name: '清除分组' }).click();
  await page.locator('.el-message-box').getByRole('button', { name: '清除' }).click();
  await expect(page.getByText('尚未生成分组')).toBeVisible();
  // 无分组时报名区回到正式/外挂两列。
  await expect(page.locator('.roster-section .position-title')).toHaveText([
    '正式球员',
    '外挂球员',
  ]);
  await page.locator('.toolbar').getByRole('button', { name: '生成分组' }).click();
  await expect(page.locator('.group-admin-column')).toHaveCount(2);

  const firstTeamColumn = page.locator('.group-admin-column').nth(0);
  const secondTeamColumn = page.locator('.group-admin-column').nth(1);
  const player = firstTeamColumn.locator('.group-player').first();
  const playerName = await player.locator('strong').innerText();
  await player.click();
  await page.getByRole('button', { name: '移到 B 队' }).click();
  await expect(secondTeamColumn.getByText(playerName).first()).toBeVisible();
  await expect(firstTeamColumn.getByText(playerName)).toHaveCount(0);

  await page.getByRole('button', { name: '保存分组' }).click();
  await confirmGroupingDialogs(page);
  await expect(page.locator('.toolbar .el-tag').filter({ hasText: '已保存' })).toBeVisible();

  await page.getByRole('button', { name: '清除分组' }).click();
  await page.locator('.el-message-box').getByRole('button', { name: '清除' }).click();
  await expect(page.getByText('尚未生成分组')).toBeVisible();

  await page.locator('.toolbar').getByRole('button', { name: '生成分组' }).click();
  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('分差')).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B05 阵型模板复制、重命名、删除', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/lineup');
  await expect(page).toHaveURL(/\/admin\/lineup$/);
  await expect(page.locator('.template-card--preset')).toHaveCount(8);

  const preset = page.locator('.template-card--preset').first();
  await expect(preset.getByRole('button', { name: '重命名' })).toBeDisabled();
  await expect(preset.getByRole('button', { name: '删除' })).toBeDisabled();
  await expect(preset.getByText('预置阵型只读，可复制后修改。')).toBeVisible();

  const stamp = Date.now();
  const createdName = `测试阵型${stamp}`;
  const renamedName = `测试阵型改${stamp}`;

  await preset.getByRole('button', { name: '复制并新建' }).click();
  const copyDialog = page.locator('.el-dialog').filter({ hasText: '复制预置阵型' });
  await copyDialog.getByPlaceholder('模板名称').fill(createdName);
  await copyDialog.getByRole('button', { name: '创建模板' }).click();
  await expect(
    page.locator('.template-card--custom').filter({ hasText: createdName }),
  ).toBeVisible();

  await page
    .locator('.template-card--custom')
    .filter({ hasText: createdName })
    .getByRole('button', { name: '重命名' })
    .click();
  const renameDialog = page.locator('.el-dialog').filter({ hasText: '重命名自定义模板' });
  await renameDialog.getByPlaceholder('新的模板名称').fill(renamedName);
  await renameDialog.getByRole('button', { name: '保存名称' }).click();
  await expect(
    page.locator('.template-card--custom').filter({ hasText: renamedName }),
  ).toBeVisible();

  await page
    .locator('.template-card--custom')
    .filter({ hasText: renamedName })
    .getByRole('button', { name: '删除' })
    .click();
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click();
  await expect(page.locator('.template-card--custom').filter({ hasText: renamedName })).toHaveCount(
    0,
  );

  expect(errors).toEqual([]);
});

test('B 端：B05 选阵型并排满 8 人首发后保存，C03 只读查看', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  // 下拉排除当前选中，1001/1003 又同名同状态，改用深链明确落到 1001。
  await page.goto('/admin/lineup?match=1001');
  await expect(page).toHaveURL(/\/admin\/lineup\?match=1001$/);
  await expect(page.locator('.pitch')).toBeVisible();

  await page.locator('.formation-picker').click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: '3-2-2（预置）' })
    .first()
    .click();
  // 重复运行时旧阵容已有首发，切换阵型会弹确认。
  const switchBox = page.locator('.el-message-box');
  const needsSwitch = await switchBox
    .waitFor({ state: 'visible', timeout: 1500 })
    .then(() => true)
    .catch(() => false);
  if (needsSwitch) {
    await switchBox.getByRole('button', { name: '切换' }).click();
  }

  for (let index = 0; index < 8; index += 1) {
    const slot = page.locator('.pitch-slot:not(.pitch-slot--filled)').first();
    if ((await slot.count()) === 0) {
      break;
    }
    await page.locator('.bench-item--bench .bench-select').first().click();
    await slot.click();
  }
  await expect(page.locator('.pitch-slot--filled')).toHaveCount(8);
  await page.getByRole('button', { name: '保存阵容' }).click();
  await expect(page.getByText('阵容已保存')).toBeVisible();
  await expect(page.locator('.lineup-version')).toContainText('v');

  // C 端正式球员只读查看同一场比赛的阵容（1023 为 B 端套件专用，避开 1002 限流）。
  await page.goto('http://localhost:5273/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/matches$/);

  await page.goto('http://localhost:5273/matches/1001');
  await page.getByRole('button', { name: '阵容' }).click();
  await expect(page.locator('.lineup-slot--filled')).toHaveCount(8);
  await expect(page.getByText('测试门将01').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /保存阵容/ })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：只读球员可查看分组但不能生成', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.locator('.admin-sidebar')).toBeVisible();

  await page.goto('/admin/grouping?match=1001');
  await expect(page).toHaveURL(/\/admin\/grouping\?match=1001$/);

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByRole('button', { name: /生成分组|重新生成/ })).toBeDisabled();

  expect(errors).toEqual([]);
});

test('B 端：B04 有计分事件只读推导比分，无计分事件录入比分', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/events');
  await expect(page).toHaveURL(/\/admin\/events$/);
  await expect(page.getByText('事件 / 比分')).toBeVisible();

  // 1004 场进行中，1002 已确认报名。
  await pickMatch(page, 'M06事件联调赛');

  // 前置清理：web 项目的事件用例可能先跑；删除遗留事件，确保从“无计分事件”开始。
  await expect(
    page.locator('.event-row').first().or(page.getByText('还没有事件记录')),
  ).toBeVisible();
  const existingRows = page.locator('.event-row');
  let remaining = await existingRows.count();
  while (remaining > 0) {
    await existingRows.first().getByRole('button', { name: '删除' }).click();
    await page
      .getByRole('dialog', { name: '删除事件' })
      .getByRole('button', { name: '删除' })
      .click();
    await expect(existingRows).toHaveCount(remaining - 1);
    remaining -= 1;
  }

  // 无计分事件：可直接录入比分。
  await expect(page.locator('.score-row input')).toHaveCount(2);
  await expect(page.getByRole('button', { name: '保存比分' })).toBeVisible();
  await expect(page.locator('.score-row input').nth(0)).toBeEnabled();

  await page.getByRole('button', { name: '录入事件' }).click();
  const dialog = page.locator('.el-dialog');
  await expect(dialog.getByText('录入事件')).toBeVisible();
  await dialog.getByRole('combobox', { name: '球员', exact: true }).click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: '测试后卫01' })
    .first()
    .click();
  await dialog.locator('.el-form-item').filter({ hasText: '分钟' }).locator('input').fill('77');
  await dialog.getByRole('button', { name: '保存事件' }).click();

  const row = page
    .locator('.event-row')
    .filter({ hasText: '进球' })
    .filter({ hasText: '测试后卫01' })
    .filter({ hasText: "77'" })
    .first();
  await expect(row).toBeVisible();

  // 有计分事件：只读展示推导比分，不提供比分输入与保存。
  await expect(page.locator('.score-final')).toHaveText('1 : 0');
  await expect(page.locator('.score-row input')).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存比分' })).toHaveCount(0);

  await row.getByRole('button', { name: '编辑' }).click();
  await dialog.locator('.el-form-item').filter({ hasText: '分钟' }).locator('input').fill('78');
  await dialog.getByRole('button', { name: '保存事件' }).click();
  const updatedRow = page
    .locator('.event-row')
    .filter({ hasText: '测试后卫01' })
    .filter({ hasText: "78'" })
    .first();
  await expect(updatedRow).toBeVisible();
  await expect(page.locator('.score-final')).toHaveText('1 : 0');

  // 删除计分事件后恢复比分录入。
  await updatedRow.getByRole('button', { name: '删除' }).click();
  await page
    .getByRole('dialog', { name: '删除事件' })
    .getByRole('button', { name: '删除' })
    .click();
  await expect(page.locator('.score-row input')).toHaveCount(2);
  await expect(page.getByRole('button', { name: '保存比分' })).toBeVisible();

  const scoreInputs = page.locator('.score-row input');
  await scoreInputs.nth(0).fill('2');
  await scoreInputs.nth(1).fill('1');
  await page.getByRole('button', { name: '保存比分' }).click();
  await expect(scoreInputs.nth(0)).toHaveValue('2');
  await expect(scoreInputs.nth(1)).toHaveValue('1');

  // 只有非计分事件时，比分仍可继续编辑。
  await page.getByRole('button', { name: '录入事件' }).click();
  await dialog.locator('.el-form-item').filter({ hasText: '类型' }).locator('.el-select').click();
  await page.locator('.el-select-dropdown__item:visible').filter({ hasText: '黄牌' }).click();
  await dialog.getByRole('combobox', { name: '球员', exact: true }).click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: '测试后卫01' })
    .first()
    .click();
  await dialog.locator('.el-form-item').filter({ hasText: '分钟' }).locator('input').fill('30');
  await dialog.getByRole('button', { name: '保存事件' }).click();
  await expect(page.locator('.event-row').filter({ hasText: '黄牌' }).first()).toBeVisible();
  await expect(page.locator('.score-row input').nth(0)).toHaveValue('2');
  await expect(page.locator('.score-row input').nth(1)).toHaveValue('1');
  await expect(page.getByRole('button', { name: '保存比分' })).toBeVisible();

  // 收尾清理：删除本轮录入的非计分事件，保持比赛无事件，便于重复运行。
  await page
    .locator('.event-row')
    .filter({ hasText: '黄牌' })
    .first()
    .getByRole('button', { name: '删除' })
    .click();
  await page
    .getByRole('dialog', { name: '删除事件' })
    .getByRole('button', { name: '删除' })
    .click();
  await expect(page.locator('.event-row')).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：侧栏按 sections 过滤，直访无权限分区显示 403', async ({ page }) => {
  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  // 本期不设工作台：登录后落首个有权限分区；1023 仅有报名/考勤、阵容、分组。
  await expect(page).toHaveURL(/\/admin\/registrations$/);
  await expect(page.getByRole('menuitem', { name: '报名/考勤' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '比赛管理' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: '活动管理' })).toHaveCount(0);
  // P-98：正式球员持 ledger:view:any，可进账本只读分区。
  await expect(page.getByRole('menuitem', { name: '账本管理' })).toBeVisible();

  // 侧栏按 02 §2.4 分组：无权限分区的组不渲染。
  const sidebar = page.locator('.admin-sidebar');
  await expect(sidebar.getByText('运营', { exact: true })).toBeVisible();
  await expect(sidebar.getByText('战术', { exact: true })).toBeVisible();
  await expect(sidebar.getByText('球队', { exact: true })).toBeVisible();
  await expect(sidebar.getByText('系统', { exact: true })).toHaveCount(0);

  // 直访 /admin/ 也应落首个有权限分区，而不是已下线的 dashboard。
  await page.goto('/admin/');
  await expect(page).toHaveURL(/\/admin\/registrations$/);

  await page.goto('/admin/matches');
  await expect(page).toHaveURL(/\/admin\/forbidden\?section=matches$/);
  await expect(page.getByText('403 无权限访问')).toBeVisible();

  await page.goto('/admin/activities');
  await expect(page).toHaveURL(/\/admin\/forbidden\?section=activities$/);

  // 账本只读：可看汇总与流水，不出现维护入口。
  await page.goto('/admin/ledger');
  await expect(page).toHaveURL(/\/admin\/ledger$/);
  await expect(page.getByRole('heading', { name: '账本管理', level: 2 })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增流水' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '分类管理' })).toHaveCount(0);
  await expect(page.getByRole('columnheader', { name: '操作' })).toHaveCount(0);

  // LED-12：只读球员的分类筛选走 B 端只读别名，不依赖 C 端 Cookie。
  await page.locator('.category-filter').click();
  await expect(
    page
      .locator('.el-select__popper:visible .el-select-dropdown__item')
      .filter({ hasText: '场地' }),
  ).toBeVisible();
});

test('B 端：B12 活动管理页渲染与关键操作可见', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);

  await page.getByRole('menuitem', { name: '活动管理' }).click();
  await expect(page).toHaveURL(/\/admin\/activities$/);
  await expect(page.getByRole('heading', { name: '活动管理', level: 2 })).toBeVisible();
  await expect(page.getByRole('columnheader', { name: '活动名' })).toBeVisible();
  await expect(page.getByRole('button', { name: '新建活动' })).toBeVisible();

  await page.getByRole('button', { name: '新建活动' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '创建活动' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.el-form-item').filter({ hasText: '活动名称' })).toBeVisible();
  await expect(
    dialog.locator('.el-form-item').filter({ hasText: '场地环境' }).locator('.image-upload-slot'),
  ).toHaveCount(10);

  // 超过单张上限走弹窗提示，不进入上传流程。
  await dialog.locator('input[type=file]').setInputFiles({
    name: 'oversize.png',
    mimeType: 'image/png',
    buffer: Buffer.alloc(5 * 1024 * 1024 + 1),
  });
  await expect(page.getByText('图片不能超过 5MB')).toBeVisible();
  await page.getByRole('button', { name: '知道了' }).click();
  await expect(page.getByText('图片不能超过 5MB')).toBeHidden();

  // 批量选择超过剩余张数时整批拒绝。
  await dialog.locator('input[type=file]').setInputFiles(
    Array.from({ length: 11 }, (_, index) => ({
      name: `batch-${index}.png`,
      mimeType: 'image/png',
      buffer: VALID_PNG,
    })),
  );
  await expect(page.getByText('场地环境图片最多 10 张')).toBeVisible();
  await page.getByRole('button', { name: '知道了' }).click();
  await expect(page.getByText('场地环境图片最多 10 张')).toBeHidden();

  // 批量选择合法图片时按顺序逐张上传并各自落位。
  let uploaded = 0;
  await page.route('**/api/v1/b/files/activity-image', async (route) => {
    uploaded += 1;
    if (uploaded === 1) {
      // 留出窗口断言上传中的进度覆盖层。
      await new Promise((resolve) => setTimeout(resolve, 800));
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: {
          objectKey: `activity/1/batch-${uploaded}.jpg`,
          signedUrl: `data:image/png;base64,${VALID_PNG.toString('base64')}`,
        },
        requestId: 'e2e-upload',
      }),
    });
  });
  await dialog.locator('input[type=file]').setInputFiles([
    { name: 'batch-a.png', mimeType: 'image/png', buffer: VALID_PNG },
    { name: 'batch-b.png', mimeType: 'image/png', buffer: VALID_PNG },
  ]);
  await expect(dialog.locator('.image-progress')).toHaveCount(1);
  await expect(dialog.locator('.image-progress .el-progress')).toBeVisible();
  await expect(dialog.locator('.image-preview')).toHaveCount(2);
  await expect(dialog.locator('.image-progress')).toHaveCount(0);
  expect(uploaded).toBe(2);
  await page.unroute('**/api/v1/b/files/activity-image');

  // 批量中途失败：保留已成功项、停止后续上传并弹窗。
  await dialog.getByRole('button', { name: '移除' }).first().click();
  await dialog.getByRole('button', { name: '移除' }).first().click();
  await expect(dialog.locator('.image-preview')).toHaveCount(0);

  let attempts = 0;
  await page.route('**/api/v1/b/files/activity-image', async (route) => {
    attempts += 1;
    if (attempts === 2) {
      // 用 200 + 错误 envelope 模拟服务端失败，避免测试把 500 计入 console error。
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 50001,
          message: '上传失败，请稍后重试',
          data: null,
          requestId: 'e2e-upload-fail',
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: {
          objectKey: `activity/1/partial-${attempts}.jpg`,
          signedUrl: `data:image/png;base64,${VALID_PNG.toString('base64')}`,
        },
        requestId: 'e2e-upload',
      }),
    });
  });
  await dialog.locator('input[type=file]').setInputFiles([
    { name: 'partial-a.png', mimeType: 'image/png', buffer: VALID_PNG },
    { name: 'partial-b.png', mimeType: 'image/png', buffer: VALID_PNG },
    { name: 'partial-c.png', mimeType: 'image/png', buffer: VALID_PNG },
  ]);
  await expect(page.getByText('上传失败，请稍后重试')).toBeVisible();
  await page.getByRole('button', { name: '知道了' }).click();
  await expect(dialog.locator('.image-preview')).toHaveCount(1);
  expect(attempts).toBe(2);
  await page.unroute('**/api/v1/b/files/activity-image');

  await dialog.getByRole('button', { name: '移除' }).first().click();
  await expect(dialog.locator('.image-preview')).toHaveCount(0);
  await dialog.getByRole('button', { name: '取消' }).click();
  await expect(dialog).toBeHidden();

  // 仅断言表头无法证明列表渲染；用真实接口写入一条活动后断言行出现。
  const csrfResponse = await page.request.get('/api/v1/b/auth/csrf');
  expect(csrfResponse.ok()).toBe(true);
  const csrf = (await csrfResponse.json()) as {
    data: { token: string; headerName?: string };
  };
  const title = `E2E活动${Date.now()}`;
  const startAt = new Date(Date.now() + 24 * 60 * 60 * 1000);
  const created = await page.request.post('/api/v1/b/activities', {
    headers: {
      [csrf.data.headerName ?? 'X-XSRF-TOKEN']: csrf.data.token,
      'Idempotency-Key': `e2e-activity-${Date.now()}`,
    },
    data: {
      title,
      type: 0,
      startAt: startAt.toISOString(),
      endAt: new Date(startAt.getTime() + 2 * 60 * 60 * 1000).toISOString(),
      signupDeadline: new Date(startAt.getTime() - 2 * 60 * 60 * 1000).toISOString(),
      capacity: 10,
      familyAllowed: false,
      costPerPerson: 100,
      location: 'E2E 测试场地',
      locationImageKeys: [],
      description: 'B12 smoke',
    },
  });
  expect(created.ok()).toBe(true);

  await page.reload();
  const row = page.locator('.el-table__row').filter({ hasText: title });
  await expect(row).toBeVisible();

  // 编辑已独立成按钮；状态下拉提供报名中并支持与报名截止互转。
  await expect(row.getByRole('button', { name: '编辑' })).toBeVisible();
  await row.getByRole('button', { name: '编辑' }).click();
  const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑活动' });
  await expect(editDialog).toBeVisible();
  await editDialog.getByRole('button', { name: '取消' }).click();
  await expect(editDialog).toBeHidden();

  await row.getByRole('button', { name: '状态' }).click();
  await expect(page.getByRole('menuitem', { name: '报名中' })).toBeVisible();
  await page.getByRole('menuitem', { name: '报名截止' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '报名截止' })).toBeVisible();

  await row.getByRole('button', { name: '状态' }).click();
  await page.getByRole('menuitem', { name: '报名中' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '报名中' })).toBeVisible();

  // 已结束仍可编辑（P-113）。
  await row.getByRole('button', { name: '状态' }).click();
  await page.getByRole('menuitem', { name: '已结束' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '已结束' })).toBeVisible();
  await row.getByRole('button', { name: '编辑' }).click();
  await expect(editDialog).toBeVisible();
  await editDialog.getByRole('button', { name: '取消' }).click();
  await expect(editDialog).toBeHidden();

  // 已取消可恢复为报名中（P-114）。
  await row.getByRole('button', { name: '状态' }).click();
  await page.getByRole('menuitem', { name: '报名中' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '报名中' })).toBeVisible();
  await row.getByRole('button', { name: '状态' }).click();
  await page.getByRole('menuitem', { name: '取消' }).click();
  const cancelDialog = page.locator('.el-dialog').filter({ hasText: '取消活动' });
  await expect(cancelDialog).toBeVisible();
  // 取消原因可选（P-120）。
  await cancelDialog.getByRole('button', { name: '确认取消' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '已取消' })).toBeVisible();
  // 已取消仍可编辑（P-113）。
  await row.getByRole('button', { name: '编辑' }).click();
  await expect(editDialog).toBeVisible();
  await editDialog.getByRole('button', { name: '取消' }).click();
  await expect(editDialog).toBeHidden();
  await row.getByRole('button', { name: '状态' }).click();
  await page.getByRole('menuitem', { name: '报名中' }).click();
  await expect(row.locator('.el-tag').filter({ hasText: '报名中' })).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B13 账本管理页渲染汇总与关键操作', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);
  await page.getByRole('menuitem', { name: '账本管理' }).click();
  await expect(page).toHaveURL(/\/admin\/ledger$/);
  await expect(page.getByRole('heading', { name: '账本管理', level: 2 })).toBeVisible();
  await expect(page.locator('.summary-card')).toHaveCount(3);
  await expect(page.getByRole('columnheader', { name: '摘要' })).toBeVisible();
  await expect(page.getByRole('button', { name: '新增流水' })).toBeVisible();
  await expect(page.getByRole('button', { name: '分类管理' })).toBeVisible();

  await page.getByRole('button', { name: '新增流水' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '新增流水' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.el-form-item').filter({ hasText: '金额' })).toBeVisible();
  await dialog.getByRole('button', { name: '取消' }).click();
  await expect(dialog).toBeHidden();

  await page.getByRole('button', { name: '分类管理' }).click();
  const drawer = page.locator('.el-drawer').filter({ hasText: '分类管理' });
  await expect(drawer).toBeVisible();
  await expect(drawer.getByRole('button', { name: '新增分类' })).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B02 创建 → 编辑 → 延期 → 重新排期 → 取消', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);

  const title = `E2E比赛${Date.now()}`;
  const start = futureInstant(10);

  await page.getByRole('button', { name: '新建比赛' }).click();
  const createDialog = page.locator('.el-dialog').filter({ hasText: '创建比赛' });
  await expect(createDialog).toBeVisible();

  // 比赛类型下拉绑定数字模型，需显示中文且不出现筛选项「全部类型」
  const typeSelect = createDialog
    .locator('.el-form-item')
    .filter({ hasText: '类型' })
    .locator('.el-select');
  await expect(typeSelect).toContainText('队内赛');
  await typeSelect.click();
  const typeDropdown = page.locator('.el-select__popper:visible');
  await expect(
    typeDropdown.locator('.el-select-dropdown__item').filter({ hasText: '全部类型' }),
  ).toHaveCount(0);
  await typeDropdown.locator('.el-select-dropdown__item').filter({ hasText: '友谊赛' }).click();
  await expect(createDialog.getByText('我方主客')).toBeVisible();

  await createDialog.getByPlaceholder('例如 橙队VS兰队').fill(title);
  await fillDialogDateTime(createDialog, '开始时间', start);
  await fillDialogDateTime(
    createDialog,
    '结束时间',
    new Date(start.getTime() + 2 * 60 * 60 * 1000),
  );
  await createDialog.getByRole('button', { name: '创建比赛' }).click();

  const createdRow = matchRow(page, title);
  await expect(createdRow).toBeVisible();
  await expect(createdRow.getByText('报名中')).toBeVisible();

  await chooseRowAction(page, createdRow, '编辑');
  const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑比赛' });
  await expect(editDialog).toBeVisible();
  await editDialog.getByPlaceholder('例如 橙队VS兰队').fill(`${title}-改`);
  await editDialog.getByPlaceholder('比赛场地').fill('E2E球场');
  await editDialog.getByRole('button', { name: '保存修改' }).click();

  const editedTitle = `${title}-改`;
  const editedRow = matchRow(page, editedTitle);
  await expect(editedRow).toBeVisible();
  await expect(editedRow.getByText('E2E球场')).toBeVisible();

  await chooseStatus(page, editedRow, '已延期');
  const postponeDialog = page.locator('.el-dialog').filter({ hasText: '延期比赛' });
  await expect(postponeDialog).toBeVisible();
  const postponedStart = futureInstant(11);
  await fillDialogDateTime(postponeDialog, '开始时间', postponedStart);
  await fillDialogDateTime(
    postponeDialog,
    '结束时间',
    new Date(postponedStart.getTime() + 2 * 60 * 60 * 1000),
  );
  await postponeDialog.getByRole('button', { name: '确认延期' }).click();
  await expect(matchRow(page, editedTitle).getByText('已延期')).toBeVisible();

  // 已延期仍可编辑基本信息，保存后状态保持已延期。
  await chooseRowAction(page, matchRow(page, editedTitle), '编辑');
  const postponedEditDialog = page.locator('.el-dialog').filter({ hasText: '编辑比赛' });
  await expect(postponedEditDialog).toBeVisible();
  await expect(
    postponedEditDialog.getByText('编辑不会改变比赛状态；恢复报名请使用重新排期。'),
  ).toBeVisible();
  await postponedEditDialog.getByPlaceholder('比赛场地').fill('E2E延期球场');
  await postponedEditDialog.getByRole('button', { name: '保存修改' }).click();
  await expect(matchRow(page, editedTitle).getByText('E2E延期球场')).toBeVisible();
  await expect(matchRow(page, editedTitle).getByText('已延期')).toBeVisible();

  await chooseRowAction(page, matchRow(page, editedTitle), '重新排期');
  const rescheduleDialog = page.locator('.el-dialog').filter({ hasText: '确认重新排期' });
  await expect(rescheduleDialog).toBeVisible();
  const rescheduledStart = futureInstant(12);
  await fillDialogDateTime(rescheduleDialog, '开始时间', rescheduledStart);
  await fillDialogDateTime(
    rescheduleDialog,
    '结束时间',
    new Date(rescheduledStart.getTime() + 2 * 60 * 60 * 1000),
  );
  await rescheduleDialog.getByRole('button', { name: '确认重新排期' }).click();
  await expect(matchRow(page, editedTitle).getByText('报名中')).toBeVisible();

  await chooseStatus(page, matchRow(page, editedTitle), '已取消');
  const cancelDialog = page.locator('.el-dialog').filter({ hasText: '取消比赛' });
  await expect(cancelDialog).toBeVisible();
  await cancelDialog.getByPlaceholder('取消原因（将记入审计）').fill('E2E 收尾清理');
  await cancelDialog.getByRole('button', { name: '确认取消' }).click();

  const messageBox = page.locator('.el-message-box');
  await expect(messageBox).toBeVisible();
  await messageBox.getByRole('button', { name: '确认取消' }).click();
  await expect(matchRow(page, editedTitle).getByText('已取消')).toBeVisible();

  // 已取消仍可编辑名称，保存后状态保持已取消。
  await chooseRowAction(page, matchRow(page, editedTitle), '编辑');
  const cancelledEditDialog = page.locator('.el-dialog').filter({ hasText: '编辑比赛' });
  await expect(cancelledEditDialog).toBeVisible();
  const cancelledTitle = `${title}-取消后编辑`;
  await cancelledEditDialog.getByPlaceholder('例如 橙队VS兰队').fill(cancelledTitle);
  await cancelledEditDialog.getByRole('button', { name: '保存修改' }).click();
  const cancelledRow = matchRow(page, cancelledTitle);
  await expect(cancelledRow.getByText('已取消')).toBeVisible();

  // 合法集：已结束不可延期、已取消不可再取消，禁用项用 title 说明原因。
  const finishedRow = page
    .locator('.el-table__row')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已结束' });
  await openRowMenu(page, finishedRow);
  await expect(
    page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '编辑' }).first(),
  ).not.toHaveClass(/is-disabled/);
  await closeRowMenu(page);

  await finishedRow.getByRole('button', { name: '状态' }).click();
  const postponedItem = page
    .locator('.el-dropdown-menu__item:visible')
    .filter({ hasText: '已延期' })
    .first();
  await expect(postponedItem).toHaveClass(/is-disabled/);
  await expect(postponedItem).toHaveAttribute('title', '当前状态不可延期');
  await closeRowMenu(page);

  await cancelledRow.getByRole('button', { name: '状态' }).click();
  const cancelledItem = page
    .locator('.el-dropdown-menu__item:visible')
    .filter({ hasText: '已取消' })
    .first();
  await expect(cancelledItem).toHaveClass(/is-disabled/);
  await expect(cancelledItem).toHaveAttribute('title', '当前状态不可取消');
  await closeRowMenu(page);

  expect(errors).toEqual([]);
});

test('B 端：B02 比赛列表字段与赛季编辑', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);

  const headerTexts = (await page.locator('.el-table__header th').allInnerTexts()).map((text) =>
    text.trim(),
  );
  expect(headerTexts.slice(0, 5)).toEqual(['ID', '赛季', '场次', '比赛', '类型']);
  expect(headerTexts.some((text) => text.includes('开始时间'))).toBe(true);
  expect(headerTexts.some((text) => text.includes('结束时间'))).toBe(true);
  expect(headerTexts.some((text) => text.includes('创建时间'))).toBe(true);

  const title = `E2E比赛字段${Date.now()}`;
  const start = futureInstant(15);
  const end = new Date(start.getTime() + 2 * 60 * 60 * 1000);

  await page.getByRole('button', { name: '新建比赛' }).click();
  const createDialog = page.locator('.el-dialog').filter({ hasText: '创建比赛' });
  await expect(createDialog).toBeVisible();
  await expect(createDialog.locator('.el-form-item').filter({ hasText: /^场次/ })).toHaveCount(0);
  await createDialog.getByPlaceholder('例如 橙队VS兰队').fill(title);
  const createSeasonInput = createDialog.getByPlaceholder('如 2026');
  await createSeasonInput.fill('2077');
  await createSeasonInput.blur();
  await fillDialogDateTime(createDialog, '开始时间', start);
  await fillDialogDateTime(createDialog, '结束时间', end);
  await createDialog.getByRole('button', { name: '创建比赛' }).click();

  const createdRow = matchRow(page, title);
  await expect(createdRow).toBeVisible();
  const createdCells = createdRow.locator('td');
  await expect(createdCells.nth(1)).toHaveText('2077');
  const created = await matchAdminItemFor(page, title, '2077');
  await expect(createdCells.nth(0)).toHaveText(String(created.id));
  await expect(createdCells.nth(2)).toHaveText(String(created.seasonMatchNo));
  await expect(createdCells.nth(2)).toHaveText(/^\d+$/);
  await expect(createdCells.nth(5)).toHaveText(shanghaiDateTimeInput(start).replaceAll('-', '/'));
  await expect(createdCells.nth(6)).toHaveText(shanghaiDateTimeInput(end).replaceAll('-', '/'));

  // 目标赛季先放一场，保证被编辑比赛重算后的 seasonMatchNo 与其 round 不同，
  // 这样“场次”列若仍展示 round 就会被断言抓住。
  const baselineTitle = `E2E基准${Date.now()}`;
  await page.getByRole('button', { name: '新建比赛' }).click();
  const baselineDialog = page.locator('.el-dialog').filter({ hasText: '创建比赛' });
  await expect(baselineDialog).toBeVisible();
  await baselineDialog.getByPlaceholder('例如 橙队VS兰队').fill(baselineTitle);
  const baselineSeasonInput = baselineDialog.getByPlaceholder('如 2026');
  await baselineSeasonInput.fill('2078');
  await baselineSeasonInput.blur();
  const baselineStart = futureInstant(16);
  await fillDialogDateTime(baselineDialog, '开始时间', baselineStart);
  await fillDialogDateTime(
    baselineDialog,
    '结束时间',
    new Date(baselineStart.getTime() + 2 * 60 * 60 * 1000),
  );
  await baselineDialog.getByRole('button', { name: '创建比赛' }).click();
  await expect(matchRow(page, baselineTitle)).toBeVisible();

  await chooseRowAction(page, createdRow, '编辑');
  const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑比赛' });
  await expect(editDialog).toBeVisible();
  await expect(editDialog.locator('.el-form-item').filter({ hasText: /^场次/ })).toHaveCount(0);
  const seasonInput = editDialog.getByPlaceholder('如 2026');
  await expect(seasonInput).toBeEditable();
  await expect(seasonInput).toHaveValue('2077');
  await seasonInput.fill('2078');
  await seasonInput.blur();
  await editDialog.getByRole('button', { name: '保存修改' }).click();

  const editedRow = matchRow(page, title);
  await expect(editedRow.locator('td').nth(1)).toHaveText('2078');
  const edited = await matchAdminItemFor(page, title, '2078');
  // round 不随赛季变化，seasonMatchNo 会重算；两者不同才能证明列表取的是 seasonMatchNo。
  expect(edited.seasonMatchNo).not.toBe(edited.round);
  await expect(editedRow.locator('td').nth(2)).toHaveText(String(edited.seasonMatchNo));
  await expect(editedRow.locator('td').nth(2)).toHaveText(/^\d+$/);

  expect(errors).toEqual([]);
});

test('B 端：B02 只读权限不显示写操作', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['match:view:any'];
    body.data.sections = ['matches'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });
  await loginAdmin(page);

  await expect(page).toHaveURL(/\/admin\/matches$/);
  await expect(page.getByText('橙队VS兰队').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '新建比赛' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '状态' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '更多' })).toHaveCount(0);
  await expect(page.locator('.el-table__header th').filter({ hasText: '操作' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: '比赛管理' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '报名/考勤' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B07 筛选 → 编辑 → 头像', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 1022 为 B07 专用管理员，避免与主套件共享 1001 的 AUTH-15 登录限流。
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/players');
  await expect(page).toHaveURL(/\/admin\/players$/);
  await expect(page.getByRole('heading', { level: 2, name: '球员' })).toBeVisible();

  // 姓名 + 类型筛选生效并回到第 1 页。
  await page.getByPlaceholder('搜索球员姓名').fill('测试外挂01');
  await page.getByRole('button', { name: '搜索' }).click();
  const guestRow = page.locator('.el-table__row').filter({ hasText: '测试外挂01' });
  await expect(guestRow).toBeVisible();
  await expect(page.locator('.el-table__row')).toHaveCount(1);
  await expect(guestRow).toContainText('外挂');

  await page.getByRole('button', { name: '重置' }).click();

  // 1003 由种子预置 openid，微信列与解绑入口应可见。
  await page.getByPlaceholder('搜索球员姓名').fill('测试后卫02');
  await page.getByRole('button', { name: '搜索' }).click();
  const wechatRow = page.locator('.el-table__row').filter({ hasText: '测试后卫02' });
  await expect(wechatRow).toContainText('已绑定');
  await openRowMenu(page, wechatRow);
  await expect(
    page.locator('.el-dropdown-menu__item:visible').filter({ hasText: '解绑微信' }),
  ).toBeVisible();
  await closeRowMenu(page);

  // 1021 无号码，种子区块会复位 region/avatar，保证 E2E 可重复。
  await page.getByPlaceholder('搜索球员姓名').fill('测试待审核01');
  await page.getByRole('button', { name: '搜索' }).click();
  const row = page.locator('.el-table__row').filter({ hasText: '测试待审核01' });
  await expect(row).toBeVisible();
  await chooseRowAction(page, row, '编辑');
  const dialog = page.locator('.el-dialog').filter({ hasText: '编辑球员' });
  await expect(dialog).toBeVisible();

  await dialog.getByPlaceholder('请输入姓名').fill('测试待审核01改');
  await dialog.getByPlaceholder('如：上海').fill('E2E上海');
  const skillInput = dialog
    .locator('.el-form-item')
    .filter({ hasText: '星级' })
    .locator('.el-input-number input');
  await skillInput.fill('4.25');
  await skillInput.blur();

  // 本地校验失败路径不需要真实 OSS。
  await dialog.locator('input[type=file]').setInputFiles({
    name: 'avatar.gif',
    mimeType: 'image/gif',
    buffer: Buffer.from('GIF89a'),
  });
  await expect(dialog.getByText('仅支持 JPG/PNG/WebP')).toBeVisible();

  const upload = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/players/1021/avatar' &&
      response.request().method() === 'POST',
  );
  await dialog.locator('input[type=file]').setInputFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: VALID_PNG,
  });
  expect((await upload).ok()).toBe(true);
  await expect(dialog.locator('img[alt="头像预览"]')).toBeVisible();

  const patched = page.waitForRequest(
    (request) =>
      new URL(request.url()).pathname === '/api/v1/b/players/1021' && request.method() === 'PATCH',
  );
  await dialog.getByRole('button', { name: '保存修改' }).click();
  const body = (await patched).postDataJSON() as Record<string, unknown>;
  expect(body.name).toBe('测试待审核01改');
  expect(body.region).toBe('E2E上海');
  expect(body.skill).toBe(4.25);
  // 1021 无号码；未填写时不提交 jersey，后端保留 NULL。
  expect(body.jersey).toBeUndefined();
  expect(String(body.avatarUrl)).toMatch(/^avatar\/1021\/[0-9A-HJKMNP-TV-Z]{26}\.jpg$/);
  await expect(page.getByText('球员已更新')).toBeVisible();

  await page.getByPlaceholder('搜索球员姓名').fill('测试待审核01改');
  await page.getByRole('button', { name: '搜索' }).click();
  const editedStars = page
    .locator('.el-table__row')
    .filter({ hasText: '测试待审核01改' })
    .locator('.skill-stars');
  await expect(editedStars).toHaveAttribute('aria-label', '星级 4.25/5');
  await expect(editedStars.locator('.skill-star--full')).toHaveCount(4);
  await expect(editedStars.locator('.skill-star--empty')).toHaveCount(1);
  const editedRow = page.locator('.el-table__row').filter({ hasText: '测试待审核01改' });
  await expect(editedRow).toBeVisible();
  await expect(editedRow).toContainText('E2E上海');

  // 半星档筛选：4 星命中 4.00–4.49，4 星半命中 4.50–4.99；5 星不得混入。
  await page.getByPlaceholder('搜索球员姓名').fill('');
  const skillFilter = page
    .locator('.el-form-item')
    .filter({ hasText: '星级' })
    .locator('.el-select');
  await skillFilter.click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: /^4 星$/ })
    .click();
  await page.getByRole('button', { name: '搜索' }).click();
  await expect(page.locator('.el-table__row').filter({ hasText: '测试待审核01改' })).toBeVisible();
  await expect(page.locator('.el-table__row').filter({ hasText: '测试前锋01' })).toHaveCount(0);

  await skillFilter.click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: /^4 星半$/ })
    .click();
  await page.getByRole('button', { name: '搜索' }).click();
  await expect(page.locator('.el-table__row').filter({ hasText: '测试后卫04' })).toBeVisible();
  await expect(page.locator('.el-table__row').filter({ hasText: '测试待审核01改' })).toHaveCount(0);

  // 类型、状态、管理员列头可排序，请求带对应 sort。
  for (const [label, expected] of [
    ['类型', 'type:asc'],
    ['管理员', 'admin:asc'],
    ['状态', 'status:asc'],
  ] as const) {
    const sorted = page.waitForRequest(
      (request) =>
        new URL(request.url()).pathname === '/api/v1/b/players' && request.method() === 'GET',
    );
    await page
      .locator('.el-table__header th')
      .filter({ hasText: label })
      .click({ position: { x: 10, y: 14 } });
    expect(new URL((await sorted).url()).searchParams.get('sort')).toBe(expected);
  }

  // 同一会话重载 /b/me 验证只读权限，避免额外登录触发 AUTH-15 限流。
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['player:view:any'];
    body.data.sections = ['players'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });
  await page.reload();
  await expect(page).toHaveURL(/\/admin\/players$/);
  await expect(page.getByRole('menuitem', { name: '球员' })).toBeVisible();
  await expect(page.locator('.el-table__row').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '更多' })).toHaveCount(0);
  await expect(page.locator('.el-table__header th').filter({ hasText: '操作' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B08 新建角色 → 勾选权限 → 保存 → 重载 → 重命名 → 删除', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 复用 1022 管理员，避免与主套件共享 1001 的 AUTH-15 登录限流。
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await expect(page).toHaveURL(/\/admin\/roles$/);
  await expect(page.getByRole('heading', { level: 2, name: '角色权限' })).toBeVisible();
  // 10 组对应 02 §6.10 的 37 个权限码（含活动、账本、内容三组）。
  await expect(page.locator('.permission-group')).toHaveCount(10);

  const code = `E2E_${Date.now()}`;
  await page.getByRole('button', { name: '新建角色' }).click();
  const createDialog = page.locator('.el-dialog').filter({ hasText: '新建角色' });
  await createDialog.getByPlaceholder('例如 COACH').fill(code);
  await createDialog.getByPlaceholder('例如 教练').fill('E2E教练');

  const created = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/roles' &&
      response.request().method() === 'POST',
  );
  await createDialog.getByRole('button', { name: '创建' }).click();
  expect((await created).ok()).toBe(true);
  await expect(page.getByText('角色已创建')).toBeVisible();

  const roleItem = page.locator('.role-item').filter({ hasText: code });
  await expect(roleItem).toBeVisible();
  await expect(roleItem).toHaveClass(/active/);

  // 新角色默认无权限：保存前必须至少勾选一项。
  await page.getByRole('button', { name: '保存权限' }).click();
  await expect(page.getByText('至少选择一个权限')).toBeVisible();

  const matchPermission = page.locator('.permission-item').filter({ hasText: 'match:view:any' });
  await matchPermission.click();
  await expect(matchPermission.locator('input')).toBeChecked();

  const permissionsSaved = page.waitForResponse(
    (response) =>
      /^\/api\/v1\/b\/roles\/\d+\/permissions$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: '保存权限' }).click();
  const permissionsResponse = await permissionsSaved;
  expect(permissionsResponse.ok()).toBe(true);
  expect(permissionsResponse.request().headers()['idempotency-key']).toBeTruthy();
  expect(
    (permissionsResponse.request().postDataJSON() as { permissions: string[] }).permissions,
  ).toEqual(['match:view:any']);
  await expect(page.getByText('权限已保存')).toBeVisible();

  await page.reload();
  await expect(page.locator('.role-item').filter({ hasText: code })).toBeVisible();
  await page.locator('.role-item').filter({ hasText: code }).click();
  await expect(
    page.locator('.permission-item').filter({ hasText: 'match:view:any' }).locator('input'),
  ).toBeChecked();

  await page.getByRole('button', { name: '重命名' }).click();
  const renameDialog = page.locator('.el-dialog').filter({ hasText: '重命名角色' });
  await renameDialog.getByPlaceholder('请输入角色名称').fill('E2E教练改');
  await renameDialog.getByRole('button', { name: '保存' }).click();
  await expect(page.getByText('角色已重命名')).toBeVisible();
  await expect(page.locator('.role-item').filter({ hasText: 'E2E教练改' })).toBeVisible();

  await page.getByRole('button', { name: '删除' }).click();
  const messageBox = page.locator('.el-message-box');
  await messageBox.getByRole('button', { name: '删除' }).click();
  await expect(page.getByText('角色已删除')).toBeVisible();
  await expect(page.locator('.role-item').filter({ hasText: code })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B08 只读权限不显示写操作', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['role:view:any'];
    body.data.sections = ['roles'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });

  await loginAdmin(page, '13910000022', /\/admin\/roles$/);
  await expect(page.locator('.role-item').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '新建角色' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重命名' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存权限' })).toHaveCount(0);
  await expect(page.locator('.el-checkbox.is-disabled').first()).toBeVisible();

  expect(errors).toEqual([]);
});

async function searchAdminUsers(page: Page, keyword: string): Promise<void> {
  // 等 AUTH-13 返回后再读行数据，避免读到上一次搜索的残留结果。
  const loaded = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/users' &&
      response.request().method() === 'GET' &&
      new URL(response.url()).searchParams.get('keyword') === keyword,
  );
  await page.getByPlaceholder('搜索姓名或手机号').fill(keyword);
  await page.getByRole('button', { name: '搜索' }).click();
  await loaded;
}

function adminUserRow(page: Page, name: string): Locator {
  return page.locator('.el-table__row').filter({ hasText: name });
}

function userRoleCheckbox(page: Page, label: string): Locator {
  return page.locator('.user-role-editor .el-checkbox').filter({ hasText: label });
}

function userRolesCell(row: Locator): Locator {
  return row.locator('.user-roles-cell');
}

function waitUserRolesSave(page: Page, userId: number) {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/b/users/${userId}/roles` &&
      response.request().method() === 'PUT',
  );
}

/**
 * 1001 在套件中登录次数接近 AUTH-15 上限；被限流时按服务端 retryAfterSeconds 等待后重试。
 * 返回是否发生过 429，供用例只忽略这条预期内的资源错误。
 */
async function loginAdminRetrying(page: Page, phone: string): Promise<boolean> {
  let rateLimited = false;
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/v1/b/auth/login' &&
        response.request().method() === 'POST',
    );
    await page.goto('/admin/login');
    await page.getByPlaceholder('请输入手机号').fill(phone);
    await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
    await page.getByRole('button', { name: '登录' }).click();
    const response = await responsePromise;
    if (response.status() !== 429 || attempt === 1) {
      await expect(page).toHaveURL(/\/admin\/matches$/);
      return rateLimited;
    }
    rateLimited = true;
    const body = (await response.json().catch(() => null)) as {
      data?: { retryAfterSeconds?: number };
    } | null;
    const retryAfter = Math.max(1, body?.data?.retryAfterSeconds ?? 60);
    await page.waitForTimeout((retryAfter + 1) * 1000);
  }
  return rateLimited;
}

test('B 端：B08 用户角色分配 → 分配 ADMIN → 撤回', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await expect(page.locator('.user-roles-panel')).toBeVisible();

  await searchAdminUsers(page, '13910000002');
  const row = adminUserRow(page, '测试后卫01');
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '分配角色' }).click();
  await expect(page.locator('.user-role-editor')).toBeVisible();

  try {
    // 上次失败可能残留 ADMIN：先复原，保证用例可重复。
    if (await userRoleCheckbox(page, '管理员').locator('input').isChecked()) {
      await userRoleCheckbox(page, '管理员').click();
      const normalized = waitUserRolesSave(page, 1002);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await normalized).ok()).toBe(true);
    }
    await expect(userRoleCheckbox(page, '正式球员').locator('input')).toBeChecked();
    await expect(userRoleCheckbox(page, '外挂球员').locator('input')).not.toBeChecked();

    await userRoleCheckbox(page, '管理员').click();
    const saved = waitUserRolesSave(page, 1002);
    await page.getByRole('button', { name: '保存用户角色' }).click();
    const response = await saved;
    expect(response.ok()).toBe(true);
    expect(response.request().headers()['idempotency-key']).toBeTruthy();
    expect((response.request().postDataJSON() as { roles: number[] }).roles).toHaveLength(2);
    await expect(page.getByText('用户角色已保存')).toBeVisible();
    await expect(userRolesCell(row)).toContainText('管理员');
    await expect(userRolesCell(row)).toContainText('正式球员');
  } finally {
    // 撤回 ADMIN 复原，避免断言失败后把 1002 留在管理员状态。
    await searchAdminUsers(page, '13910000002');
    const current = adminUserRow(page, '测试后卫01');
    await expect(current).toBeVisible();
    if ((await userRolesCell(current).innerText()).includes('管理员')) {
      await current.getByRole('button', { name: '分配角色' }).click();
      await userRoleCheckbox(page, '管理员').click();
      const restored = waitUserRolesSave(page, 1002);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await restored).ok()).toBe(true);
    }
  }

  const finalRow = adminUserRow(page, '测试后卫01');
  await expect(userRolesCell(finalRow)).not.toContainText('管理员');
  await expect(userRolesCell(finalRow)).toContainText('正式球员');

  expect(errors).toEqual([]);
});

test('B 端：B08 用户角色 —— 不能移除最后一个管理员', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  // 1001 是票面主角；先降级 1022，使 1001 成为唯一管理员。
  await loginAdminRetrying(page, '13910000001');

  await page.goto('/admin/roles');
  await expect(page.locator('.user-roles-panel')).toBeVisible();

  try {
    await searchAdminUsers(page, '13910000022');
    const secondary = adminUserRow(page, '测试管理员02');
    await expect(secondary).toBeVisible();
    await secondary.getByRole('button', { name: '分配角色' }).click();
    if (await userRoleCheckbox(page, '管理员').locator('input').isChecked()) {
      await userRoleCheckbox(page, '管理员').click();
      const demoted = waitUserRolesSave(page, 1022);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await demoted).ok()).toBe(true);
      await expect(userRolesCell(secondary)).not.toContainText('管理员');
    }

    // 以 AUTH-13 的新结果确认服务端已降级，再尝试移除 1001 的 ADMIN。
    await searchAdminUsers(page, '13910000022');
    await expect(userRolesCell(adminUserRow(page, '测试管理员02'))).not.toContainText('管理员');

    await searchAdminUsers(page, '13910000001');
    const primary = adminUserRow(page, '测试门将01');
    await expect(primary).toBeVisible();
    await primary.getByRole('button', { name: '分配角色' }).click();
    await expect(userRoleCheckbox(page, '管理员').locator('input')).toBeChecked();
    await userRoleCheckbox(page, '管理员').click();
    const rejected = waitUserRolesSave(page, 1001);
    await page.getByRole('button', { name: '保存用户角色' }).click();
    expect((await rejected).status()).toBe(409);
    await expect(page.getByText('不能移除最后一个管理员')).toBeVisible();
    await expect(userRolesCell(primary)).toContainText('管理员');
  } finally {
    // 无论断言成败都恢复 1022 的 ADMIN，避免污染 B07/B08 其他用例。
    await searchAdminUsers(page, '13910000022');
    const secondary = adminUserRow(page, '测试管理员02');
    await expect(secondary).toBeVisible();
    if (!(await userRolesCell(secondary).innerText()).includes('管理员')) {
      await secondary.getByRole('button', { name: '分配角色' }).click();
      await userRoleCheckbox(page, '管理员').click();
      const restored = waitUserRolesSave(page, 1022);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await restored).ok()).toBe(true);
    }
  }

  // 409 是预期的业务拒绝，浏览器会记录一条资源加载错误，其余 console error 仍需为零。
  expect(errors.filter((message) => !message.includes('status of 409'))).toEqual([]);
});

test('B 端：B08 用户角色 —— 外挂转正需到 B07', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await searchAdminUsers(page, '13910000016');
  const row = adminUserRow(page, '测试外挂01');
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '分配角色' }).click();
  await expect(userRoleCheckbox(page, '外挂球员').locator('input')).toBeChecked();

  await userRoleCheckbox(page, '正式球员').click();
  await expect(page.getByText('外挂转正请到 B07 球员页操作')).toBeVisible();
  await expect(userRoleCheckbox(page, '正式球员').locator('input')).not.toBeChecked();
  await expect(userRoleCheckbox(page, '外挂球员').locator('input')).toBeChecked();

  expect(errors).toEqual([]);
});

test('B 端：B08 用户角色卡片仅对 role:manage:any 渲染', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const userRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/b/users') {
      userRequests.push(request.url());
    }
  });
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['role:view:any'];
    body.data.sections = ['roles'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });

  await loginAdmin(page, '13910000022', /\/admin\/roles$/);
  await expect(page.locator('.role-item').first()).toBeVisible();
  await expect(page.locator('.user-roles-panel')).toHaveCount(0);
  expect(userRequests).toEqual([]);

  expect(errors).toEqual([]);
});

function settingsFormItem(page: Page, label: string): Locator {
  return page.locator('.settings-form .el-form-item').filter({ hasText: label });
}

function settingsNumberInput(page: Page, label: string): Locator {
  return settingsFormItem(page, label).locator('input');
}

function waitSettingsGet(page: Page): Promise<Response> {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/settings' &&
      response.request().method() === 'GET',
  );
}

async function fillSettingsNumber(page: Page, label: string, value: number): Promise<void> {
  const input = settingsNumberInput(page, label);
  await input.fill(String(value));
  await input.press('Enter');
  await input.blur();
  await expect(input).toHaveValue(String(value));
}

test('B 端：B09 修改报名上限并保存，刷新仍生效', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 1022 为管理端专用管理员，避开 1001 在整套用例中的 AUTH-15 限流。
  await loginAdmin(page, '13910000022');

  const loaded = waitSettingsGet(page);
  await page.goto('/admin/settings');
  await expect(page).toHaveURL(/\/admin\/settings$/);
  await expect(page.getByRole('heading', { level: 2, name: '系统设置' })).toBeVisible();
  await loaded;

  await fillSettingsNumber(page, '正式球员报名上限', 31);
  await fillSettingsNumber(page, '外挂球员报名上限', 6);

  const patched = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/settings' &&
      response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: '保存设置' }).click();
  expect((await patched).status()).toBe(200);
  await expect(page.getByText('设置已保存')).toBeVisible();

  const reloaded = waitSettingsGet(page);
  await page.reload();
  await reloaded;
  await expect(settingsNumberInput(page, '正式球员报名上限')).toHaveValue('31');
  await expect(settingsNumberInput(page, '外挂球员报名上限')).toHaveValue('6');

  expect(errors).toEqual([]);
});

test('B 端：B09 不再展示分组权重参数', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  const loaded = waitSettingsGet(page);
  await page.goto('/admin/settings');
  await expect(page.getByRole('heading', { level: 2, name: '系统设置' })).toBeVisible();
  await loaded;

  await expect(page.locator('.settings-form')).not.toContainText('分组手动权重');
  await expect(page.locator('.settings-form')).not.toContainText('分组自动权重');
  await expect(page.locator('.settings-form')).toContainText('分组直接使用球员星级');

  expect(errors).toEqual([]);
});

test('B 端：B10 操作日志筛选、展开与脱敏快照', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/audit');
  await expect(page).toHaveURL(/\/admin\/audit$/);
  await expect(page.getByRole('heading', { level: 2, name: '操作日志' })).toBeVisible();
  const auditHeaders = (await page.locator('.el-table__header th').allInnerTexts()).map((text) =>
    text.trim(),
  );
  expect(auditHeaders).toContain('创建时间');
  expect(auditHeaders).toContain('操作时间');

  // 未知动作与对象类型按原值展示（B10 fixtures 6003）；用 allow-create 直接筛出，
  // 避免 fixture 因分页或运行期日志累积而漂出第 1 页。
  const actionSelect = page.locator('.audit-filters .el-select').first();
  await actionSelect.click();
  await page.keyboard.type('E2E_UNKNOWN_ACTION');
  await page.keyboard.press('Enter');
  await page.keyboard.press('Escape');

  const unknownLoaded = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/b/audit-logs' &&
      url.searchParams.get('action') === 'E2E_UNKNOWN_ACTION'
    );
  });
  await page.getByRole('button', { name: '搜索' }).click();
  await unknownLoaded;
  await expect(
    page.locator('.el-table__row').filter({ hasText: 'E2E_UNKNOWN_ACTION' }),
  ).toBeVisible();
  await expect(page.locator('.el-table__row').filter({ hasText: 'UNKNOWN_TARGET' })).toBeVisible();

  const resetLoaded = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/b/audit-logs' && !url.searchParams.has('action');
  });
  await page.getByRole('button', { name: '重置' }).click();
  await resetLoaded;

  // 用仅存在于 fixture 的操作人 + 动作锁定 6001，避免被运行期 SETTINGS_UPDATE 干扰。
  await page.getByPlaceholder('姓名或用户 ID').fill('E2E审计员');
  await actionSelect.click();
  await page.locator('.el-select-dropdown__item').filter({ hasText: '修改系统设置' }).click();

  const filtered = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/b/audit-logs' &&
      url.searchParams.get('action') === 'SETTINGS_UPDATE' &&
      url.searchParams.get('actor') === 'E2E审计员'
    );
  });
  await page.getByRole('button', { name: '搜索' }).click();
  await filtered;

  const row = page.locator('.el-table__row').filter({ hasText: '修改系统设置' });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('E2E审计员');

  await row.locator('.el-table__expand-icon').click();
  await expect(page.getByText('变更前')).toBeVisible();
  await expect(page.getByText('变更后')).toBeVisible();
  // 后端脱敏：快照中只出现掩码手机号，原文不得泄露。
  await expect(page.getByText('139****0001').first()).toBeVisible();
  await expect(page.getByText('13900000001')).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B11 创建入队邀请、复制链接并撤销', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  // 1022 为管理端专用管理员；B11 位于套件末尾，已接近 AUTH-15 限流，按服务端提示重试。
  const rateLimited = await loginAdminRetrying(page, '13910000022');

  // 先记录创建前的列表总数，后面用它识别创建后的新列表响应。
  const initialList = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/invites' &&
      response.request().method() === 'GET',
  );
  await page.goto('/admin/invites');
  await expect(page).toHaveURL(/\/admin\/invites$/);
  await expect(page.getByRole('heading', { level: 2, name: '邀请管理' })).toBeVisible();
  const beforeTotal = ((await (await initialList).json()) as { data: { total: number } }).data
    .total;

  await page.getByRole('button', { name: '新建邀请' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '新建邀请' });
  await expect(dialog).toBeVisible();
  // 默认入队邀请、7 天、不限次数，直接提交即可。
  await expect(dialog.getByRole('radio', { name: '入队邀请' })).toBeChecked();

  const created = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/invites' &&
      response.request().method() === 'POST',
  );
  // 只认总数 +1 的列表响应，避免把页面初始加载的旧响应当作创建后的刷新。
  const refreshed = page.waitForResponse(async (response) => {
    if (
      new URL(response.url()).pathname !== '/api/v1/b/invites' ||
      response.request().method() !== 'GET'
    ) {
      return false;
    }
    const body = (await response.json().catch(() => null)) as {
      data?: { total?: number };
    } | null;
    return body?.data?.total === beforeTotal + 1;
  });
  await dialog.getByRole('button', { name: '创建邀请' }).click();
  const response = await created;
  expect(response.ok()).toBe(true);
  expect(response.request().headers()['idempotency-key']).toBeTruthy();
  expect((await refreshed).ok()).toBe(true);

  const resultDialog = page.locator('.el-dialog').filter({ hasText: '邀请已创建' });
  await expect(resultDialog).toBeVisible();
  const urlInput = resultDialog.locator('input');
  const inviteUrl = await urlInput.inputValue();
  expect(inviteUrl).toMatch(/\/invite\/[0-9a-f]{32}$/);

  await resultDialog.getByRole('button', { name: '复制链接' }).click();
  await expect(resultDialog.getByRole('button', { name: '已复制' })).toBeVisible();
  const clipboard = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboard).toBe(inviteUrl);

  await resultDialog.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(resultDialog).toBeHidden();

  // 列表按 id 倒序，新创建的邀请在第一行；列表行不提供复制。
  const firstRow = page.locator('.el-table__row').first();
  await expect(firstRow).toContainText('入队邀请');
  await expect(firstRow.locator('.el-tag')).toHaveText('有效');
  await expect(firstRow.getByRole('button', { name: '复制' })).toHaveCount(0);

  await firstRow.getByRole('button', { name: '撤销' }).click();
  const box = page.locator('.el-message-box');
  await expect(box).toBeVisible();
  await box.getByRole('button', { name: '撤销' }).click();
  await expect(page.getByText('邀请已撤销')).toBeVisible();
  await expect(firstRow.locator('.el-tag')).toHaveText('已撤销');
  await expect(firstRow.getByRole('button', { name: '撤销' })).toBeDisabled();

  // 仅当登录确实被限流重试时，忽略对应的 429 资源错误。
  expect(errors.filter((message) => !(rateLimited && message.includes('status of 429')))).toEqual(
    [],
  );
});

test('B 端：B11 比赛邀请未选比赛时阻止提交', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  const posts: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/b/invites' && request.method() === 'POST') {
      posts.push(request.url());
    }
  });
  const rateLimited = await loginAdminRetrying(page, '13910000022');

  await page.goto('/admin/invites');
  await page.getByRole('button', { name: '新建邀请' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '新建邀请' });
  const matchListLoaded = () =>
    page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/v1/c/matches' &&
        response.request().method() === 'GET',
    );

  // Element Plus 的 radio input 不可见，点 label 才能触发。
  const firstMatches = matchListLoaded();
  await dialog.locator('.el-radio').filter({ hasText: '比赛邀请' }).click();
  await expect(dialog.getByText('关联比赛')).toBeVisible();
  expect((await firstMatches).ok()).toBe(true);

  await dialog.getByRole('button', { name: '创建邀请' }).click();
  await expect(dialog.locator('.create-error')).toHaveText('请选择比赛');
  expect(posts).toEqual([]);

  // 切换类型后清空旧错误；再次切回比赛邀请会重新拉取比赛，不使用永久缓存。
  await dialog.locator('.el-radio').filter({ hasText: '入队邀请' }).click();
  await expect(dialog.locator('.create-error')).toHaveCount(0);
  const secondMatches = matchListLoaded();
  await dialog.locator('.el-radio').filter({ hasText: '比赛邀请' }).click();
  expect((await secondMatches).ok()).toBe(true);

  // 仅当登录确实被限流重试时，忽略对应的 429 资源错误。
  expect(errors.filter((message) => !(rateLimited && message.includes('status of 429')))).toEqual(
    [],
  );
});

test('无效交接票据回到登录页并提示', async ({ page }) => {
  await page.goto('/admin/#ticket=invalid');

  await expect(page).toHaveURL(/\/admin\/login\?redirect=/);
  await expect(page.getByText('自动登录已失效，请重新登录')).toBeVisible();
  await expect(page.getByPlaceholder('请输入手机号')).toBeVisible();
});
