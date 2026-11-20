import { describe, expect, it } from 'vitest';

import type {
  EventFormPlayer,
  GroupingView,
  MatchEvent,
  MatchSummary,
} from '../miniprogram/types/api';
import {
  allowedEventTypes,
  buildEventSubmission,
  eventCreateAllowed,
  eventEditAllowed,
  eventFormView,
  eventPayload,
  eventTeamPickers,
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

const pickerPlayers: EventFormPlayer[] = [
  { id: 11, name: '张三', jersey: 7, position: 3 },
  { id: 12, name: '李四' },
];

describe('eventTeamPickers', () => {
  it('rebuilds options and maps the selected ids back to indices', () => {
    const pickers = eventTeamPickers({
      match: match(),
      players: pickerPlayers,
      isAdmin: true,
      ownPlayerId: null,
      team: 0,
      selected: { playerId: 12, assistPlayerId: 11, inPlayerId: 12, outPlayerId: 11 },
    });

    expect(pickers.playerOptions).toEqual([
      { id: null, label: '请选择' },
      { id: 11, label: '张三 #7 · 前锋' },
      { id: 12, label: '李四 #— · —' },
    ]);
    expect(pickers.assistOptions[0]).toEqual({ id: null, label: '无' });
    expect(pickers.playerIndex).toBe(2);
    expect(pickers.assistIndex).toBe(1);
    expect(pickers.inIndex).toBe(2);
    expect(pickers.outIndex).toBe(1);
  });

  it('limits non-admins in external matches to their own player', () => {
    const pickers = eventTeamPickers({
      match: match(),
      players: pickerPlayers,
      isAdmin: false,
      ownPlayerId: 11,
      team: 0,
      selected: { playerId: 12 },
    });

    expect(pickers.playerOptions.map((option) => option.id)).toEqual([null, 11]);
    expect(pickers.playerIndex).toBe(0);
    expect(pickers.assistOptions.map((option) => option.id)).toEqual([null, 11, 12]);
    expect(pickers.inOptions.map((option) => option.id)).toEqual([null, 11, 12]);
  });

  it('keeps only the empty options for the opponent side of an external match', () => {
    const pickers = eventTeamPickers({
      match: match(),
      players: pickerPlayers,
      isAdmin: true,
      ownPlayerId: null,
      team: 1,
    });

    expect(pickers.playerOptions).toEqual([{ id: null, label: '请选择' }]);
    expect(pickers.assistOptions).toEqual([{ id: null, label: '无' }]);
    expect(pickers.playerIndex).toBe(0);
  });

  it('rebuilds options from the saved grouping of an internal match', () => {
    const pickers = eventTeamPickers({
      match: match({ type: 1 }),
      players: pickerPlayers,
      grouping: savedGrouping,
      isAdmin: true,
      ownPlayerId: null,
      team: 1,
      selected: { playerId: 12 },
    });

    expect(pickers.playerOptions.map((option) => option.id)).toEqual([null, 12]);
    expect(pickers.playerIndex).toBe(1);
  });
});

describe('eventFormView', () => {
  it('builds type, team and half options with defaults for a new event', () => {
    const view = eventFormView({
      match: match(),
      players: confirmed,
      grouping: null,
      editing: null,
      isAdmin: false,
      ownPlayerId: 11,
    });

    expect(view.typeOptions).toEqual([
      { value: 0, label: '进球' },
      { value: 1, label: '乌龙球' },
      { value: 3, label: '黄牌' },
      { value: 4, label: '红牌' },
    ]);
    expect(view.typeIndex).toBe(0);
    expect(view.teamOptions).toEqual([
      { value: 0, label: '云河FC' },
      { value: 1, label: '对手FC' },
    ]);
    expect(view.teamIndex).toBe(0);
    expect(view.teamLocked).toBe(true);
    expect(view.halfIndex).toBe(0);
    expect(view.minute).toBe('');
    expect(view.penalty).toBe(false);
    expect(view.playerOptions.map((option) => option.id)).toEqual([null, 11]);
  });

  it('restores the edited event selection', () => {
    const view = eventFormView({
      match: match({ type: 1 }),
      players: confirmed,
      grouping: savedGrouping,
      editing: {
        type: 3,
        team: 1,
        player: { id: 12 },
        assistPlayer: { id: 11 },
        inPlayer: { id: 11 },
        outPlayer: { id: 12 },
        minute: 45,
        half: 1,
        penalty: true,
      },
      isAdmin: true,
      ownPlayerId: 11,
    });

    expect(view.typeOptions.map((option) => option.value)).toEqual([0, 1, 2, 3, 4]);
    expect(view.typeIndex).toBe(3);
    expect(view.teamIndex).toBe(1);
    expect(view.teamLocked).toBe(false);
    expect(view.halfIndex).toBe(1);
    expect(view.minute).toBe('45');
    expect(view.penalty).toBe(true);
    expect(view.playerIndex).toBe(1);
    expect(view.assistIndex).toBe(0);
    expect(view.inIndex).toBe(0);
    expect(view.outIndex).toBe(1);
  });

  it('defaults the team from the saved grouping for non-admins', () => {
    const view = eventFormView({
      match: match({ type: 1 }),
      players: confirmed,
      grouping: savedGrouping,
      editing: null,
      isAdmin: false,
      ownPlayerId: 12,
    });

    expect(view.teamIndex).toBe(1);
    expect(view.teamLocked).toBe(false);
    expect(view.playerOptions.map((option) => option.id)).toEqual([null, 12]);
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

const submissionOptions = {
  typeOptions: [
    { value: 0, label: '进球' },
    { value: 2, label: '换人' },
  ],
  playerOptions: [
    { id: null, label: '请选择' },
    { id: 11, label: '张三' },
  ],
  assistOptions: [
    { id: null, label: '无' },
    { id: 12, label: '李四' },
  ],
  inOptions: [
    { id: null, label: '请选择' },
    { id: 11, label: '张三' },
  ],
  outOptions: [
    { id: null, label: '请选择' },
    { id: 12, label: '李四' },
  ],
};

describe('buildEventSubmission', () => {
  it('maps picker indices to ids and carries the editing event version', () => {
    const submission = buildEventSubmission({
      ...submissionOptions,
      typeIndex: 0,
      teamIndex: 0,
      playerIndex: 1,
      assistIndex: 1,
      inIndex: 1,
      outIndex: 1,
      halfIndex: 1,
      minute: '23',
      penalty: true,
      version: 4,
      editing: { id: 9, version: 2 },
    });

    expect(submission).toEqual({
      ok: true,
      payload: {
        type: 0,
        team: 0,
        playerId: 11,
        assistPlayerId: 12,
        inPlayerId: null,
        outPlayerId: null,
        minute: 23,
        half: 1,
        penalty: true,
      },
      eventId: 9,
      version: 4,
    });
  });

  it('falls back to the editing version, then to null', () => {
    const base = {
      ...submissionOptions,
      typeIndex: 0,
      teamIndex: 0,
      playerIndex: 1,
      assistIndex: 0,
      inIndex: 0,
      outIndex: 0,
      halfIndex: 0,
      minute: '10',
      penalty: false,
    };

    expect(
      buildEventSubmission({ ...base, version: 0, editing: { id: 9, version: 3 } }),
    ).toMatchObject({ ok: true, version: 3 });
    expect(buildEventSubmission({ ...base, version: 0, editing: null })).toMatchObject({
      ok: true,
      version: null,
    });
    expect(
      buildEventSubmission({ ...base, version: 2, editing: { id: 9, version: 3 } }),
    ).toMatchObject({ ok: true, version: 2 });
  });

  it('returns the validation message instead of a payload', () => {
    const submission = buildEventSubmission({
      ...submissionOptions,
      typeIndex: 0,
      teamIndex: 0,
      playerIndex: 0,
      assistIndex: 0,
      inIndex: 0,
      outIndex: 0,
      halfIndex: 0,
      minute: '',
      penalty: false,
      version: 1,
      editing: null,
    });

    expect(submission).toEqual({ ok: false, error: '请填写 1–120 的比赛分钟' });
  });

  it('accepts a substitution using the in/out pickers', () => {
    const submission = buildEventSubmission({
      ...submissionOptions,
      typeIndex: 1,
      teamIndex: 1,
      playerIndex: 0,
      assistIndex: 0,
      inIndex: 1,
      outIndex: 1,
      halfIndex: 0,
      minute: '60',
      penalty: false,
      version: 1,
      editing: { id: 9, version: 1 },
    });

    expect(submission).toEqual({
      ok: true,
      payload: {
        type: 2,
        team: 1,
        playerId: null,
        assistPlayerId: null,
        inPlayerId: 11,
        outPlayerId: 12,
        minute: 60,
        half: 0,
        penalty: false,
      },
      eventId: 9,
      version: 1,
    });
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
