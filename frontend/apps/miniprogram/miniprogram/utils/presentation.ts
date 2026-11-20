// 与 frontend/packages/shared/src/match.ts 及 Web features/matches/presentation.ts 保持一致；
// 小程序没有打包链路、不引共享包，因此本地移植并由 Vitest 锁定口径。
import type { MatchSummary } from '../types/api';

export type StatusTone =
  | 'open'
  | 'closed'
  | 'locked'
  | 'live'
  | 'finished'
  | 'postponed'
  | 'cancelled';
export type TypeTone = 'external' | 'internal' | 'training' | 'friendly';
export type ResultTone = 'win' | 'draw' | 'loss' | 'muted';

const MATCH_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '报名中',
  1: '报名截止',
  2: '已锁定',
  3: '进行中',
  4: '已结束',
  5: '已延期',
  6: '已取消',
};

const MATCH_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '对外比赛',
  1: '队内赛',
  2: '训练赛',
  3: '友谊赛',
};

export function statusTone(status: number | null | undefined): StatusTone {
  switch (status) {
    case 0:
      return 'open';
    case 1:
      return 'closed';
    case 2:
      return 'locked';
    case 3:
      return 'live';
    case 4:
      return 'finished';
    case 5:
      return 'postponed';
    case 6:
      return 'cancelled';
    default:
      return 'closed';
  }
}

export function typeTone(type: number | null | undefined): TypeTone {
  switch (type) {
    case 1:
      return 'internal';
    case 2:
      return 'training';
    case 3:
      return 'friendly';
    default:
      return 'external';
  }
}

export function resultTone(resultText: string | null | undefined): ResultTone {
  if (!resultText) {
    return 'muted';
  }
  if (resultText.includes('胜')) {
    return 'win';
  }
  if (resultText.includes('平')) {
    return 'draw';
  }
  if (resultText.includes('负')) {
    return 'loss';
  }
  return 'muted';
}

/** 仅已结束且比分不平时返回胜方；0 主队、1 客队。 */
export function winnerSide(
  match: Pick<MatchSummary, 'status' | 'scoreHome' | 'scoreAway'>,
): 0 | 1 | null {
  if (match.status !== 4) {
    return null;
  }
  const home = match.scoreHome;
  const away = match.scoreAway;
  if (home === null || home === undefined || away === null || away === undefined) {
    return null;
  }
  if (home === away) {
    return null;
  }
  return home > away ? 0 : 1;
}

export function queuePercent(used: number, capacity: number): number {
  if (!capacity || capacity <= 0) {
    return 0;
  }
  return Math.max(0, Math.min(100, Math.round((used / capacity) * 100)));
}

export function scoreText(score: Pick<MatchSummary, 'scoreHome' | 'scoreAway'>): string {
  if (score.scoreHome === null || score.scoreHome === undefined) {
    return 'VS';
  }
  return `${score.scoreHome} : ${score.scoreAway ?? 0}`;
}

export function matchStatusText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (MATCH_STATUS_TEXT[code] ?? '');
}

export function matchTypeText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (MATCH_TYPE_TEXT[code] ?? '');
}

/** 赛程场次文案：2026赛季第5场（累计5场）；比赛缺失时返回空串。 */
export function seasonMatchText(
  match: Pick<MatchSummary, 'season' | 'seasonMatchNo' | 'totalMatchNo'> | null | undefined,
): string {
  if (!match) {
    return '';
  }
  return `${match.season}赛季第${match.seasonMatchNo}场（累计${match.totalMatchNo}场）`;
}
