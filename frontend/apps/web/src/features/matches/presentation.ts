import type { MatchStatusTone, MatchSummary } from '@yunhefc/shared';
import { matchStatusTone } from '@yunhefc/shared';

export type MatchTagType = Exclude<MatchStatusTone, 'neutral'> | 'primary';

/** 中立状态在 Vant 中映射为主色标签。 */
export function matchTagType(status: number | null | undefined): MatchTagType {
  const tone = matchStatusTone(status);
  return tone === 'neutral' ? 'primary' : tone;
}

export function scoreText(score: Pick<MatchSummary, 'scoreHome' | 'scoreAway'>): string {
  if (score.scoreHome === null || score.scoreHome === undefined) {
    return 'VS';
  }
  return `${score.scoreHome} : ${score.scoreAway ?? 0}`;
}
