import { expect, test, type Page } from '@playwright/test';

import { collectConsoleErrors } from './support';

/** STA-03 累计口径（与 packages/shared LeaderboardTotals 字段一致）。 */
interface LeaderboardTotals {
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

/** STA-03 列表项（与 packages/shared LeaderboardEntry 字段一致）。 */
interface LeaderboardEntry {
  playerId: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel?: string;
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
  career: LeaderboardTotals | null;
  history: LeaderboardTotals | null;
}

function currentShanghaiSeason(): string {
  return String(new Date(Date.now() + 8 * 60 * 60 * 1000).getUTCFullYear());
}

const CURRENT = currentShanghaiSeason();

const BASE_HISTORY: LeaderboardTotals = {
  appearances: 20,
  goals: 10,
  assists: 6,
  ownGoals: 2,
  internalAppearances: 10,
  internalWins: 6,
  winRate: 60,
  goalsPerMatch: 0.5,
  assistsPerMatch: 0.3,
};

const BASE_CAREER: LeaderboardTotals = {
  appearances: 30,
  goals: 15,
  assists: 9,
  ownGoals: 3,
  internalAppearances: 15,
  internalWins: 10,
  winRate: 66.7,
  goalsPerMatch: 0.5,
  assistsPerMatch: 0.3,
};

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
    career: BASE_CAREER,
    history: BASE_HISTORY,
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
    career: BASE_CAREER,
    history: BASE_HISTORY,
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
    career: BASE_CAREER,
    history: BASE_HISTORY,
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
    career: BASE_CAREER,
    history: BASE_HISTORY,
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
    career: BASE_CAREER,
    history: BASE_HISTORY,
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
          ? patchEntries(13, { assists: 6 }).map((entry) => ({
              ...entry,
              career: null,
              history: null,
            }))
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

test('C10 排行榜：访客默认出场榜、7 榜切换不重新请求', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards');

  await expect(page).toHaveURL(/\/leaderboards$/);
  await expect(page.getByRole('heading', { name: '排行榜' })).toBeVisible();
  await expect(page.getByLabel('赛季筛选')).toHaveValue(CURRENT);

  const tabs = page.locator('.detail-tab');
  await expect(tabs).toHaveText(['出场', '进球', '助攻', '场均进球', '场均助攻', '胜率', '乌龙']);
  await expect(activeTab(page)).toHaveText('出场');

  await expect(page.locator('.lb-row')).toHaveCount(5);
  // 出场降序；零出场球员仍保留在计数榜。
  await expect(names(page)).toHaveText(['排行甲', '排行乙', '排行丙', '排行戊', '排行丁']);
  await expect(ranks(page)).toHaveText(['1', '2', '3', '4', '5']);

  // P-86：名次表头标签隐藏但保留占位；表头与行内球员列起点一致。
  const headRank = page.locator('.lb-head-rank');
  await expect(headRank).toHaveText('名次');
  await expect(headRank).toBeHidden();
  const [headPlayer, rowPlayer] = await Promise.all([
    page.locator('.lb-head-player').boundingBox(),
    page.locator('.lb-row .lb-player').first().boundingBox(),
  ]);
  expect(headPlayer).not.toBeNull();
  expect(rowPlayer).not.toBeNull();
  expect(Math.abs(headPlayer!.x - rowPlayer!.x)).toBeLessThanOrEqual(1);

  expect(requests).toHaveLength(1);

  for (const label of ['进球', '助攻', '场均进球', '场均助攻', '胜率', '乌龙', '出场']) {
    await board(page, label).click();
    await expect(activeTab(page)).toHaveText(label);
  }
  // 切榜只在前端重排，不重新请求 STA-03。
  expect(requests).toHaveLength(1);

  expect(errors).toEqual([]);
});

test('C10 排行榜：三组数据只展示当前榜单主指标', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards');
  await expect(page.locator('.lb-row')).toHaveCount(5);
  await expect(page.locator('.lb-head-stat')).toHaveText(['当前赛季', '历史累计', '生涯累计']);
  await expect(page.locator('.lb-stat--career')).toHaveCount(5);
  await expect(page.locator('.lb-stat--history')).toHaveCount(5);

  const firstRow = page.locator('.lb-row').filter({ hasText: '排行甲' });
  await expect(firstRow.locator('.lb-stat--current .lb-stat-value')).toHaveText('10');
  await expect(firstRow.locator('.lb-stat--career .lb-stat-value')).toHaveText('30');
  await expect(firstRow.locator('.lb-stat--history .lb-stat-value')).toHaveText('20');

  // 切到进球榜后三组都只显示进球数，且不重新请求。
  await board(page, '进球').click();
  const firstRowAfter = page.locator('.lb-row').filter({ hasText: '排行甲' });
  await expect(firstRowAfter.locator('.lb-stat--current .lb-stat-value')).toHaveText('5');
  await expect(firstRowAfter.locator('.lb-stat--career .lb-stat-value')).toHaveText('15');
  await expect(firstRowAfter.locator('.lb-stat--history .lb-stat-value')).toHaveText('10');
  expect(requests).toHaveLength(1);

  expect(errors).toEqual([]);
});

test('C10 排行榜：生涯累计窗口隐藏累计固定列', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards?scope=all');
  await expect(page.locator('.lb-row')).toHaveCount(5);
  await expect(page.locator('.lb-head-stat')).toHaveText(['生涯累计']);
  await expect(page.locator('.lb-stat--career')).toHaveCount(0);
  await expect(page.locator('.lb-stat--history')).toHaveCount(0);

  const select = page.getByLabel('赛季筛选');
  await expect(select).toHaveValue('');
  await expect(select.locator('option', { hasText: '生涯累计' })).toHaveCount(0);
  await expect(select.locator('option', { hasText: '请选择赛季' })).toBeDisabled();

  await select.selectOption(CURRENT);
  await expect(page).toHaveURL(/\/leaderboards$/);
  await expect(page.locator('.lb-stat--career')).toHaveCount(5);
  await expect(page.locator('.lb-stat--history')).toHaveCount(5);

  expect(errors).toEqual([]);
});

test('C10 排行榜：赛季切换写入 URL 并按窗口重新请求', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const requests = await mockLeaderboard(page);
  await mockSeasons(page);

  await page.goto('/leaderboards');
  await expect(page.locator('.lb-row')).toHaveCount(5);

  const select = page.getByLabel('赛季筛选');
  await select.selectOption('2025');
  await expect(page).toHaveURL(/\/leaderboards\?season=2025$/);
  await expect(names(page).first()).toHaveText('排行甲');
  await expect(page.locator('.lb-head-stat--current')).toHaveText('2025 赛季');
  expect(requests).toHaveLength(2);
  expect(requests[1]?.get('season')).toBe('2025');
  expect(requests[1]?.get('scope')).toBe('season');

  // 切换赛季后榜单仍保留，且切榜不请求。
  await board(page, '助攻').click();
  await expect(page).toHaveURL(/\/leaderboards\?season=2025&board=assists$/);
  expect(requests).toHaveLength(2);

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
  const mainValues = page.locator('.lb-stat--current .lb-stat-value');
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
  await expect(page.getByLabel('赛季筛选')).toHaveValue('2025');
  await expect(activeTab(page)).toHaveText('助攻');
  await expect(page.locator('.lb-row')).toHaveCount(5);

  await page.goto('/leaderboards?season=26&scope=career&board=shirt');
  await expect(page.getByLabel('赛季筛选')).toHaveValue(CURRENT);
  await expect(activeTab(page)).toHaveText('出场');
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
  const select = page.getByLabel('赛季筛选');
  await expect(select).toHaveValue('2025');
  await expect(select.locator('option')).toHaveText([CURRENT, '2025']);

  expect(errors).toEqual([]);
});
