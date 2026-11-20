import type { MatchSummary } from '../types/api';
import { formatDayStartTime } from './datetime';
import {
  matchStatusText,
  matchTypeText,
  queuePercent,
  resultTone,
  scoreText,
  seasonMatchText,
  statusTone,
  typeTone,
  winnerSide,
  type ResultTone,
  type StatusTone,
  type TypeTone,
} from './presentation';

/** Hero、比赛卡片与比赛详情头部共用的比赛展示字段。 */
export interface MatchCoreView {
  typeText: string;
  typeTone: TypeTone;
  statusText: string;
  statusTone: StatusTone;
  homeTeamName: string;
  awayTeamName: string;
  homeWinner: boolean;
  awayWinner: boolean;
  scoreText: string;
  ordinalText: string;
  queues: QueueView[];
}

export interface QueueView {
  key: string;
  label: string;
  count: number;
  capacity: number;
  percent: number;
}

export interface MatchCardView extends MatchCoreView {
  id: number;
  dateText: string;
  resultText: string;
  resultTone: ResultTone;
  venue: string;
}

export function queuesOf(match: MatchSummary): QueueView[] {
  return [
    {
      key: 'formal',
      label: '正式',
      count: match.confirmedCount,
      capacity: match.capacity,
      percent: queuePercent(match.confirmedCount, match.capacity),
    },
    {
      key: 'guest',
      label: '外挂',
      count: match.guestCount,
      capacity: match.guestCapacity,
      percent: queuePercent(match.guestCount, match.guestCapacity),
    },
    {
      key: 'waitlist',
      label: '候补',
      count: match.waitlistCount,
      capacity: match.waitlistCapacity,
      percent: queuePercent(match.waitlistCount, match.waitlistCapacity),
    },
  ];
}

/** displayed 用于事件推导比分等覆盖值；缺省使用比赛自身比分。 */
export function buildMatchCore(
  match: MatchSummary,
  displayed?: Pick<MatchSummary, 'scoreHome' | 'scoreAway'> | null,
): MatchCoreView {
  const score = displayed ?? match;
  const winner = winnerSide({
    status: match.status,
    scoreHome: score.scoreHome,
    scoreAway: score.scoreAway,
  });
  return {
    typeText: matchTypeText(match.type),
    typeTone: typeTone(match.type),
    statusText: matchStatusText(match.status),
    statusTone: statusTone(match.status),
    homeTeamName: match.homeTeamName,
    awayTeamName: match.awayTeamName,
    homeWinner: winner === 0,
    awayWinner: winner === 1,
    scoreText: scoreText(score),
    ordinalText: seasonMatchText(match),
    queues: queuesOf(match),
  };
}

export function buildMatchCard(match: MatchSummary): MatchCardView {
  return {
    ...buildMatchCore(match),
    id: match.id,
    dateText: formatDayStartTime(match.startAt),
    resultText: match.resultText ?? '',
    resultTone: resultTone(match.resultText),
    venue: match.venue,
  };
}
