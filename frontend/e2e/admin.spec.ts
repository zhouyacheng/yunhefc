import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** B06 保存可能依次弹出“调整已保存分组”和“保存分组”确认。 */
async function confirmGroupingDialogs(page: Page): Promise<void> {
  for (let attempt = 0; attempt < 3; attempt += 1) {
    const box = page.locator('.el-message-box');
    const visible = await box
      .waitFor({ state: 'visible', timeout: 3000 })
      .then(() => true)
      .catch(() => false);
    if (!visible) {
      return;
    }
    // 弹窗关闭动画会让按钮瞬时脱离 DOM，强制点击避免 Playwright 重试到超时。
    await box
      .getByRole('button', { name: /继续调整|仍然保存|确定/ })
      .first()
      .click({ force: true });
    await box.waitFor({ state: 'hidden' }).catch(() => undefined);
  }
}

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

test('B 端：B06 调整 → 保存 → 清除 → 重新生成', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/grouping');
  await page.locator('.match-picker').click();
  await page
    .locator('.el-select-dropdown__item')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已锁定' })
    .click();

  // 从空分组开始，保证后续断言不依赖上一轮遗留状态。
  if ((await page.getByRole('button', { name: '清除分组' }).count()) > 0) {
    await page.getByRole('button', { name: '清除分组' }).click();
    await page.locator('.el-message-box').getByRole('button', { name: '清除' }).click();
  }
  await expect(page.getByText('尚未生成分组')).toBeVisible();
  await page.locator('.toolbar').getByRole('button', { name: '生成分组' }).click();
  await expect(page.locator('.group-admin-column')).toHaveCount(2);

  const firstTeamColumn = page.locator('.group-admin-column').nth(0);
  const secondTeamColumn = page.locator('.group-admin-column').nth(1);
  const player = firstTeamColumn.locator('.group-player').first();
  const playerName = await player.locator('strong').innerText();
  await player.click();
  await page.getByRole('button', { name: '移到 B 队' }).click();
  await expect(secondTeamColumn.getByText(playerName).first()).toBeVisible();
  await expect(firstTeamColumn.getByText(playerName)).toHaveCount(0);

  await page.getByRole('button', { name: '保存分组' }).click();
  await confirmGroupingDialogs(page);
  await expect(page.locator('.toolbar .el-tag').filter({ hasText: '已保存' })).toBeVisible();

  await page.getByRole('button', { name: '清除分组' }).click();
  await page.locator('.el-message-box').getByRole('button', { name: '清除' }).click();
  await expect(page.getByText('尚未生成分组')).toBeVisible();

  await page.locator('.toolbar').getByRole('button', { name: '生成分组' }).click();
  await expect(page.locator('.group-admin-column')).toHaveCount(2);
  await expect(page.getByText('总分差')).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B05 阵型模板复制、重命名、删除', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/lineup');
  await expect(page).toHaveURL(/\/admin\/lineup$/);
  await expect(page.locator('.template-card--preset')).toHaveCount(8);

  const preset = page.locator('.template-card--preset').first();
  await expect(preset.getByRole('button', { name: '重命名' })).toBeDisabled();
  await expect(preset.getByRole('button', { name: '删除' })).toBeDisabled();
  await expect(preset.getByText('预置阵型只读，可复制后修改。')).toBeVisible();

  const stamp = Date.now();
  const createdName = `测试阵型${stamp}`;
  const renamedName = `测试阵型改${stamp}`;

  await preset.getByRole('button', { name: '复制并新建' }).click();
  const copyDialog = page.locator('.el-dialog').filter({ hasText: '复制预置阵型' });
  await copyDialog.getByPlaceholder('模板名称').fill(createdName);
  await copyDialog.getByRole('button', { name: '创建模板' }).click();
  await expect(
    page.locator('.template-card--custom').filter({ hasText: createdName }),
  ).toBeVisible();

  await page
    .locator('.template-card--custom')
    .filter({ hasText: createdName })
    .getByRole('button', { name: '重命名' })
    .click();
  const renameDialog = page.locator('.el-dialog').filter({ hasText: '重命名自定义模板' });
  await renameDialog.getByPlaceholder('新的模板名称').fill(renamedName);
  await renameDialog.getByRole('button', { name: '保存名称' }).click();
  await expect(
    page.locator('.template-card--custom').filter({ hasText: renamedName }),
  ).toBeVisible();

  await page
    .locator('.template-card--custom')
    .filter({ hasText: renamedName })
    .getByRole('button', { name: '删除' })
    .click();
  await page.locator('.el-message-box').getByRole('button', { name: '删除' }).click();
  await expect(page.locator('.template-card--custom').filter({ hasText: renamedName })).toHaveCount(
    0,
  );

  expect(errors).toEqual([]);
});

test('B 端：B05 选阵型并排满 8 人首发后保存，C03 只读查看', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000001');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/admin\/matches$/);

  await page.goto('/admin/lineup');
  await expect(page).toHaveURL(/\/admin\/lineup$/);
  await page.locator('.match-picker').click();
  await page
    .locator('.el-select-dropdown__item')
    .filter({ hasText: '橙队VS兰队' })
    .filter({ hasText: '已锁定' })
    .click();
  await expect(page.locator('.pitch')).toBeVisible();

  await page.locator('.formation-picker').click();
  await page
    .locator('.el-select-dropdown__item:visible')
    .filter({ hasText: '3-2-2（预置）' })
    .first()
    .click();
  // 重复运行时旧阵容已有首发，切换阵型会弹确认。
  const switchBox = page.locator('.el-message-box');
  const needsSwitch = await switchBox
    .waitFor({ state: 'visible', timeout: 1500 })
    .then(() => true)
    .catch(() => false);
  if (needsSwitch) {
    await switchBox.getByRole('button', { name: '切换' }).click();
  }

  for (let index = 0; index < 8; index += 1) {
    const slot = page.locator('.pitch-slot:not(.pitch-slot--filled)').first();
    if ((await slot.count()) === 0) {
      break;
    }
    await page.locator('.bench-item--bench .bench-select').first().click();
    await slot.click();
  }
  await expect(page.locator('.pitch-slot--filled')).toHaveCount(8);
  await page.getByRole('button', { name: '保存阵容' }).click();
  await expect(page.getByText('阵容已保存')).toBeVisible();
  await expect(page.locator('.lineup-version')).toContainText('v');

  // C 端正式球员只读查看同一场比赛的阵容。
  await page.goto('http://localhost:5273/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000002');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(/\/matches$/);

  await page.goto('http://localhost:5273/matches/1001');
  await page.getByRole('button', { name: '阵容' }).click();
  await expect(page.locator('.lineup-slot--filled')).toHaveCount(8);
  await expect(page.getByText('测试门将01').first()).toBeVisible();
  await expect(page.getByRole('button', { name: /保存阵容/ })).toHaveCount(0);

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
