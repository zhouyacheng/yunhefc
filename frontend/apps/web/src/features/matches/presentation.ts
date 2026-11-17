import type { DerivedScore, MatchSummary } from '@yunhefc/shared';

export type StatusTone =
  'open' | 'closed' | 'locked' | 'live' | 'finished' | 'postponed' | 'cancelled';
export type ResultTone = 'win' | 'draw' | 'loss' | 'muted';

/** 与原型 prototype/assets/css/style.css 的状态徽标一一对应。 */
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

/** 有事件推导值时优先展示推导值，否则使用服务端返回的最终比分。 */
export function resolveDisplayedScore(
  match: Pick<MatchSummary, 'scoreHome' | 'scoreAway'> | null | undefined,
  derived: Pick<DerivedScore, 'scoreHome' | 'scoreAway'> | null | undefined,
): Pick<MatchSummary, 'scoreHome' | 'scoreAway'> | null {
  return derived ?? match ?? null;
}

export function scoreText(score: Pick<MatchSummary, 'scoreHome' | 'scoreAway'>): string {
  if (score.scoreHome === null || score.scoreHome === undefined) {
    return 'VS';
  }
  return `${score.scoreHome} : ${score.scoreAway ?? 0}`;
}
