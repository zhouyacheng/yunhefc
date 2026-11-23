// 移植自 Web features/leaderboard/ranking.ts；小程序不引共享包，靠 Vitest 锁定口径。
import type { LeaderboardEntry, LeaderboardTotals } from '../types/api';
import { avatarInitial, avatarSrc } from './avatar';

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

/** 可 setData 的 Tab 数据；顺序即 02 §5.11 的榜单顺序。 */
export interface LeaderboardBoardTab {
  key: LeaderboardBoardKey;
  label: string;
}

export interface RankedEntry {
  entry: LeaderboardEntry;
  rank: number;
}

export interface LeaderboardBoard {
  key: LeaderboardBoardKey;
  label: string;
  eligible: (entry: LeaderboardEntry) => boolean;
  mainText: (totals: LeaderboardTotals) => string;
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
  mainText: (totals: LeaderboardTotals) => string,
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
  numerator: (entry: LeaderboardEntry) => number,
  denominator: (entry: LeaderboardEntry) => number,
  secondary: ((entry: LeaderboardEntry) => number)[],
  mainText: (totals: LeaderboardTotals) => string,
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
  };
}

const percent = (totals: LeaderboardTotals): string =>
  totals.winRate === null ? '—' : `${totals.winRate.toFixed(1)}%`;

/** 榜单顺序即 02 §5.11 的 Tab 顺序；默认出场榜。 */
const LEADERBOARD_BOARDS: readonly BoardDef[] = [
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

export const LEADERBOARD_BOARD_TABS: readonly LeaderboardBoardTab[] = LEADERBOARD_BOARDS.map(
  ({ key, label }) => ({ key, label }),
);

const BOARD_BY_KEY = new Map(LEADERBOARD_BOARDS.map((board) => [board.key, board]));

function boardDef(key: LeaderboardBoardKey): BoardDef {
  return BOARD_BY_KEY.get(key) ?? BOARD_BY_KEY.get('appearances')!;
}

export function leaderboardBoard(key: LeaderboardBoardKey): LeaderboardBoard {
  return boardDef(key);
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
  const board = boardDef(key);
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

/** WXML 行视图：预计算名次与展示文案，视图层不调用函数。 */
export interface LeaderboardRowView {
  playerId: number;
  rank: number;
  name: string;
  avatarUrl: string;
  initial: string;
  currentText: string;
  careerText: string | null;
  historyText: string | null;
  currentZero: boolean;
  careerZero: boolean;
  historyZero: boolean;
}

/** 与 shared `isZeroOrPlaceholder` 同口径；小程序不引共享包。 */
function isZeroOrPlaceholder(text: string | null): boolean {
  return text === '0' || text === '—';
}

export function buildLeaderboardRows(
  entries: readonly LeaderboardEntry[],
  key: LeaderboardBoardKey,
): LeaderboardRowView[] {
  const board = leaderboardBoard(key);
  return rankEntries(entries, key).map(({ entry, rank }) => {
    const currentText = board.mainText(entry);
    const careerText = entry.career ? board.mainText(entry.career) : null;
    const historyText = entry.history ? board.mainText(entry.history) : null;
    return {
      playerId: entry.playerId,
      rank,
      name: entry.name,
      avatarUrl: avatarSrc(entry.avatarUrl) ?? '',
      initial: avatarInitial(entry.name),
      currentText,
      careerText,
      historyText,
      currentZero: isZeroOrPlaceholder(currentText),
      careerZero: isZeroOrPlaceholder(careerText),
      historyZero: isZeroOrPlaceholder(historyText),
    };
  });
}
