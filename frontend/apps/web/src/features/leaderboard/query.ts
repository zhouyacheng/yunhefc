import type { LeaderboardScope } from '@yunhefc/shared';

import { parseTeamSeason, serializeTeamSeason } from '../team/query';

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

export interface LeaderboardQuery {
  season: string;
  scope: LeaderboardScope;
  board: LeaderboardBoardKey;
}

/** STA-03 请求参数：赛季口径显式带 season，生涯口径忽略 season。 */
export type LeaderboardRequest = Record<string, string>;

const BOARD_KEY_SET = new Set<string>(LEADERBOARD_BOARD_KEYS);

function firstValue(raw: unknown): string {
  const value = Array.isArray(raw) ? raw[0] : raw;
  return typeof value === 'string' ? value.trim() : '';
}

/** 非法或缺失回退默认进球榜。 */
export function parseLeaderboardBoard(raw: unknown): LeaderboardBoardKey {
  const value = firstValue(raw);
  return BOARD_KEY_SET.has(value) ? (value as LeaderboardBoardKey) : 'goals';
}

/** 仅精确匹配 all 时进入生涯累计。 */
export function parseLeaderboardScope(raw: unknown): LeaderboardScope {
  return firstValue(raw) === 'all' ? 'all' : 'season';
}

export function parseLeaderboardQuery(
  raw: Record<string, unknown>,
  now = new Date(),
): LeaderboardQuery {
  return {
    season: parseTeamSeason(raw.season, now),
    scope: parseLeaderboardScope(raw.scope),
    board: parseLeaderboardBoard(raw.board),
  };
}

/** URL 只保留非默认值；生涯累计写 scope=all 并省略 season。 */
export function serializeLeaderboardQuery(
  query: LeaderboardQuery,
  now = new Date(),
): Record<string, string> {
  const result: Record<string, string> =
    query.scope === 'all' ? { scope: 'all' } : serializeTeamSeason(query.season, now);
  if (query.board !== 'goals') {
    result.board = query.board;
  }
  return result;
}

export function toLeaderboardRequest(query: LeaderboardQuery): LeaderboardRequest {
  return query.scope === 'all' ? { scope: 'all' } : { season: query.season, scope: 'season' };
}
