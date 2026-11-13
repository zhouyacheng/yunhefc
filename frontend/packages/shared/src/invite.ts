import type { Invite } from './auth';

/** INV-05 响应；token 只返回一次。 */
export interface InviteCreated {
  inviteUrl: string;
  expiresAt: string;
}

export type InviteStatusTone = 'success' | 'info' | 'warning';

export interface InviteStatusMeta {
  text: string;
  tone: InviteStatusTone;
}

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

const STATUS_META: Record<InviteDisplayStatus, InviteStatusMeta> = {
  active: { text: '有效', tone: 'success' },
  revoked: { text: '已撤销', tone: 'info' },
  expired: { text: '已过期', tone: 'warning' },
  exhausted: { text: '已用尽', tone: 'warning' },
};

/** 状态文本与标签配色来自同一次判定，避免两处调用出现不一致。 */
export function inviteStatusMeta(
  invite: Pick<Invite, 'status' | 'expiresAt' | 'exhausted'>,
  now: number = Date.now(),
): InviteStatusMeta {
  return STATUS_META[displayStatus(invite, now)];
}

/** maxUses 为 null 表示不限次数，不能显示成「/ 0」。 */
export function inviteQuotaText(invite: Pick<Invite, 'usedCount' | 'maxUses'>): string {
  return `${invite.usedCount} / ${invite.maxUses == null ? '不限' : invite.maxUses}`;
}

export function inviteMatchTitle(invite: Pick<Invite, 'match'>): string {
  return invite.match?.title || '—';
}
