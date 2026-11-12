import type { Registration } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import { USER_STATUS } from '../auth/user-status';
import { homeCta, type HomeCtaInput } from './cta';

const DEADLINE = '2026-11-12T10:30:00Z';

function registration(status: number): Registration {
  return {
    id: 1,
    playerId: 1002,
    type: 1,
    status,
    registeredAt: '2026-11-12T09:00:00Z',
    cancelledAt: null,
    active: status === 0 || status === 1,
  };
}

function input(overrides: Partial<HomeCtaInput> = {}): HomeCtaInput {
  return {
    authenticated: true,
    userStatus: USER_STATUS.ACTIVE,
    playerType: 1,
    matchStatus: 0,
    now: Date.parse('2026-11-12T10:00:00Z'),
    signupDeadline: DEADLINE,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
    myRegistration: null,
    ...overrides,
  };
}

describe('homeCta', () => {
  it('asks guests to log in', () => {
    expect(homeCta(input({ authenticated: false }))).toEqual({
      label: '登录后报名',
      action: 'login',
      disabled: false,
      reason: '',
    });
  });

  it('blocks pending accounts', () => {
    expect(homeCta(input({ userStatus: USER_STATUS.PENDING }))).toEqual({
      label: '账号待审核，通过后可报名',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('shows detail for a confirmed registration', () => {
    expect(homeCta(input({ myRegistration: registration(0) }))).toEqual({
      label: '查看详情',
      action: 'view-detail',
      disabled: false,
      reason: '已报名',
    });
  });

  it('shows detail for a waitlisted registration', () => {
    expect(homeCta(input({ myRegistration: registration(1) }))).toEqual({
      label: '查看详情',
      action: 'view-detail',
      disabled: false,
      reason: '候补中',
    });
  });

  it('signs up a formal player while capacity remains', () => {
    expect(homeCta(input({ confirmedCount: 29 }))).toEqual({
      label: '报名',
      action: 'register',
      disabled: false,
      reason: '',
    });
  });

  it('offers the waitlist when the formal pool is full', () => {
    expect(homeCta(input({ confirmedCount: 30, waitlistCount: 1 }))).toEqual({
      label: '加入候补',
      action: 'register',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
    });
  });

  it('blocks when both the formal pool and waitlist are full', () => {
    expect(homeCta(input({ confirmedCount: 30, waitlistCount: 2 }))).toEqual({
      label: '名额与候补已满',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('blocks guests when the guest pool is full', () => {
    expect(homeCta(input({ playerType: 0, guestCount: 4, confirmedCount: 30 }))).toEqual({
      label: '外挂名额已满',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('signs up a guest while the guest pool remains', () => {
    expect(homeCta(input({ playerType: 0, guestCount: 3 }))).toEqual({
      label: '报名',
      action: 'register',
      disabled: false,
      reason: '',
    });
  });

  it('closes signup exactly at the deadline', () => {
    expect(homeCta(input({ now: Date.parse(DEADLINE) }))).toEqual({
      label: '报名已截止',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('keeps signup open just before the deadline', () => {
    expect(homeCta(input({ now: Date.parse(DEADLINE) - 1 })).disabled).toBe(false);
  });

  it.each([
    [1, '报名已截止'],
    [2, '报名已锁定'],
    [3, '比赛已开始'],
    [4, '比赛已结束'],
    [5, '比赛已延期'],
    [6, '比赛已取消'],
  ] as const)('blocks when match status is %i', (matchStatus, label) => {
    expect(homeCta(input({ matchStatus }))).toEqual({
      label,
      action: 'none',
      disabled: true,
      reason: '',
    });
  });
});
