import { Buffer } from 'node:buffer';

import { expect, test, type Locator, type Page, type Response } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** 可被浏览器解码并重新压缩的 2×2 PNG。 */
const VALID_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAAqADAAQAAAABAAAAAgAAAADtGLyqAAAAFElEQVQIHWP4z8DAAMIM////ZwAAHu8E/CH5t8cAAAAASUVORK5CYII=',
  'base64',
);

async function loginAdmin(
  page: Page,
  phone = '13910000001',
  landing: RegExp = /\/admin\/matches$/,
): Promise<void> {
  await page.goto('/admin/login');
  await page.getByPlaceholder('请输入手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();
  await expect(page).toHaveURL(landing);
}

const shanghaiInputFormatter = new Intl.DateTimeFormat('sv-SE', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

/** 表单统一按上海墙钟填写，与页面的 Asia/Shanghai 展示一致。 */
function shanghaiDateTimeInput(date: Date): string {
  return shanghaiInputFormatter.format(date);
}

/** 未来某天上海时间 20:00（UTC 12:00），避免受浏览器时区影响。 */
function futureInstant(daysAhead: number): Date {
  const date = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000);
  date.setUTCHours(12, 0, 0, 0);
  return date;
}

async function fillDialogDateTime(dialog: Locator, label: string, date: Date): Promise<void> {
  const input = dialog.locator('.el-form-item').filter({ hasText: label }).locator('input').first();
  await input.click();
  await input.fill(shanghaiDateTimeInput(date));
  await input.press('Enter');
  await input.blur();
  await expect(input).toHaveValue(shanghaiDateTimeInput(date));
}

function matchRow(page: Page, title: string): Locator {
  return page.locator('.el-table__row').filter({ hasText: title });
}

async function chooseStatus(page: Page, row: Locator, label: string): Promise<void> {
  await row.getByRole('button', { name: '状态' }).click();
  await page.locator('.el-dropdown-menu__item:visible').filter({ hasText: label }).first().click();
}

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

  // 等分组与名单请求落定，避免在空态过渡中误判「尚未生成分组」。
  await expect(page.locator('.el-loading-mask')).toHaveCount(0);

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

  // C 端正式球员只读查看同一场比赛的阵容（1023 为 B 端套件专用，避开 1002 限流）。
  await page.goto('http://localhost:5273/login');
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
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
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
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
  await page.getByPlaceholder('请输入手机号').fill('13910000023');
  await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
  await page.getByRole('button', { name: '登录' }).click();

  // 本期不设工作台：登录后落首个有权限分区；1023 仅有报名/考勤、阵容、分组。
  await expect(page).toHaveURL(/\/admin\/registrations$/);
  await expect(page.getByRole('menuitem', { name: '报名/考勤' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '比赛管理' })).toHaveCount(0);

  // 直访 /admin/ 也应落首个有权限分区，而不是已下线的 dashboard。
  await page.goto('/admin/');
  await expect(page).toHaveURL(/\/admin\/registrations$/);

  await page.goto('/admin/matches');
  await expect(page).toHaveURL(/\/admin\/forbidden\?section=matches$/);
  await expect(page.getByText('403 无权限访问')).toBeVisible();
});

test('B 端：B02 创建 → 编辑 → 延期 → 重新排期 → 取消', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page);

  const title = `E2E比赛${Date.now()}`;
  const start = futureInstant(10);

  await page.getByRole('button', { name: '新建比赛' }).click();
  const createDialog = page.locator('.el-dialog').filter({ hasText: '创建比赛' });
  await expect(createDialog).toBeVisible();
  await createDialog.getByPlaceholder('例如 橙队VS兰队').fill(title);
  await fillDialogDateTime(createDialog, '开始时间', start);
  await fillDialogDateTime(
    createDialog,
    '结束时间',
    new Date(start.getTime() + 2 * 60 * 60 * 1000),
  );
  await createDialog.getByRole('button', { name: '创建比赛' }).click();

  const createdRow = matchRow(page, title);
  await expect(createdRow).toBeVisible();
  await expect(createdRow.getByText('报名中')).toBeVisible();

  await createdRow.getByRole('button', { name: '编辑' }).click();
  const editDialog = page.locator('.el-dialog').filter({ hasText: '编辑比赛' });
  await expect(editDialog).toBeVisible();
  await editDialog.getByPlaceholder('例如 橙队VS兰队').fill(`${title}-改`);
  await editDialog.getByPlaceholder('比赛场地').fill('E2E球场');
  await editDialog.getByRole('button', { name: '保存修改' }).click();

  const editedTitle = `${title}-改`;
  const editedRow = matchRow(page, editedTitle);
  await expect(editedRow).toBeVisible();
  await expect(editedRow.getByText('E2E球场')).toBeVisible();

  await chooseStatus(page, editedRow, '已延期');
  const postponeDialog = page.locator('.el-dialog').filter({ hasText: '延期比赛' });
  await expect(postponeDialog).toBeVisible();
  const postponedStart = futureInstant(11);
  await fillDialogDateTime(postponeDialog, '开始时间', postponedStart);
  await fillDialogDateTime(
    postponeDialog,
    '结束时间',
    new Date(postponedStart.getTime() + 2 * 60 * 60 * 1000),
  );
  await postponeDialog.getByRole('button', { name: '确认延期' }).click();
  await expect(matchRow(page, editedTitle).getByText('已延期')).toBeVisible();

  await matchRow(page, editedTitle).getByRole('button', { name: '重新排期' }).click();
  const rescheduleDialog = page.locator('.el-dialog').filter({ hasText: '重新排期' });
  await expect(rescheduleDialog).toBeVisible();
  const rescheduledStart = futureInstant(12);
  await fillDialogDateTime(rescheduleDialog, '开始时间', rescheduledStart);
  await fillDialogDateTime(
    rescheduleDialog,
    '结束时间',
    new Date(rescheduledStart.getTime() + 2 * 60 * 60 * 1000),
  );
  await rescheduleDialog.getByRole('button', { name: '确认重新排期' }).click();
  await expect(matchRow(page, editedTitle).getByText('报名中')).toBeVisible();

  await chooseStatus(page, matchRow(page, editedTitle), '已取消');
  const cancelDialog = page.locator('.el-dialog').filter({ hasText: '取消比赛' });
  await expect(cancelDialog).toBeVisible();
  await cancelDialog.getByPlaceholder('取消原因（将记入审计）').fill('E2E 收尾清理');
  await cancelDialog.getByRole('button', { name: '确认取消' }).click();

  const messageBox = page.locator('.el-message-box');
  await expect(messageBox).toBeVisible();
  await messageBox.getByRole('button', { name: '确认取消' }).click();
  await expect(matchRow(page, editedTitle).getByText('已取消')).toBeVisible();

  expect(errors).toEqual([]);
});

test('B 端：B02 只读权限不显示写操作', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['match:view:any'];
    body.data.sections = ['matches'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });
  await loginAdmin(page);

  await expect(page).toHaveURL(/\/admin\/matches$/);
  await expect(page.getByText('橙队VS兰队').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '新建比赛' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '状态' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重新排期' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: '比赛管理' })).toBeVisible();
  await expect(page.getByRole('menuitem', { name: '报名/考勤' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B07 筛选 → 编辑 → 头像', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 1022 为 B07 专用管理员，避免与主套件共享 1001 的 AUTH-15 登录限流。
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/players');
  await expect(page).toHaveURL(/\/admin\/players$/);
  await expect(page.getByRole('heading', { level: 2, name: '球员' })).toBeVisible();

  // 姓名 + 类型筛选生效并回到第 1 页。
  await page.getByPlaceholder('搜索球员姓名').fill('测试外挂01');
  await page.getByRole('button', { name: '搜索' }).click();
  const guestRow = page.locator('.el-table__row').filter({ hasText: '测试外挂01' });
  await expect(guestRow).toBeVisible();
  await expect(page.locator('.el-table__row')).toHaveCount(1);
  await expect(guestRow).toContainText('外挂');

  await page.getByRole('button', { name: '重置' }).click();

  // 1003 由种子预置 openid，微信列与解绑入口应可见。
  await page.getByPlaceholder('搜索球员姓名').fill('测试后卫02');
  await page.getByRole('button', { name: '搜索' }).click();
  const wechatRow = page.locator('.el-table__row').filter({ hasText: '测试后卫02' });
  await expect(wechatRow).toContainText('已绑定');
  await expect(wechatRow.getByRole('button', { name: '解绑微信' })).toBeVisible();

  // 1021 无号码，种子区块会复位 region/avatar，保证 E2E 可重复。
  await page.getByPlaceholder('搜索球员姓名').fill('测试待审核01');
  await page.getByRole('button', { name: '搜索' }).click();
  const row = page.locator('.el-table__row').filter({ hasText: '测试待审核01' });
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '编辑' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '编辑球员' });
  await expect(dialog).toBeVisible();

  await dialog.getByPlaceholder('请输入姓名').fill('测试待审核01改');
  await dialog.getByPlaceholder('如：上海').fill('E2E上海');

  // 本地校验失败路径不需要真实 OSS。
  await dialog.locator('input[type=file]').setInputFiles({
    name: 'avatar.gif',
    mimeType: 'image/gif',
    buffer: Buffer.from('GIF89a'),
  });
  await expect(dialog.getByText('仅支持 JPG/PNG/WebP')).toBeVisible();

  const upload = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/players/1021/avatar' &&
      response.request().method() === 'POST',
  );
  await dialog.locator('input[type=file]').setInputFiles({
    name: 'avatar.png',
    mimeType: 'image/png',
    buffer: VALID_PNG,
  });
  expect((await upload).ok()).toBe(true);
  await expect(dialog.locator('img[alt="头像预览"]')).toBeVisible();

  const patched = page.waitForRequest(
    (request) =>
      new URL(request.url()).pathname === '/api/v1/b/players/1021' && request.method() === 'PATCH',
  );
  await dialog.getByRole('button', { name: '保存修改' }).click();
  const body = (await patched).postDataJSON() as Record<string, unknown>;
  expect(body.name).toBe('测试待审核01改');
  expect(body.region).toBe('E2E上海');
  // 1021 无号码；未填写时不提交 jersey，后端保留 NULL。
  expect(body.jersey).toBeUndefined();
  expect(String(body.avatarUrl)).toMatch(/^avatar\/1021\/[0-9A-HJKMNP-TV-Z]{26}\.jpg$/);
  await expect(page.getByText('球员已更新')).toBeVisible();

  await page.getByPlaceholder('搜索球员姓名').fill('测试待审核01改');
  await page.getByRole('button', { name: '搜索' }).click();
  const editedRow = page.locator('.el-table__row').filter({ hasText: '测试待审核01改' });
  await expect(editedRow).toBeVisible();
  await expect(editedRow).toContainText('E2E上海');

  // 同一会话重载 /b/me 验证只读权限，避免额外登录触发 AUTH-15 限流。
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['player:view:any'];
    body.data.sections = ['players'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });
  await page.reload();
  await expect(page).toHaveURL(/\/admin\/players$/);
  await expect(page.getByRole('menuitem', { name: '球员' })).toBeVisible();
  await expect(page.locator('.el-table__row').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '编辑' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '换绑' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '解绑微信' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '通过' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '转正' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '注销' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B08 新建角色 → 勾选权限 → 保存 → 重载 → 重命名 → 删除', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 复用 1022 管理员，避免与主套件共享 1001 的 AUTH-15 登录限流。
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await expect(page).toHaveURL(/\/admin\/roles$/);
  await expect(page.getByRole('heading', { level: 2, name: '角色权限' })).toBeVisible();
  await expect(page.locator('.permission-group')).toHaveCount(7);

  const code = `E2E_${Date.now()}`;
  await page.getByRole('button', { name: '新建角色' }).click();
  const createDialog = page.locator('.el-dialog').filter({ hasText: '新建角色' });
  await createDialog.getByPlaceholder('例如 COACH').fill(code);
  await createDialog.getByPlaceholder('例如 教练').fill('E2E教练');

  const created = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/roles' &&
      response.request().method() === 'POST',
  );
  await createDialog.getByRole('button', { name: '创建' }).click();
  expect((await created).ok()).toBe(true);
  await expect(page.getByText('角色已创建')).toBeVisible();

  const roleItem = page.locator('.role-item').filter({ hasText: code });
  await expect(roleItem).toBeVisible();
  await expect(roleItem).toHaveClass(/active/);

  // 新角色默认无权限：保存前必须至少勾选一项。
  await page.getByRole('button', { name: '保存权限' }).click();
  await expect(page.getByText('至少选择一个权限')).toBeVisible();

  const matchPermission = page.locator('.permission-item').filter({ hasText: 'match:view:any' });
  await matchPermission.click();
  await expect(matchPermission.locator('input')).toBeChecked();

  const permissionsSaved = page.waitForResponse(
    (response) =>
      /^\/api\/v1\/b\/roles\/\d+\/permissions$/.test(new URL(response.url()).pathname) &&
      response.request().method() === 'PUT',
  );
  await page.getByRole('button', { name: '保存权限' }).click();
  const permissionsResponse = await permissionsSaved;
  expect(permissionsResponse.ok()).toBe(true);
  expect(permissionsResponse.request().headers()['idempotency-key']).toBeTruthy();
  expect(
    (permissionsResponse.request().postDataJSON() as { permissions: string[] }).permissions,
  ).toEqual(['match:view:any']);
  await expect(page.getByText('权限已保存')).toBeVisible();

  await page.reload();
  await expect(page.locator('.role-item').filter({ hasText: code })).toBeVisible();
  await page.locator('.role-item').filter({ hasText: code }).click();
  await expect(
    page.locator('.permission-item').filter({ hasText: 'match:view:any' }).locator('input'),
  ).toBeChecked();

  await page.getByRole('button', { name: '重命名' }).click();
  const renameDialog = page.locator('.el-dialog').filter({ hasText: '重命名角色' });
  await renameDialog.getByPlaceholder('请输入角色名称').fill('E2E教练改');
  await renameDialog.getByRole('button', { name: '保存' }).click();
  await expect(page.getByText('角色已重命名')).toBeVisible();
  await expect(page.locator('.role-item').filter({ hasText: 'E2E教练改' })).toBeVisible();

  await page.getByRole('button', { name: '删除' }).click();
  const messageBox = page.locator('.el-message-box');
  await messageBox.getByRole('button', { name: '删除' }).click();
  await expect(page.getByText('角色已删除')).toBeVisible();
  await expect(page.locator('.role-item').filter({ hasText: code })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B08 只读权限不显示写操作', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['role:view:any'];
    body.data.sections = ['roles'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });

  await loginAdmin(page, '13910000022', /\/admin\/roles$/);
  await expect(page.locator('.role-item').first()).toBeVisible();
  await expect(page.getByRole('button', { name: '新建角色' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '重命名' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '删除' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: '保存权限' })).toHaveCount(0);
  await expect(page.locator('.el-checkbox.is-disabled').first()).toBeVisible();

  expect(errors).toEqual([]);
});

async function searchAdminUsers(page: Page, keyword: string): Promise<void> {
  // 等 AUTH-13 返回后再读行数据，避免读到上一次搜索的残留结果。
  const loaded = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/users' &&
      response.request().method() === 'GET' &&
      new URL(response.url()).searchParams.get('keyword') === keyword,
  );
  await page.getByPlaceholder('搜索姓名或手机号').fill(keyword);
  await page.getByRole('button', { name: '搜索' }).click();
  await loaded;
}

function adminUserRow(page: Page, name: string): Locator {
  return page.locator('.el-table__row').filter({ hasText: name });
}

function userRoleCheckbox(page: Page, label: string): Locator {
  return page.locator('.user-role-editor .el-checkbox').filter({ hasText: label });
}

function userRolesCell(row: Locator): Locator {
  return row.locator('.user-roles-cell');
}

function waitUserRolesSave(page: Page, userId: number) {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/b/users/${userId}/roles` &&
      response.request().method() === 'PUT',
  );
}

/** 1001 在套件中登录次数接近 AUTH-15 上限；被限流时按服务端 retryAfterSeconds 等待后重试。 */
async function loginAdminRetrying(page: Page, phone: string): Promise<void> {
  for (let attempt = 0; attempt < 2; attempt += 1) {
    const responsePromise = page.waitForResponse(
      (response) =>
        new URL(response.url()).pathname === '/api/v1/b/auth/login' &&
        response.request().method() === 'POST',
    );
    await page.goto('/admin/login');
    await page.getByPlaceholder('请输入手机号').fill(phone);
    await page.getByPlaceholder('请输入密码').fill('Passw0rd!');
    await page.getByRole('button', { name: '登录' }).click();
    const response = await responsePromise;
    if (response.status() !== 429 || attempt === 1) {
      await expect(page).toHaveURL(/\/admin\/matches$/);
      return;
    }
    const body = (await response.json().catch(() => null)) as {
      data?: { retryAfterSeconds?: number };
    } | null;
    const retryAfter = Math.max(1, body?.data?.retryAfterSeconds ?? 60);
    await page.waitForTimeout((retryAfter + 1) * 1000);
  }
}

test('B 端：B08 用户角色分配 → 分配 ADMIN → 撤回', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await expect(page.locator('.user-roles-panel')).toBeVisible();

  await searchAdminUsers(page, '13910000002');
  const row = adminUserRow(page, '测试后卫01');
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '分配角色' }).click();
  await expect(page.locator('.user-role-editor')).toBeVisible();

  try {
    // 上次失败可能残留 ADMIN：先复原，保证用例可重复。
    if (await userRoleCheckbox(page, '管理员').locator('input').isChecked()) {
      await userRoleCheckbox(page, '管理员').click();
      const normalized = waitUserRolesSave(page, 1002);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await normalized).ok()).toBe(true);
    }
    await expect(userRoleCheckbox(page, '正式球员').locator('input')).toBeChecked();
    await expect(userRoleCheckbox(page, '外挂球员').locator('input')).not.toBeChecked();

    await userRoleCheckbox(page, '管理员').click();
    const saved = waitUserRolesSave(page, 1002);
    await page.getByRole('button', { name: '保存用户角色' }).click();
    const response = await saved;
    expect(response.ok()).toBe(true);
    expect(response.request().headers()['idempotency-key']).toBeTruthy();
    expect((response.request().postDataJSON() as { roles: number[] }).roles).toHaveLength(2);
    await expect(page.getByText('用户角色已保存')).toBeVisible();
    await expect(userRolesCell(row)).toContainText('管理员');
    await expect(userRolesCell(row)).toContainText('正式球员');
  } finally {
    // 撤回 ADMIN 复原，避免断言失败后把 1002 留在管理员状态。
    await searchAdminUsers(page, '13910000002');
    const current = adminUserRow(page, '测试后卫01');
    await expect(current).toBeVisible();
    if ((await userRolesCell(current).innerText()).includes('管理员')) {
      await current.getByRole('button', { name: '分配角色' }).click();
      await userRoleCheckbox(page, '管理员').click();
      const restored = waitUserRolesSave(page, 1002);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await restored).ok()).toBe(true);
    }
  }

  const finalRow = adminUserRow(page, '测试后卫01');
  await expect(userRolesCell(finalRow)).not.toContainText('管理员');
  await expect(userRolesCell(finalRow)).toContainText('正式球员');

  expect(errors).toEqual([]);
});

test('B 端：B08 用户角色 —— 不能移除最后一个管理员', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  // 1001 是票面主角；先降级 1022，使 1001 成为唯一管理员。
  await loginAdminRetrying(page, '13910000001');

  await page.goto('/admin/roles');
  await expect(page.locator('.user-roles-panel')).toBeVisible();

  try {
    await searchAdminUsers(page, '13910000022');
    const secondary = adminUserRow(page, '测试管理员02');
    await expect(secondary).toBeVisible();
    await secondary.getByRole('button', { name: '分配角色' }).click();
    if (await userRoleCheckbox(page, '管理员').locator('input').isChecked()) {
      await userRoleCheckbox(page, '管理员').click();
      const demoted = waitUserRolesSave(page, 1022);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await demoted).ok()).toBe(true);
      await expect(userRolesCell(secondary)).not.toContainText('管理员');
    }

    // 以 AUTH-13 的新结果确认服务端已降级，再尝试移除 1001 的 ADMIN。
    await searchAdminUsers(page, '13910000022');
    await expect(userRolesCell(adminUserRow(page, '测试管理员02'))).not.toContainText('管理员');

    await searchAdminUsers(page, '13910000001');
    const primary = adminUserRow(page, '测试门将01');
    await expect(primary).toBeVisible();
    await primary.getByRole('button', { name: '分配角色' }).click();
    await expect(userRoleCheckbox(page, '管理员').locator('input')).toBeChecked();
    await userRoleCheckbox(page, '管理员').click();
    const rejected = waitUserRolesSave(page, 1001);
    await page.getByRole('button', { name: '保存用户角色' }).click();
    expect((await rejected).status()).toBe(409);
    await expect(page.getByText('不能移除最后一个管理员')).toBeVisible();
    await expect(userRolesCell(primary)).toContainText('管理员');
  } finally {
    // 无论断言成败都恢复 1022 的 ADMIN，避免污染 B07/B08 其他用例。
    await searchAdminUsers(page, '13910000022');
    const secondary = adminUserRow(page, '测试管理员02');
    await expect(secondary).toBeVisible();
    if (!(await userRolesCell(secondary).innerText()).includes('管理员')) {
      await secondary.getByRole('button', { name: '分配角色' }).click();
      await userRoleCheckbox(page, '管理员').click();
      const restored = waitUserRolesSave(page, 1022);
      await page.getByRole('button', { name: '保存用户角色' }).click();
      expect((await restored).ok()).toBe(true);
    }
  }

  // 409 是预期的业务拒绝，浏览器会记录一条资源加载错误，其余 console error 仍需为零。
  expect(errors.filter((message) => !message.includes('status of 409'))).toEqual([]);
});

test('B 端：B08 用户角色 —— 外挂转正需到 B07', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/roles');
  await searchAdminUsers(page, '13910000016');
  const row = adminUserRow(page, '测试外挂01');
  await expect(row).toBeVisible();
  await row.getByRole('button', { name: '分配角色' }).click();
  await expect(userRoleCheckbox(page, '外挂球员').locator('input')).toBeChecked();

  await userRoleCheckbox(page, '正式球员').click();
  await expect(page.getByText('外挂转正请到 B07 球员页操作')).toBeVisible();
  await expect(userRoleCheckbox(page, '正式球员').locator('input')).not.toBeChecked();
  await expect(userRoleCheckbox(page, '外挂球员').locator('input')).toBeChecked();

  expect(errors).toEqual([]);
});

test('B 端：B08 用户角色卡片仅对 role:manage:any 渲染', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const userRequests: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/b/users') {
      userRequests.push(request.url());
    }
  });
  await page.route('**/api/v1/b/me', async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.data.permissions = ['role:view:any'];
    body.data.sections = ['roles'];
    await route.fulfill({
      status: response.status(),
      headers: { 'content-type': 'application/json' },
      body: JSON.stringify(body),
    });
  });

  await loginAdmin(page, '13910000022', /\/admin\/roles$/);
  await expect(page.locator('.role-item').first()).toBeVisible();
  await expect(page.locator('.user-roles-panel')).toHaveCount(0);
  expect(userRequests).toEqual([]);

  expect(errors).toEqual([]);
});

function settingsFormItem(page: Page, label: string): Locator {
  return page.locator('.settings-form .el-form-item').filter({ hasText: label });
}

function settingsNumberInput(page: Page, label: string): Locator {
  return settingsFormItem(page, label).locator('input');
}

function waitSettingsGet(page: Page): Promise<Response> {
  return page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/settings' &&
      response.request().method() === 'GET',
  );
}

async function fillSettingsNumber(page: Page, label: string, value: number): Promise<void> {
  const input = settingsNumberInput(page, label);
  await input.fill(String(value));
  await input.press('Enter');
  await input.blur();
  await expect(input).toHaveValue(String(value));
}

test('B 端：B09 修改报名上限与分组权重并保存，刷新仍生效', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  // 1022 为管理端专用管理员，避开 1001 在整套用例中的 AUTH-15 限流。
  await loginAdmin(page, '13910000022');

  const loaded = waitSettingsGet(page);
  await page.goto('/admin/settings');
  await expect(page).toHaveURL(/\/admin\/settings$/);
  await expect(page.getByRole('heading', { level: 2, name: '系统设置' })).toBeVisible();
  await loaded;

  await fillSettingsNumber(page, '正式球员报名上限', 31);
  await fillSettingsNumber(page, '分组手动权重', 65);
  await fillSettingsNumber(page, '分组自动权重', 35);

  const patched = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/settings' &&
      response.request().method() === 'PATCH',
  );
  await page.getByRole('button', { name: '保存设置' }).click();
  expect((await patched).status()).toBe(200);
  await expect(page.getByText('设置已保存')).toBeVisible();

  const reloaded = waitSettingsGet(page);
  await page.reload();
  await reloaded;
  await expect(settingsNumberInput(page, '正式球员报名上限')).toHaveValue('31');
  await expect(settingsNumberInput(page, '分组手动权重')).toHaveValue('65');
  await expect(settingsNumberInput(page, '分组自动权重')).toHaveValue('35');

  expect(errors).toEqual([]);
});

test('B 端：B09 权重合计非法时字段级提示且不发 PATCH', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const patches: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/b/settings' && request.method() === 'PATCH') {
      patches.push(request.url());
    }
  });
  await loginAdmin(page, '13910000022');

  const loaded = waitSettingsGet(page);
  await page.goto('/admin/settings');
  await expect(page.getByRole('heading', { level: 2, name: '系统设置' })).toBeVisible();
  await loaded;

  await fillSettingsNumber(page, '分组手动权重', 60);
  await fillSettingsNumber(page, '分组自动权重', 30);

  await page.getByRole('button', { name: '保存设置' }).click();
  const manualError = settingsFormItem(page, '分组手动权重').locator('.el-form-item__error');
  await expect(manualError).toHaveText('手动与自动权重合计必须为 100');
  expect(patches).toEqual([]);

  expect(errors).toEqual([]);
});

test('B 端：B10 操作日志筛选、展开与脱敏快照', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await loginAdmin(page, '13910000022');

  await page.goto('/admin/audit');
  await expect(page).toHaveURL(/\/admin\/audit$/);
  await expect(page.getByRole('heading', { level: 2, name: '操作日志' })).toBeVisible();

  // 未知动作与对象类型按原值展示（B10 fixtures 6003）；用 allow-create 直接筛出，
  // 避免 fixture 因分页或运行期日志累积而漂出第 1 页。
  const actionSelect = page.locator('.audit-filters .el-select').first();
  await actionSelect.click();
  await page.keyboard.type('E2E_UNKNOWN_ACTION');
  await page.keyboard.press('Enter');
  await page.keyboard.press('Escape');

  const unknownLoaded = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/b/audit-logs' &&
      url.searchParams.get('action') === 'E2E_UNKNOWN_ACTION'
    );
  });
  await page.getByRole('button', { name: '搜索' }).click();
  await unknownLoaded;
  await expect(
    page.locator('.el-table__row').filter({ hasText: 'E2E_UNKNOWN_ACTION' }),
  ).toBeVisible();
  await expect(page.locator('.el-table__row').filter({ hasText: 'UNKNOWN_TARGET' })).toBeVisible();

  const resetLoaded = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/b/audit-logs' && !url.searchParams.has('action');
  });
  await page.getByRole('button', { name: '重置' }).click();
  await resetLoaded;

  // 用仅存在于 fixture 的操作人 + 动作锁定 6001，避免被运行期 SETTINGS_UPDATE 干扰。
  await page.getByPlaceholder('姓名或用户 ID').fill('E2E审计员');
  await actionSelect.click();
  await page.locator('.el-select-dropdown__item').filter({ hasText: '修改系统设置' }).click();

  const filtered = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return (
      url.pathname === '/api/v1/b/audit-logs' &&
      url.searchParams.get('action') === 'SETTINGS_UPDATE' &&
      url.searchParams.get('actor') === 'E2E审计员'
    );
  });
  await page.getByRole('button', { name: '搜索' }).click();
  await filtered;

  const row = page.locator('.el-table__row').filter({ hasText: '修改系统设置' });
  await expect(row).toHaveCount(1);
  await expect(row).toContainText('E2E审计员');

  await row.locator('.el-table__expand-icon').click();
  await expect(page.getByText('变更前')).toBeVisible();
  await expect(page.getByText('变更后')).toBeVisible();
  // 后端脱敏：快照中只出现掩码手机号，原文不得泄露。
  await expect(page.getByText('139****0001').first()).toBeVisible();
  await expect(page.getByText('13900000001')).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('B 端：B11 创建入队邀请、复制链接并撤销', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  // 1022 为管理端专用管理员；B11 位于套件末尾，已接近 AUTH-15 限流，按服务端提示重试。
  await loginAdminRetrying(page, '13910000022');

  await page.goto('/admin/invites');
  await expect(page).toHaveURL(/\/admin\/invites$/);
  await expect(page.getByRole('heading', { level: 2, name: '邀请管理' })).toBeVisible();

  await page.getByRole('button', { name: '新建邀请' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '新建邀请' });
  await expect(dialog).toBeVisible();
  // 默认入队邀请、7 天、不限次数，直接提交即可。
  await expect(dialog.getByRole('radio', { name: '入队邀请' })).toBeChecked();

  const created = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/invites' &&
      response.request().method() === 'POST',
  );
  const refreshed = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/b/invites' &&
      response.request().method() === 'GET',
  );
  await dialog.getByRole('button', { name: '创建邀请' }).click();
  const response = await created;
  expect(response.ok()).toBe(true);
  expect(response.request().headers()['idempotency-key']).toBeTruthy();
  // 等创建后的列表刷新落定，避免后续断言落在旧行上。
  expect((await refreshed).ok()).toBe(true);

  const resultDialog = page.locator('.el-dialog').filter({ hasText: '邀请已创建' });
  await expect(resultDialog).toBeVisible();
  const urlInput = resultDialog.locator('input');
  const inviteUrl = await urlInput.inputValue();
  expect(inviteUrl).toMatch(/\/invite\/[0-9a-f]{32}$/);

  await resultDialog.getByRole('button', { name: '复制链接' }).click();
  await expect(resultDialog.getByRole('button', { name: '已复制' })).toBeVisible();
  const clipboard = await page.evaluate(() => navigator.clipboard.readText());
  expect(clipboard).toBe(inviteUrl);

  await resultDialog.getByRole('button', { name: '关闭', exact: true }).click();
  await expect(resultDialog).toBeHidden();

  // 列表按 id 倒序，新创建的邀请在第一行；列表行不提供复制。
  const firstRow = page.locator('.el-table__row').first();
  await expect(firstRow).toContainText('入队邀请');
  await expect(firstRow.locator('.el-tag')).toHaveText('有效');
  await expect(firstRow.getByRole('button', { name: '复制' })).toHaveCount(0);

  await firstRow.getByRole('button', { name: '撤销' }).click();
  const box = page.locator('.el-message-box');
  await expect(box).toBeVisible();
  await box.getByRole('button', { name: '撤销' }).click();
  await expect(page.getByText('邀请已撤销')).toBeVisible();
  await expect(firstRow.locator('.el-tag')).toHaveText('已撤销');
  await expect(firstRow.getByRole('button', { name: '撤销' })).toBeDisabled();

  expect(errors).toEqual([]);
});

test('B 端：B11 比赛邀请未选比赛时阻止提交', async ({ page }) => {
  test.setTimeout(120_000);
  const errors = collectConsoleErrors(page);
  const posts: string[] = [];
  page.on('request', (request) => {
    if (new URL(request.url()).pathname === '/api/v1/b/invites' && request.method() === 'POST') {
      posts.push(request.url());
    }
  });
  await loginAdminRetrying(page, '13910000022');

  await page.goto('/admin/invites');
  await page.getByRole('button', { name: '新建邀请' }).click();
  const dialog = page.locator('.el-dialog').filter({ hasText: '新建邀请' });
  // Element Plus 的 radio input 不可见，点 label 才能触发。
  await dialog.locator('.el-radio').filter({ hasText: '比赛邀请' }).click();
  await expect(dialog.getByText('关联比赛')).toBeVisible();

  await dialog.getByRole('button', { name: '创建邀请' }).click();
  await expect(dialog.locator('.dialog-error')).toHaveText('请选择比赛');
  expect(posts).toEqual([]);

  expect(errors).toEqual([]);
});
