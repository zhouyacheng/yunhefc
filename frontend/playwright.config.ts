import { defineConfig, devices } from '@playwright/test';

/**
 * 打真实本地后端：先启动 backend（dev profile）并在 dev 库执行 scripts/sql/test-data.sql。
 * Vite dev server 由本配置拉起；后端不在本配置内启动。
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  reporter: [['list']],
  use: {
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'web',
      testMatch: /web\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5273' },
    },
    {
      name: 'admin',
      testMatch: /admin\.spec\.ts/,
      use: { ...devices['Desktop Chrome'], baseURL: 'http://localhost:5274' },
    },
  ],
  webServer: [
    {
      command: 'pnpm --filter @yunhefc/web dev',
      url: 'http://localhost:5273',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
    {
      command: 'pnpm --filter @yunhefc/admin dev',
      url: 'http://localhost:5274/admin/',
      reuseExistingServer: !process.env.CI,
      timeout: 120_000,
    },
  ],
});
