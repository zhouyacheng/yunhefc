import { describe, expect, it } from 'vitest';

import {
  parseLeaderboardBoard,
  parseLeaderboardQuery,
  parseLeaderboardScope,
  serializeLeaderboardQuery,
  toLeaderboardRequest,
} from './query';

const now = new Date('2026-06-01T00:00:00Z');

describe('parseLeaderboardBoard', () => {
  it('接受白名单榜单', () => {
    expect(parseLeaderboardBoard('winRate')).toBe('winRate');
    expect(parseLeaderboardBoard(' ownGoals ')).toBe('ownGoals');
  });

  it('数组取首值', () => {
    expect(parseLeaderboardBoard(['assists', 'goals'])).toBe('assists');
  });

  it('非法或缺失回退出场榜', () => {
    expect(parseLeaderboardBoard(undefined)).toBe('appearances');
    expect(parseLeaderboardBoard('shirt')).toBe('appearances');
  });
});

describe('parseLeaderboardScope', () => {
  it('仅 all 进入生涯累计', () => {
    expect(parseLeaderboardScope('all')).toBe('all');
    expect(parseLeaderboardScope(['all'])).toBe('all');
    expect(parseLeaderboardScope('season')).toBe('season');
    expect(parseLeaderboardScope('career')).toBe('season');
    expect(parseLeaderboardScope(undefined)).toBe('season');
  });
});

describe('parseLeaderboardQuery', () => {
  it('缺失时默认当前赛季、赛季口径与出场榜', () => {
    expect(parseLeaderboardQuery({}, now)).toEqual({
      season: '2026',
      scope: 'season',
      board: 'appearances',
    });
  });

  it('解析赛季、生涯与榜单', () => {
    expect(parseLeaderboardQuery({ season: '2025', scope: 'all', board: 'assists' }, now)).toEqual({
      season: '2025',
      scope: 'all',
      board: 'assists',
    });
  });

  it('非法值静默回退', () => {
    expect(parseLeaderboardQuery({ season: '26', scope: 'career', board: 'x' }, now)).toEqual({
      season: '2026',
      scope: 'season',
      board: 'appearances',
    });
  });

  it('空字符串查询与数组取值', () => {
    expect(
      parseLeaderboardQuery({ season: ['2025', '2024'], scope: [''], board: ['winRate'] }, now),
    ).toEqual({ season: '2025', scope: 'season', board: 'winRate' });
    expect(parseLeaderboardQuery({ season: '', scope: '', board: '' }, now)).toEqual({
      season: '2026',
      scope: 'season',
      board: 'appearances',
    });
  });
});

describe('serializeLeaderboardQuery', () => {
  it('默认赛季、赛季口径与出场榜不写 URL', () => {
    expect(
      serializeLeaderboardQuery({ season: '2026', scope: 'season', board: 'appearances' }, now),
    ).toEqual({});
  });

  it('历史赛季与非默认榜单写入 URL', () => {
    expect(
      serializeLeaderboardQuery({ season: '2025', scope: 'season', board: 'assists' }, now),
    ).toEqual({ season: '2025', board: 'assists' });
  });

  it('生涯累计写 scope=all 并省略 season', () => {
    expect(
      serializeLeaderboardQuery({ season: '2025', scope: 'all', board: 'appearances' }, now),
    ).toEqual({ scope: 'all' });
    expect(
      serializeLeaderboardQuery({ season: '2025', scope: 'all', board: 'winRate' }, now),
    ).toEqual({ scope: 'all', board: 'winRate' });
  });
});

describe('toLeaderboardRequest', () => {
  it('赛季口径显式带 season（含默认年份）', () => {
    expect(toLeaderboardRequest({ season: '2026', scope: 'season', board: 'goals' })).toEqual({
      season: '2026',
      scope: 'season',
    });
  });

  it('生涯口径忽略 season', () => {
    expect(toLeaderboardRequest({ season: '2026', scope: 'all', board: 'goals' })).toEqual({
      scope: 'all',
    });
  });
});
