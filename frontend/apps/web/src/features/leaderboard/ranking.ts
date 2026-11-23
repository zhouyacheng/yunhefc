import type { LeaderboardEntry } from '@yunhefc/shared';

import type { LeaderboardBoardKey } from './query';

export interface RankedEntry {
  entry: LeaderboardEntry;
  rank: number;
}

export interface LeaderboardBoard {
  key: LeaderboardBoardKey;
  label: string;
  eligible: (entry: LeaderboardEntry) => boolean;
  mainText: (entry: LeaderboardEntry) => string;
  secondaryText: (entry: LeaderboardEntry) => string;
}

interface BoardDef extends LeaderboardBoard {
  /** 排序比较器：主指标降序，返回正数表示 a 排在 b 之后。 */
  compareMain: (a: LeaderboardEntry, b: LeaderboardEntry) => number;
  /** 名次并列判定：只看主指标原始值。 */
  sameMain: (a: LeaderboardEntry, b: LeaderboardEntry) => boolean;
  /** 次级指标降序链。 */
  secondary: ((entry: LeaderboardEntry) => number)[];
}

const allEligible = (): boolean => true;

function integerBoard(
  key: LeaderboardBoardKey,
  label: string,
  main: (entry: LeaderboardEntry) => number,
  secondary: ((entry: LeaderboardEntry) => number)[],
  mainText: (entry: LeaderboardEntry) => string,
  secondaryText: (entry: LeaderboardEntry) => string,
): BoardDef {
  return {
    key,
    label,
    eligible: allEligible,
    compareMain: (a, b) => main(b) - main(a),
    sameMain: (a, b) => main(a) === main(b),
    secondary,
    mainText,
    secondaryText,
  };
}

function ratioBoard(
  key: LeaderboardBoardKey,
  label: string,
  numerator: (entry: LeaderboardEntry) => number,
  denominator: (entry: LeaderboardEntry) => number,
  secondary: ((entry: LeaderboardEntry) => number)[],
  mainText: (entry: LeaderboardEntry) => string,
  secondaryText: (entry: LeaderboardEntry) => string,
): BoardDef {
  // 交叉相乘比较原始分数，避免两位小数展示值影响排序与并列判定。
  const cross = (a: LeaderboardEntry, b: LeaderboardEntry): number =>
    numerator(a) * denominator(b) - numerator(b) * denominator(a);
  return {
    key,
    label,
    eligible: (entry) => denominator(entry) > 0,
    compareMain: (a, b) => cross(b, a),
    sameMain: (a, b) => cross(a, b) === 0,
    secondary,
    mainText,
    secondaryText,
  };
}

const percent = (entry: LeaderboardEntry): string =>
  entry.winRate === null ? '—' : `${entry.winRate.toFixed(1)}%`;

/** 榜单顺序即 02 §5.11 的 Tab 顺序；默认出场榜。 */
export const LEADERBOARD_BOARDS: readonly BoardDef[] = [
  integerBoard(
    'appearances',
    '出场',
    (entry) => entry.appearances,
    [(entry) => entry.goals, (entry) => entry.assists],
    (entry) => `${entry.appearances} 场`,
    (entry) => `进球 ${entry.goals} · 助攻 ${entry.assists}`,
  ),
  integerBoard(
    'goals',
    '进球',
    (entry) => entry.goals,
    [(entry) => entry.assists, (entry) => entry.appearances],
    (entry) => `${entry.goals} 球`,
    (entry) => `助攻 ${entry.assists} · 出场 ${entry.appearances}`,
  ),
  integerBoard(
    'assists',
    '助攻',
    (entry) => entry.assists,
    [(entry) => entry.goals, (entry) => entry.appearances],
    (entry) => `${entry.assists} 助`,
    (entry) => `进球 ${entry.goals} · 出场 ${entry.appearances}`,
  ),
  ratioBoard(
    'goalsPerMatch',
    '场均进球',
    (entry) => entry.goals,
    (entry) => entry.appearances,
    [(entry) => entry.goals],
    (entry) => entry.goalsPerMatch.toFixed(2),
    (entry) => `进球 ${entry.goals} / 出场 ${entry.appearances}`,
  ),
  ratioBoard(
    'assistsPerMatch',
    '场均助攻',
    (entry) => entry.assists,
    (entry) => entry.appearances,
    [(entry) => entry.assists],
    (entry) => entry.assistsPerMatch.toFixed(2),
    (entry) => `助攻 ${entry.assists} / 出场 ${entry.appearances}`,
  ),
  ratioBoard(
    'winRate',
    '胜率',
    (entry) => entry.internalWins,
    (entry) => entry.internalAppearances,
    [(entry) => entry.internalWins],
    percent,
    (entry) => `队内赛 ${entry.internalWins} 胜 / ${entry.internalAppearances} 场`,
  ),
  integerBoard(
    'ownGoals',
    '乌龙',
    (entry) => entry.ownGoals,
    [(entry) => entry.appearances],
    (entry) => `${entry.ownGoals} 乌龙`,
    (entry) => `出场 ${entry.appearances}`,
  ),
];

const BOARD_BY_KEY = new Map(LEADERBOARD_BOARDS.map((board) => [board.key, board]));

export function leaderboardBoard(key: LeaderboardBoardKey): BoardDef {
  return BOARD_BY_KEY.get(key) ?? BOARD_BY_KEY.get('appearances')!;
}

function compareEntries(a: LeaderboardEntry, b: LeaderboardEntry, board: BoardDef): number {
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
export function rankEntries(
  entries: readonly LeaderboardEntry[],
  key: LeaderboardBoardKey,
): RankedEntry[] {
  const board = leaderboardBoard(key);
  const sorted = entries.filter(board.eligible).slice();
  sorted.sort((a, b) => compareEntries(a, b, board));

  let previous: LeaderboardEntry | null = null;
  let previousRank = 0;
  return sorted.map((entry, index) => {
    const rank = previous !== null && board.sameMain(previous, entry) ? previousRank : index + 1;
    previous = entry;
    previousRank = rank;
    return { entry, rank };
  });
}
