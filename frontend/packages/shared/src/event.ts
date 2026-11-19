import { isOpponentLineupSide } from './lineup';
import { isInternalMatchType, type EventTeam, type GroupingView, type MatchSummary } from './match';

export interface EventTeamRosterInput<T extends { id: number }> {
  match: Pick<MatchSummary, 'type' | 'ourSide'> | null | undefined;
  team: EventTeam;
  confirmed: readonly T[];
  grouping?: Pick<GroupingView, 'savedAt' | 'teams'> | null;
}

/**
 * 事件选人范围：对外比赛仅我方；队内/训练赛有已保存分组时按分组过滤，
 * 草稿或未生成分组不约束（02 §5.3 / §6.6、06 §39）。
 */
export function eventTeamRoster<T extends { id: number }>(input: EventTeamRosterInput<T>): T[] {
  const { match, team, confirmed, grouping } = input;
  if (!match) {
    return [];
  }
  if (isOpponentLineupSide(match, team)) {
    return [];
  }
  if (isInternalMatchType(match.type) && grouping?.savedAt) {
    const members = team === 0 ? grouping.teams.A : grouping.teams.B;
    const ids = new Set(members.map((player) => player.playerId));
    return confirmed.filter((player) => ids.has(player.id));
  }
  return [...confirmed];
}
