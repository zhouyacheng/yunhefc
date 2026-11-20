// 比赛详情头部与事件时间线：与 Web C03（MatchDetailPage.vue）口径一致；
// 小程序只做只读展示，不包含报名、名单、邀请与管理员操作。
import type { DerivedScore, MatchDetail, MatchEvent } from '../types/api';
import { formatDateWithYear, formatDateTimeWithYear, formatTimeRange } from './datetime';
import { buildMatchCore, type MatchCoreView } from './match-view';
import {
  eventHalfText,
  eventTypeText,
  isCancelledStatus,
  isPostponedStatus,
  isSubstitutionType,
  resolveDisplayedScore,
  resultTone,
  type ResultTone,
} from './presentation';

export interface MatchDetailHeaderView extends MatchCoreView {
  resultText: string;
  resultTone: ResultTone;
  dateText: string;
  timeRangeText: string;
  signupDeadlineText: string;
  cancelLockText: string;
  venue: string;
  isCancelled: boolean;
  isPostponed: boolean;
  cancelledCountsText: string;
}

export interface EventRowView {
  id: number;
  timeText: string;
  typeText: string;
  detailText: string;
  teamText: string;
  side: 'home' | 'away';
}

export function buildMatchDetailHeader(
  detail: MatchDetail,
  derived: DerivedScore | null | undefined,
): MatchDetailHeaderView {
  // 事件推导值优先；其次详情自带的 derivedScore；最后兜底比分。
  const displayed = resolveDisplayedScore(detail, derived ?? detail.derivedScore ?? null);
  return {
    ...buildMatchCore(detail, displayed),
    resultText: detail.resultText ?? '',
    resultTone: resultTone(detail.resultText),
    dateText: formatDateWithYear(detail.startAt),
    timeRangeText: formatTimeRange(detail.startAt, detail.endAt),
    signupDeadlineText: formatDateTimeWithYear(detail.signupDeadline),
    cancelLockText: formatDateTimeWithYear(detail.cancelLock),
    venue: detail.venue,
    isCancelled: isCancelledStatus(detail.status),
    isPostponed: isPostponedStatus(detail.status),
    cancelledCountsText: `历史报名：正式 ${detail.confirmedCount} · 外挂 ${detail.guestCount} · 候补 ${detail.waitlistCount}`,
  };
}

/** 与 Web C03 的事件明细文案一致。 */
export function eventDetailText(event: MatchEvent): string {
  if (isSubstitutionType(event.type)) {
    return `${event.inPlayer?.name ?? '—'} 换上 · ${event.outPlayer?.name ?? '—'} 换下`;
  }
  const parts = [event.player?.name ?? '—'];
  if (event.assistPlayer?.name) {
    parts.push(`助攻 ${event.assistPlayer.name}`);
  }
  if (event.penalty) {
    parts.push('点球');
  }
  return parts.join(' · ');
}

function sideOf(team: number): 'home' | 'away' {
  return team === 1 ? 'away' : 'home';
}

export function buildEventRows(
  events: readonly MatchEvent[] | null | undefined,
  detail: Pick<MatchDetail, 'homeTeamName' | 'awayTeamName'>,
): EventRowView[] {
  return (events ?? []).map((event) => {
    const half = eventHalfText(event.half);
    const side = sideOf(event.team);
    return {
      id: event.id,
      timeText: half ? `${half} ${event.minute}'` : `${event.minute}'`,
      typeText: eventTypeText(event.type),
      detailText: eventDetailText(event),
      teamText: side === 'away' ? detail.awayTeamName : detail.homeTeamName,
      side,
    };
  });
}
