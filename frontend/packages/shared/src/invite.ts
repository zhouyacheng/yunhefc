import type { Invite } from './auth';

/** 后端 InviteView.match 只返回比赛摘要子集；auth.ts 保持 unknown，这里统一收窄。 */
export interface InviteMatchRef {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  venue: string;
  status: number;
  homeTeamName?: string;
  awayTeamName?: string;
}

/** INV-05 响应；token 只返回一次。 */
export interface InviteCreated {
  inviteUrl: string;
  expiresAt: string;
}

export type InviteStatusTone = 'success' | 'info' | 'warning';

type InviteDisplayStatus = 'active' | 'revoked' | 'expired' | 'exhausted';

export function inviteTypeText(type: number | null | undefined): string {
  if (type === 0) {
    return '入队邀请';
  }
  return type === 1 ? '比赛邀请' : '—';
}

/** 撤销 > 过期 > 用尽 > 有效；过期按当前时间重算，避免列表停留期间显示失真。 */
function displayStatus(
  invite: Pick<Invite, 'status' | 'expiresAt' | 'exhausted'>,
  now: number,
): InviteDisplayStatus {
  if (invite.status === 1) {
    return 'revoked';
  }
  const expiresAt = Date.parse(invite.expiresAt);
  if (invite.status === 2 || (!Number.isNaN(expiresAt) && expiresAt < now)) {
    return 'expired';
  }
  return invite.exhausted ? 'exhausted' : 'active';
}

export function inviteStatusText(
  invite: Pick<Invite, 'status' | 'expiresAt' | 'exhausted'>,
  now: number = Date.now(),
): string {
  const status = displayStatus(invite, now);
  if (status === 'revoked') {
    return '已撤销';
  }
  if (status === 'expired') {
    return '已过期';
  }
  return status === 'exhausted' ? '已用尽' : '有效';
}

export function inviteStatusTone(
  invite: Pick<Invite, 'status' | 'expiresAt' | 'exhausted'>,
  now: number = Date.now(),
): InviteStatusTone {
  const status = displayStatus(invite, now);
  if (status === 'active') {
    return 'success';
  }
  return status === 'revoked' ? 'info' : 'warning';
}

/** maxUses 为 null 表示不限次数，不能显示成「/ 0」。 */
export function inviteQuotaText(invite: Pick<Invite, 'usedCount' | 'maxUses'>): string {
  return `${invite.usedCount} / ${invite.maxUses == null ? '不限' : invite.maxUses}`;
}

export function inviteMatchTitle(invite: Pick<Invite, 'match'>): string {
  const match = invite.match as InviteMatchRef | null | undefined;
  return match && typeof match.title === 'string' && match.title ? match.title : '—';
}
