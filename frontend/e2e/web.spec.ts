import { expect, test } from '@playwright/test';

import { collectConsoleErrors } from './support';

test('C 端：登录后看到真实赛程', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('.match-card')).toHaveCount(2);
  await expect(page.getByText('已锁定').first()).toBeVisible();

  expect(errors).toEqual([]);
});

test('C 端：未登录访问受保护页跳登录并带 redirect', async ({ page }) => {
  await page.goto('/account');

  await expect(page).toHaveURL(/\/login\?redirect=/);
  expect(page.url()).toContain('/account');
});
