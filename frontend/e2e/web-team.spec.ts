import { expect, test } from '@playwright/test';

import { collectConsoleErrors } from './support';

test('C04 球队：四组名单、展开更多与公开字段', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const rosterRequests: string[] = [];
  const statsRequests: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname === '/api/v1/c/team/players') {
      rosterRequests.push(url.search);
    } else if (url.pathname === '/api/v1/c/team/stats') {
      statsRequests.push(url.search);
    }
  });

  await page.goto('/team');

  await expect(page.getByRole('heading', { name: '球队' })).toBeVisible();

  const titles = page.locator('.team-group__title');
  await expect(titles).toHaveCount(4);
  await expect(titles.nth(0)).toContainText('前锋');
  await expect(titles.nth(1)).toContainText('中场');
  await expect(titles.nth(2)).toContainText('后卫');
  await expect(titles.nth(3)).toContainText('门将');

  // 种子中场共 12 人：预览阶段人数未知，显示 10+；默认 10 人，展开后出现剩余球员。
  const midfield = page.locator('.team-group').nth(1);
  await expect(titles.nth(1)).toContainText('10+ 人');
  await expect(midfield.locator('.player-card')).toHaveCount(10);
  await expect(midfield.getByText('测试中场11')).toHaveCount(0);

  await midfield.getByRole('button', { name: '展开更多' }).click();
  await expect(midfield.locator('.player-card')).toHaveCount(12);
  await expect(titles.nth(1)).toContainText('12 人');
  await expect(midfield.getByText('测试中场11')).toBeVisible();
  await expect(midfield.getByRole('button', { name: '收起' })).toBeVisible();

  await midfield.getByRole('button', { name: '收起' }).click();
  await expect(midfield.locator('.player-card')).toHaveCount(10);

  // 首屏 limit=10，首次展开 all=true，收起不再请求。
  expect(rosterRequests).toHaveLength(2);
  expect(rosterRequests[0]).toContain('limit=10');
  expect(rosterRequests[1]).toContain('all=true');
  expect(statsRequests).toHaveLength(1);

  // 公开字段范围：不含手机号（含脱敏格式），不含外挂球员，含本赛季统计。
  const rosterText = await page.locator('.team-groups').innerText();
  expect(rosterText).not.toMatch(/1\d{10}|1\d{2}\*{4}\d{4}/);
  await expect(page.getByText('测试外挂01')).toHaveCount(0);
  await expect(page.getByText('外挂')).toHaveCount(0);

  const card = midfield.locator('.player-card').filter({ hasText: '测试中场01' });
  await expect(card.locator('.player-card__stats')).toContainText('出场 1');
  await expect(card.locator('.player-card__stats')).toContainText('进球 0');

  expect(errors).toEqual([]);
});

test('C04 球队：统计延迟不阻塞名单', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await page.route(
    (url) => url.pathname === '/api/v1/c/team/stats',
    async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 1500));
      await route.continue();
    },
  );

  await page.goto('/team');
  await expect(page.locator('.team-group__title')).toHaveCount(4);

  // 名单先于统计渲染：统计仍挂起时显示降级值，返回后自动补齐。
  const card = page
    .locator('.team-group')
    .nth(1)
    .locator('.player-card')
    .filter({ hasText: '测试中场01' });
  await expect(card.locator('.player-card__stats')).toContainText('进球 —');
  await expect(card.locator('.player-card__stats')).toContainText('进球 0', { timeout: 5000 });

  expect(errors).toEqual([]);
});

test('C04 球队：统计缺失时降级展示', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  let statsRequests = 0;
  await page.route(
    (url) => url.pathname === '/api/v1/c/team/stats',
    async (route) => {
      statsRequests += 1;
      // 200 + 业务错误 envelope：不产生浏览器资源错误日志。
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 50001, message: '统计暂不可用', data: null }),
      });
    },
  );

  await page.goto('/team');
  await expect(page.locator('.team-group__title')).toHaveCount(4);
  await expect.poll(() => statsRequests).toBe(1);

  const card = page
    .locator('.team-group')
    .nth(1)
    .locator('.player-card')
    .filter({ hasText: '测试中场01' });
  await expect(card.locator('.player-card__stats')).toContainText('出场 1');
  await expect(card.locator('.player-card__stats')).toContainText('进球 —');
  await expect(card.locator('.player-card__stats')).toContainText('助攻 —');

  expect(errors).toEqual([]);
});
