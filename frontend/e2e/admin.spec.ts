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

test('B 端：B06 生成分组并展示 A/B', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/grouping');
  await expect(page).toHaveURL(/\/admin\/grouping$/);

  // 1001 场为已锁定队内赛，种子数据已生成 10 A / 9 B。
  await page.locator('.match-picker').click();
  await page
    .locator('.el-select-dropdown__item')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已锁定' })
    .click();

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('测试门将01').first()).toBeVisible();
  await expect(page.getByText('总分差')).toBeVisible();

  await page.getByRole('button', { name: /生成分组|重新生成/ }).click();
  await page
    .locator('.el-message-box')
    .getByRole('button', { name: /重新生成|确定/ })
    .click({ timeout: 2000 })
    .catch(() => undefined);

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('测试门将01').first()).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：只读球员可查看分组但不能生成', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page.locator('.admin-sidebar')).toBeVisible();

  await page.goto('/admin/grouping');
  await expect(page).toHaveURL(/\/admin\/grouping$/);
  await page.locator('.match-picker').click();
  await page
    .locator('.el-select-dropdown__item')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已锁定' })
    .click();

  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByRole('button', { name: /生成分组|重新生成/ })).toBeDisabled();

  expect(errors).toEqual([]);
});

test('B 端：B04 录入/编辑事件并保存比分', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/events');
  await expect(page).toHaveURL(/\/admin\/events$/);
  await expect(page.getByText('事件 / 比分')).toBeVisible();

  // 1004 场进行中，1002 已确认报名；重跑脚本会软删除上一轮事件。
  await page.locator('.match-picker').click();
  await page.locator('.el-select-dropdown__item').filter({ hasText: 'M06事件联调赛' }).click();
  await expect(page.getByText('事件推导')).toBeVisible();

  await page.getByRole('button', { name: '录入事件' }).click();
  const dialog = page.locator('.el-dialog');
  await expect(dialog.getByText('录入事件')).toBeVisible();
  await dialog.getByRole('combobox', { name: '球员', exact: true }).click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: '测试后卫01' })
    .first()
    .click();
  await dialog.locator('.el-form-item').filter({ hasText: '分钟' }).locator('input').fill('77');
  await dialog.getByRole('button', { name: '保存事件' }).click();

  const row = page
    .locator('.event-row')
    .filter({ hasText: '进球' })
    .filter({ hasText: '测试后卫01' })
    .filter({ hasText: "77'" })
    .first();
  await expect(row).toBeVisible();

  await row.getByRole('button', { name: '编辑' }).click();
  await dialog.locator('.el-form-item').filter({ hasText: '分钟' }).locator('input').fill('78');
  await dialog.getByRole('button', { name: '保存事件' }).click();
  await expect(
    page.locator('.event-row').filter({ hasText: '测试后卫01' }).filter({ hasText: "78'" }).first(),
  ).toBeVisible();

  // 保存一个与当前推导比分不同的存储比分，确保断言真的依赖 PATCH 成功。
  const derivedText = await page.locator('.score-meta dd').nth(1).innerText();
  const [derivedHome, derivedAway] = derivedText.split(':').map((part) => Number(part.trim()));
  const newHome = (derivedHome + 1) % 256;
  const newAway = (derivedAway + 1) % 256;
  const scoreInputs = page.locator('.score-row input');
  await scoreInputs.nth(0).fill(String(newHome));
  await scoreInputs.nth(1).fill(String(newAway));
  await page.getByRole('button', { name: '保存比分' }).click();
  await expect(page.locator('.score-meta dd').first()).toHaveText(`${newHome} : ${newAway}`);

  // 存储比分与事件推导不一致时只提示不阻断。
  await expect(page.locator('.hint--warning').first()).toContainText('不一致');

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
