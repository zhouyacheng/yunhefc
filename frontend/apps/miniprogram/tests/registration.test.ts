import { describe, expect, it } from 'vitest';

import {
  REGISTRATION_STATUS,
  promotionText,
  registrationDecision,
  type RegistrationEligibilityInput,
} from '../miniprogram/utils/registration';

const DEADLINE = '2026-11-13T12:30:00Z';
const CANCEL_LOCK = '2026-11-13T12:45:00Z';
const SIGNUP_PERMISSION = 'registration:create:own';

function input(
  overrides: Partial<RegistrationEligibilityInput> = {},
): RegistrationEligibilityInput {
  return {
    authenticated: true,
    pending: false,
    playerType: 1,
    permissions: [SIGNUP_PERMISSION],
    matchStatus: 0,
    now: Date.parse('2026-11-13T12:00:00Z'),
    signupDeadline: DEADLINE,
    cancelLock: CANCEL_LOCK,
    queue: {
      capacity: 30,
      guestCapacity: 4,
      waitlistCapacity: 2,
      confirmedCount: 0,
      guestCount: 0,
      waitlistCount: 0,
    },
    myRegistration: null,
    ...overrides,
  };
}

function signup(overrides: Partial<RegistrationEligibilityInput> = {}) {
  return registrationDecision(input(overrides)).signup;
}

function cancel(overrides: Partial<RegistrationEligibilityInput> = {}) {
  return registrationDecision(
    input({ myRegistration: { status: REGISTRATION_STATUS.CONFIRMED }, ...overrides }),
  ).cancel;
}

describe('registrationDecision signup', () => {
  it('asks visitors to log in', () => {
    expect(signup({ authenticated: false })).toEqual({
      kind: 'anonymous',
      action: 'login',
      visible: true,
      label: '登录后报名',
      disabled: false,
      reason: '',
      registrationStatus: null,
    });
  });

  it('hides the entry for pending accounts and explains why', () => {
    expect(signup({ pending: true })).toEqual({
      kind: 'pending',
      action: 'none',
      visible: false,
      label: '',
      disabled: true,
      reason: '账号待审核，通过后可报名',
      registrationStatus: null,
    });
  });

  it('reports an active registration instead of offering signup', () => {
    for (const status of [REGISTRATION_STATUS.CONFIRMED, REGISTRATION_STATUS.WAITLISTED]) {
      expect(signup({ myRegistration: { status } })).toEqual({
        kind: 'registered',
        action: 'none',
        visible: false,
        label: '',
        disabled: true,
        reason: '',
        registrationStatus: status,
      });
    }
  });

  it('ignores registrations that are no longer active', () => {
    for (const status of [REGISTRATION_STATUS.CANCELLED, REGISTRATION_STATUS.REMOVED]) {
      expect(signup({ myRegistration: { status } })).toMatchObject({
        kind: 'open',
        action: 'signup',
        label: '报名',
        disabled: false,
      });
    }
  });

  it('keeps an active registration ahead of match status', () => {
    expect(
      signup({ matchStatus: 3, myRegistration: { status: REGISTRATION_STATUS.CONFIRMED } }),
    ).toMatchObject({ kind: 'registered', registrationStatus: REGISTRATION_STATUS.CONFIRMED });
  });

  it.each([
    [1, '报名已截止'],
    [2, '报名已锁定'],
    [3, '比赛已开始'],
    [4, '比赛已结束'],
    [5, '比赛已延期'],
    [6, '比赛已取消'],
    [9, '当前不可报名'],
  ] as const)('blocks signup when match status is %i', (matchStatus, label) => {
    expect(signup({ matchStatus })).toEqual({
      kind: 'blocked',
      action: 'none',
      visible: true,
      label,
      disabled: true,
      reason: '',
      registrationStatus: null,
    });
  });

  it('blocks signup when the deadline is unreadable', () => {
    expect(signup({ signupDeadline: 'not-a-date' })).toMatchObject({
      kind: 'blocked',
      visible: true,
      label: '报名截止时间无效',
      disabled: true,
    });
  });

  it('closes signup exactly at the deadline', () => {
    expect(signup({ now: Date.parse(DEADLINE) })).toMatchObject({
      kind: 'blocked',
      label: '报名已截止',
      disabled: true,
    });
  });

  it('blocks signup without the registration permission', () => {
    expect(signup({ permissions: [] })).toMatchObject({
      kind: 'blocked',
      label: '没有报名权限',
      disabled: true,
    });
  });

  it('offers the waitlist when the formal pool is full', () => {
    expect(signup({ queue: { ...input().queue, confirmedCount: 30, waitlistCount: 1 } })).toEqual({
      kind: 'waitlist',
      action: 'signup',
      visible: true,
      label: '加入候补',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
      registrationStatus: null,
    });
  });

  it('blocks when the formal pool and waitlist are full', () => {
    expect(
      signup({ queue: { ...input().queue, confirmedCount: 30, waitlistCount: 2 } }),
    ).toMatchObject({
      kind: 'blocked',
      label: '名额与候补已满',
      disabled: true,
    });
  });

  it('blocks guests when the guest pool is full', () => {
    for (const playerType of [0, null]) {
      expect(
        signup({ playerType, queue: { ...input().queue, guestCount: 4, confirmedCount: 30 } }),
      ).toMatchObject({ kind: 'blocked', label: '外挂名额已满', disabled: true });
    }
  });

  it('opens signup for a formal player while formal slots remain', () => {
    expect(signup()).toEqual({
      kind: 'open',
      action: 'signup',
      visible: true,
      label: '报名',
      disabled: false,
      reason: '',
      registrationStatus: null,
    });
  });

  it('opens signup for a guest while guest slots remain, even when the formal pool is full', () => {
    expect(
      signup({ playerType: 0, queue: { ...input().queue, confirmedCount: 30 } }),
    ).toMatchObject({ kind: 'open', label: '报名', disabled: false });
  });
});

describe('registrationDecision cancel', () => {
  it('is hidden for visitors, pending users and inactive registrations', () => {
    expect(cancel({ authenticated: false })).toEqual({
      visible: false,
      label: '',
      disabled: true,
      reason: '',
    });
    expect(cancel({ pending: true }).visible).toBe(false);
    expect(cancel({ myRegistration: null }).visible).toBe(false);
    expect(cancel({ myRegistration: { status: REGISTRATION_STATUS.CANCELLED } }).visible).toBe(
      false,
    );
    expect(cancel({ myRegistration: { status: REGISTRATION_STATUS.REMOVED } }).visible).toBe(false);
  });

  it('labels confirmed and waitlisted registrations differently', () => {
    expect(cancel({ myRegistration: { status: REGISTRATION_STATUS.CONFIRMED } })).toEqual({
      visible: true,
      label: '取消报名',
      disabled: false,
      reason: '',
    });
    expect(cancel({ myRegistration: { status: REGISTRATION_STATUS.WAITLISTED } })).toEqual({
      visible: true,
      label: '取消候补',
      disabled: false,
      reason: '',
    });
  });

  it('keeps cancellation open after the signup deadline but before the lock', () => {
    const afterDeadline = Date.parse('2026-11-13T12:40:00Z');
    expect(
      cancel({
        matchStatus: 1,
        now: afterDeadline,
        myRegistration: { status: REGISTRATION_STATUS.CONFIRMED },
      }),
    ).toMatchObject({ visible: true, disabled: false, reason: '' });
  });

  it('disables cancellation exactly at the lock time', () => {
    expect(cancel({ now: Date.parse(CANCEL_LOCK) })).toMatchObject({
      visible: true,
      label: '取消报名',
      disabled: true,
      reason: '已过取消截止，请联系管理员',
    });
  });

  it('blocks cancellation when the lock time is unreadable', () => {
    expect(cancel({ cancelLock: 'not-a-date' })).toMatchObject({
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
    [9, '当前比赛状态不可取消'],
  ] as const)('explains why cancellation is blocked at status %i', (matchStatus, reason) => {
    expect(cancel({ matchStatus })).toMatchObject({
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
