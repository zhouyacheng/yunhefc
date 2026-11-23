import type { LeaderboardEntry, LeaderboardTotals } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import { buildLeaderboardRows } from './rows';

const totals = (patch: Partial<LeaderboardTotals>): LeaderboardTotals => ({
  appearances: 0,
  goals: 0,
  assists: 0,
  ownGoals: 0,
  internalAppearances: 0,
  internalWins: 0,
  winRate: null,
  goalsPerMatch: 0,
  assistsPerMatch: 0,
  ...patch,
});

const base: LeaderboardEntry = {
  playerId: 1,
  name: '球员',
  jersey: null,
  position: 0,
  avatarUrl: null,
  appearances: 10,
  goals: 5,
  assists: 2,
  ownGoals: 0,
  internalAppearances: 3,
  internalWins: 2,
  winRate: 66.7,
  goalsPerMatch: 0.5,
  assistsPerMatch: 0.2,
  career: totals({ appearances: 30, goals: 15, assists: 6, goalsPerMatch: 0.5 }),
  history: totals({ appearances: 20, goals: 10, assists: 4, goalsPerMatch: 0.5 }),
};

describe('buildLeaderboardRows', () => {
  it('当前窗口与两组累计都只格式化当前榜单主指标', () => {
    const [row] = buildLeaderboardRows([base], 'appearances');
    expect(row?.currentText).toBe('10');
    expect(row?.careerText).toBe('30');
    expect(row?.historyText).toBe('20');
    expect(row?.currentZero).toBe(false);
    expect(row?.careerZero).toBe(false);
    expect(row?.historyZero).toBe(false);

    const [goals] = buildLeaderboardRows([base], 'goals');
    expect(goals?.currentText).toBe('5');
    expect(goals?.careerText).toBe('15');
    expect(goals?.historyText).toBe('10');
  });

  it('零值与破折号标记为弱化，累计缺失时不输出文本', () => {
    const zero = {
      ...base,
      appearances: 0,
      career: totals({}),
      history: totals({}),
    };
    const [row] = buildLeaderboardRows([zero], 'appearances');
    expect(row?.currentZero).toBe(true);
    expect(row?.careerZero).toBe(true);
    expect(row?.historyZero).toBe(true);

    const [all] = buildLeaderboardRows([{ ...base, career: null, history: null }], 'appearances');
    expect(all?.careerText).toBeNull();
    expect(all?.historyText).toBeNull();

    const [winRate] = buildLeaderboardRows([{ ...base, winRate: null }], 'winRate');
    expect(winRate?.currentText).toBe('—');
    expect(winRate?.currentZero).toBe(true);
  });
});
