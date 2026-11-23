/** STA-03 排行榜口径：赛季或生涯累计。 */
export type LeaderboardScope = 'season' | 'all';

/** 排行榜累计口径：生涯累计含当前赛季，历史累计不含当前赛季。 */
export interface LeaderboardTotals {
  appearances: number;
  goals: number;
  assists: number;
  ownGoals: number;
  internalAppearances: number;
  internalWins: number;
  winRate: number | null;
  goalsPerMatch: number;
  assistsPerMatch: number;
}

/** 仅匹配裸 0 与占位符 —；0.00 等格式化零值不匹配。 */
export function isZeroOrPlaceholder(text: string | null): boolean {
  return text === '0' || text === '—';
}

/** 当前榜单窗口的展示名：生涯累计 / 当前赛季 / YYYY 赛季。 */
export function leaderboardMainLabel(
  scope: LeaderboardScope,
  selectedSeason: string,
  currentSeason: string,
): string {
  if (scope === 'all') {
    return '生涯累计';
  }
  return selectedSeason === currentSeason ? '当前赛季' : `${selectedSeason} 赛季`;
}

/** STA-03 LeaderboardEntry（05 §4）：排序与名次由前端按 02 §5.11 计算。 */
export interface LeaderboardEntry {
  playerId: number;
  name: string;
  jersey: number | null;
  position: number;
  avatarUrl: string | null;
  appearances: number;
  goals: number;
  assists: number;
  ownGoals: number;
  internalAppearances: number;
  internalWins: number;
  /** 0–100 百分比，1 位小数；分母为 0 时为 null。 */
  winRate: number | null;
  /** 2 位小数；无出场为 0。 */
  goalsPerMatch: number;
  assistsPerMatch: number;
  /** 生涯累计（当前赛季 + 历史累计，不晚于当前自然年；未来赛季不计）；scope=all 时为 null。 */
  career: LeaderboardTotals | null;
  /** 历史累计（早于当前自然年，不含当前赛季）；scope=all 时为 null。 */
  history: LeaderboardTotals | null;
}

/** STA-03 响应；生涯累计时 season 为 null。 */
export interface Leaderboard {
  season: string | null;
  scope: LeaderboardScope;
  items: LeaderboardEntry[];
}
