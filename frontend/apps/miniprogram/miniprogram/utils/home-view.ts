import type { AuthUser, MatchDetail, MatchSummary, PlayerView } from '../types/api';
import { formatDayStartTime } from './datetime';
import { homeCta, type HomeCtaResult } from './home-cta';
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

/** 与 Web HomePage.vue 的 FEATURES 保持一致。 */
export const HOME_FEATURES = [
  { icon: '📈', title: '排行榜与身价', text: '年度出场、进球、助攻、胜率、缺勤与 AI 身价' },
  { icon: '💰', title: '球队账本', text: '收入/支出分类，按月/年统计' },
  { icon: '📸', title: '动态与相册', text: '比赛照片、B 站视频、点赞留言' },
  { icon: '🗳️', title: '投票', text: '标题、图片选项、多选、截止时间' },
  { icon: '📣', title: '公众号提醒', text: '赛事发布与模板消息' },
  { icon: '🏆', title: '资产与荣誉', text: '器材台账、球队里程碑' },
];

/** Hero、最近比赛卡片与比赛详情头部共用的比赛展示字段。 */
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

export interface HeroView extends MatchCoreView {
  id: number;
  metaText: string;
  cta: HomeCtaResult;
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
export function coreOf(
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

export function buildHero(
  detail: MatchDetail,
  user: AuthUser | null,
  player: PlayerView | null,
  now: number,
): HeroView {
  return {
    ...coreOf(detail),
    id: detail.id,
    metaText: `${formatDayStartTime(detail.startAt)} · ${detail.venue}`,
    cta: homeCta({
      authenticated: user !== null,
      userStatus: user?.status ?? null,
      playerType: player?.type ?? null,
      matchStatus: detail.status,
      now,
      signupDeadline: detail.signupDeadline,
      queue: detail,
      myRegistration: detail.myRegistration ?? null,
    }),
  };
}

export function buildMatchCard(match: MatchSummary): MatchCardView {
  return {
    ...coreOf(match),
    id: match.id,
    dateText: formatDayStartTime(match.startAt),
    resultText: match.resultText ?? '',
    resultTone: resultTone(match.resultText),
    venue: match.venue,
  };
}
