import type { PlayerStats } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  avatarInitial,
  avatarSrc,
  canExpandGroup,
  GROUP_PREVIEW_SIZE,
  groupByPosition,
  mergeTeamStats,
  visibleGroupPlayers,
  type TeamPlayer,
} from './merge-stats';

function player(overrides: Partial<TeamPlayer> = {}): TeamPlayer {
  return {
    id: 1001,
    name: '测试球员',
    jersey: 21,
    position: 3,
    positionLabel: 'FORWARD',
    region: '上海',
    avatarUrl: null,
    skill: 4,
    type: 1,
    appearances: 3,
    ...overrides,
  };
}

function stats(playerId: number, appearances: number, goals: number, assists: number): PlayerStats {
  return { playerId, season: '2026', appearances, goals, assists };
}

describe('mergeTeamStats', () => {
  it('merges stats by playerId and keeps PLR-01 order', () => {
    const players = [player({ id: 2 }), player({ id: 1 }), player({ id: 3 })];
    const merged = mergeTeamStats(players, [stats(1, 5, 2, 1), stats(3, 4, 0, 3)]);

    expect(merged.map((item) => item.id)).toEqual([2, 1, 3]);
    expect(merged[1]).toMatchObject({ appearances: 5, goals: 2, assists: 1 });
    expect(merged[2]).toMatchObject({ appearances: 4, goals: 0, assists: 3 });
  });

  it('falls back to PLR-01 appearances and null goals/assists when stats are missing', () => {
    const merged = mergeTeamStats([player({ id: 9, appearances: 7 })], []);

    expect(merged[0]).toMatchObject({ appearances: 7, goals: null, assists: null });
  });
});

describe('groupByPosition', () => {
  it('groups in 前锋 → 中场 → 后卫 → 门将 order', () => {
    const groups = groupByPosition(
      mergeTeamStats(
        [
          player({ id: 1, position: 0 }),
          player({ id: 2, position: 1 }),
          player({ id: 3, position: 2 }),
          player({ id: 4, position: 3 }),
          player({ id: 5, position: 2 }),
        ],
        [],
      ),
    );

    expect(groups.map((group) => group.position)).toEqual([3, 2, 1, 0]);
    expect(groups.map((group) => group.players.length)).toEqual([1, 2, 1, 1]);
  });

  it('keeps server order inside each group and drops empty groups', () => {
    const groups = groupByPosition(
      mergeTeamStats(
        [
          player({ id: 11, position: 2 }),
          player({ id: 12, position: 3 }),
          player({ id: 13, position: 2 }),
          player({ id: 14, position: 0 }),
        ],
        [],
      ),
    );

    expect(groups.map((group) => group.position)).toEqual([3, 2, 0]);
    expect(groups[1]?.players.map((item) => item.id)).toEqual([11, 13]);
  });
});

describe('visibleGroupPlayers', () => {
  const group = {
    position: 2,
    players: mergeTeamStats(
      Array.from({ length: 12 }, (_, index) => player({ id: index + 1, position: 2 })),
      [],
    ),
  };

  it('previews the first 10 players by default', () => {
    expect(visibleGroupPlayers(group, false)).toHaveLength(GROUP_PREVIEW_SIZE);
    expect(visibleGroupPlayers(group, false)[9]?.id).toBe(10);
  });

  it('shows the whole group when expanded', () => {
    expect(visibleGroupPlayers(group, true)).toHaveLength(12);
  });
});

describe('canExpandGroup', () => {
  function groupOf(size: number) {
    return {
      position: 2,
      players: mergeTeamStats(
        Array.from({ length: size }, (_, index) => player({ id: index + 1, position: 2 })),
        [],
      ),
    };
  }

  it('always expands groups longer than the preview size', () => {
    expect(canExpandGroup(groupOf(12), { fullLoaded: true, truncated: false })).toBe(true);
    expect(canExpandGroup(groupOf(12), { fullLoaded: false, truncated: true })).toBe(true);
  });

  it('expands a group at the preview limit only while the roster is truncated', () => {
    expect(canExpandGroup(groupOf(10), { fullLoaded: false, truncated: true })).toBe(true);
    expect(canExpandGroup(groupOf(10), { fullLoaded: false, truncated: false })).toBe(false);
    expect(canExpandGroup(groupOf(10), { fullLoaded: true, truncated: true })).toBe(false);
  });

  it('never expands a complete group shorter than the preview size', () => {
    expect(canExpandGroup(groupOf(4), { fullLoaded: false, truncated: true })).toBe(false);
  });
});

describe('avatarSrc', () => {
  it('accepts http(s) signed URLs only', () => {
    expect(avatarSrc('https://oss.example.com/a.jpg?sign=1')).toBe(
      'https://oss.example.com/a.jpg?sign=1',
    );
    expect(avatarSrc('http://oss.example.com/a.jpg')).toBe('http://oss.example.com/a.jpg');
    expect(avatarSrc(null)).toBeNull();
    expect(avatarSrc(undefined)).toBeNull();
    expect(avatarSrc('')).toBeNull();
    expect(avatarSrc('avatar/1/a.jpg')).toBeNull();
  });
});

describe('avatarInitial', () => {
  it('uses the first character of the name and falls back to ?', () => {
    expect(avatarInitial('测试中场01')).toBe('测');
    expect(avatarInitial('🐯小虎')).toBe('🐯');
    expect(avatarInitial('  ')).toBe('?');
  });
});
