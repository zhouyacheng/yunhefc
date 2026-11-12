import type { InviteLandingView } from './types';

/** invite_link.type：0-入队；1-比赛。 */
export const INVITE_TYPE = { TEAM: 0, MATCH: 1 } as const;
/** invite_link.status：0-有效；1-已撤销；2-已过期。 */
export const INVITE_STATUS = { ACTIVE: 0, REVOKED: 1, EXPIRED: 2 } as const;

export type InviteLandingState = 'notFound' | 'invalid' | 'team' | 'match' | 'member';

export interface InviteStateInput {
  /** INV-01 响应；null 表示 token 不存在。 */
  invite: InviteLandingView | null;
  /** 已是正式球员（player.type=1），或本次接受邀请成功。 */
  member: boolean;
}

/** 状态判断顺序：失效 → 比赛邀请 → 已是成员 → 可加入。 */
export function inviteLandingState(input: InviteStateInput): InviteLandingState {
  const { invite, member } = input;
  if (!invite) {
    return 'notFound';
  }
  if (invite.status !== INVITE_STATUS.ACTIVE || invite.exhausted) {
    return 'invalid';
  }
  if (invite.type === INVITE_TYPE.MATCH) {
    return 'match';
  }
  return member ? 'member' : 'team';
}

export function inviteTypeText(type: number): string {
  return type === INVITE_TYPE.MATCH ? '比赛邀请' : '入队邀请';
}

/** 后端 statusLabel 为英文枚举，失效原因统一按 code 输出中文。 */
export function inviteInvalidText(invite: InviteLandingView | null): string {
  if (invite?.status === INVITE_STATUS.EXPIRED) {
    return '该邀请已过期';
  }
  if (invite?.status === INVITE_STATUS.REVOKED) {
    return '该邀请已被撤销';
  }
  if (invite?.exhausted) {
    return '该邀请的使用次数已用完';
  }
  return '该邀请已失效';
}

/** maxUses 为 null 表示不限次数，不能显示「剩余 0 次」。 */
export function remainingUsesText(
  invite: Pick<InviteLandingView, 'maxUses' | 'usedCount' | 'remainingUses'>,
): string {
  if (invite.maxUses === null) {
    return '不限';
  }
  const remaining = invite.remainingUses ?? Math.max(0, invite.maxUses - invite.usedCount);
  return `${remaining} 次`;
}

export interface MatchInviteCapacity {
  confirmedCount: number;
  capacity: number;
  signupDeadline: string;
}

export type MatchInviteHint = 'open' | 'full' | 'closed';

/** 比赛邀请提示：先判报名截止，再判名额已满。 */
export function matchInviteHint(
  detail: MatchInviteCapacity | null,
  now: number,
): MatchInviteHint | null {
  if (!detail) {
    return null;
  }
  const deadline = Date.parse(detail.signupDeadline);
  if (Number.isFinite(deadline) && deadline <= now) {
    return 'closed';
  }
  if (detail.capacity > 0 && detail.confirmedCount >= detail.capacity) {
    return 'full';
  }
  return 'open';
}
