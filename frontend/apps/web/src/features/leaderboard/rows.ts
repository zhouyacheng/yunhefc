import type { LeaderboardEntry } from '@yunhefc/shared';
import { isZeroOrPlaceholder } from '@yunhefc/shared';

import type { LeaderboardBoardKey } from './ranking';
import { leaderboardBoard, rankEntries, type RankedEntry } from './ranking';

/** 榜单行视图：当前窗口与两组累计都只保留当前榜单主指标。 */
export interface LeaderboardRow extends RankedEntry<LeaderboardEntry> {
  currentText: string;
  careerText: string | null;
  historyText: string | null;
  currentZero: boolean;
  careerZero: boolean;
  historyZero: boolean;
}

export function buildLeaderboardRows(
  entries: readonly LeaderboardEntry[],
  key: LeaderboardBoardKey,
): LeaderboardRow[] {
  const board = leaderboardBoard(key);
  return rankEntries(entries, key).map((row) => {
    const currentText = board.mainText(row.entry);
    const careerText = row.entry.career ? board.mainText(row.entry.career) : null;
    const historyText = row.entry.history ? board.mainText(row.entry.history) : null;
    return {
      ...row,
      currentText,
      careerText,
      historyText,
      currentZero: isZeroOrPlaceholder(currentText),
      careerZero: isZeroOrPlaceholder(careerText),
      historyZero: isZeroOrPlaceholder(historyText),
    };
  });
}
