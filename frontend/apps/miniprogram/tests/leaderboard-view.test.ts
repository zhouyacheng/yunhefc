import { describe, expect, it } from 'vitest';

import type { LeaderboardEntry } from '../miniprogram/types/api';
import {
  buildLeaderboardRows,
  LEADERBOARD_BOARD_KEYS,
  LEADERBOARD_BOARD_TABS,
} from '../miniprogram/utils/leaderboard-view';

const base: LeaderboardEntry = {
  playerId: 1,
  name: '球员',
  jersey: 10,
  position: 2,
  avatarUrl: null,
  appearances: 0,
  goals: 0,
  assists: 0,
  ownGoals: 0,
  internalAppearances: 0,
  internalWins: 0,
  winRate: null,
  goalsPerMatch: 0,
  assistsPerMatch: 0,
  career: null,
  history: null,
};

function entry(patch: Partial<LeaderboardEntry>): LeaderboardEntry {
  return { ...base, ...patch };
}

describe('榜单 Tab', () => {
  it('顺序与文案来自排名单源', () => {
    expect(LEADERBOARD_BOARD_TABS.map((board) => board.key)).toEqual([...LEADERBOARD_BOARD_KEYS]);
    expect(LEADERBOARD_BOARD_TABS.map((board) => board.label)).toEqual([
      '出场',
      '进球',
      '助攻',
      '场均进球',
      '场均助攻',
      '胜率',
      '乌龙',
    ]);
  });
});

describe('行视图', () => {
  it('预计算名次、头像回落与当前榜文案', () => {
    const rows = buildLeaderboardRows(
      [
        entry({
          playerId: 1,
          name: '张三',
          jersey: 7,
          position: 3,
          avatarUrl: 'https://cdn.example.com/a.png',
          goals: 2,
          assists: 1,
        }),
        entry({
          playerId: 2,
          name: '李四',
          jersey: null,
          position: 0,
          avatarUrl: 'oss://key',
          goals: 1,
        }),
      ],
      'goals',
    );

    expect(rows[0]).toMatchObject({
      playerId: 1,
      rank: 1,
      name: '张三',
      avatarUrl: 'https://cdn.example.com/a.png',
      initial: '张',
      currentText: '2',
    });
    expect(rows[1]).toMatchObject({
      playerId: 2,
      avatarUrl: '',
      initial: '李',
    });
  });

  it('空名单返回空行', () => {
    expect(buildLeaderboardRows([], 'goals')).toEqual([]);
  });

  it('生涯与历史累计只展示当前榜单主指标，无累计返回 null', () => {
    const history = {
      appearances: 12,
      goals: 5,
      assists: 3,
      ownGoals: 1,
      internalAppearances: 6,
      internalWins: 4,
      winRate: 66.7,
      goalsPerMatch: 0.42,
      assistsPerMatch: 0.25,
    };
    const rows = buildLeaderboardRows(
      [
        entry({
          playerId: 1,
          career: { ...history, appearances: 15, goals: 6 },
          history,
        }),
        entry({ playerId: 2, name: '无累计', career: null, history: null }),
      ],
      'appearances',
    );

    expect(rows[0].careerText).toBe('15');
    expect(rows[0].historyText).toBe('12');
    expect(rows[1].careerText).toBeNull();
    expect(rows[1].historyText).toBeNull();
  });

  it('切换榜单时累计展示当前榜单主指标', () => {
    const history = {
      appearances: 12,
      goals: 5,
      assists: 3,
      ownGoals: 1,
      internalAppearances: 6,
      internalWins: 4,
      winRate: null,
      goalsPerMatch: 0,
      assistsPerMatch: 0,
    };
    const entries = [entry({ playerId: 1, career: history, history })];
    const appearances = buildLeaderboardRows(entries, 'appearances');
    const goals = buildLeaderboardRows(entries, 'goals');

    expect(appearances[0].careerText).toBe('12');
    expect(goals[0].careerText).toBe('5');
    expect(appearances[0].historyText).toBe('12');
    expect(goals[0].historyText).toBe('5');
  });
});
