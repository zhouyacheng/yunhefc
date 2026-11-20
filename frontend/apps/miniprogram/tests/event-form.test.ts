import { describe, expect, it } from 'vitest';

import type { GroupingView, MatchEvent, MatchSummary } from '../miniprogram/types/api';
import {
  allowedEventTypes,
  eventCreateAllowed,
  eventEditAllowed,
  eventPayload,
  eventTeamRoster,
  teamLockedFor,
  validateEventForm,
  type EventFormState,
} from '../miniprogram/utils/event-form';

const END_AT = '2026-12-06T22:00:00+08:00';
const NOW = Date.parse('2026-12-06T23:00:00+08:00');

function match(overrides: Partial<MatchSummary> = {}): MatchSummary {
  return {
    id: 1,
    title: '比赛',
    type: 0,
    typeLabel: 'OFFICIAL',
    startAt: '2026-12-06T20:00:00+08:00',
    endAt: END_AT,
    venue: '球场',
    season: '2026',
    homeTeamName: '云河FC',
    awayTeamName: '对手FC',
    ourSide: 0,
    scoreHome: 0,
    scoreAway: 0,
    status: 3,
    statusLabel: 'IN_PROGRESS',
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

const confirmed = [
  { id: 11, name: '主队球员' },
  { id: 12, name: '客队球员' },
];

const savedGrouping: GroupingView = {
  savedAt: '2026-12-06T12:00:00+08:00',
  diff: 0,
  warnings: [],
  teams: {
    A: [{ playerId: 11, name: '主队球员', position: 2 }],
    B: [{ playerId: 12, name: '客队球员', position: 2 }],
  },
};

function form(overrides: Partial<EventFormState> = {}): EventFormState {
  return {
    type: 0,
    team: 0,
    playerId: 11,
    assistPlayerId: null,
    inPlayerId: null,
    outPlayerId: null,
    minute: 20,
    half: 0,
    penalty: false,
    ...overrides,
  };
}

describe('eventTeamRoster', () => {
  it('limits external matches to our side', () => {
    expect(eventTeamRoster({ match: match(), team: 0, confirmed }).map((p) => p.id)).toEqual([
      11, 12,
    ]);
    expect(eventTeamRoster({ match: match(), team: 1, confirmed })).toEqual([]);
  });

  it('filters internal matches by saved grouping only', () => {
    const internal = match({ type: 1 });
    expect(
      eventTeamRoster({ match: internal, team: 1, confirmed, grouping: savedGrouping }).map(
        (p) => p.id,
      ),
    ).toEqual([12]);
    expect(
      eventTeamRoster({
        match: internal,
        team: 1,
        confirmed,
        grouping: { ...savedGrouping, savedAt: null },
      }).map((p) => p.id),
    ).toEqual([11, 12]);
  });
});

describe('allowedEventTypes / teamLockedFor', () => {
  it('lets only admins record substitutions', () => {
    expect(allowedEventTypes(true)).toEqual([0, 1, 2, 3, 4]);
    expect(allowedEventTypes(false)).toEqual([0, 1, 3, 4]);
  });

  it('locks the team for non-admins in external matches', () => {
    expect(teamLockedFor(match(), false)).toBe(true);
    expect(teamLockedFor(match({ type: 1 }), false)).toBe(false);
    expect(teamLockedFor(match(), true)).toBe(false);
  });
});

describe('validateEventForm', () => {
  it('requires a 1–120 minute', () => {
    expect(validateEventForm(form({ minute: null }))).toBe('请填写 1–120 的比赛分钟');
    expect(validateEventForm(form({ minute: 121 }))).toBe('请填写 1–120 的比赛分钟');
  });

  it('requires two different players for a substitution', () => {
    expect(validateEventForm(form({ type: 2 }))).toBe('换人事件必须选择换上和换下球员');
    expect(validateEventForm(form({ type: 2, inPlayerId: 11, outPlayerId: 11 }))).toBe(
      '换上和换下球员不能相同',
    );
  });

  it('requires a player and forbids self-assist', () => {
    expect(validateEventForm(form({ playerId: null }))).toBe('请选择球员');
    expect(validateEventForm(form({ assistPlayerId: 11 }))).toBe('助攻球员不能是进球球员本人');
    expect(validateEventForm(form())).toBeNull();
  });
});

describe('eventPayload', () => {
  it('clears fields that do not belong to the event type', () => {
    expect(
      eventPayload(
        form({
          type: 3,
          playerId: 11,
          assistPlayerId: 12,
          inPlayerId: 11,
          outPlayerId: 12,
          penalty: true,
        }),
      ),
    ).toEqual({
      type: 3,
      team: 0,
      playerId: 11,
      assistPlayerId: null,
      inPlayerId: null,
      outPlayerId: null,
      minute: 20,
      half: 0,
      penalty: false,
    });

    expect(
      eventPayload(form({ type: 2, playerId: null, inPlayerId: 11, outPlayerId: 12 })),
    ).toMatchObject({ type: 2, playerId: null, inPlayerId: 11, outPlayerId: 12 });
  });
});

describe('eventCreateAllowed / eventEditAllowed', () => {
  it('requires an in-progress/finished match, permission and the 24h window for players', () => {
    expect(
      eventCreateAllowed({
        matchStatus: 3,
        hasCreate: true,
        isAdmin: false,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(true);
    expect(
      eventCreateAllowed({
        matchStatus: 5,
        hasCreate: true,
        isAdmin: false,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(false);
    expect(
      eventCreateAllowed({
        matchStatus: 3,
        hasCreate: false,
        isAdmin: false,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(false);
    expect(
      eventCreateAllowed({
        matchStatus: 4,
        hasCreate: true,
        isAdmin: false,
        endAt: END_AT,
        now: Date.parse('2026-12-08T00:00:00+08:00'),
      }),
    ).toBe(false);
  });

  it('lets players edit only their own non-substitution events within 24h', () => {
    const event = {
      id: 1,
      type: 0,
      team: 0,
      minute: 10,
      half: 0,
      penalty: false,
      version: 1,
      player: { id: 11, name: '球员' },
    } as MatchEvent;

    expect(
      eventEditAllowed(event, {
        ownPlayerId: 11,
        hasEditOwn: true,
        matchStatus: 3,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(true);
    expect(
      eventEditAllowed(event, {
        ownPlayerId: 12,
        hasEditOwn: true,
        matchStatus: 3,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(false);
    expect(
      eventEditAllowed(
        { ...event, type: 2 },
        { ownPlayerId: 11, hasEditOwn: true, matchStatus: 3, endAt: END_AT, now: NOW },
      ),
    ).toBe(false);
    expect(
      eventEditAllowed(event, {
        ownPlayerId: 11,
        hasEditOwn: false,
        matchStatus: 3,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(false);
    expect(
      eventEditAllowed(event, {
        ownPlayerId: 11,
        hasEditOwn: true,
        matchStatus: 2,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(true);
    expect(
      eventEditAllowed(event, {
        ownPlayerId: 11,
        hasEditOwn: true,
        matchStatus: 5,
        endAt: END_AT,
        now: NOW,
      }),
    ).toBe(false);
  });
});
