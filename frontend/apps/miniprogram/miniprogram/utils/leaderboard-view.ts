// 排行榜 WXML 行视图；排名核心见 leaderboard-engine.ts（小程序为唯一宿主，Web 反向引用）。
import type { LeaderboardEntry } from '../types/api';
import { avatarInitial, avatarSrc } from './avatar';
import {
  LEADERBOARD_BOARDS,
  leaderboardBoard,
  rankEntries,
  type LeaderboardBoardKey,
} from './leaderboard-engine';

export { LEADERBOARD_BOARD_KEYS } from './leaderboard-engine';
export type { LeaderboardBoardKey } from './leaderboard-engine';

/** 可 setData 的 Tab 数据；顺序即 02 §5.11 的榜单顺序。 */
export interface LeaderboardBoardTab {
  key: LeaderboardBoardKey;
  label: string;
}

export const LEADERBOARD_BOARD_TABS: readonly LeaderboardBoardTab[] = LEADERBOARD_BOARDS.map(
  ({ key, label }) => ({ key, label }),
);

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
