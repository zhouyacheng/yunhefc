import type { AuthUser, MatchDetail, PlayerView } from '../types/api';
import { formatDayStartTime } from './datetime';
import { homeCta, type HomeCtaResult } from './home-cta';
import { buildMatchCore, type MatchCoreView } from './match-view';
import { isPending } from './permissions';
import { registrationDecision } from './registration';

/** 与 Web HomePage.vue 的 FEATURES 保持一致。 */
export const HOME_FEATURES = [
  { icon: '📈', title: '排行榜与身价', text: '年度出场、进球、助攻、胜率、缺勤与 AI 身价' },
  { icon: '💰', title: '球队账本', text: '收入/支出分类，按月/年统计' },
  { icon: '📸', title: '动态与相册', text: '比赛照片、B 站视频、点赞留言' },
  { icon: '🗳️', title: '投票', text: '标题、图片选项、多选、截止时间' },
  { icon: '📣', title: '公众号提醒', text: '赛事发布与模板消息' },
  { icon: '🏆', title: '资产与荣誉', text: '器材台账、球队里程碑' },
];

export interface HeroView extends MatchCoreView {
  id: number;
  metaText: string;
  cta: HomeCtaResult;
}

export function buildHero(
  detail: MatchDetail,
  user: AuthUser | null,
  player: PlayerView | null,
  permissions: readonly string[] | null,
  now: number,
): HeroView {
  const { signup } = registrationDecision({
    authenticated: user !== null,
    pending: isPending(user?.status),
    playerType: player?.type ?? null,
    permissions,
    matchStatus: detail.status,
    now,
    signupDeadline: detail.signupDeadline,
    cancelLock: detail.cancelLock,
    queue: detail,
    myRegistration: detail.myRegistration ?? null,
  });
  return {
    ...buildMatchCore(detail),
    id: detail.id,
    metaText: `${formatDayStartTime(detail.startAt)} · ${detail.venue}`,
    cta: homeCta(signup),
  };
}
