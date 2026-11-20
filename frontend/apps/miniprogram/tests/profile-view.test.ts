import { describe, expect, it } from 'vitest';

import type {
  AuthUser,
  MatchSummary,
  MyRegistration,
  Notification,
  PlayerView,
} from '../miniprogram/types/api';
import {
  decorateNotification,
  decorateRegistration,
  identityOf,
} from '../miniprogram/utils/profile-view';

const user: AuthUser = {
  id: 7,
  name: '账号名',
  phoneMasked: '139****0000',
  status: 1,
  statusLabel: 'ACTIVE',
  roles: ['PLAYER'],
  createdAt: '2026-11-01T10:00:00+08:00',
  wechatBound: true,
};

const player: PlayerView = {
  id: 1,
  name: '球员',
  jersey: 9,
  type: 1,
  typeLabel: 'PLAYER',
  position: 3,
  positionLabel: '前锋',
  skill: 4.5,
  avatarUrl: null,
};

function summary(overrides: Partial<MatchSummary> = {}): MatchSummary {
  return {
    id: 11,
    title: '比赛',
    type: 1,
    typeLabel: 'INTERNAL',
    startAt: '2026-12-06T20:00:00+08:00',
    endAt: '2026-12-06T22:00:00+08:00',
    venue: '聚龙足球场',
    season: '2026',
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    scoreHome: 2,
    scoreAway: 1,
    resultText: '胜',
    status: 0,
    statusLabel: 'SIGNUP_OPEN',
    totalMatchNo: 9,
    seasonMatchNo: 5,
    capacity: 20,
    guestCapacity: 4,
    waitlistCapacity: 3,
    confirmedCount: 10,
    guestCount: 2,
    waitlistCount: 1,
    ...overrides,
  };
}

describe('profile view', () => {
  it('builds identity from player and roles', () => {
    expect(identityOf(user, player)).toMatchObject({
      name: '球员',
      initial: '球',
      roleText: '正式球员',
      phoneMasked: '139****0000',
      jerseyText: '9',
      positionText: '前锋',
      skillText: '4.50',
      skillPercent: 90,
      pending: false,
    });
  });

  it('marks pending accounts and falls back to the account name', () => {
    const identity = identityOf({ ...user, status: 0, roles: [] }, null);
    expect(identity).toMatchObject({
      name: '账号名',
      roleText: '—',
      jerseyText: '—',
      positionText: '—',
      skillText: '—',
      skillPercent: 0,
      pending: true,
    });
  });

  it('decorates notifications with Shanghai time and unread state', () => {
    const item: Notification = {
      id: 3,
      type: 1,
      typeLabel: 'MATCH',
      title: '比赛提醒',
      body: '明天 20:00',
      refType: 'MATCH',
      refId: 11,
      readAt: null,
      createdAt: '2026-12-03T20:00:00+08:00',
    };

    expect(decorateNotification(item)).toMatchObject({
      id: 3,
      timeText: '2026-12-03 20:00',
      unread: true,
      refType: 'MATCH',
      refId: 11,
    });
    expect(decorateNotification({ ...item, readAt: '2026-12-04T10:00:00+08:00' }).unread).toBe(
      false,
    );
  });

  it('decorates registrations and voids cancelled matches', () => {
    const row: MyRegistration = {
      registration: { id: 5, type: 1, status: 0, registeredAt: '2026-12-01T10:00:00+08:00' },
      match: summary(),
    };

    expect(decorateRegistration(row)).toMatchObject({
      matchId: 11,
      statusText: '已确认',
      statusTone: 'open',
      timeText: '2026-12-06 20:00',
      seasonText: '2026赛季第5场（累计9场）',
      matchStatusText: '报名中',
      matchStatusTone: 'open',
    });

    expect(
      decorateRegistration({ ...row, match: summary({ status: 6, statusLabel: 'CANCELLED' }) }),
    ).toMatchObject({ statusText: '已作废', statusTone: 'closed', matchStatusTone: 'cancelled' });
  });
});
