import type { PlayerPublic, PlayerStats } from '@yunhefc/shared';

/** PLR-01 TeamPlayerView（05 §3.3）：公开名单字段 + 本赛季出场数。 */
export interface TeamPlayer extends PlayerPublic {
  appearances: number;
  secondaryPositionLabels?: string[];
  typeLabel?: string;
}

export interface TeamPlayersView {
  items: TeamPlayer[];
  total: number;
}

export interface TeamPlayerWithStats extends TeamPlayer {
  goals: number | null;
  assists: number | null;
}

export interface TeamPositionGroup {
  position: number;
  players: TeamPlayerWithStats[];
}

/** PLR-01 的服务端分组顺序：前锋 → 中场 → 后卫 → 门将。 */
export const TEAM_POSITION_ORDER = [3, 2, 1, 0] as const;

/** 按 playerId 合并 STA-01；缺失统计降级为 PLR-01 出场数与「—」进球/助攻。 */
export function mergeTeamStats(players: TeamPlayer[], stats: PlayerStats[]): TeamPlayerWithStats[] {
  const byPlayer = new Map(stats.map((row) => [row.playerId, row]));

  return players.map((player) => {
    const row = byPlayer.get(player.id);
    return {
      ...player,
      appearances: row?.appearances ?? player.appearances,
      goals: row ? row.goals : null,
      assists: row ? row.assists : null,
    };
  });
}

/** 按固定位置顺序分组；组内保持 PLR-01 顺序，不重排。 */
export function groupByPosition(players: TeamPlayerWithStats[]): TeamPositionGroup[] {
  return TEAM_POSITION_ORDER.map((position) => ({
    position,
    players: players.filter((player) => player.position === position),
  })).filter((group) => group.players.length > 0);
}

/** 只接受后端现签的 http(s) 签名 URL；对象键或空值回落姓名首字占位。 */
export function avatarSrc(url: string | null | undefined): string | null {
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : null;
}

/** 取姓名首个字符；按码点切分，避免 emoji 等代理对截断。 */
export function avatarInitial(name: string): string {
  const [first] = Array.from(name.trim());
  return first ?? '?';
}
