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

export interface GroupExpandState {
  fullLoaded: boolean;
  truncated: boolean;
  previewSize?: number;
}

/** PLR-01 的服务端分组顺序：前锋 → 中场 → 后卫 → 门将。 */
export const TEAM_POSITION_ORDER = [3, 2, 1, 0] as const;

/** 每组默认预览人数。 */
export const GROUP_PREVIEW_SIZE = 10;

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

/** 组内预览切片：展开显示全部，否则前 10 人。 */
export function visibleGroupPlayers(
  group: TeamPositionGroup,
  expanded: boolean,
  size = GROUP_PREVIEW_SIZE,
): TeamPlayerWithStats[] {
  return expanded ? group.players : group.players.slice(0, size);
}

/**
 * 是否显示组内展开入口：全量已知时按真实人数判断；预览阶段只有整队被截断，
 * 且该组正好达到上限时，无法排除后续球员。
 */
export function canExpandGroup(group: TeamPositionGroup, state: GroupExpandState): boolean {
  const previewSize = state.previewSize ?? GROUP_PREVIEW_SIZE;
  if (group.players.length > previewSize) {
    return true;
  }
  return !state.fullLoaded && state.truncated && group.players.length === previewSize;
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
