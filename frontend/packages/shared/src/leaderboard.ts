/** STA-03 排行榜口径：赛季或生涯累计。 */
export type LeaderboardScope = 'season' | 'all';

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
}

/** STA-03 响应；生涯累计时 season 为 null。 */
export interface Leaderboard {
  season: string | null;
  scope: LeaderboardScope;
  items: LeaderboardEntry[];
}
