import { expect, test } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

/**
 * 1021 接受入队邀请会转正并加入球队名单，影响依赖待审核账号与名单人数的用例。
 * 文件名排在 web.spec.ts 之后，保证本用例最后执行；每次运行前需重放 test-data.sql 复位 1021。
 */
test('C07：待审核账号接受入队邀请成为正式成员', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page, '13910000021', 'Passw0rd!');
  await page.goto('/invite/B3-INVITE-TEAM');

  await page.getByRole('button', { name: '加入球队' }).click();
  await expect(page.getByTestId('invite-member')).toContainText('你已是球队成员，无需重复加入');
  await expect(page.getByRole('button', { name: '加入球队' })).toHaveCount(0);

  expect(errors).toEqual([]);
});
