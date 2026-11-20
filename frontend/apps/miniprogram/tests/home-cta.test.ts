import { describe, expect, it } from 'vitest';

import { homeCta } from '../miniprogram/utils/home-cta';
import { REGISTRATION_STATUS, type SignupDecision } from '../miniprogram/utils/registration';

function decision(overrides: Partial<SignupDecision> = {}): SignupDecision {
  return {
    kind: 'open',
    action: 'signup',
    visible: true,
    label: '报名',
    disabled: false,
    reason: '',
    registrationStatus: null,
    ...overrides,
  };
}

describe('homeCta', () => {
  it('maps the anonymous decision to the login entry', () => {
    expect(homeCta(decision({ kind: 'anonymous', action: 'login', label: '登录后报名' }))).toEqual({
      label: '登录后报名',
      action: 'login',
      disabled: false,
      reason: '',
    });
  });

  it('maps the pending decision to a disabled CTA carrying the blocked reason', () => {
    expect(
      homeCta(
        decision({
          kind: 'pending',
          action: 'none',
          visible: false,
          label: '',
          disabled: true,
          reason: '账号待审核，通过后可报名',
        }),
      ),
    ).toEqual({
      label: '账号待审核，通过后可报名',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('maps an active registration to the detail entry', () => {
    expect(
      homeCta(
        decision({
          kind: 'registered',
          action: 'none',
          visible: false,
          label: '',
          disabled: true,
          registrationStatus: REGISTRATION_STATUS.CONFIRMED,
        }),
      ),
    ).toEqual({ label: '查看详情', action: 'view-detail', disabled: false, reason: '已报名' });
    expect(
      homeCta(
        decision({
          kind: 'registered',
          action: 'none',
          visible: false,
          label: '',
          disabled: true,
          registrationStatus: REGISTRATION_STATUS.WAITLISTED,
        }),
      ),
    ).toEqual({ label: '查看详情', action: 'view-detail', disabled: false, reason: '候补中' });
  });

  it('maps the open decision to the register action', () => {
    expect(homeCta(decision())).toEqual({
      label: '报名',
      action: 'register',
      disabled: false,
      reason: '',
    });
  });

  it('maps the waitlist decision to the register action with its hint', () => {
    expect(
      homeCta(
        decision({
          kind: 'waitlist',
          label: '加入候补',
          reason: '正式名额已满，报名将进入候补',
        }),
      ),
    ).toEqual({
      label: '加入候补',
      action: 'register',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
    });
  });

  it('maps blocked decisions to a disabled CTA carrying the decision label', () => {
    expect(
      homeCta(
        decision({
          kind: 'blocked',
          action: 'none',
          label: '名额与候补已满',
          disabled: true,
        }),
      ),
    ).toEqual({ label: '名额与候补已满', action: 'none', disabled: true, reason: '' });
  });
});
