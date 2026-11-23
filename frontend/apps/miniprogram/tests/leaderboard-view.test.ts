import { describe, expect, it } from 'vitest';

import type { LeaderboardEntry } from '../miniprogram/types/api';
import {
  buildLeaderboardRows,
  LEADERBOARD_BOARD_KEYS,
  LEADERBOARD_BOARD_TABS,
} from '../miniprogram/utils/leaderboard-view';
import { leaderboardBoard, rankEntries } from '../miniprogram/utils/leaderboard-engine';

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

function names(rows: Array<{ entry: LeaderboardEntry }>): string[] {
  return rows.map((row) => row.entry.name);
}

function ranks(rows: Array<{ rank: number }>): number[] {
  return rows.map((row) => row.rank);
}

describe('榜单定义', () => {
  it('顺序与 02 §5.11 一致，默认出场', () => {
    expect(LEADERBOARD_BOARD_TABS.map((board) => board.key)).toEqual([...LEADERBOARD_BOARD_KEYS]);
    expect(leaderboardBoard('appearances').label).toBe('出场');
    expect(leaderboardBoard('missing' as never).label).toBe('出场');
  });
});

describe('排序与次级指标', () => {
  it('进球降序，同进球按助攻再按出场', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', goals: 5, assists: 1, appearances: 9 }),
        entry({ playerId: 2, name: '乙', goals: 5, assists: 3, appearances: 8 }),
        entry({ playerId: 3, name: '丙', goals: 4, assists: 9, appearances: 10 }),
        entry({ playerId: 4, name: '丁', goals: 5, assists: 3, appearances: 10 }),
      ],
      'goals',
    );

    // 乙/丁 进球与助攻相同，出场多者在前；甲次之；丙最后。
    expect(names(rows)).toEqual(['丁', '乙', '甲', '丙']);
  });

  it('号码升序兜底（无号码最后），再按球员 ID 升序', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '无号甲', goals: 0, jersey: null }),
        entry({ playerId: 2, name: '九号', goals: 0, jersey: 9 }),
        entry({ playerId: 3, name: '无号乙', goals: 0, jersey: null }),
        entry({ playerId: 4, name: '七号', goals: 0, jersey: 7 }),
      ],
      'goals',
    );

    expect(names(rows)).toEqual(['七号', '九号', '无号甲', '无号乙']);
  });
});

describe('标准竞赛排名', () => {
  it('主指标并列同名次（1、1、3）', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', goals: 5 }),
        entry({ playerId: 2, name: '乙', goals: 5 }),
        entry({ playerId: 3, name: '丙', goals: 2 }),
      ],
      'goals',
    );

    expect(ranks(rows)).toEqual([1, 1, 3]);
  });

  it('并列后继续同名次（5,5,2,2 → 1,1,3,3）', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', goals: 5 }),
        entry({ playerId: 2, name: '乙', goals: 5 }),
        entry({ playerId: 3, name: '丙', goals: 2 }),
        entry({ playerId: 4, name: '丁', goals: 2 }),
      ],
      'goals',
    );

    expect(ranks(rows)).toEqual([1, 1, 3, 3]);
  });

  it('主指标并列但次级不同，仍取同名次', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', goals: 5, assists: 1 }),
        entry({ playerId: 2, name: '乙', goals: 5, assists: 3 }),
        entry({ playerId: 3, name: '丙', goals: 4 }),
      ],
      'goals',
    );

    expect(names(rows)).toEqual(['乙', '甲', '丙']);
    expect(ranks(rows)).toEqual([1, 1, 3]);
  });
});

describe('场均与胜率：原始分数比较', () => {
  it('1/3 与 2/6 原始同分，同名次且按次级指标排序', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', appearances: 3, goals: 1, goalsPerMatch: 0.33 }),
        entry({ playerId: 2, name: '乙', appearances: 6, goals: 2, goalsPerMatch: 0.33 }),
        entry({ playerId: 3, name: '丙', appearances: 6, goals: 1, goalsPerMatch: 0.17 }),
      ],
      'goalsPerMatch',
    );

    expect(ranks(rows)).toEqual([1, 1, 3]);
    expect(names(rows)).toEqual(['乙', '甲', '丙']);
  });

  it('原始分数排序优先于两位小数展示值', () => {
    // 1/3 ≈ 0.333，33/100 = 0.33；展示都为 0.33，但原始分数 1/3 更高。
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '三分之一', appearances: 3, goals: 1, goalsPerMatch: 0.33 }),
        entry({ playerId: 2, name: '三成三', appearances: 100, goals: 33, goalsPerMatch: 0.33 }),
      ],
      'goalsPerMatch',
    );

    expect(names(rows)).toEqual(['三分之一', '三成三']);
    expect(ranks(rows)).toEqual([1, 2]);
  });

  it('胜率并列（2/3 与 4/6）同名次，按胜场次级排序', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', internalAppearances: 3, internalWins: 2, winRate: 66.7 }),
        entry({ playerId: 2, name: '丙', internalAppearances: 6, internalWins: 4, winRate: 66.7 }),
        entry({ playerId: 3, name: '乙', internalAppearances: 8, internalWins: 4, winRate: 50 }),
      ],
      'winRate',
    );

    expect(names(rows)).toEqual(['丙', '甲', '乙']);
    expect(ranks(rows)).toEqual([1, 1, 3]);
  });

  it('零分母球员不进入场均/胜率榜，但保留在计数榜', () => {
    const rows = [
      entry({
        playerId: 1,
        name: '有出场',
        appearances: 2,
        goals: 1,
        internalAppearances: 2,
        internalWins: 1,
      }),
      entry({
        playerId: 2,
        name: '零出场',
        appearances: 0,
        goals: 2,
        internalAppearances: 0,
        internalWins: 0,
      }),
    ];

    expect(names(rankEntries(rows, 'goalsPerMatch'))).toEqual(['有出场']);
    expect(names(rankEntries(rows, 'assistsPerMatch'))).toEqual(['有出场']);
    expect(names(rankEntries(rows, 'winRate'))).toEqual(['有出场']);
    expect(names(rankEntries(rows, 'goals'))).toEqual(['零出场', '有出场']);
    expect(names(rankEntries(rows, 'appearances'))).toEqual(['有出场', '零出场']);
  });
});

describe('展示文案', () => {
  it('场均两位小数、胜率一位小数、零值正常展示', () => {
    expect(leaderboardBoard('goalsPerMatch').mainText(entry({ goalsPerMatch: 0.5 }))).toBe('0.50');
    expect(leaderboardBoard('assistsPerMatch').mainText(entry({ assistsPerMatch: 0 }))).toBe(
      '0.00',
    );
    expect(leaderboardBoard('winRate').mainText(entry({ winRate: 66.7 }))).toBe('66.7%');
    expect(leaderboardBoard('winRate').mainText(entry({ winRate: null }))).toBe('—');
    expect(leaderboardBoard('ownGoals').mainText(entry({ ownGoals: 0 }))).toBe('0');
  });

});

describe('行视图', () => {
  it('预计算名次、号码、位置、头像回落与当前榜文案', () => {
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
