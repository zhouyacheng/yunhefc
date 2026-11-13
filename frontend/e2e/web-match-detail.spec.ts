import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

interface RosterResponse {
  data: {
    items: Array<{ id: number; player: { id: number } }>;
  };
}

function queueNumbers(text: string): { confirmed: number; capacity: number } {
  const match = /(\d+)\s*\/\s*(\d+)/.exec(text);
  return {
    confirmed: Number(match?.[1] ?? 0),
    capacity: Number(match?.[2] ?? 0),
  };
}

/** 未来某天上海墙钟（Asia/Shanghai），用于 datetime-local 填写。 */
function shanghaiDayTime(daysAhead: number, hour: number): string {
  const day = new Date(Date.now() + daysAhead * 24 * 60 * 60 * 1000 + 8 * 60 * 60 * 1000);
  const year = day.getUTCFullYear();
  const month = String(day.getUTCMonth() + 1).padStart(2, '0');
  const date = String(day.getUTCDate()).padStart(2, '0');
  return `${year}-${month}-${date}T${String(hour).padStart(2, '0')}:00`;
}

async function chooseStatus(page: Page, matchId: number, value: string): Promise<{ ok: boolean }> {
  const select = page.getByTestId('status-select');
  const responsePromise = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === `/api/v1/c/matches/${matchId}/status` &&
      response.request().method() === 'POST',
  );
  await select.selectOption(value);
  const response = await responsePromise;
  expect(response.request().headers()['idempotency-key']).toBeTruthy();
  return { ok: response.ok() };
}

function trackApiPaths(page: Page): string[] {
  const paths: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname.startsWith('/api/v1/c/')) {
      paths.push(url.pathname);
    }
  });
  return paths;
}

test('C03 访客：登录引导且不探测会话', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const paths = trackApiPaths(page);

  await page.goto('/matches/1005');

  await expect(page.getByTestId('registration-card')).toContainText('登录后可报名与签到');
  await expect(page.getByText('登录后查看名单与考勤')).toBeVisible();
  await expect(page.getByTestId('share-button')).toBeVisible();
  await expect(page.getByTestId('signup-button')).toHaveCount(0);
  await expect(page.getByTestId('cancel-registration')).toHaveCount(0);

  expect(paths).not.toContain('/api/v1/c/me');
  expect(paths).not.toContain('/api/v1/c/matches/1005/registrations');
  expect(paths).not.toContain('/api/v1/c/matches/1005/registrations/me');
  expect(paths).not.toContain('/api/v1/c/matches/1005/attendance');
  expect(errors).toEqual([]);
});

test('C03 待审核：只读且不请求全队名单', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const paths = trackApiPaths(page);

  await loginAs(page, '13910000021', 'Passw0rd!');
  await page.goto('/matches/1005');

  await expect(page.getByTestId('pending-banner')).toBeVisible();
  await expect(page.getByTestId('signup-button')).toHaveCount(0);
  await expect(page.getByTestId('cancel-registration')).toHaveCount(0);
  await expect(page.getByTestId('invite-button')).toHaveCount(0);
  await expect(page.getByText('账号待审核，暂不可查看全队名单。')).toBeVisible();

  expect(paths).not.toContain('/api/v1/c/matches/1005/registrations');
  expect(paths).not.toContain('/api/v1/c/matches/1005/registrations/me');
  expect(paths).not.toContain('/api/v1/c/matches/1005/attendance');
  expect(errors).toEqual([]);
});

test('C03 外挂：仅本人报名与考勤', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const paths = trackApiPaths(page);

  await loginAs(page, '13910000016', 'Passw0rd!');
  await page.goto('/matches/1002');

  await expect(page.getByTestId('registration-status')).toContainText('已确认');
  await expect(page.locator('.attendance-status')).toContainText('正常');
  await expect(page.locator('.attendance-status')).toContainText('自助签到');
  await expect(page.getByText('外挂仅显示本人报名。')).toBeVisible();
  await expect(page.getByTestId('roster-row')).toHaveCount(1);
  await expect(page.getByTestId('roster-row')).toContainText('测试外挂01');
  await expect(page.getByText('测试门将01')).toHaveCount(0);
  await expect(page.getByTestId('invite-button')).toHaveCount(0);

  expect(paths).not.toContain('/api/v1/c/matches/1002/registrations');
  expect(paths).not.toContain('/api/v1/c/matches/1002/attendance');
  expect(errors).toEqual([]);
});

test('C03 作废场次：横幅、历史条数与操作隐藏', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.route('**/api/v1/c/matches/1005', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }
    const response = await route.fetch();
    const body = (await response.json()) as {
      data: Record<string, unknown>;
    };
    body.data = {
      ...body.data,
      status: 6,
      statusLabel: 'CANCELLED',
      confirmedCount: 12,
      guestCount: 2,
      waitlistCount: 1,
    };
    await route.fulfill({ response, json: body });
  });

  await page.goto('/matches/1005');

  await expect(page.getByTestId('cancelled-banner')).toContainText('报名作废');
  await expect(page.getByTestId('cancelled-history')).toContainText('正式 12');
  await expect(page.getByTestId('cancelled-history')).toContainText('外挂 2');
  await expect(page.getByTestId('cancelled-history')).toContainText('候补 1');
  await expect(page.getByTestId('signup-button')).toHaveCount(0);
  await expect(page.getByTestId('cancel-registration')).toHaveCount(0);
  await expect(page.getByTestId('check-in-button')).toHaveCount(0);
  await expect(page.getByTestId('roster-row')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('C03 正式球员：报名、邀请一次性链接、取消并真实递补', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: async (text: string) => {
          (window as unknown as { __copied?: string }).__copied = text;
        },
      },
      configurable: true,
    });
  });

  await loginAs(page, '13910000023', 'Passw0rd!');
  await page.goto('/matches/1005');

  const formal = page.getByTestId('queue-formal');
  const initialFormal = queueNumbers(await formal.innerText());

  const signupResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/registrations' &&
      response.request().method() === 'POST',
  );
  await page.getByTestId('signup-button').click();
  const signup = await signupResponse;
  expect(signup.request().headers()['idempotency-key']).toBeTruthy();
  await expect(page.getByTestId('registration-status')).toContainText('已确认');
  await expect
    .poll(async () => queueNumbers(await formal.innerText()).confirmed)
    .toBe(initialFormal.confirmed + 1);

  await expect(page.getByTestId('invite-button')).toBeVisible();
  await page.getByTestId('invite-button').click();
  await expect(page.getByTestId('invite-days')).toHaveValue('7');
  await expect(page.getByTestId('invite-max-uses')).toHaveValue('');

  const inviteResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/invites' &&
      response.request().method() === 'POST',
  );
  await page.getByTestId('invite-submit').click();
  const invite = await inviteResponse;
  expect(invite.request().headers()['idempotency-key']).toBeTruthy();
  expect(invite.request().postDataJSON()).toEqual({ expiresInDays: 7 });
  const inviteBody = (await invite.json()) as { data: { inviteUrl: string } };
  expect(inviteBody.data.inviteUrl).toMatch(/\/invite\/[0-9a-f]{32}$/);
  await expect(page.getByTestId('invite-link')).toHaveValue(inviteBody.data.inviteUrl);

  await page.getByTestId('invite-copy').click();
  expect(await page.evaluate(() => (window as unknown as { __copied?: string }).__copied)).toBe(
    inviteBody.data.inviteUrl,
  );
  await page.getByTestId('invite-close').click();
  await expect(page.getByTestId('invite-link')).toHaveCount(0);

  const cancelResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/registrations/me' &&
      response.request().method() === 'DELETE',
  );
  await page.getByTestId('cancel-registration').click();
  const cancel = await cancelResponse;
  expect(cancel.request().headers()['idempotency-key']).toBeTruthy();

  await expect(page.getByTestId('action-message')).toContainText('测试前锋01 已递补为正式报名');
  await expect(page.getByTestId('roster-formal')).toContainText('测试前锋01');
  await expect(page.getByTestId('roster-waitlist')).not.toContainText('候补第');
  await expect
    .poll(async () => queueNumbers(await formal.innerText()).confirmed)
    .toBe(initialFormal.confirmed + 1);
  expect(errors).toEqual([]);
});

test('C03 名单与考勤：分段、候补顺序与左连接', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page, '13910000023', 'Passw0rd!');
  await page.goto('/matches/1002');

  const formal = page.getByTestId('roster-formal');
  await expect(formal.getByTestId('roster-row')).toHaveCount(10);
  const keeper = formal.locator('[data-player-id="1001"]');
  await expect(keeper.getByTestId('roster-attendance')).toContainText('正常');
  await expect(keeper.getByTestId('roster-attendance')).toContainText('自助签到');
  const absent = formal.locator('[data-player-id="1009"]');
  await expect(absent.getByTestId('roster-attendance')).toContainText('缺勤');
  await expect(absent.getByTestId('roster-attendance')).toContainText('系统');

  const waitlist = page.getByTestId('roster-waitlist');
  await expect(waitlist.getByTestId('roster-row')).toHaveCount(1);
  await expect(waitlist).toContainText('测试前锋01');
  await expect(waitlist).toContainText('候补第 1 顺位');

  const guest = page.getByTestId('roster-guest');
  await expect(guest.getByTestId('roster-row')).toHaveCount(2);
  await expect(page.locator('[data-player-id="1020"]')).toHaveCount(0);

  await page.goto('/matches/1001');
  await expect(page.getByTestId('roster-formal').getByTestId('roster-row')).toHaveCount(15);
  await expect(page.getByTestId('roster-guest').getByTestId('roster-row')).toHaveCount(4);
  await expect(page.getByTestId('roster-formal')).toContainText('未记录');
  await expect(page.getByTestId('roster-waitlist').getByTestId('roster-row')).toHaveCount(0);
  expect(errors).toEqual([]);
});

test('C03 分享：无原生分享时复制稳定 URL', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'share', { value: undefined, configurable: true });
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: async (text: string) => {
          (window as unknown as { __copied?: string }).__copied = text;
        },
      },
      configurable: true,
    });
  });

  await page.goto('/matches/1005');
  await page.getByTestId('share-button').click();

  await expect(page.getByTestId('share-message')).toContainText('链接已复制');
  expect(await page.evaluate(() => (window as unknown as { __copied?: string }).__copied)).toBe(
    'http://localhost:5273/matches/1005',
  );
  expect(errors).toEqual([]);
});

test('C03 分享：支持 navigator.share 时调起系统分享', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'share', {
      value: async (data: ShareData) => {
        (window as unknown as { __shared?: ShareData }).__shared = data;
      },
      configurable: true,
    });
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: async (text: string) => {
          (window as unknown as { __copied?: string }).__copied = text;
        },
      },
      configurable: true,
    });
  });

  await page.goto('/matches/1005');
  await page.getByTestId('share-button').click();

  await expect(page.getByTestId('share-message')).toContainText('已调起系统分享');
  const shared = await page.evaluate(
    () => (window as unknown as { __shared?: ShareData }).__shared,
  );
  expect(shared?.url).toBe('http://localhost:5273/matches/1005');
  expect(
    await page.evaluate(() => (window as unknown as { __copied?: string }).__copied),
  ).toBeUndefined();
  expect(errors).toEqual([]);
});

test.describe('C03 分享：微信内置浏览器', () => {
  test.use({ userAgent: 'Mozilla/5.0 (iPhone) MicroMessenger/8.0.0' });

  test('提示右上角分享且不复制', async ({ page }) => {
    const errors = collectConsoleErrors(page);
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'clipboard', {
        value: {
          writeText: async (text: string) => {
            (window as unknown as { __copied?: string }).__copied = text;
          },
        },
        configurable: true,
      });
    });

    await page.goto('/matches/1005');
    await page.getByTestId('share-button').click();

    await expect(page.getByTestId('share-message')).toContainText('右上角');
    expect(
      await page.evaluate(() => (window as unknown as { __copied?: string }).__copied),
    ).toBeUndefined();
    expect(errors).toEqual([]);
  });
});

test('C03 管理员：状态合法集、延期与取消并恢复', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page, '13910000001', 'Passw0rd!');
  await page.goto('/matches/1005');

  const select = page.getByTestId('status-select');
  await expect(select.locator('option')).toHaveCount(8);
  await expect(select.locator('option[value="5"]')).toHaveJSProperty('disabled', false);
  await expect(select.locator('option[value="6"]')).toHaveJSProperty('disabled', false);

  await chooseStatus(page, 1005, '1');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名截止');
  await chooseStatus(page, 1005, '0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');

  await select.selectOption('5');
  await expect(page.getByTestId('status-start-at')).toBeVisible();
  await page.getByTestId('status-start-at').fill(shanghaiDayTime(1, 20));
  await page.getByTestId('status-end-at').fill(shanghaiDayTime(1, 22));
  const postponeResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/status' &&
      response.request().method() === 'POST',
  );
  await page.getByTestId('status-submit').click();
  const postpone = await postponeResponse;
  expect(postpone.request().headers()['idempotency-key']).toBeTruthy();
  const postponeBody = postpone.request().postDataJSON() as {
    status: number;
    version: number;
    startAt: string;
    endAt: string;
  };
  expect(postponeBody).toMatchObject({ status: 5 });
  expect(typeof postponeBody.version).toBe('number');
  expect(postponeBody.startAt).toMatch(/T12:00:00/);
  await expect(page.getByTestId('postponed-banner')).toBeVisible();

  await chooseStatus(page, 1005, '0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');

  await select.selectOption('6');
  await page.getByTestId('status-submit').click();
  await expect(page.getByTestId('status-dialog-error')).toContainText('请输入取消原因');
  await page.getByTestId('status-reason').fill('场地原因');
  const cancelResponse = page.waitForResponse(
    (response) =>
      new URL(response.url()).pathname === '/api/v1/c/matches/1005/status' &&
      response.request().method() === 'POST',
  );
  await page.getByTestId('status-submit').click();
  const cancel = await cancelResponse;
  const cancelBody = cancel.request().postDataJSON() as { status: number; reason: string };
  expect(cancelBody).toMatchObject({ status: 6, reason: '场地原因' });
  await expect(page.getByTestId('cancelled-banner')).toBeVisible();
  await expect(page.getByTestId('cancelled-history')).toContainText('正式');

  await chooseStatus(page, 1005, '0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');
  expect(errors).toEqual([]);
});

test('C03 管理员：B03 链接、返回重载与真实递补', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await loginAs(page, '13910000001', 'Passw0rd!');
  await page.goto('/matches/1002');

  const select = page.getByTestId('status-select');
  await expect(select.locator('option[value="5"]')).toHaveJSProperty('disabled', true);
  await expect(select.locator('option[value="6"]')).toHaveJSProperty('disabled', true);
  await expect(select.locator('option[value="0"]')).toHaveJSProperty('disabled', false);

  await chooseStatus(page, 1002, '0');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('报名中');

  const adminLink = page.getByTestId('roster-admin-link');
  await expect(adminLink).toHaveAttribute('href', /\/admin\/registrations\?match=1002$/);

  const rosterResponse = await page.request.get(
    '/api/v1/c/matches/1002/registrations?status=0&pageSize=100',
  );
  const roster = (await rosterResponse.json()) as RosterResponse;
  const target = roster.data.items.find((item) => item.player.id === 1002);
  expect(target).toBeTruthy();

  // C 端与 B 端使用不同的会话 Cookie（YHFC_C / YHFC_B）；B03 移除需要先建立 B 会话。
  const csrfResponse = await page.request.get('/api/v1/b/auth/csrf');
  const csrf = (await csrfResponse.json()) as {
    data: { token: string; headerName?: string };
  };
  const headerName = csrf.data.headerName ?? 'X-XSRF-TOKEN';
  const bLogin = await page.request.post('/api/v1/b/auth/login', {
    headers: { [headerName]: csrf.data.token },
    data: { phone: '13910000001', password: 'Passw0rd!' },
  });
  expect(bLogin.ok()).toBe(true);
  const bLoginBody = (await bLogin.json()) as { data: { csrfToken: string } };

  await page.route(/\/admin\/registrations\?match=1002$/, (route) =>
    route.fulfill({ status: 200, contentType: 'text/html', body: '<html>admin</html>' }),
  );

  await adminLink.click();
  await expect(page).toHaveURL(/localhost:5274\/admin\/registrations\?match=1002$/);
  errors.length = 0;

  const deleteResponse = await page.request.delete(`/api/v1/b/registrations/${target?.id ?? 0}`, {
    headers: {
      [headerName]: bLoginBody.data.csrfToken,
      'Idempotency-Key': globalThis.crypto.randomUUID(),
    },
  });
  if (!deleteResponse.ok()) {
    throw new Error(
      `DELETE /b/registrations failed: ${deleteResponse.status()} ${await deleteResponse.text()}`,
    );
  }
  const deleteBody = (await deleteResponse.json()) as { data: { promoted: boolean } };
  expect(deleteBody.data.promoted).toBe(true);

  await page.goBack();
  await expect(page).toHaveURL(/\/matches\/1002$/);
  await expect(page.getByTestId('roster-diff')).toContainText('测试后卫01 已移除');
  await expect(page.getByTestId('roster-diff')).toContainText('测试前锋01 已递补为正式报名');
  await expect(page.getByTestId('roster-formal')).toContainText('测试前锋01');
  await expect(page.getByTestId('roster-waitlist').getByTestId('roster-row')).toHaveCount(0);

  await chooseStatus(page, 1002, '4');
  await expect(page.locator('.match-hero__top .van-tag')).toContainText('已结束');
  expect(errors).toEqual([]);
});

test('C03 不存在的比赛进入 404', async ({ page }) => {
  await page.goto('/matches/999999');
  await expect(page).toHaveURL(/\/404$/);
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible();
});

test('C03 加载失败可重试', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  let failed = false;

  await page.route('**/api/v1/c/matches/1005', async (route) => {
    if (route.request().method() !== 'GET') {
      await route.continue();
      return;
    }
    if (!failed) {
      failed = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 50001, message: '服务暂不可用', data: null }),
      });
      return;
    }
    await route.continue();
  });

  await page.goto('/matches/1005');
  await expect(page.getByText('服务暂不可用')).toBeVisible();
  await page.getByRole('button', { name: '重试' }).click();
  await expect(page.locator('.match-hero')).toBeVisible();
  expect(errors).toEqual([]);
});
