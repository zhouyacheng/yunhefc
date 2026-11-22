import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** STA-03 列表项（与 packages/shared LeaderboardEntry 字段一致）。 */
interface LeaderboardEntry {
  playerId: number;
  name: string;
  jersey: number | null;
  position: number;
  avatarUrl: string | null;
  appearances: number;
  goals: number;
  assists: number;
  ownGoals: number;
  internalAppearances: number;
  internalWins: number;
  winRate: number | null;
  goalsPerMatch: number;
  assistsPerMatch: number;
}

function currentShanghaiSeason(): string {
  return String(new Date(Date.now() + 8 * 60 * 60 * 1000).getUTCFullYear());
}

const CURRENT = currentShanghaiSeason();

const BASE_ENTRIES: LeaderboardEntry[] = [
  {
    playerId: 11,
    name: '排行甲',
    jersey: 7,
    position: 3,
    avatarUrl: null,
    appearances: 10,
    goals: 5,
    assists: 1,
    ownGoals: 0,
    internalAppearances: 3,
    internalWins: 2,
    winRate: 66.7,
    goalsPerMatch: 0.5,
    assistsPerMatch: 0.1,
  },
  {
    playerId: 12,
    name: '排行乙',
    jersey: 9,
    position: 2,
    avatarUrl: null,
    appearances: 8,
    goals: 5,
    assists: 3,
    ownGoals: 1,
    internalAppearances: 8,
    internalWins: 4,
    winRate: 50,
    goalsPerMatch: 0.63,
    assistsPerMatch: 0.38,
  },
  {
    playerId: 13,
    name: '排行丙',
    jersey: 6,
    position: 3,
    avatarUrl: null,
    appearances: 6,
    goals: 4,
    assists: 2,
    ownGoals: 0,
    internalAppearances: 6,
    internalWins: 4,
    winRate: 66.7,
    goalsPerMatch: 0.67,
    assistsPerMatch: 0.33,
  },
  {
    playerId: 14,
    name: '排行丁',
    jersey: null,
    position: 1,
    avatarUrl: null,
    appearances: 0,
    goals: 2,
    assists: 2,
    ownGoals: 2,
    internalAppearances: 0,
    internalWins: 0,
    winRate: null,
    goalsPerMatch: 0,
    assistsPerMatch: 0,
  },
  {
    playerId: 15,
    name: '排行戊',
    jersey: 1,
    position: 2,
    avatarUrl: null,
    appearances: 5,
    goals: 2,
    assists: 2,
    ownGoals: 0,
    internalAppearances: 5,
    internalWins: 2,
    winRate: 40,
    goalsPerMatch: 0.4,
    assistsPerMatch: 0.4,
  },
];

function patchEntries(playerId: number, patch: Partial<LeaderboardEntry>): LeaderboardEntry[] {
  return BASE_ENTRIES.map((entry) =>
    entry.playerId === playerId ? { ...entry, ...patch } : entry,
  );
}

/** 按 scope/season 返回可区分的榜单数据；captures 记录每次 STA-03 请求参数。 */
async function mockLeaderboard(page: Page): Promise<URLSearchParams[]> {
  const requests: URLSearchParams[] = [];
  await page.route(
    (url) => url.pathname === '/api/v1/c/team/leaderboards',
    (route) => {
      const params = new URL(route.request().url()).searchParams;
      requests.push(params);
      const scope = params.get('scope') === 'all' ? 'all' : 'season';
      const season = scope === 'all' ? null : (params.get('season') ?? CURRENT);
      const items =
        scope === 'all'
          ? patchEntries(13, { assists: 6 })
          : season === '2025'
            ? patchEntries(11, { goals: 8, goalsPerMatch: 0.8 })
            : BASE_ENTRIES;
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: { season, scope, items },
        }),
      });
    },
  );
  return requests;
}

async function mockSeasons(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/seasons',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 0, message: 'ok', data: { items: [CURRENT, '2025'] } }),
      }),
  );
}

const board = (page: Page, label: string) => page.getByRole('button', { name: label, exact: true });
const activeTab = (page: Page) => page.locator('.detail-tab--active');
const names = (page: Page) => page.locator('.lb-name');
const ranks = (page: Page) => page.locator('.lb-rank');

test('C10 排行榜：访客默认进球榜、7 榜切换不重新请求', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards');

  await expect(page).toHaveURL(/\/leaderboards$/);
  await expect(page.getByRole('heading', { name: '排行榜' })).toBeVisible();
  await expect(page.getByLabel('赛季或生涯累计')).toHaveValue(CURRENT);

  const tabs = page.locator('.detail-tab');
  await expect(tabs).toHaveText(['出场', '进球', '助攻', '场均进球', '场均助攻', '胜率', '乌龙']);
  await expect(activeTab(page)).toHaveText('进球');

  await expect(page.locator('.lb-row')).toHaveCount(5);
  // 进球并列按助攻定序；同为 2 球时出场多者在前。
  await expect(names(page)).toHaveText(['排行乙', '排行甲', '排行丙', '排行戊', '排行丁']);
  await expect(ranks(page)).toHaveText(['1', '1', '3', '4', '4']);
  expect(requests).toHaveLength(1);

  for (const label of ['出场', '助攻', '场均进球', '场均助攻', '胜率', '乌龙', '进球']) {
    await board(page, label).click();
    await expect(activeTab(page)).toHaveText(label);
  }
  // 切榜只在前端重排，不重新请求 STA-03。
  expect(requests).toHaveLength(1);

  expect(errors).toEqual([]);
});

test('C10 排行榜：赛季与生涯切换写入 URL 并按窗口重新请求', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards');
  await expect(page.locator('.lb-row')).toHaveCount(5);

  const select = page.getByLabel('赛季或生涯累计');
  await select.selectOption('2025');
  await expect(page).toHaveURL(/\/leaderboards\?season=2025$/);
  await expect(names(page).first()).toHaveText('排行甲');
  expect(requests).toHaveLength(2);
  expect(requests[1]?.get('season')).toBe('2025');
  expect(requests[1]?.get('scope')).toBe('season');

  // 切换赛季后榜单仍保留，且切榜不请求。
  await board(page, '助攻').click();
  await expect(page).toHaveURL(/\/leaderboards\?season=2025&board=assists$/);
  expect(requests).toHaveLength(2);

  await select.selectOption('all');
  await expect(page).toHaveURL(/\/leaderboards\?scope=all&board=assists$/);
  await expect(activeTab(page)).toHaveText('助攻');
  await expect(names(page).first()).toHaveText('排行丙');
  expect(requests).toHaveLength(3);
  expect(requests[2]?.get('scope')).toBe('all');
  expect(requests[2]?.has('season')).toBe(false);

  expect(errors).toEqual([]);
});

test('C10 排行榜：胜率榜排除零分母并展示口径说明', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards?scope=all&board=winRate');

  await expect(activeTab(page)).toHaveText('胜率');
  await expect(names(page)).toHaveText(['排行丙', '排行甲', '排行乙', '排行戊']);
  await expect(ranks(page)).toHaveText(['1', '1', '3', '4']);
  const mainValues = page.locator('.lb-main');
  await expect(mainValues.nth(0)).toHaveText('66.7%');
  await expect(mainValues.nth(1)).toHaveText('66.7%');
  await expect(page.getByText('排行丁')).toHaveCount(0);
  await expect(page.getByText(/胜率只统计队内赛/)).toBeVisible();

  expect(errors).toEqual([]);
});

test('C10 排行榜：直链恢复与非法参数静默回退', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards?season=2025&board=assists');
  await expect(page.getByLabel('赛季或生涯累计')).toHaveValue('2025');
  await expect(activeTab(page)).toHaveText('助攻');
  await expect(page.locator('.lb-row')).toHaveCount(5);

  await page.goto('/leaderboards?season=26&scope=career&board=shirt');
  await expect(page.getByLabel('赛季或生涯累计')).toHaveValue(CURRENT);
  await expect(activeTab(page)).toHaveText('进球');
  await expect(page.locator('.lb-row')).toHaveCount(5);
  expect(requests.at(-1)?.get('season')).toBe(CURRENT);
  expect(requests.at(-1)?.get('scope')).toBe('season');

  expect(errors).toEqual([]);
});

test('C10 排行榜：加载失败可重试', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  let calls = 0;
  await page.route(
    (url) => url.pathname === '/api/v1/c/team/leaderboards',
    (route) => {
      calls += 1;
      if (calls === 1) {
        return route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ code: 50001, message: '统计暂不可用', data: null }),
        });
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 0,
          message: 'ok',
          data: { season: CURRENT, scope: 'season', items: BASE_ENTRIES },
        }),
      });
    },
  );
  await mockSeasons(page);

  await page.goto('/leaderboards');
  await expect(page.getByText('统计暂不可用')).toBeVisible();
  await page.getByRole('button', { name: '重试' }).click();
  await expect(page.locator('.lb-row')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C10 排行榜：赛季选项失败不阻塞榜单且保留当前选择', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockLeaderboard(page);
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/seasons',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 50001, message: '赛季暂不可用', data: null }),
      }),
  );

  await page.goto('/leaderboards?season=2025');

  await expect(page.locator('.lb-row')).toHaveCount(5);
  const select = page.getByLabel('赛季或生涯累计');
  await expect(select).toHaveValue('2025');
  await expect(select.locator('option')).toHaveText(['生涯累计', CURRENT, '2025']);

  expect(errors).toEqual([]);
});
