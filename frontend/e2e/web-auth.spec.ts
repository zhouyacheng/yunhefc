import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** 每次运行使用未注册过的手机号，避免重复执行命中 40907。 */
function freshPhone(): string {
  const suffix = String(Date.now()).slice(-8);
  return `139${suffix}`;
}

async function fillLogin(page: Page, phone: string, password: string) {
  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: '登录' }).click();
}

test('C05：待审核账号登录后进入赛程并可访问个人中心', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await fillLogin(page, '13910000021', 'Passw0rd!');
  await expect(page).toHaveURL(/\/matches$/);

  await page.goto('/profile');
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: '我的', exact: true })).toBeVisible();

  expect(errors).toEqual([]);
});

test('C05：模式写入 URL，可直达注册与找回密码', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login?mode=register');
  await expect(page.getByRole('tab', { name: '注册' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByPlaceholder('6 位数字验证码')).toBeVisible();

  await page.getByRole('tab', { name: '找回密码' }).click();
  await expect(page).toHaveURL(/mode=reset/);
  await expect(page.getByPlaceholder('请输入注册手机号')).toBeVisible();
  await expect(page.getByPlaceholder('6 位数字验证码')).toBeVisible();

  await page.getByRole('tab', { name: '登录' }).click();
  await expect(page).toHaveURL(/mode=login/);
  await expect(page.getByPlaceholder('请输入密码')).toBeVisible();

  expect(errors).toEqual([]);
});

test('C05：注册新手机号显示待审核并落在首页', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const phone = freshPhone();

  await page.goto('/login?mode=register');
  await page.getByPlaceholder('请输入手机号').fill(phone);
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText(/开发环境验证码：123456/)).toBeVisible();
  await page.getByPlaceholder('6 位数字验证码').fill('123456');
  await page.getByPlaceholder('8–64 位，含字母和数字').fill('Passw0rd!');
  await page.getByPlaceholder('请输入姓名').fill('测试新队员');
  await page.getByPlaceholder('请选择主位置').click();
  await page.getByText('后卫', { exact: true }).click();
  await page.getByRole('button', { name: '注册' }).click();

  await expect(page.getByText('账号待审核，审核通过后即可参与比赛')).toBeVisible();
  await expect(page).toHaveURL(/\/$/);

  expect(errors).toEqual([]);
});

test('C05：找回密码成功提示并切回登录', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login?mode=reset');
  await page.getByPlaceholder('请输入注册手机号').fill('13910000021');
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText(/开发环境验证码：123456/)).toBeVisible();
  await page.getByPlaceholder('6 位数字验证码').fill('123456');
  await page.getByPlaceholder('8–64 位，含字母和数字').fill('Passw0rd!');
  await page.getByRole('button', { name: '重置密码' }).click();

  await expect(page.getByText('密码已重置，请使用新密码登录')).toBeVisible();
  await expect(page.getByRole('tab', { name: '登录' })).toHaveAttribute('aria-selected', 'true');

  expect(errors).toEqual([]);
});

test('C05：错误密码与重复手机号使用可读文案', async ({ page }) => {
  await fillLogin(page, '13910000021', 'WrongPass1');
  await expect(page.getByText('手机号或密码错误')).toBeVisible();

  await page.goto('/login?mode=register');
  await page.getByPlaceholder('请输入手机号').fill('13910000021');
  await page.getByRole('button', { name: '获取验证码' }).click();
  await expect(page.getByText(/开发环境验证码：123456/)).toBeVisible();
  await page.getByPlaceholder('6 位数字验证码').fill('123456');
  await page.getByPlaceholder('8–64 位，含字母和数字').fill('Passw0rd!');
  await page.getByPlaceholder('请输入姓名').fill('重复注册');
  await page.getByPlaceholder('请选择主位置').click();
  await page.getByText('门将', { exact: true }).click();
  await page.getByRole('button', { name: '注册' }).click();

  await expect(page.getByText('该手机号已注册，请直接登录')).toBeVisible();
});

test('C05：邀请上下文展示与失效降级提示', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login?mode=register&inviteToken=B3-INVITE-TEAM');
  const context = page.getByTestId('invite-context');
  await expect(context).toContainText('入队邀请');
  await expect(context).toContainText('云河FC');
  await expect(context).toContainText('邀请人 测试后卫01');

  await page.goto('/login?mode=register&inviteToken=B3-INVITE-EXPIRED');
  await expect(page.getByTestId('invite-context')).toContainText('该邀请已失效');
  await expect(page.getByRole('button', { name: '注册' })).toBeVisible();

  expect(errors).toEqual([]);
});
