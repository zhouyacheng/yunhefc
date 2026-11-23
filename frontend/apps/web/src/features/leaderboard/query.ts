import { parseTeamSeason, serializeTeamSeason } from '../team/query';
import {
  DEFAULT_LEADERBOARD_BOARD,
  LEADERBOARD_BOARD_KEYS,
  type LeaderboardBoardKey,
} from './ranking';

export interface LeaderboardQuery {
  season: string;
  board: LeaderboardBoardKey;
}

/** STA-03 请求参数：页面恒为赛季窗口，显式带 scope=season。 */
export type LeaderboardRequest = Record<string, string>;

const BOARD_KEY_SET = new Set<string>(LEADERBOARD_BOARD_KEYS);

function firstValue(raw: unknown): string {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return typeof value === 'string' ? value.trim() : '';
}

/** 非法或缺失回退默认出场榜。 */
export function parseLeaderboardBoard(raw: unknown): LeaderboardBoardKey {
  const value = firstValue(raw);
  return BOARD_KEY_SET.has(value) ? (value as LeaderboardBoardKey) : DEFAULT_LEADERBOARD_BOARD;
}

/** URL 中的 scope 已不再支持，静默忽略。 */
export function parseLeaderboardQuery(
  raw: Record<string, unknown>,
  now = new Date(),
): LeaderboardQuery {
  return {
    season: parseTeamSeason(raw.season, now),
    board: parseLeaderboardBoard(raw.board),
  };
}

/** URL 只保留非默认值。 */
export function serializeLeaderboardQuery(
  query: LeaderboardQuery,
  now = new Date(),
): Record<string, string> {
  const result: Record<string, string> = serializeTeamSeason(query.season, now);
  if (query.board !== 'appearances') {
    result.board = query.board;
  }
  return result;
}

export function toLeaderboardRequest(query: LeaderboardQuery): LeaderboardRequest {
  return { season: query.season, scope: 'season' };
}
