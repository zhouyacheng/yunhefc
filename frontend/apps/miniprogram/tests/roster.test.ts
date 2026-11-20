import { describe, expect, it } from 'vitest';

import type { AttendanceRow, RegistrationRow } from '../miniprogram/types/api';
import { buildRoster, emptyRoster } from '../miniprogram/utils/roster';

function row(
  id: number,
  playerId: number,
  type: number,
  status: number,
  name = `球员${playerId}`,
): RegistrationRow {
  return {
    id,
    type,
    status,
    registeredAt: '2026-12-01T10:00:00+08:00',
    player: { id: playerId, name, jersey: playerId, position: 2, skill: 5 },
  };
}

function attendance(playerId: number, status: number): AttendanceRow {
  return {
    id: playerId,
    status,
    source: 0,
    recordedAt: '2026-12-06T19:00:00+08:00',
    player: { id: playerId, name: `球员${playerId}`, jersey: playerId, position: 2 },
  };
}

describe('buildRoster', () => {
  it('splits formal/guest confirmations and numbers the waitlist', () => {
    const roster = buildRoster(
      [row(1, 11, 1, 0, '正式A'), row(2, 12, 0, 0, '外挂A')],
      [row(3, 13, 1, 1, '候补A'), row(4, 14, 1, 1, '候补B')],
      [],
    );

    expect(roster.formalConfirmed.map((entry) => entry.row.player.name)).toEqual(['正式A']);
    expect(roster.guestConfirmed.map((entry) => entry.row.player.name)).toEqual(['外挂A']);
    expect(
      roster.formalWaitlist.map((entry) => [entry.row.player.name, entry.waitlistOrder]),
    ).toEqual([
      ['候补A', 1],
      ['候补B', 2],
    ]);
    expect(roster.total).toBe(4);
  });

  it('joins attendance by player id and leaves missing records empty', () => {
    const roster = buildRoster([row(1, 11, 1, 0), row(2, 12, 1, 0)], [], [attendance(11, 0)]);

    expect(roster.formalConfirmed[0].attendance?.status).toBe(0);
    expect(roster.formalConfirmed[1].attendance).toBeNull();
  });

  it('ignores cancelled rows and deduplicates players', () => {
    const roster = buildRoster([row(1, 11, 1, 0), row(2, 11, 1, 0), row(3, 12, 1, 2)], [], []);

    expect(roster.formalConfirmed).toHaveLength(1);
  });

  it('returns an empty roster', () => {
    expect(emptyRoster()).toEqual({
      formalConfirmed: [],
      formalWaitlist: [],
      guestConfirmed: [],
      total: 0,
    });
  });
});
