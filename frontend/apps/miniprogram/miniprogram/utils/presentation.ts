// 与 frontend/packages/shared/src/match.ts 及 Web features/matches/presentation.ts 保持一致；
// 小程序没有打包链路、不引共享包，因此本地移植并由 Vitest 锁定口径。
import type { DerivedScore, MatchSummary } from '../types/api';

export type StatusTone =
  'open' | 'closed' | 'locked' | 'live' | 'finished' | 'postponed' | 'cancelled';
export type TypeTone = 'external' | 'internal' | 'training' | 'friendly';
export type ResultTone = 'win' | 'draw' | 'loss' | 'muted';

export const MATCH_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '报名中',
  1: '报名截止',
  2: '已锁定',
  3: '进行中',
  4: '已结束',
  5: '已延期',
  6: '已取消',
};

export const MATCH_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '对外比赛',
  1: '队内赛',
  2: '训练赛',
  3: '友谊赛',
};

export const EVENT_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '进球',
  1: '乌龙球',
  2: '换人',
  3: '黄牌',
  4: '红牌',
};

export const EVENT_HALF_TEXT: Readonly<Record<number, string>> = {
  0: '上半场',
  1: '下半场',
};

export const POSITION_TEXT: Readonly<Record<number, string>> = {
  0: '门将',
  1: '后卫',
  2: '中场',
  3: '前锋',
};

export const REGISTRATION_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '已确认',
  1: '候补中',
  2: '已取消',
  3: '已移除',
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

export function eventTypeText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (EVENT_TYPE_TEXT[code] ?? '');
}

export function eventHalfText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (EVENT_HALF_TEXT[code] ?? '');
}

export function isCancelledStatus(code: number | null | undefined): boolean {
  return code === 6;
}

export function isPostponedStatus(code: number | null | undefined): boolean {
  return code === 5;
}

export function isSubstitutionType(type: number | null | undefined): boolean {
  return type === 2;
}

/** 有事件推导值时优先展示推导值，否则使用服务端返回的最终比分。 */
export function resolveDisplayedScore(
  match: Pick<MatchSummary, 'scoreHome' | 'scoreAway'> | null | undefined,
  derived: Pick<DerivedScore, 'scoreHome' | 'scoreAway'> | null | undefined,
): Pick<MatchSummary, 'scoreHome' | 'scoreAway'> | null {
  return derived ?? match ?? null;
}

export function positionText(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : (POSITION_TEXT[code] ?? '—');
}

export function registrationStatusText(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : (REGISTRATION_STATUS_TEXT[code] ?? '—');
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
