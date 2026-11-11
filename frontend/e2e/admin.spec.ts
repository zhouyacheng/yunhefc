import { expect, test } from '@playwright/test';

import { collectConsoleErrors } from './support';

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

  await page.goto('/admin/registrations');
  await expect(page).toHaveURL(/\/admin\/registrations$/);
  await expect(page.getByText('正式球员').first()).toBeVisible();

  // 1001 场为“橙队VS兰队 · 已锁定”，含 19 名确认报名球员。
  await page.locator('.match-picker').click();
  await page
    .locator('.el-select-dropdown__item')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已锁定' })
    .click();

  const row = page.locator('.roster-row').filter({ hasText: '测试门将01' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '正常' }).click();
  await expect(row.locator('.attendance-state')).toContainText('正常');
  await expect(row.locator('.attendance-state')).toContainText('管理员');

  expect(errors).toEqual([]);
});

test('B 端：侧栏按 sections 过滤，直访无权限分区显示 403', async ({ page }) => {
  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/\/admin\/dashboard$/);
  await expect(page.getByRole('menuitem', { name: '报名/考勤' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '比赛管理' })).toHaveCount(0);

  await page.goto('/admin/matches');
  await expect(page).toHaveURL(/\/admin\/forbidden\?section=matches$/);
  await expect(page.getByText('403 无权限访问')).toBeVisible();
});
