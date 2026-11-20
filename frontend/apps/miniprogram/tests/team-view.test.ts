import { describe, expect, it } from 'vitest';

import type { PlayerPublic, PlayerStats } from '../miniprogram/types/api';
import { avatarInitial, avatarSrc, buildTeamGroups } from '../miniprogram/utils/team-view';

function player(overrides: Partial<PlayerPublic> = {}): PlayerPublic {
  return {
    id: 1,
    name: '张三',
    jersey: 7,
    position: 3,
    positionLabel: '前锋',
    region: '广州',
    skill: 4.5,
    type: 1,
    appearances: 2,
    ...overrides,
  };
}

function stats(overrides: Partial<PlayerStats> = {}): PlayerStats {
  return {
    playerId: 1,
    season: '2026',
    appearances: 5,
    goals: 3,
    assists: 1,
    ...overrides,
  };
}

describe('buildTeamGroups', () => {
  it('merges season stats and falls back to PLR-01 appearances', () => {
    const groups = buildTeamGroups(
      [player({ id: 1 }), player({ id: 2, name: '李四', position: 2, jersey: null })],
      [stats({ playerId: 1 })],
    );
    const byId = new Map(groups.flatMap((group) => group.players).map((row) => [row.id, row]));

    expect(byId.get(1)).toMatchObject({
      jerseyText: '#7',
      appearances: 5,
      goalsText: '3',
      assistsText: '1',
    });
    expect(byId.get(2)).toMatchObject({
      jerseyText: '#—',
      appearances: 2,
      goalsText: '—',
      assistsText: '—',
    });
  });

  it('orders groups forward → midfield → defence → keeper and drops empty groups', () => {
    const groups = buildTeamGroups(
      [
        player({ id: 1, position: 0, name: '门将' }),
        player({ id: 2, position: 3, name: '前锋' }),
        player({ id: 3, position: 2, name: '中场' }),
      ],
      [],
    );

    expect(groups.map((group) => [group.position, group.label])).toEqual([
      [3, '前锋'],
      [2, '中场'],
      [0, '门将'],
    ]);
  });

  it('builds avatar and meta display fields', () => {
    const [group] = buildTeamGroups(
      [
        player({
          id: 1,
          avatarUrl: 'https://cdn.example.com/a.png',
          region: null,
          skill: 4.5,
        }),
      ],
      [],
    );

    expect(group.players[0]).toMatchObject({
      avatarUrl: 'https://cdn.example.com/a.png',
      initial: '张',
      regionText: '—',
      stars: [
        { part: 'full', key: 0 },
        { part: 'full', key: 1 },
        { part: 'full', key: 2 },
        { part: 'full', key: 3 },
        { part: 'half', key: 4 },
      ],
    });
  });

  it('returns no groups for an empty roster', () => {
    expect(buildTeamGroups([], [])).toEqual([]);
  });
});

describe('avatar helpers', () => {
  it('only accepts http(s) URLs', () => {
    expect(avatarSrc('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png');
    expect(avatarSrc('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png');
    expect(avatarSrc('oss://key')).toBeNull();
    expect(avatarSrc(null)).toBeNull();
  });

  it('uses the first code point for the fallback initial', () => {
    expect(avatarInitial('张三')).toBe('张');
    expect(avatarInitial('  ')).toBe('?');
  });
});
