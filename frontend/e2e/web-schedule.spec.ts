import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

/** 清空预填月份，让只测其他条件的用例回到“全部时间”。 */
async function clearMonth(page: Page): Promise<void> {
  await page.getByRole('button', { name: '清除月份', exact: true }).click();
}

test('C02 赛程：状态筛选写入 URL，刷新后恢复', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await expect(page.locator('.match-card')).toHaveCount(5);
  await expect(page.locator('.match-card__meta').first()).toContainText(
    /\d{4}\/\d{2}\/\d{2} \d{2}:\d{2} ~ (?:\d{2}\/\d{2} )?\d{2}:\d{2}/,
  );
  await expect(page.getByRole('button', { name: '搜索', exact: true })).toHaveCount(0);

  await clearMonth(page);
  await page.locator('select[name="status"]').selectOption('4');

  await expect(page).toHaveURL(/status=4/);
  await expect(page).toHaveURL(/month=all/);
  await expect(page.locator('.match-card')).toHaveCount(2);
  await expect(page.locator('.match-card-link').first()).toHaveAttribute('href', '/matches/1004');

  await page.reload();
  await expect(page.locator('select[name="status"]')).toHaveValue('4');
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('全部月份');
  await expect(page.locator('.match-card')).toHaveCount(2);

  expect(errors).toEqual([]);
});

test('C02 赛程：类型 0 作为有效筛选值', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await clearMonth(page);
  await page.locator('select[name="type"]').selectOption('0');

  await expect(page).toHaveURL(/type=0/);
  await expect(page.locator('.match-card')).toHaveCount(1);
  await expect(page.locator('.match-card-link')).toHaveAttribute('href', '/matches/1004');

  expect(errors).toEqual([]);
});

test('C02 赛程：进详情再后退恢复筛选', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  // 详情页会对访客探测 /c/me 并产生预期 401；登录后验证纯筛选恢复路径。
  await loginAs(page);
  await page.goto('/matches?status=4');
  await expect(page.locator('.match-card')).toHaveCount(2);

  await page.locator('.match-card-link').first().click();
  await expect(page).toHaveURL(/\/matches\/\d+$/);

  await page.goBack();
  await expect(page).toHaveURL(/status=4/);
  await expect(page.locator('select[name="status"]')).toHaveValue('4');
  await expect(page.locator('.match-card')).toHaveCount(2);

  expect(errors).toEqual([]);
});

test('C02 赛程：重置清空 URL 与筛选', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches?status=4&venue=聚龙');
  await expect(page.locator('.match-card')).toHaveCount(2);

  await page.getByRole('button', { name: '重置', exact: true }).click();

  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('.match-card')).toHaveCount(5);
  await expect(page.locator('select[name="status"]')).toHaveValue('');
  await expect(page.locator('input[name="venue"]')).toHaveValue('');
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText(/^\d{4}-\d{2}$/);

  // 重置只更新表单草稿，不应在防抖后把预填月份写回 URL。
  await page.waitForTimeout(500);
  await expect(page).toHaveURL(/\/matches$/);

  expect(errors).toEqual([]);
});

test('C02 赛程：URL 已是默认值时重置也清空表单草稿', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await page.locator('input[name="venue"]').fill('聚龙');
  await page.getByRole('button', { name: '重置', exact: true }).click();

  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('input[name="venue"]')).toHaveValue('');
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C02 赛程：地点无匹配显示空态与重置入口', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await clearMonth(page);
  await page.locator('input[name="venue"]').fill('不存在球场');

  await expect(page).toHaveURL(/venue=/);
  await expect(page.getByText('暂无符合条件的比赛')).toBeVisible();
  await expect(page.locator('.match-card')).toHaveCount(0);

  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C02 赛程：默认预填当月但不自动筛选', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');

  // 等待超过自动搜索防抖窗口，确认首屏预填月份不会被自动应用。
  await page.waitForTimeout(500);
  await expect(page).not.toHaveURL(/month=|startDate=|endDate=/);
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText(/^\d{4}-\d{2}$/);
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C02 赛程：清空月份查看全部时间并可刷新恢复', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await clearMonth(page);

  await expect(page).toHaveURL(/month=all/);
  await expect(page.locator('.match-card')).toHaveCount(5);

  await page.reload();
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('全部月份');
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C02 赛程：月份筛选生效，重置后重新预填当月', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches?month=2020-01');
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('2020-01');
  await expect(page.getByText('暂无符合条件的比赛')).toBeVisible();

  await page.getByRole('button', { name: '重置', exact: true }).click();

  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText(/^\d{4}-\d{2}$/);
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C02 赛程：自定义时间从 URL 恢复，选月份后清空范围', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches?startDate=2026-11-01&endDate=2026-11-01');
  await expect(page.getByRole('button', { name: '选择自定义时间' })).toHaveText(
    '2026-11-01 至 2026-11-01',
  );
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('全部月份');

  await page.getByRole('button', { name: '选择月份' }).click();
  await page.getByRole('button', { name: '确认', exact: true }).click();
  await expect(page.getByRole('button', { name: '选择自定义时间' })).toHaveText('选择日期范围');

  await expect(page).toHaveURL(/month=\d{4}-\d{2}/);
  await expect(page).not.toHaveURL(/startDate|endDate/);

  expect(errors).toEqual([]);
});

test('C02 赛程：Calendar 选择同一天范围并生效', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await page.getByRole('button', { name: '选择自定义时间' }).click();
  await expect(page.locator('.van-calendar')).toBeVisible();

  const days = page.locator('.van-calendar__day:not(.van-calendar__day--disabled)');
  await days.first().click();
  await days.first().click();
  await page.locator('.van-calendar__confirm').click();

  await expect(page.getByRole('button', { name: '选择自定义时间' })).toHaveText(/至/);
  await expect(page.getByRole('button', { name: '选择月份' })).toHaveText('全部月份');

  await expect(page).toHaveURL(/startDate=\d{4}-\d{2}-\d{2}/);
  await expect(page).toHaveURL(/endDate=\d{4}-\d{2}-\d{2}/);
  await expect(page).not.toHaveURL(/month=/);

  expect(errors).toEqual([]);
});

test('C02 赛程：地点连续输入在防抖窗口内只请求一次', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await expect(page.locator('.match-card')).toHaveCount(5);

  const requests: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/api/v1/c/matches?')) {
      requests.push(request.url());
    }
  });

  await page.locator('input[name="venue"]').pressSequentially('聚龙', { delay: 80 });
  await expect(page).toHaveURL(/venue=/);
  await page.waitForTimeout(500);

  expect(requests).toHaveLength(1);

  expect(errors).toEqual([]);
});

test('C02 赛程：地点回车立即生效', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await expect(page.locator('.match-card')).toHaveCount(5);

  const venue = page.locator('input[name="venue"]');
  await venue.fill('聚龙');
  await venue.press('Enter');

  await expect(page).toHaveURL(/venue=/);

  expect(errors).toEqual([]);
});

test('C02 赛程：改每页条数回到第 1 页', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches?page=2');
  await expect(page.locator('.schedule-empty')).toBeVisible();

  await page.locator('select[name="pageSize"]').selectOption('20');

  await expect(page).not.toHaveURL(/[?&]page=/);
  await expect(page.locator('.match-card')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test.describe('C02 赛程移动端', () => {
  test.use({ viewport: { width: 390, height: 844 } });

  test('筛选使用底部抽屉', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/matches');
    await expect(page.locator('.match-card')).toHaveCount(5);
    await expect(page.locator('.schedule-filters')).toHaveCount(0);

    await page.locator('.schedule-filters__trigger').click();
    await expect(page.locator('.schedule-filters__drawer')).toBeVisible();

    await clearMonth(page);
    await page.locator('select[name="status"]').selectOption('4');

    await expect(page).toHaveURL(/status=4/);
    await expect(page.locator('.match-card')).toHaveCount(2);
    // 条件变更即生效，抽屉保持打开，由遮罩关闭。
    await expect(page.locator('.schedule-filters__drawer')).toBeVisible();

    // 抽屉占住底部，点遮罩左上角关闭。
    await page.locator('.van-overlay').click({ position: { x: 10, y: 10 } });
    await expect(page.locator('.schedule-filters__drawer')).toBeHidden();

    expect(errors).toEqual([]);
  });
});
