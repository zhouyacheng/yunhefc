// 移植自 shared event.ts 与 Web MatchDetailPage.vue 的事件表单规则。
import type {
  EventCreate,
  EventFormPlayer,
  GroupingView,
  MatchEvent,
  MatchSummary,
} from '../types/api';
import { isInternalMatchType } from './match-type';
import { eventEditWindowOpen, eventEntryOpen } from './match-window';
import { EVENT_TYPE_TEXT, positionText } from './presentation';

export interface EventFormState {
  type: number;
  team: number;
  playerId: number | null;
  assistPlayerId: number | null;
  inPlayerId: number | null;
  outPlayerId: number | null;
  minute: number | null;
  half: number;
  penalty: boolean;
}

function isOpponentSide(
  match: Pick<MatchSummary, 'type' | 'ourSide'> | null | undefined,
  side: number,
): boolean {
  if (
    !match ||
    match.type === null ||
    match.type === undefined ||
    isInternalMatchType(match.type)
  ) {
    return false;
  }
  return side !== (match.ourSide ?? 0);
}

export interface EventTeamRosterInput<T extends { id: number }> {
  match: Pick<MatchSummary, 'type' | 'ourSide'> | null | undefined;
  team: number;
  confirmed: readonly T[];
  grouping?: Pick<GroupingView, 'savedAt' | 'teams'> | null;
}

/** 对外比赛仅我方；队内/训练赛有已保存分组时按分组过滤，草稿或未生成不约束。 */
export function eventTeamRoster<T extends { id: number }>(input: EventTeamRosterInput<T>): T[] {
  const { match, team, confirmed, grouping } = input;
  if (!match) {
    return [];
  }
  if (isOpponentSide(match, team)) {
    return [];
  }
  if (isInternalMatchType(match.type) && grouping?.savedAt) {
    const members = team === 0 ? grouping.teams.A : grouping.teams.B;
    const ids = new Set(members.map((player) => player.playerId));
    return confirmed.filter((player) => ids.has(player.id));
  }
  return [...confirmed];
}

export function allowedEventTypes(isAdmin: boolean): number[] {
  return isAdmin ? [0, 1, 2, 3, 4] : [0, 1, 3, 4];
}

export interface PlayerOption {
  id: number | null;
  label: string;
}

export interface ValueOption {
  value: number;
  label: string;
}

function playerLabel(player: EventFormPlayer): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '—' : player.jersey;
  return `${player.name} #${jersey} · ${positionText(player.position ?? -1)}`;
}

function optionsOf(players: readonly EventFormPlayer[], emptyLabel: string): PlayerOption[] {
  return [
    { id: null, label: emptyLabel },
    ...players.map((player) => ({ id: player.id, label: playerLabel(player) })),
  ];
}

function indexOfPlayer(options: readonly PlayerOption[], id: number | null | undefined): number {
  if (id === null || id === undefined) {
    return 0;
  }
  const index = options.findIndex((option) => option.id === id);
  return index >= 0 ? index : 0;
}

export interface EventTeamSelection {
  playerId?: number | null;
  assistPlayerId?: number | null;
  inPlayerId?: number | null;
  outPlayerId?: number | null;
}

export interface EventTeamPickersInput {
  match: Pick<MatchSummary, 'type' | 'ourSide'> | null | undefined;
  players: readonly EventFormPlayer[];
  grouping?: Pick<GroupingView, 'savedAt' | 'teams'> | null;
  isAdmin: boolean;
  ownPlayerId: number | null;
  team: number;
  selected?: EventTeamSelection | null;
}

export interface EventTeamPickers {
  playerOptions: PlayerOption[];
  playerIndex: number;
  assistOptions: PlayerOption[];
  assistIndex: number;
  inOptions: PlayerOption[];
  inIndex: number;
  outOptions: PlayerOption[];
  outIndex: number;
}

/** 由球队与已确认球员重建四个 picker 的选项与索引；选择失效时回到首个选项。 */
export function eventTeamPickers(input: EventTeamPickersInput): EventTeamPickers {
  const selected = input.selected ?? {};
  const teamPlayers = eventTeamRoster({
    match: input.match,
    team: input.team,
    confirmed: input.players,
    grouping: input.grouping,
  });
  const locked = teamLockedFor(input.match, input.isAdmin);
  const pickable = locked
    ? teamPlayers.filter((player) => player.id === input.ownPlayerId)
    : teamPlayers;
  const teamAll = optionsOf(teamPlayers, '请选择');
  const playerOptions = optionsOf(pickable, '请选择');
  const assistOptions = optionsOf(teamPlayers, '无');
  return {
    playerOptions,
    playerIndex: indexOfPlayer(playerOptions, selected.playerId),
    assistOptions,
    assistIndex: indexOfPlayer(assistOptions, selected.assistPlayerId),
    inOptions: teamAll,
    inIndex: indexOfPlayer(teamAll, selected.inPlayerId),
    outOptions: teamAll,
    outIndex: indexOfPlayer(teamAll, selected.outPlayerId),
  };
}

export function teamLockedFor(
  match: Pick<MatchSummary, 'type'> | null | undefined,
  isAdmin: boolean,
): boolean {
  return !isAdmin && !isInternalMatchType(match?.type);
}

/** 队内赛按已保存分组默认主/客；对外比赛固定我方。 */
export function defaultEventTeam(
  match: Pick<MatchSummary, 'type' | 'ourSide'> | null | undefined,
  grouping: Pick<GroupingView, 'savedAt' | 'teams'> | null | undefined,
  ownPlayerId: number | null,
  isAdmin: boolean,
): number {
  if (!isInternalMatchType(match?.type)) {
    return match?.ourSide ?? 0;
  }
  const saved = grouping?.savedAt ? grouping : null;
  if (
    !isAdmin &&
    saved &&
    ownPlayerId !== null &&
    saved.teams.B.some((player) => player.playerId === ownPlayerId)
  ) {
    return 1;
  }
  return 0;
}

export interface EventFormEditing {
  type: number;
  team: number;
  player?: { id: number } | null;
  assistPlayer?: { id: number } | null;
  inPlayer?: { id: number } | null;
  outPlayer?: { id: number } | null;
  minute: number;
  half: number;
  penalty: boolean;
}

export interface EventFormViewInput {
  match: MatchSummary | null | undefined;
  players: readonly EventFormPlayer[];
  grouping?: Pick<GroupingView, 'savedAt' | 'teams'> | null;
  editing?: EventFormEditing | null;
  isAdmin: boolean;
  ownPlayerId: number | null;
}

export interface EventFormView extends EventTeamPickers {
  typeOptions: ValueOption[];
  typeIndex: number;
  teamOptions: ValueOption[];
  teamIndex: number;
  teamLocked: boolean;
  halfIndex: number;
  minute: string;
  penalty: boolean;
}

/** 打开弹层时的表单快照：选项、默认球队与编辑回填一次算完。 */
export function eventFormView(input: EventFormViewInput): EventFormView {
  const editing = input.editing ?? null;
  const typeOptions = allowedEventTypes(input.isAdmin).map((value) => ({
    value,
    label: EVENT_TYPE_TEXT[value],
  }));
  const team =
    editing?.team ??
    defaultEventTeam(input.match, input.grouping, input.ownPlayerId, input.isAdmin);
  return {
    typeOptions,
    typeIndex: Math.max(
      0,
      typeOptions.findIndex((option) => option.value === (editing?.type ?? 0)),
    ),
    teamOptions: [
      { value: 0, label: input.match?.homeTeamName ?? '主队' },
      { value: 1, label: input.match?.awayTeamName ?? '客队' },
    ],
    teamIndex: team === 1 ? 1 : 0,
    teamLocked: teamLockedFor(input.match, input.isAdmin),
    halfIndex: editing?.half === 1 ? 1 : 0,
    minute: editing ? String(editing.minute) : '',
    penalty: Boolean(editing?.penalty),
    ...eventTeamPickers({
      match: input.match,
      players: input.players,
      grouping: input.grouping,
      isAdmin: input.isAdmin,
      ownPlayerId: input.ownPlayerId,
      team,
      selected: {
        playerId: editing?.player?.id ?? null,
        assistPlayerId: editing?.assistPlayer?.id ?? null,
        inPlayerId: editing?.inPlayer?.id ?? null,
        outPlayerId: editing?.outPlayer?.id ?? null,
      },
    }),
  };
}

export function validateEventForm(form: EventFormState): string | null {
  if (!form.minute || form.minute < 1 || form.minute > 120) {
    return '请填写 1–120 的比赛分钟';
  }
  if (form.type === 2) {
    if (!form.inPlayerId || !form.outPlayerId) {
      return '换人事件必须选择换上和换下球员';
    }
    if (form.inPlayerId === form.outPlayerId) {
      return '换上和换下球员不能相同';
    }
    return null;
  }
  if (!form.playerId) {
    return '请选择球员';
  }
  if (form.type === 0 && form.assistPlayerId === form.playerId) {
    return '助攻球员不能是进球球员本人';
  }
  return null;
}

export function eventPayload(form: EventFormState): EventCreate {
  const substitution = form.type === 2;
  const goal = form.type === 0;
  return {
    type: form.type,
    team: form.team,
    playerId: substitution ? null : form.playerId,
    assistPlayerId: goal ? form.assistPlayerId : null,
    inPlayerId: substitution ? form.inPlayerId : null,
    outPlayerId: substitution ? form.outPlayerId : null,
    minute: form.minute ?? 0,
    half: form.half,
    penalty: goal ? form.penalty : false,
  };
}

export interface EventFormValues {
  typeIndex: number;
  teamIndex: number;
  playerIndex: number;
  assistIndex: number;
  inIndex: number;
  outIndex: number;
  halfIndex: number;
  minute: string;
  penalty: boolean;
}

export interface EventSubmissionInput extends EventFormValues {
  typeOptions: readonly ValueOption[];
  playerOptions: readonly PlayerOption[];
  assistOptions: readonly PlayerOption[];
  inOptions: readonly PlayerOption[];
  outOptions: readonly PlayerOption[];
  version: number;
  editing?: { id: number; version: number } | null;
}

export type EventSubmission =
  | { ok: true; payload: EventCreate; eventId: number | null; version: number | null }
  | { ok: false; error: string };

/** 由 picker 索引还原领域状态；校验失败返回文案，成功返回 payload 与乐观锁版本。 */
export function buildEventSubmission(input: EventSubmissionInput): EventSubmission {
  const state: EventFormState = {
    type: input.typeOptions[input.typeIndex]?.value ?? 0,
    team: input.teamIndex === 1 ? 1 : 0,
    playerId: input.playerOptions[input.playerIndex]?.id ?? null,
    assistPlayerId: input.assistOptions[input.assistIndex]?.id ?? null,
    inPlayerId: input.inOptions[input.inIndex]?.id ?? null,
    outPlayerId: input.outOptions[input.outIndex]?.id ?? null,
    minute: input.minute ? Number(input.minute) : null,
    half: input.halfIndex === 1 ? 1 : 0,
    penalty: input.penalty,
  };
  const error = validateEventForm(state);
  if (error) {
    return { ok: false, error };
  }
  return {
    ok: true,
    payload: eventPayload(state),
    eventId: input.editing?.id ?? null,
    version: input.version || input.editing?.version || null,
  };
}

export interface EventCreateInput {
  matchStatus: number;
  hasCreate: boolean;
  isAdmin: boolean;
  endAt: string;
  now: number;
}

export function eventCreateAllowed(input: EventCreateInput): boolean {
  if (!input.hasCreate || !eventEntryOpen(input.matchStatus)) {
    return false;
  }
  return input.isAdmin || eventEditWindowOpen(input.endAt, input.now);
}

export interface EventEditInput {
  ownPlayerId: number | null;
  hasEditOwn: boolean;
  matchStatus: number;
  endAt: string;
  now: number;
}

/** 球员仅可编辑本人非换人事件且赛后 24h 内；与 Web C03 一致（只排除已取消/已延期）。 */
export function eventEditAllowed(event: MatchEvent, input: EventEditInput): boolean {
  if (input.matchStatus === 5 || input.matchStatus === 6) {
    return false;
  }
  if (!input.hasEditOwn || event.type === 2 || event.player?.id !== input.ownPlayerId) {
    return false;
  }
  return eventEditWindowOpen(input.endAt, input.now);
}
