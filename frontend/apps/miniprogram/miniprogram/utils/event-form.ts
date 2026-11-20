// 移植自 shared event.ts 与 Web MatchDetailPage.vue 的事件表单规则。
import type { EventCreate, GroupingView, MatchEvent, MatchSummary } from '../types/api';
import { isInternalMatchType } from './match-type';
import { eventEditWindowOpen, eventEntryOpen } from './match-window';

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
