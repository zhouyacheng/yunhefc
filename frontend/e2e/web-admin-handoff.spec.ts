import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C 端管理员点击入口免登录进入 B 端', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page, '13910000001');
  const entry = page.getByTestId('admin-entry');
  await expect(entry).toBeVisible();

  await entry.click();

  await expect(page).toHaveURL(/localhost:5274\/admin\/matches$/);
  await expect(page.locator('.admin-shell')).toBeVisible();
  await expect(page.getByPlaceholder('请输入手机号')).toHaveCount(0);
  await expect(page).not.toHaveURL(/ticket=/);
  expect(errors).toEqual([]);
});

test('具备后台读权限的球员也能免登录进入 B 端', async ({ page }) => {
  await loginAs(page, '13910000002');
  await expect(page.getByTestId('admin-entry')).toBeVisible();

  await page.getByTestId('admin-entry').click();

  await expect(page).toHaveURL(/localhost:5274\/admin\/registrations$/);
  await expect(page.locator('.admin-shell')).toBeVisible();
});

test('无后台权限的账号不显示管理后台入口', async ({ page }) => {
  await loginAs(page, '13910000016');
  await expect(page.getByTestId('admin-entry')).toHaveCount(0);
});

test('待审核账号不显示管理后台入口', async ({ page }) => {
  await loginAs(page, '13910000021');
  await expect(page.getByTestId('admin-entry')).toHaveCount(0);
});

test('直接访问 /admin/ 不会复用 C 端会话', async ({ page }) => {
  await loginAs(page, '13910000001');

  await page.goto('http://localhost:5274/admin/');

  await expect(page).toHaveURL(/localhost:5274\/admin\/login\?redirect=/);
  await expect(page.getByPlaceholder('请输入手机号')).toBeVisible();
});
