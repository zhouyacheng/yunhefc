import type { Player } from '@yunhefc/shared';

/** INV-01 team：TeamRef，后端可能返回 null。 */
export interface InviteTeamRef {
  id: number;
  name: string;
  shortName: string;
}

/** INV-01 match：后端实际只返回摘要字段，不含容量与报名数。 */
export interface InviteMatchSummary {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  venue: string;
  status: number;
  homeTeamName: string;
  awayTeamName: string;
}

/** INV-01 inviter：UserSummary 子集。 */
export interface InviteInviter {
  id: number;
  name: string;
  phoneMasked?: string;
  status?: number;
  statusLabel?: string;
  roles?: string[];
  type?: number;
  typeLabel?: string;
}

/** INV-01 `GET /c/invites/{token}`，按后端 InviteView 实际响应声明。 */
export interface InviteLandingView {
  id: number;
  type: number;
  typeLabel?: string;
  team: InviteTeamRef | null;
  matchId: number | null;
  match: InviteMatchSummary | null;
  inviter: InviteInviter | null;
  expiresAt: string;
  maxUses: number | null;
  usedCount: number;
  remainingUses: number | null;
  status: number;
  statusLabel?: string;
  exhausted: boolean;
  createdAt: string;
}

/** INV-02 `POST /c/invites/{token}/accept`。 */
export interface InviteAcceptResult {
  player: Player;
  matchId: number | null;
}
