import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

test('C07：访客看到入队邀请并带邀请上下文去登录/注册', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/invite/B3-INVITE-TEAM');
  await expect(page.getByRole('heading', { name: '邀请' })).toBeVisible();

  const card = page.getByTestId('invite-team');
  await expect(card).toContainText('入队邀请');
  await expect(card).toContainText('云河FC');
  await expect(card).toContainText('邀请人 测试后卫01');
  await expect(card).toContainText('剩余次数：不限');
  await expect(page.getByRole('button', { name: '加入球队' })).toHaveCount(0);

  await page.getByRole('button', { name: '注册', exact: true }).click();
  await expect(page).toHaveURL(/\/login\?/);
  const registerUrl = new URL(page.url());
  expect(registerUrl.searchParams.get('mode')).toBe('register');
  expect(registerUrl.searchParams.get('redirect')).toBe('/invite/B3-INVITE-TEAM');
  expect(registerUrl.searchParams.get('inviteToken')).toBe('B3-INVITE-TEAM');

  await page.goto('/invite/B3-INVITE-TEAM');
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/login\?/);
  expect(new URL(page.url()).searchParams.get('redirect')).toBe('/invite/B3-INVITE-TEAM');

  expect(errors).toEqual([]);
});

test('C07：球员接受比赛邀请后进入比赛详情', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  // 1002/1001 在共享套件内登录次数接近每手机号 10 次/分钟限流；
  // 改用 1021（待审核）验证比赛邀请对 PENDING 同样放行，且不改变账号状态。
  await loginAs(page, '13910000021', 'Passw0rd!');
  await page.goto('/invite/B3-INVITE-MATCH');

  const card = page.getByTestId('invite-match');
  await expect(card).toContainText('比赛邀请');
  await expect(card).toContainText('B3首页联调赛');
  await expect(card).toContainText('地点：聚龙足球场');
  await expect(card).toContainText('名额：');

  await page.getByRole('button', { name: '去比赛' }).click();
  await expect(page).toHaveURL(/\/matches\/1005$/);

  expect(errors).toEqual([]);
});

test('C07：不存在的邀请进入 404', async ({ page }) => {
  // 真实 404 资源会留下浏览器网络日志，本用例只断言页面结果。
  await page.goto('/invite/B3-INVITE-MISSING');
  await expect(page).toHaveURL(/\/404$/);
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();
});

test('C07：过期、撤销与用尽的邀请显示失效说明', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  for (const [token, reason] of [
    ['B3-INVITE-EXPIRED', '该邀请已过期'],
    ['B3-INVITE-REVOKED', '该邀请已被撤销'],
    ['B3-INVITE-EXHAUSTED', '该邀请的使用次数已用完'],
  ] as const) {
    await page.goto(`/invite/${token}`);
    await expect(page.getByTestId('invite-invalid')).toContainText(reason);
    await expect(page.getByRole('button', { name: '加入球队' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: '登录', exact: true })).toHaveCount(0);
  }

  await page.getByRole('button', { name: '返回首页' }).click();
  await expect(page).toHaveURL(/\/$/);

  expect(errors).toEqual([]);
});
