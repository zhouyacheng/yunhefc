import type { LineupView, MatchSummary } from '../types/api';
import { isInternalMatchType } from './match-type';
import { positionText } from './presentation';

export interface LineupSlotRow {
  slotKey: string;
  x: number;
  y: number;
  roleText: string;
  jerseyText: string;
  name: string;
  filled: boolean;
}

export interface LineupBenchRow {
  id: number;
  label: string;
}

export interface LineupBoardView {
  formationName: string;
  slots: LineupSlotRow[];
  bench: LineupBenchRow[];
  opponent: boolean;
}

/** 对外比赛中非我方一侧仅记录阵型与空槽位。 */
function isOpponentLineupSide(
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

/** 与 C03 lineupPlayerLabel 一致：姓名 + 球衣号，不带位置后缀。 */
function playerLabel(player: { name: string; jersey?: number | null }): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey}`;
}

export function buildLineupBoard(
  lineup: LineupView,
  match: Pick<MatchSummary, 'type' | 'ourSide'>,
): LineupBoardView {
  const starterBySlot = new Map(lineup.starters.map((starter) => [starter.slotKey, starter]));
  return {
    formationName: lineup.formation.name,
    slots: lineup.formation.slots.map((slot) => {
      const starter = starterBySlot.get(slot.slotKey);
      const player = starter?.player ?? null;
      return {
        slotKey: slot.slotKey,
        x: slot.x,
        y: slot.y,
        roleText: positionText(slot.role),
        jerseyText: player ? `#${player.jersey ?? '—'}` : '',
        name: player ? player.name : '空位',
        filled: Boolean(player),
      };
    }),
    bench: lineup.bench.map((player) => ({ id: player.id, label: playerLabel(player) })),
    opponent: isOpponentLineupSide(match, lineup.side),
  };
}
