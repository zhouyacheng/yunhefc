// 移植自 Web features/team/merge-stats.ts；小程序不引共享包，靠 Vitest 锁定口径。
import type { PlayerPublic, PlayerStats } from '../types/api';
import { avatarInitial, avatarSrc } from './avatar';
import { positionText } from './presentation';
import { starParts, type SkillStarMark } from './skill';

export { avatarInitial, avatarSrc } from './avatar';

export interface TeamPlayerView {
  id: number;
  name: string;
  position: number;
  jerseyText: string;
  positionText: string;
  regionText: string;
  skill: number;
  stars: SkillStarMark[];
  appearances: number;
  goalsText: string;
  assistsText: string;
  avatarUrl: string;
  initial: string;
}

export interface TeamGroupView {
  position: number;
  label: string;
  players: TeamPlayerView[];
}

/** PLR-01 的服务端分组顺序：前锋 → 中场 → 后卫 → 门将。 */
const POSITION_ORDER = [3, 2, 1, 0] as const;

export function buildTeamGroups(
  players: readonly PlayerPublic[],
  stats: readonly PlayerStats[] | null | undefined,
): TeamGroupView[] {
  const byPlayer = new Map((stats ?? []).map((row) => [row.playerId, row]));
  const rows: TeamPlayerView[] = players.map((player) => {
    const row = byPlayer.get(player.id);
    return {
      id: player.id,
      name: player.name,
      position: player.position,
      jerseyText: `#${player.jersey ?? '—'}`,
      positionText: positionText(player.position),
      regionText: player.region || '—',
      skill: player.skill,
      stars: starParts(player.skill),
      appearances: row?.appearances ?? player.appearances ?? 0,
      goalsText: row ? String(row.goals) : '—',
      assistsText: row ? String(row.assists) : '—',
      avatarUrl: avatarSrc(player.avatarUrl) ?? '',
      initial: avatarInitial(player.name),
    };
  });

  return POSITION_ORDER.map((position) => ({
    position,
    label: positionText(position),
    players: rows.filter((row) => row.position === position),
  })).filter((group) => group.players.length > 0);
}
