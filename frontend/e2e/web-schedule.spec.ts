import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C02 赛程：状态筛选写入 URL，刷新后恢复', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await expect(page.locator('.match-card')).toHaveCount(5);

  await page.locator('select[name="status"]').selectOption('4');
  await page.getByRole('button', { name: '搜索', exact: true }).click();

  await expect(page).toHaveURL(/status=4/);
  await expect(page.locator('.match-card')).toHaveCount(2);
  await expect(page.locator('.match-card-link').first()).toHaveAttribute('href', '/matches/1004');

  await page.reload();
  await expect(page.locator('select[name="status"]')).toHaveValue('4');
  await expect(page.locator('.match-card')).toHaveCount(2);

  expect(errors).toEqual([]);
});

test('C02 赛程：类型 0 作为有效筛选值', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/matches');
  await page.locator('select[name="type"]').selectOption('0');
  await page.getByRole('button', { name: '搜索', exact: true }).click();

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
  await page.locator('input[name="venue"]').fill('不存在球场');
  await page.getByRole('button', { name: '搜索', exact: true }).click();

  await expect(page).toHaveURL(/venue=/);
  await expect(page.getByText('暂无符合条件的比赛')).toBeVisible();
  await expect(page.locator('.match-card')).toHaveCount(0);

  await page.getByRole('button', { name: '重置筛选', exact: true }).click();
  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('.match-card')).toHaveCount(5);

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

    await page.locator('select[name="status"]').selectOption('4');
    await page.getByRole('button', { name: '搜索', exact: true }).click();

    await expect(page).toHaveURL(/status=4/);
    await expect(page.locator('.match-card')).toHaveCount(2);

    expect(errors).toEqual([]);
  });
});
