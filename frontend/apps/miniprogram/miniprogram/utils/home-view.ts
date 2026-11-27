import type { AuthUser, MatchDetail, PlayerView } from '../types/api';
import { formatDayStartTime } from './datetime';
import { homeCta, type HomeCtaResult } from './home-cta';
import { buildMatchCore, type MatchCoreView } from './match-view';
import { isPending } from './permissions';
import { registrationDecision } from './registration';

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
