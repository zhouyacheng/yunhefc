import { REGISTRATION_STATUS } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import {
  cancelState,
  promotionText,
  signupState,
  type CancelStateInput,
  type SignupStateInput,
} from './registration';

const DEADLINE = '2026-11-13T12:30:00Z';
const CANCEL_LOCK = '2026-11-13T12:45:00Z';

function signupInput(overrides: Partial<SignupStateInput> = {}, queue = {}): SignupStateInput {
  return {
    authenticated: true,
    pending: false,
    playerType: 1,
    matchStatus: 0,
    now: Date.parse('2026-11-13T12:00:00Z'),
    signupDeadline: DEADLINE,
    queue: {
      capacity: 30,
      guestCapacity: 4,
      waitlistCapacity: 2,
      confirmedCount: 0,
      guestCount: 0,
      waitlistCount: 0,
      ...queue,
    },
    myRegistration: null,
    canSignup: true,
    ...overrides,
  };
}

function cancelInput(overrides: Partial<CancelStateInput> = {}): CancelStateInput {
  return {
    authenticated: true,
    pending: false,
    matchStatus: 0,
    cancelLock: CANCEL_LOCK,
    myRegistration: { status: REGISTRATION_STATUS.CONFIRMED },
    now: Date.parse('2026-11-13T12:00:00Z'),
    ...overrides,
  };
}

describe('signupState', () => {
  it('asks visitors to log in', () => {
    expect(signupState(signupInput({ authenticated: false }))).toEqual({
      visible: true,
      label: '登录后报名',
      action: 'login',
      disabled: false,
      reason: '',
    });
  });

  it('hides signup for pending accounts', () => {
    const state = signupState(signupInput({ pending: true }));
    expect(state.visible).toBe(false);
    expect(state.reason).toBe('账号待审核，通过后可报名');
  });

  it('hides signup when already confirmed or waitlisted', () => {
    expect(signupState(signupInput({ myRegistration: { status: 0 } })).visible).toBe(false);
    expect(signupState(signupInput({ myRegistration: { status: 1 } })).visible).toBe(false);
  });

  it('signs up a formal player while capacity remains', () => {
    expect(signupState(signupInput({}, { confirmedCount: 29 }))).toMatchObject({
      label: '报名',
      action: 'signup',
      disabled: false,
    });
  });

  it('offers the waitlist when the formal pool is full', () => {
    expect(signupState(signupInput({}, { confirmedCount: 30, waitlistCount: 1 }))).toEqual({
      visible: true,
      label: '加入候补',
      action: 'signup',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
    });
  });

  it('blocks when the formal pool and waitlist are full', () => {
    expect(signupState(signupInput({}, { confirmedCount: 30, waitlistCount: 2 }))).toMatchObject({
      label: '名额与候补已满',
      disabled: true,
    });
  });

  it('blocks guests when the guest pool is full', () => {
    expect(
      signupState(signupInput({ playerType: 0 }, { guestCount: 4, confirmedCount: 30 })),
    ).toMatchObject({ label: '外挂名额已满', disabled: true });
  });

  it('treats a missing player type as a guest', () => {
    expect(signupState(signupInput({ playerType: null }, { guestCount: 4 }))).toMatchObject({
      label: '外挂名额已满',
      disabled: true,
    });
  });

  it('signs up a guest while the guest pool remains', () => {
    expect(signupState(signupInput({ playerType: 0 }, { guestCount: 3 }))).toMatchObject({
      label: '报名',
      action: 'signup',
      disabled: false,
    });
  });

  it('closes signup exactly at the deadline', () => {
    expect(signupState(signupInput({ now: Date.parse(DEADLINE) }))).toMatchObject({
      label: '报名已截止',
      disabled: true,
    });
  });

  it('rejects an invalid deadline', () => {
    expect(signupState(signupInput({ signupDeadline: 'not-a-date' }))).toMatchObject({
      label: '报名截止时间无效',
      disabled: true,
    });
  });

  it('blocks without registration permission when signup would otherwise be open', () => {
    expect(signupState(signupInput({ canSignup: false }))).toMatchObject({
      label: '没有报名权限',
      disabled: true,
    });
  });

  it.each([
    [1, '报名已截止'],
    [2, '报名已锁定'],
    [3, '比赛已开始'],
    [4, '比赛已结束'],
    [5, '比赛已延期'],
    [6, '比赛已取消'],
  ] as const)('blocks when match status is %i', (matchStatus, label) => {
    expect(signupState(signupInput({ matchStatus }))).toMatchObject({ label, disabled: true });
  });
});

describe('cancelState', () => {
  it('is hidden for visitors, pending users and users without an active registration', () => {
    expect(cancelState(cancelInput({ authenticated: false })).visible).toBe(false);
    expect(cancelState(cancelInput({ pending: true })).visible).toBe(false);
    expect(cancelState(cancelInput({ myRegistration: null })).visible).toBe(false);
    expect(
      cancelState(cancelInput({ myRegistration: { status: REGISTRATION_STATUS.CANCELLED } }))
        .visible,
    ).toBe(false);
  });

  it('labels confirmed and waitlisted registrations differently', () => {
    expect(cancelState(cancelInput()).label).toBe('取消报名');
    expect(
      cancelState(cancelInput({ myRegistration: { status: REGISTRATION_STATUS.WAITLISTED } }))
        .label,
    ).toBe('取消候补');
  });

  it('allows cancellation while signup is open or closed and before the lock', () => {
    for (const matchStatus of [0, 1]) {
      expect(cancelState(cancelInput({ matchStatus }))).toMatchObject({
        visible: true,
        disabled: false,
      });
    }
  });

  it('disables cancellation exactly at the lock time', () => {
    expect(cancelState(cancelInput({ now: Date.parse(CANCEL_LOCK) }))).toMatchObject({
      disabled: true,
      reason: '已过取消截止，请联系管理员',
    });
  });

  it('rejects an invalid lock time', () => {
    expect(cancelState(cancelInput({ cancelLock: 'not-a-date' }))).toMatchObject({
      disabled: true,
      reason: '取消截止时间无效',
    });
  });

  it.each([
    [2, '报名已锁定，请联系管理员'],
    [3, '比赛已开始，无法取消'],
    [4, '比赛已结束，无法取消'],
    [5, '比赛已延期，无法取消'],
    [6, '比赛已取消'],
  ] as const)('explains why cancellation is blocked at status %i', (matchStatus, reason) => {
    expect(cancelState(cancelInput({ matchStatus }))).toMatchObject({
      visible: true,
      disabled: true,
      reason,
    });
  });
});

describe('promotionText', () => {
  it('names the promoted player', () => {
    expect(promotionText({ promoted: true }, '测试前锋01')).toBe(
      '已取消报名，测试前锋01 已递补为正式报名',
    );
  });

  it('falls back when the promoted player name is unknown', () => {
    expect(promotionText({ promoted: true })).toBe('已取消报名，候补球员已递补为正式报名');
  });

  it('distinguishes cancelling a waitlist entry', () => {
    expect(promotionText({ promoted: false }, null, 1)).toBe('已取消候补');
    expect(promotionText({ promoted: false }, null, 0)).toBe('已取消报名');
  });
});
