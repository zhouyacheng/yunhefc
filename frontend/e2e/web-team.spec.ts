import { expect, test } from '@playwright/test';

import { collectConsoleErrors } from './support';

function currentShanghaiSeason(): string {
  return String(new Date(Date.now() + 8 * 60 * 60 * 1000).getUTCFullYear());
}

test('C04 球队：四组名单、全量展示与公开字段', async ({ page }) => {
  const season = currentShanghaiSeason();
  const errors = collectConsoleErrors(page);
  const rosterRequests: string[] = [];
  const statsRequests: string[] = [];
  const seasonRequests: string[] = [];
  page.on('request', (request) => {
    const url = new URL(request.url());
    if (url.pathname === '/api/v1/c/team/players') {
      rosterRequests.push(url.search);
    } else if (url.pathname === '/api/v1/c/team/stats') {
      statsRequests.push(url.search);
    } else if (url.pathname === '/api/v1/c/matches/seasons') {
      seasonRequests.push(url.search);
    }
  });

  await page.goto('/team');

  await expect(page.getByRole('heading', { name: '球队' })).toBeVisible();
  await expect(page.getByLabel('赛季')).toHaveValue(season);

  const titles = page.locator('.team-group__title');
  await expect(titles).toHaveCount(4);
  await expect(titles.nth(0)).toContainText('前锋');
  await expect(titles.nth(1)).toContainText('中场');
  await expect(titles.nth(2)).toContainText('后卫');
  await expect(titles.nth(3)).toContainText('门将');

  // 种子中场共 12 人：首屏一次取全量，直接展示全部球员，无展开/收起入口。
  const midfield = page.locator('.team-group').nth(1);
  await expect(titles.nth(1)).toContainText('12 人');
  await expect(midfield.locator('.player-card')).toHaveCount(12);
  await expect(midfield.getByText('测试中场11')).toBeVisible();
  await expect(midfield.getByRole('button', { name: '展开更多' })).toHaveCount(0);
  await expect(midfield.getByRole('button', { name: '收起' })).toHaveCount(0);

  // 名单只请求一次且为全量；三个请求都显式带当前赛季。
  expect(rosterRequests).toHaveLength(1);
  expect(rosterRequests[0]).toContain('all=true');
  expect(rosterRequests[0]).toContain(`season=${season}`);
  expect(statsRequests).toHaveLength(1);
  expect(statsRequests[0]).toContain(`season=${season}`);
  expect(seasonRequests).toHaveLength(1);

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

test('C04 球队：切换赛季更新 URL、请求与统计', async ({ page }) => {
  const current = currentShanghaiSeason();
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
  // MAT-14 后端覆盖由后端 IT 保证；这里补充一个历史赛季以验证切换交互。
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/seasons',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, message: 'ok', data: { items: [current, '2025'] } }),
      }),
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/team');

  const select = page.getByLabel('赛季');
  await expect(select).toBeVisible();
  await expect(select).toHaveValue(current);

  await select.selectOption('2025');
  await expect(page).toHaveURL(/\/team\?season=2025$/);
  await expect(select).toHaveValue('2025');
  await expect(page.locator('.team-group__title')).toHaveCount(4);
  expect(rosterRequests.some((search) => search.includes('season=2025'))).toBe(true);
  expect(statsRequests.some((search) => search.includes('season=2025'))).toBe(true);

  const card = page
    .locator('.team-group')
    .nth(1)
    .locator('.player-card')
    .filter({ hasText: '测试中场01' });
  await expect(card.locator('.player-card__stats')).toContainText('出场 0');
  await expect(card.locator('.player-card__stats')).toContainText('进球 0');

  // 星级按半星粒度展示，aria-label 保留两位小数。
  const defenderCard = page
    .locator('.team-group')
    .nth(2)
    .locator('.player-card')
    .filter({ hasText: '测试后卫01' });
  await expect(defenderCard.locator('.player-card__stars')).toHaveText('★★★★☆');
  await expect(defenderCard.locator('.player-card__stars')).toHaveAttribute(
    'aria-label',
    '星级 4.25/5',
  );
  const halfStarCard = page
    .locator('.team-group')
    .nth(2)
    .locator('.player-card')
    .filter({ hasText: '测试后卫04' });
  await expect(halfStarCard.locator('.player-card__stars')).toHaveText('★★★★½');

  await select.selectOption(current);
  await expect(page).toHaveURL(/\/team$/);
  expect(rosterRequests[rosterRequests.length - 1]).toContain(`season=${current}`);
  expect(statsRequests[statsRequests.length - 1]).toContain(`season=${current}`);

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
