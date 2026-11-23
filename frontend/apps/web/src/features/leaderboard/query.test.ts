import { describe, expect, it } from 'vitest';

import {
  parseLeaderboardBoard,
  parseLeaderboardQuery,
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

describe('parseLeaderboardQuery', () => {
  it('缺失时默认当前赛季与出场榜', () => {
    expect(parseLeaderboardQuery({}, now)).toEqual({
      season: '2026',
      board: 'appearances',
    });
  });

  it('解析赛季与榜单，忽略 scope 参数', () => {
    expect(parseLeaderboardQuery({ season: '2025', scope: 'all', board: 'assists' }, now)).toEqual({
      season: '2025',
      board: 'assists',
    });
  });

  it('非法值静默回退', () => {
    expect(parseLeaderboardQuery({ season: '26', scope: 'career', board: 'x' }, now)).toEqual({
      season: '2026',
      board: 'appearances',
    });
  });

  it('空字符串查询与数组取值', () => {
    expect(
      parseLeaderboardQuery({ season: ['2025', '2024'], scope: [''], board: ['winRate'] }, now),
    ).toEqual({ season: '2025', board: 'winRate' });
    expect(parseLeaderboardQuery({ season: '', scope: '', board: '' }, now)).toEqual({
      season: '2026',
      board: 'appearances',
    });
  });
});

describe('serializeLeaderboardQuery', () => {
  it('默认赛季与出场榜不写 URL', () => {
    expect(serializeLeaderboardQuery({ season: '2026', board: 'appearances' }, now)).toEqual({});
  });

  it('历史赛季与非默认榜单写入 URL，不写 scope', () => {
    expect(serializeLeaderboardQuery({ season: '2025', board: 'assists' }, now)).toEqual({
      season: '2025',
      board: 'assists',
    });
  });
});

describe('toLeaderboardRequest', () => {
  it('恒为赛季窗口并显式带 scope', () => {
    expect(toLeaderboardRequest({ season: '2026', board: 'goals' })).toEqual({
      season: '2026',
      scope: 'season',
    });
    expect(toLeaderboardRequest({ season: '2025', board: 'appearances' })).toEqual({
      season: '2025',
      scope: 'season',
    });
  });
});
