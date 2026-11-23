// 排行榜排名单源：小程序运行时唯一宿主（DevTools 只编译 miniprogramRoot 内文件）；
// Web 经 @yunhefc/miniprogram/utils/leaderboard-engine 反向引用。零运行时导入。

/** 展示主指标所需的最小累计字段。 */
export interface RankableTotals {
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

/** 排名所需的最小球员字段；Web/小程序各自的 LeaderboardEntry 均结构兼容。 */
export interface RankableEntry extends RankableTotals {
  playerId: number;
  jersey: number | null;
}

export const LEADERBOARD_BOARD_KEYS = [
  'appearances',
  'goals',
  'assists',
  'goalsPerMatch',
  'assistsPerMatch',
  'winRate',
  'ownGoals',
] as const;

export type LeaderboardBoardKey = (typeof LEADERBOARD_BOARD_KEYS)[number];

/** 非法或缺失榜单键的统一回退。 */
export const DEFAULT_LEADERBOARD_BOARD: LeaderboardBoardKey = 'appearances';

export interface RankedEntry<T extends RankableEntry = RankableEntry> {
  entry: T;
  rank: number;
}

export interface LeaderboardBoard {
  key: LeaderboardBoardKey;
  label: string;
  eligible: (entry: RankableEntry) => boolean;
  mainText: (totals: RankableTotals) => string;
}

interface BoardDef extends LeaderboardBoard {
  /** 排序比较器：主指标降序，返回正数表示 a 排在 b 之后。 */
  compareMain: (a: RankableEntry, b: RankableEntry) => number;
  /** 名次并列判定：只看主指标原始值。 */
  sameMain: (a: RankableEntry, b: RankableEntry) => boolean;
  /** 次级指标降序链。 */
  secondary: ((entry: RankableEntry) => number)[];
}

const allEligible = (): boolean => true;

function integerBoard(
  key: LeaderboardBoardKey,
  label: string,
  main: (entry: RankableEntry) => number,
  secondary: ((entry: RankableEntry) => number)[],
  mainText: (totals: RankableTotals) => string,
): BoardDef {
  return {
    key,
    label,
    eligible: allEligible,
    compareMain: (a, b) => main(b) - main(a),
    sameMain: (a, b) => main(a) === main(b),
    secondary,
    mainText,
  };
}

function ratioBoard(
  key: LeaderboardBoardKey,
  label: string,
  numerator: (entry: RankableEntry) => number,
  denominator: (entry: RankableEntry) => number,
  secondary: ((entry: RankableEntry) => number)[],
  mainText: (totals: RankableTotals) => string,
): BoardDef {
  // 交叉相乘比较原始分数，避免两位小数展示值影响排序与并列判定。
  const cross = (a: RankableEntry, b: RankableEntry): number =>
    numerator(a) * denominator(b) - numerator(b) * denominator(a);
  return {
    key,
    label,
    eligible: (entry) => denominator(entry) > 0,
    compareMain: (a, b) => cross(b, a),
    sameMain: (a, b) => cross(a, b) === 0,
    secondary,
    mainText,
  };
}

const percent = (totals: RankableTotals): string =>
  totals.winRate === null ? '—' : `${totals.winRate.toFixed(1)}%`;

/** 榜单顺序即 02 §5.11 的 Tab 顺序；默认出场榜。 */
export const LEADERBOARD_BOARDS: readonly BoardDef[] = [
  integerBoard(
    'appearances',
    '出场',
    (entry) => entry.appearances,
    [(entry) => entry.goals, (entry) => entry.assists],
    (totals) => `${totals.appearances}`,
  ),
  integerBoard(
    'goals',
    '进球',
    (entry) => entry.goals,
    [(entry) => entry.assists, (entry) => entry.appearances],
    (totals) => `${totals.goals}`,
  ),
  integerBoard(
    'assists',
    '助攻',
    (entry) => entry.assists,
    [(entry) => entry.goals, (entry) => entry.appearances],
    (totals) => `${totals.assists}`,
  ),
  ratioBoard(
    'goalsPerMatch',
    '场均进球',
    (entry) => entry.goals,
    (entry) => entry.appearances,
    [(entry) => entry.goals],
    (totals) => totals.goalsPerMatch.toFixed(2),
  ),
  ratioBoard(
    'assistsPerMatch',
    '场均助攻',
    (entry) => entry.assists,
    (entry) => entry.appearances,
    [(entry) => entry.assists],
    (totals) => totals.assistsPerMatch.toFixed(2),
  ),
  ratioBoard(
    'winRate',
    '胜率',
    (entry) => entry.internalWins,
    (entry) => entry.internalAppearances,
    [(entry) => entry.internalWins],
    percent,
  ),
  integerBoard(
    'ownGoals',
    '乌龙',
    (entry) => entry.ownGoals,
    [(entry) => entry.appearances],
    (totals) => `${totals.ownGoals}`,
  ),
];

const BOARD_BY_KEY = new Map(LEADERBOARD_BOARDS.map((board) => [board.key, board]));

function boardDef(key: LeaderboardBoardKey): BoardDef {
  return BOARD_BY_KEY.get(key) ?? BOARD_BY_KEY.get(DEFAULT_LEADERBOARD_BOARD)!;
}

export function leaderboardBoard(key: LeaderboardBoardKey): LeaderboardBoard {
  return boardDef(key);
}

function compareEntries(a: RankableEntry, b: RankableEntry, board: BoardDef): number {
  const main = board.compareMain(a, b);
  if (main !== 0) {
    return main;
  }
  for (const metric of board.secondary) {
    const diff = metric(b) - metric(a);
    if (diff !== 0) {
      return diff;
    }
  }
  const jerseyA = a.jersey ?? Number.POSITIVE_INFINITY;
  const jerseyB = b.jersey ?? Number.POSITIVE_INFINITY;
  if (jerseyA !== jerseyB) {
    return jerseyA - jerseyB;
  }
  return a.playerId - b.playerId;
}

/** 过滤零分母、排序并按标准竞赛排名赋名次（1、1、3）。 */
export function rankEntries<T extends RankableEntry>(
  entries: readonly T[],
  key: LeaderboardBoardKey,
): RankedEntry<T>[] {
  const board = boardDef(key);
  const sorted = entries.filter(board.eligible).slice();
  sorted.sort((a, b) => compareEntries(a, b, board));

  let previous: RankableEntry | null = null;
  let previousRank = 0;
  return sorted.map((entry, index) => {
    const rank = previous !== null && board.sameMain(previous, entry) ? previousRank : index + 1;
    previous = entry;
    previousRank = rank;
    return { entry, rank };
  });
}
