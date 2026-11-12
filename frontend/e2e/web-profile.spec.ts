import { Buffer } from 'node:buffer';

import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

async function openProfile(page: Page): Promise<void> {
  await loginAs(page);
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/profile$/);
  await expect(page.getByRole('heading', { name: '我的', exact: true })).toBeVisible();
}

test('C06 个人中心：资料编辑、通知与我的报名', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await openProfile(page);

  const identity = page.locator('.profile-identity');
  await expect(identity).toContainText('测试后卫01');
  await expect(identity).toContainText('139****0002');

  await page.getByRole('button', { name: '编辑资料' }).click();
  await expect(page.locator('.profile-edit')).toBeVisible();

  // 头像本地校验失败路径（无需真实 OSS）。
  await page.locator('input[type=file]').setInputFiles({
    name: 'avatar.gif',
    mimeType: 'image/gif',
    buffer: Buffer.from('GIF89a'),
  });
  await expect(page.getByText('仅支持 JPG/PNG/WebP')).toBeVisible();

  // PLR-01 补齐 region/secondaryPositions 后回填。
  await expect(page.getByPlaceholder('请输入姓名')).toHaveValue('测试后卫01');
  await expect(page.getByPlaceholder('如：上海')).toHaveValue('上海');
  await page.getByPlaceholder('请输入姓名').fill('测试后卫01');
  await page.getByPlaceholder('如：上海').fill('上海');
  await page.getByLabel('主位置').selectOption('1');

  const saved = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/me/player' &&
      response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: '保存', exact: true }).click();
  expect((await saved).ok()).toBe(true);

  await expect(page.getByText('资料已保存')).toBeVisible();
  await expect(page.locator('.profile-edit')).toBeHidden();
  await expect(identity).toContainText('测试后卫01');

  const unreadItems = page.locator('.notification-item--unread');
  await expect(unreadItems.first()).toBeVisible();

  // USER 类通知只标记已读，不跳转。
  const userNotification = unreadItems.filter({ hasNotText: '查看比赛' }).first();
  await expect(userNotification).toBeVisible();
  const notificationId = await userNotification.getAttribute('data-id');
  await userNotification.click();
  await expect(page.locator(`.notification-item[data-id="${notificationId}"]`)).not.toHaveClass(
    /notification-item--unread/,
  );
  await expect(page).toHaveURL(/\/profile$/);

  // 拦截 NTF-04，保留种子未读供后续角标用例；UI 仍按成功链路处理。
  await page.route('**/api/v1/c/notifications/read-all', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 0,
        message: 'success',
        data: { updated: 6 },
        requestId: 'e2e-read-all',
      }),
    }),
  );
  await page.getByRole('button', { name: '全部已读' }).click();
  await expect(page.getByText('已全部标记为已读')).toBeVisible();
  await expect(page.locator('.notification-item--unread')).toHaveCount(0);

  const badge = page.locator('.van-tabbar-item').filter({ hasText: '我的' }).locator('.van-badge');
  await expect(badge).toHaveCount(0);

  // MATCH 类通知进入比赛详情（4007 → refId 1003）。
  await page.locator('.notification-item[data-id="4007"]').click();
  await expect(page).toHaveURL(/\/matches\/1003$/);
  await expect(page.locator('.match-hero')).toBeVisible();

  // 我的报名：默认进行中，切换历史独立分页。
  await page.goto('/profile');
  const section = page.locator('.my-registrations');
  await expect(section.getByRole('tab', { name: '进行中' })).toHaveAttribute(
    'aria-selected',
    'true',
  );
  await expect(section.getByText('M05签到联调赛')).toBeVisible();

  await section.getByRole('tab', { name: '历史' }).click();
  await expect(section.getByRole('tab', { name: '历史' })).toHaveAttribute('aria-selected', 'true');
  await expect(section.getByText('M05签到联调赛')).toHaveCount(0);
  await expect(section.getByText('M06事件联调赛')).toBeVisible();

  expect(errors).toEqual([]);
});

test('C06 待审核：显示横幅且不请求我的报名', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const registrationRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/c/me/registrations') {
      registrationRequests.push(request.url());
    }
  });

  await loginAs(page, '13910000021');
  await page.goto('/profile');
  await expect(page).toHaveURL(/\/profile$/);

  await expect(page.getByText('账号待审核')).toBeVisible();
  await expect(page.getByRole('button', { name: '编辑资料' })).toHaveCount(0);
  await expect(page.getByText('审核通过后可查看我的报名')).toBeVisible();
  await expect(page.locator('.notifications')).toBeVisible();

  expect(registrationRequests).toEqual([]);
  expect(errors).toEqual([]);
});
