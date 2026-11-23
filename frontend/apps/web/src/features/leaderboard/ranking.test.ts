import type { LeaderboardEntry } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  LEADERBOARD_BOARD_KEYS,
  LEADERBOARD_BOARDS,
  leaderboardBoard,
  rankEntries,
} from './ranking';

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

describe('Web 排名适配层', () => {
  it('榜单键与查询键同源，默认出场', () => {
    expect(LEADERBOARD_BOARDS.map((board) => board.key)).toEqual([...LEADERBOARD_BOARD_KEYS]);
    expect(leaderboardBoard('appearances').label).toBe('出场');
  });

  it('排名函数经适配层可用，并列取 1、1、3', () => {
    const rows = rankEntries(
      [
        entry({ playerId: 1, name: '甲', goals: 5 }),
        entry({ playerId: 2, name: '乙', goals: 5 }),
        entry({ playerId: 3, name: '丙', goals: 2 }),
      ],
      'goals',
    );

    expect(rows.map((row) => row.entry.name)).toEqual(['甲', '乙', '丙']);
    expect(rows.map((row) => row.rank)).toEqual([1, 1, 3]);
  });

  it('保留 LeaderboardEntry 具体类型，累计字段可读', () => {
    const rows = rankEntries(
      [
        entry({
          career: {
            appearances: 15,
            goals: 6,
            assists: 0,
            ownGoals: 0,
            internalAppearances: 0,
            internalWins: 0,
            winRate: null,
            goalsPerMatch: 0,
            assistsPerMatch: 0,
          },
        }),
      ],
      'appearances',
    );

    expect(rows[0]?.entry.career?.appearances).toBe(15);
  });
});
