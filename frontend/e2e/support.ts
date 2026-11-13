import { expect, type Page } from '@playwright/test';

/** 冒烟用例统一断言无 console error / 未捕获异常。 */
export function collectConsoleErrors(page: Page): string[] {
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') {
      errors.push(message.text());
    }
  });
  page.on('pageerror', (error) => {
    errors.push(error.message);
  });
  return errors;
}

/** 登录并等待默认落点 /matches；测试数据账号默认 1002 / Passw0rd!。 */
export async function loginAs(
  page: Page,
  phone = '13910000002',
  password = 'Passw0rd!',
): Promise<void> {
  await page.goto('/login');
  await page.getByPlaceholder('请输入手机号').fill(phone);
  await page.getByPlaceholder('请输入密码').fill(password);
  await page.getByRole('button', { name: '登录', exact: true }).click();
  await expect(page).toHaveURL(/\/matches$/);
}
