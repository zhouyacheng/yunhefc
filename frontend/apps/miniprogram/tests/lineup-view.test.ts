import { describe, expect, it } from 'vitest';

import type { LineupView, MatchSummary } from '../miniprogram/types/api';
import { buildLineupBoard } from '../miniprogram/utils/lineup-view';

function match(overrides: Partial<MatchSummary> = {}): MatchSummary {
  return {
    id: 1,
    title: '比赛',
    type: 0,
    typeLabel: 'OFFICIAL',
    startAt: '2026-12-06T20:00:00+08:00',
    endAt: '2026-12-06T22:00:00+08:00',
    venue: '球场',
    season: '2026',
    homeTeamName: '云河FC',
    awayTeamName: '对手FC',
    ourSide: 0,
    scoreHome: 0,
    scoreAway: 0,
    status: 2,
    statusLabel: 'LOCKED',
    totalMatchNo: 1,
    seasonMatchNo: 1,
    capacity: 20,
    guestCapacity: 4,
    waitlistCapacity: 3,
    confirmedCount: 10,
    guestCount: 0,
    waitlistCount: 0,
    ...overrides,
  };
}

const lineup: LineupView = {
  side: 0,
  formation: {
    id: 1,
    name: '3-3-1',
    isPreset: true,
    slots: [
      { slotKey: 'GK', role: 0, roleLabel: 'GOALKEEPER', x: 50, y: 90 },
      { slotKey: 'LB', role: 1, roleLabel: 'DEFENDER', x: 20, y: 70 },
    ],
  },
  starters: [
    { slotKey: 'GK', player: { id: 1, name: '门神', jersey: 1, position: 0 } },
    { slotKey: 'LB', player: null },
  ],
  bench: [{ id: 9, name: '替补甲', jersey: 9, position: 3 }],
};

describe('buildLineupBoard', () => {
  it('maps formation slots to filled and empty rows', () => {
    const board = buildLineupBoard(lineup, match());

    expect(board.formationName).toBe('3-3-1');
    expect(board.slots[0]).toMatchObject({
      slotKey: 'GK',
      filled: true,
      jerseyText: '#1',
      name: '门神',
    });
    expect(board.slots[1]).toMatchObject({
      slotKey: 'LB',
      filled: false,
      roleText: '后卫',
      name: '空位',
    });
    expect(board.bench[0].label).toBe('替补甲 #9');
    expect(board.opponent).toBe(false);
  });

  it('marks opponent sides for external matches only', () => {
    expect(buildLineupBoard({ ...lineup, side: 1 }, match()).opponent).toBe(true);
    expect(buildLineupBoard({ ...lineup, side: 1 }, match({ type: 1 })).opponent).toBe(false);
  });
});
