import type { AttendanceRow, RegistrationRow } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  buildRoster,
  emptyRoster,
  rosterDiff,
  rosterSnapshot,
  type RosterSections,
} from './roster';

function row(
  playerId: number,
  name: string,
  type: number,
  status: number,
  registeredAt = '2026-10-25T09:00:00Z',
): RegistrationRow {
  return {
    id: playerId,
    player: { id: playerId, name, jersey: playerId, position: 1 },
    type,
    status,
    registeredAt,
  };
}

function attendance(playerId: number, status = 0, source = 0): AttendanceRow {
  return {
    id: playerId,
    player: { id: playerId, name: `player-${playerId}`, jersey: null, position: 1 },
    status,
    source,
    recordedAt: '2026-10-25T20:00:00Z',
  };
}

describe('buildRoster', () => {
  it('splits confirmed rows into formal and guest and keeps waitlist order', () => {
    const roster = buildRoster(
      [row(1, '门将', 1, 0), row(2, '外挂', 0, 0)],
      [row(3, '候补甲', 1, 1), row(4, '候补乙', 1, 1)],
      [],
    );

    expect(roster.formalConfirmed.map((entry) => entry.row.player.name)).toEqual(['门将']);
    expect(roster.guestConfirmed.map((entry) => entry.row.player.name)).toEqual(['外挂']);
    expect(roster.formalWaitlist.map((entry) => entry.waitlistOrder)).toEqual([1, 2]);
    expect(roster.total).toBe(4);
  });

  it('left-joins attendance by playerId and leaves missing records null', () => {
    const roster = buildRoster(
      [row(1, '门将', 1, 0), row(2, '后卫', 1, 0)],
      [],
      [attendance(1, 1, 2)],
    );

    expect(roster.formalConfirmed[0]?.attendance).toMatchObject({ status: 1, source: 2 });
    expect(roster.formalConfirmed[1]?.attendance).toBeNull();
  });

  it('ignores cancelled rows and deduplicates the same player', () => {
    const roster = buildRoster(
      [row(1, '门将', 1, 0), row(1, '门将', 1, 0), row(2, '已取消', 1, 2)],
      [row(1, '门将', 1, 1)],
      [],
    );

    expect(roster.formalConfirmed).toHaveLength(1);
    expect(roster.formalWaitlist).toHaveLength(0);
    expect(roster.total).toBe(1);
  });

  it('matches attendance for waitlist rows too', () => {
    const roster = buildRoster([], [row(3, '候补', 1, 1)], [attendance(3, 2, 1)]);
    expect(roster.formalWaitlist[0]?.attendance?.status).toBe(2);
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

describe('rosterDiff', () => {
  function roster(
    formal: RegistrationRow[],
    waitlist: RegistrationRow[],
    guest: RegistrationRow[] = [],
  ): RosterSections {
    return buildRoster([...formal, ...guest], waitlist, []);
  }

  it('reports removals and promotions together', () => {
    const previous = rosterSnapshot(
      roster([row(1, '甲', 1, 0), row(2, '乙', 1, 0)], [row(3, '丙', 1, 1)]),
    );
    const next = roster([row(1, '甲', 1, 0), row(3, '丙', 1, 0)], []);

    expect(rosterDiff(previous, next)).toMatchObject({
      changed: true,
      removed: ['乙'],
      promoted: ['丙'],
      added: [],
      text: '名单已更新：乙 已移除；丙 已递补为正式报名。',
    });
  });

  it('reports a new confirmed player as added', () => {
    const previous = rosterSnapshot(roster([row(1, '甲', 1, 0)], []));
    const next = roster([row(1, '甲', 1, 0), row(2, '乙', 1, 0)], []);

    expect(rosterDiff(previous, next)).toMatchObject({
      changed: true,
      added: ['乙'],
      text: '名单已更新：乙 已加入名单。',
    });
  });

  it('returns no text when nothing changed', () => {
    const previous = rosterSnapshot(roster([row(1, '甲', 1, 0)], [row(2, '乙', 1, 1)]));
    const next = roster([row(1, '甲', 1, 0)], [row(2, '乙', 1, 1)]);

    expect(rosterDiff(previous, next)).toEqual({
      changed: false,
      promoted: [],
      removed: [],
      added: [],
      text: '',
    });
  });

  it('returns no text without a snapshot', () => {
    expect(rosterDiff(null, roster([row(1, '甲', 1, 0)], []))).toEqual({
      changed: false,
      promoted: [],
      removed: [],
      added: [],
      text: '',
    });
  });
});
