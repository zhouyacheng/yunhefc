import { describe, expect, it } from 'vitest';

import { eventTeamRoster, type GroupedPlayer, type GroupingView, type MatchSummary } from './index';

interface TestPlayer {
  id: number;
  name: string;
}

const confirmed: TestPlayer[] = [
  { id: 1, name: '门将01' },
  { id: 2, name: '后卫01' },
  { id: 3, name: '前锋01' },
  { id: 4, name: '外挂01' },
];

function grouped(ids: number[]): GroupedPlayer[] {
  return ids.map((playerId) => ({
    playerId,
    name: `球员${playerId}`,
    jersey: null,
    position: 0,
  }));
}

function grouping(
  a: number[],
  b: number[],
  savedAt: string | null = '2026-01-01T00:00:00',
): Pick<GroupingView, 'savedAt' | 'teams'> {
  return { savedAt, teams: { A: grouped(a), B: grouped(b) } };
}

const internal: Pick<MatchSummary, 'type' | 'ourSide'> = { type: 1, ourSide: null };
const external: Pick<MatchSummary, 'type' | 'ourSide'> = { type: 0, ourSide: 1 };

const ids = (players: TestPlayer[]): number[] => players.map((player) => player.id);

describe('eventTeamRoster', () => {
  it('returns every confirmed player for internal matches without a grouping', () => {
    expect(ids(eventTeamRoster({ match: internal, team: 0, confirmed }))).toEqual([1, 2, 3, 4]);
    expect(ids(eventTeamRoster({ match: internal, team: 0, confirmed, grouping: null }))).toEqual([
      1, 2, 3, 4,
    ]);
  });

  it('ignores draft groupings', () => {
    const draft = grouping([1, 2], [3, 4], null);
    expect(ids(eventTeamRoster({ match: internal, team: 0, confirmed, grouping: draft }))).toEqual([
      1, 2, 3, 4,
    ]);
  });

  it('maps HOME to group A and AWAY to group B when the grouping is saved', () => {
    const saved = grouping([1, 2], [3, 4]);
    expect(ids(eventTeamRoster({ match: internal, team: 0, confirmed, grouping: saved }))).toEqual([
      1, 2,
    ]);
    expect(ids(eventTeamRoster({ match: internal, team: 1, confirmed, grouping: saved }))).toEqual([
      3, 4,
    ]);
  });

  it('intersects grouping members with confirmed registrations', () => {
    const saved = grouping([1, 2], [3, 99]);
    const roster = confirmed.filter((player) => player.id !== 4);
    expect(
      ids(eventTeamRoster({ match: internal, team: 0, confirmed: roster, grouping: saved })),
    ).toEqual([1, 2]);
    expect(
      ids(eventTeamRoster({ match: internal, team: 1, confirmed: roster, grouping: saved })),
    ).toEqual([3]);
  });

  it('keeps only our side for external matches', () => {
    expect(ids(eventTeamRoster({ match: external, team: 1, confirmed }))).toEqual([1, 2, 3, 4]);
    expect(ids(eventTeamRoster({ match: external, team: 0, confirmed }))).toEqual([]);
  });

  it('treats training matches like internal matches', () => {
    const training: Pick<MatchSummary, 'type' | 'ourSide'> = { type: 2, ourSide: null };
    const saved = grouping([1], [2, 3]);
    expect(ids(eventTeamRoster({ match: training, team: 1, confirmed, grouping: saved }))).toEqual([
      2, 3,
    ]);
  });

  it('returns an empty list without a match', () => {
    expect(eventTeamRoster({ match: null, team: 0, confirmed })).toEqual([]);
  });
});
