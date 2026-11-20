import { describe, expect, it } from 'vitest';

import { homeCta, type HomeCtaInput } from '../miniprogram/utils/home-cta';

const DEADLINE = '2026-12-03T12:00:00+08:00';

function input(overrides: Partial<HomeCtaInput> = {}): HomeCtaInput {
  return {
    authenticated: true,
    userStatus: 1,
    playerType: 1,
    matchStatus: 0,
    now: Date.parse('2026-12-03T10:00:00+08:00'),
    signupDeadline: DEADLINE,
    queue: {
      capacity: 20,
      guestCapacity: 4,
      waitlistCapacity: 3,
      confirmedCount: 5,
      guestCount: 0,
      waitlistCount: 0,
    },
    myRegistration: null,
    ...overrides,
  };
}

describe('homeCta', () => {
  it('asks anonymous visitors to log in first', () => {
    expect(homeCta(input({ authenticated: false }))).toEqual({
      label: '登录后报名',
      action: 'login',
      disabled: false,
      reason: '',
    });
  });

  it('blocks pending accounts', () => {
    expect(homeCta(input({ userStatus: 0 }))).toEqual({
      label: '账号待审核，通过后可报名',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('treats a deregistered status like a normal account branch', () => {
    expect(homeCta(input({ userStatus: 2 }))).toEqual({
      label: '报名',
      action: 'register',
      disabled: false,
      reason: '',
    });
  });

  it('shows detail entry for confirmed and waitlisted registrations', () => {
    expect(homeCta(input({ myRegistration: { status: 0 } }))).toMatchObject({
      label: '查看详情',
      action: 'view-detail',
      disabled: false,
      reason: '已报名',
    });
    expect(homeCta(input({ myRegistration: { status: 1 } }))).toMatchObject({
      label: '查看详情',
      action: 'view-detail',
      disabled: false,
      reason: '候补中',
    });
  });

  it.each([
    [1, '报名已截止'],
    [2, '报名已锁定'],
    [3, '比赛已开始'],
    [4, '比赛已结束'],
    [5, '比赛已延期'],
    [6, '比赛已取消'],
    [9, '当前不可报名'],
  ])('disables closed match status %s', (matchStatus, label) => {
    expect(homeCta(input({ matchStatus }))).toMatchObject({
      label,
      action: 'none',
      disabled: true,
    });
  });

  it('disables at the signup deadline', () => {
    expect(homeCta(input({ now: Date.parse(DEADLINE) }))).toMatchObject({
      label: '报名已截止',
      disabled: true,
    });
  });

  it('offers the waitlist when formal slots are full', () => {
    expect(
      homeCta(input({ queue: { ...input().queue, confirmedCount: 20 } })),
    ).toMatchObject({
      label: '加入候补',
      action: 'register',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
    });
  });

  it('blocks when formal and waitlist are both full', () => {
    expect(
      homeCta(
        input({ queue: { ...input().queue, confirmedCount: 20, waitlistCount: 3 } }),
      ),
    ).toMatchObject({ label: '名额与候补已满', disabled: true });
  });

  it('blocks guests when guest slots are full', () => {
    expect(
      homeCta(
        input({ playerType: 0, queue: { ...input().queue, guestCount: 4 } }),
      ),
    ).toMatchObject({ label: '外挂名额已满', disabled: true });
  });

  it('treats a missing player as a guest', () => {
    expect(
      homeCta(
        input({ playerType: null, queue: { ...input().queue, guestCount: 4 } }),
      ),
    ).toMatchObject({ label: '外挂名额已满', disabled: true });
  });

  it('allows a formal player to register while slots remain', () => {
    expect(homeCta(input())).toEqual({
      label: '报名',
      action: 'register',
      disabled: false,
      reason: '',
    });
  });
});
