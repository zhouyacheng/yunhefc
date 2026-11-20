import type { AuthUser, MyRegistration, Notification, PlayerView } from '../types/api';
import { formatDateTime } from './datetime';
import {
  matchStatusText,
  positionText,
  registrationStatusText,
  seasonMatchText,
  statusTone,
} from './presentation';

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
  skillPercent: number;
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
}

export function identityOf(user: AuthUser, player: PlayerView | null): IdentityView {
  const name = player?.name || user.name;
  const roles = (user.roles || []).map((role) => ROLE_TEXT[role] ?? role);
  return {
    name,
    initial: name.slice(0, 1) || '云',
    roleText: roles.length ? roles.join(' / ') : '—',
    phoneMasked: user.phoneMasked,
    jerseyText:
      player?.jersey === null || player?.jersey === undefined ? '—' : String(player.jersey),
    positionText: player ? player.positionLabel || positionText(player.position) : '—',
    skillText: player ? player.skill.toFixed(2) : '—',
    skillPercent: player ? Math.max(0, Math.min(100, (player.skill / 5) * 100)) : 0,
    avatarUrl: player?.avatarUrl ?? '',
    pending: user.status === 0,
  };
}

export function decorateNotification(item: Notification): NotificationRow {
  return {
    id: item.id,
    title: item.title,
    body: item.body,
    timeText: formatDateTime(item.createdAt),
    unread: !item.readAt,
    readAt: item.readAt ?? null,
    refType: item.refType ?? null,
    refId: item.refId ?? null,
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
  };
}
