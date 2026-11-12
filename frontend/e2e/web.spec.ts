import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C 端：登录后看到真实赛程', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/\/matches$/);
  await expect(page.locator('.match-card')).toHaveCount(5);
  await expect(page.locator('.match-card').getByText('已锁定').first()).toBeVisible();

  expect(errors).toEqual([]);
});

test('C 端：登录后进入比赛详情并签到', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  await expect(page).toHaveURL(/\/matches$/);
  // test-data.sql 的 1003 场开赛时间滚动到 2 小时后，保证处于签到窗口内。
  await page.locator('.match-card-link[href="/matches/1003"]').click();

  await expect(page).toHaveURL(/\/matches\/\d+$/);
  const checkInButton = page.getByRole('button', { name: '签到' });
  await expect(checkInButton).toBeEnabled();
  await checkInButton.click();

  await expect(page.locator('.attendance-status')).toContainText('正常');
  await expect(page.locator('.attendance-status')).toContainText('自助签到');
  await expect(page.getByRole('button', { name: '签到' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C 端：比赛详情分组 Tab 只读展示', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/matches$/);

  await page.goto('/matches/1001');
  await expect(page).toHaveURL(/\/matches\/1001$/);
  await page.getByRole('button', { name: '分组' }).click();

  await expect(page.locator('.grouping-column')).toHaveCount(2);
  await expect(page.getByText('测试门将01').first()).toBeVisible();
  await expect(page.getByText('总分差')).toBeVisible();
  await expect(page.getByRole('button', { name: /生成分组|保存分组/ })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C 端：录入进球后时间线与推导比分更新', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/matches$/);

  // test-data.sql 的 1004 场处于进行中，1002 已确认报名。
  await page.locator('.match-card-link[href="/matches/1004"]').click();
  await expect(page).toHaveURL(/\/matches\/1004$/);

  await page.getByRole('button', { name: '事件' }).click();
  await expect(page.getByText('还没有事件记录')).toBeVisible();
  await page.getByRole('button', { name: '录入事件' }).click();

  await page.getByPlaceholder('1–120').fill('23');
  await page.getByRole('button', { name: '保存事件' }).click();

  await expect(page.locator('.event-card').first()).toContainText('进球');
  await expect(page.locator('.event-card').first()).toContainText('测试后卫01');
  await expect(page.locator('.match-hero__teams strong')).toHaveText('1 : 0');

  expect(errors).toEqual([]);
});

test('C 端：未登录访问受保护页跳登录并带 redirect', async ({ page }) => {
  await page.goto('/account');

  await expect(page).toHaveURL(/\/login\?redirect=/);
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/profile');
});

test('C 端：已登录访问 /account 重定向到 /profile', async ({ page }) => {
  await loginAs(page);

  await page.goto('/account');

  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: '我的' })).toBeVisible();
});
