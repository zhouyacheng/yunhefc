import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

import { collectConsoleErrors, loginAs } from './support';

/**
 * dev/mock 下走完整微信链路：AUTH-07 → mock 授权页 → AUTH-08 302 → 站内页。
 * mock 的 openid 固定为 mock-openid-mock-code，用例开头先解绑上一次运行留下的绑定。
 */
const WECHAT_UA =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/15E148 MicroMessenger/8.0.50(0x18003233) NetType/WIFI Language/zh_CN';

const MOCK_PLAYER_NAME = '微信授权联调球员';

/** 每次运行使用未注册手机号，避免重复执行命中 40907。 */
function freshPhone(): string {
  return `139${String(Date.now()).slice(-8)}`;
}

/** 管理员解绑 mock openid，保证用例可重复执行。 */
async function resetMockWechatBinding(request: APIRequestContext): Promise<void> {
  const csrfResponse = await request.get('/api/v1/b/auth/csrf');
  if (!csrfResponse.ok()) {
    throw new Error(`解绑前置：管理员 CSRF 获取失败（${csrfResponse.status()}）`);
  }
  const csrf = (await csrfResponse.json()).data as { token: string; headerName?: string };
  const login = await request.post('/api/v1/b/auth/login', {
    data: { phone: '13910000001', password: 'Passw0rd!' },
    headers: { [csrf.headerName ?? 'X-XSRF-TOKEN']: csrf.token },
  });
  if (!login.ok()) {
    throw new Error(`解绑前置：管理员登录失败（${login.status()}）`);
  }
  const csrfToken = (await login.json()).data.csrfToken as string;
  const players = await request.get('/api/v1/b/players', {
    params: { name: MOCK_PLAYER_NAME, pageSize: 50 },
  });
  if (!players.ok()) {
    throw new Error(`解绑前置：球员列表查询失败（${players.status()}）`);
  }
  const items = (await players.json()).data.items as Array<{
    userId: number | null;
    wechatBound: boolean;
  }>;
  for (const item of items) {
    if (item.userId && item.wechatBound) {
      const response = await request.delete(`/api/v1/b/users/${item.userId}/wechat-binding`, {
        headers: { 'X-XSRF-TOKEN': csrfToken },
      });
      if (!response.ok()) {
        throw new Error(`解绑前置：用户 ${item.userId} 解绑失败（${response.status()}）`);
      }
    }
  }
}

async function confirmMockAuthorize(page: Page): Promise<void> {
  await expect(page).toHaveURL(/\/api\/v1\/c\/auth\/wechat\/mock-authorize\?state=/);
  await page.getByRole('link', { name: '确认授权' }).click();
}

test.describe('微信内置浏览器', () => {
  test.use({ userAgent: WECHAT_UA });

  test('C05→C08：静默授权、首次绑定手机、老用户直接登录', async ({ page, request }) => {
    const errors = collectConsoleErrors(page);
    await resetMockWechatBinding(request);
    const phone = freshPhone();

    await page.goto('/login');
    await expect(page.getByTestId('wechat-login')).toBeVisible();
    await page.getByTestId('wechat-login').click();
    await confirmMockAuthorize(page);

    // 首次授权回到绑定页，表单必填校验。
    await expect(page).toHaveURL(/\/auth\/wechat\/callback\?bind=1&redirect=%2F$/);
    await expect(page.getByRole('heading', { name: '绑定手机号' })).toBeVisible();
    await page.getByRole('button', { name: '绑定并登录' }).click();
    await expect(page.getByText('请输入正确的手机号')).toBeVisible();
    await expect(page.getByText('请输入 6 位数字验证码')).toBeVisible();
    await expect(page.getByText('请输入姓名')).toBeVisible();
    await expect(page.getByText('请选择主位置')).toBeVisible();

    await page.getByPlaceholder('请输入手机号').fill(phone);
    await page.getByRole('button', { name: '获取验证码' }).click();
    await expect(page.getByText(/开发环境验证码：123456/)).toBeVisible();
    await page.getByPlaceholder('6 位数字验证码').fill('123456');
    await page.getByPlaceholder('请输入姓名').fill(MOCK_PLAYER_NAME);
    await page.getByPlaceholder('请选择主位置').click();
    await page.getByText('后卫', { exact: true }).click();
    await page.getByRole('button', { name: '绑定并登录' }).click();

    // 绑定成功按 redirect 回首页，个人中心显示已绑定且无绑定按钮。
    await expect(page).toHaveURL(/\/$/);
    await page.goto('/profile');
    const security = page.locator('.profile-security');
    await expect(security).toContainText('已绑定');
    await expect(security.getByRole('button', { name: '绑定微信' })).toHaveCount(0);

    // 新设备（无会话、无本地提示）：老用户再次授权直接登录并恢复头部身份。
    await page.evaluate(() => localStorage.clear());
    await page.context().clearCookies();
    await page.goto('/login?redirect=%2Fmatches');
    await page.getByTestId('wechat-login').click();
    await confirmMockAuthorize(page);
    await expect(page).toHaveURL(/\/matches$/);
    await expect(page.locator('.c-header')).toContainText(MOCK_PLAYER_NAME);
    await page.goto('/profile');
    await expect(page.locator('.profile-security')).toContainText('已绑定');

    expect(errors).toEqual([]);
  });

  test('C05：微信入口仅在微信内显示，非法 redirect 回首页', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await page.goto('/login?redirect=https%3A%2F%2Fevil.example');
    await expect(page.getByTestId('wechat-login')).toBeVisible();

    const [authorize] = await Promise.all([
      page.waitForRequest(
        (request) => new URL(request.url()).pathname === '/api/v1/c/auth/wechat/authorize',
      ),
      page.getByTestId('wechat-login').click(),
    ]);
    expect(new URL(authorize.url()).searchParams.get('redirect')).toBe('/');
    await expect(page).toHaveURL(/mock-authorize\?state=/);

    expect(errors).toEqual([]);
  });

  test('C06：未绑定时显示绑定微信，点击发起 AUTH-07 并回个人中心', async ({ page }) => {
    const errors = collectConsoleErrors(page);

    await loginAs(page);
    await page.goto('/profile');
    const security = page.locator('.profile-security');
    await expect(security).toContainText('未绑定');

    const [authorize] = await Promise.all([
      page.waitForRequest(
        (request) => new URL(request.url()).pathname === '/api/v1/c/auth/wechat/authorize',
      ),
      security.getByRole('button', { name: '绑定微信' }).click(),
    ]);
    expect(new URL(authorize.url()).searchParams.get('redirect')).toBe('/profile');
    await expect(page).toHaveURL(/mock-authorize\?state=/);

    expect(errors).toEqual([]);
  });
});

test('C05/C06：非微信浏览器不显示微信入口与绑定按钮', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  await page.goto('/login');
  await expect(page.getByTestId('wechat-login')).toHaveCount(0);

  await loginAs(page);
  await page.goto('/profile');
  await expect(page.locator('.profile-security')).toContainText('未绑定');
  await expect(page.getByRole('button', { name: '绑定微信' })).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C08：失败页提供重试与手机号登录，缺 code/state 回失败页', async ({ page }) => {
  const errors = collectConsoleErrors(page);

  // 无参数直达 → 失败页。
  await page.goto('/auth/wechat/callback');
  await expect(page.getByRole('heading', { name: '微信授权未完成' })).toBeVisible();
  await expect(page.getByRole('button', { name: '重新授权' })).toBeVisible();
  await expect(page.getByRole('link', { name: '使用手机号登录' })).toHaveAttribute(
    'href',
    /\/login/,
  );

  // 重试重新发起 AUTH-07，保留 redirect。
  const [retried] = await Promise.all([
    page.waitForRequest(
      (request) => new URL(request.url()).pathname === '/api/v1/c/auth/wechat/authorize',
    ),
    page.getByRole('button', { name: '重新授权' }).click(),
  ]);
  expect(new URL(retried.url()).searchParams.get('redirect')).toBe('/');
  await expect(page).toHaveURL(/mock-authorize\?state=/);

  // 缺 code / state 失效：后端 HTML 分支 302 到失败页。
  await page.goto('/api/v1/c/auth/wechat/callback?state=expired-state');
  await expect(page).toHaveURL(/\/auth\/wechat\/callback\?error=40001&redirect=%2F$/);
  await expect(page.getByText('微信授权已失效，请重新授权')).toBeVisible();

  // 带错误码与 redirect 的失败页：文案、重试保留 redirect、可改用手机号登录。
  await page.goto('/auth/wechat/callback?error=42201&redirect=%2Fmatches');
  await expect(page.getByText('该微信已绑定其他账号，请联系管理员解绑后再试')).toBeVisible();
  const [retriedWithRedirect] = await Promise.all([
    page.waitForRequest(
      (request) => new URL(request.url()).pathname === '/api/v1/c/auth/wechat/authorize',
    ),
    page.getByRole('button', { name: '重新授权' }).click(),
  ]);
  expect(new URL(retriedWithRedirect.url()).searchParams.get('redirect')).toBe('/matches');
  await expect(page).toHaveURL(/mock-authorize\?state=/);
  await page.goto('/auth/wechat/callback?error=42201&redirect=%2Fmatches');
  await expect(page.getByRole('link', { name: '使用手机号登录' })).toHaveAttribute(
    'href',
    /\/login\?redirect=\/matches/,
  );

  expect(errors).toEqual([]);
});
