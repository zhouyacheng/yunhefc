import type { AuthUser, MyRegistration, Notification, PlayerView } from '../types/api';
import { avatarInitial, avatarSrc } from './avatar';
import { formatDateTime } from './datetime';
import type { InvalidationResource } from './invalidation';
import {
  matchStatusText,
  positionText,
  registrationStatusText,
  seasonMatchText,
  statusTone,
} from './presentation';
import { starParts, type SkillStarMark } from './skill';

const ROLE_TEXT: Readonly<Record<string, string>> = {
  ADMIN: '管理员',
  PLAYER: '正式球员',
  GUEST: '外挂球员',
};

export interface IdentityView {
  name: string;
  initial: string;
  roleText: string;
  phoneMasked: string;
  jerseyText: string;
  positionText: string;
  skillText: string;
  stars: SkillStarMark[];
  avatarUrl: string;
  pending: boolean;
}

export interface NotificationRow {
  id: number;
  title: string;
  body: string;
  timeText: string;
  unread: boolean;
  readAt: string | null;
  refType: string | null;
  refId: number | null;
  /** 通知落点文案与页面路径；无落点为空串。 */
  linkLabel: string;
  linkUrl: string;
  /** 点击后需要失效的跨页资源。 */
  invalidate: InvalidationResource | null;
}

export interface RegistrationRowView {
  matchId: number;
  title: string;
  statusText: string;
  statusTone: string;
  timeText: string;
  venue: string;
  seasonText: string;
  matchStatusText: string;
  matchStatusTone: string;
  canCancel: boolean;
  cancelLabel: string;
  registrationStatus: number;
}

export function identityOf(user: AuthUser, player: PlayerView | null): IdentityView {
  const name = player?.name || user.name;
  const roles = (user.roles || []).map((role) => ROLE_TEXT[role] ?? role);
  return {
    name,
    initial: avatarInitial(name),
    roleText: roles.length ? roles.join(' / ') : '—',
    phoneMasked: user.phoneMasked,
    jerseyText:
      player?.jersey === null || player?.jersey === undefined ? '—' : String(player.jersey),
    positionText: player ? positionText(player.position) : '—',
    skillText: player ? player.skill.toFixed(2) : '—',
    stars: starParts(player?.skill),
    avatarUrl: avatarSrc(player?.avatarUrl) ?? '',
    pending: user.status === 0,
  };
}

function notificationLink(
  refType: string | null,
  refId: number | null,
): Pick<NotificationRow, 'linkLabel' | 'linkUrl' | 'invalidate'> {
  if (typeof refId !== 'number' || refId < 1) {
    return { linkLabel: '', linkUrl: '', invalidate: null };
  }
  if (refType === 'MATCH') {
    return {
      linkLabel: '查看比赛',
      linkUrl: `/pages/match-detail/index?id=${refId}`,
      invalidate: 'my-registrations',
    };
  }
  if (refType === 'ACTIVITY') {
    return {
      linkLabel: '查看活动',
      linkUrl: `/pages/activity-detail/index?id=${refId}`,
      invalidate: null,
    };
  }
  return { linkLabel: '', linkUrl: '', invalidate: null };
}

export function decorateNotification(item: Notification): NotificationRow {
  const refType = item.refType ?? null;
  const refId = item.refId ?? null;
  return {
    id: item.id,
    title: item.title,
    body: item.body,
    timeText: formatDateTime(item.createdAt),
    unread: !item.readAt,
    readAt: item.readAt ?? null,
    refType,
    refId,
    ...notificationLink(refType, refId),
  };
}

function registrationTone(status: number, voided: boolean): string {
  if (voided) {
    return 'closed';
  }
  if (status === 0) {
    return 'open';
  }
  if (status === 1) {
    return 'postponed';
  }
  return 'muted';
}

export function decorateRegistration(row: MyRegistration): RegistrationRowView {
  const voided = row.match.status === 6;
  const active = row.registration.status === 0 || row.registration.status === 1;
  return {
    matchId: row.match.id,
    title: row.match.title,
    statusText: voided ? '已作废' : registrationStatusText(row.registration.status),
    statusTone: registrationTone(row.registration.status, voided),
    timeText: formatDateTime(row.match.startAt),
    venue: row.match.venue,
    seasonText: seasonMatchText(row.match),
    matchStatusText: matchStatusText(row.match.status),
    matchStatusTone: statusTone(row.match.status),
    canCancel: !voided && active && (row.match.status === 0 || row.match.status === 1),
    cancelLabel: row.registration.status === 1 ? '取消候补' : '取消报名',
    registrationStatus: row.registration.status,
  };
}
