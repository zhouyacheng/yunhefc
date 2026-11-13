import { describe, expect, it } from 'vitest';

import { DEFAULT_INVITE_DAYS, inviteFormError, invitePayload } from './invite';

describe('inviteFormError', () => {
  it('accepts the defaults (7 days, unlimited uses)', () => {
    expect(inviteFormError({ expiresInDays: 7, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: '', maxUses: '' })).toBeNull();
  });

  it('accepts the inclusive day range and string inputs', () => {
    expect(inviteFormError({ expiresInDays: 1, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: 30, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: '14', maxUses: '3' })).toBeNull();
  });

  it('rejects days outside 1–30', () => {
    expect(inviteFormError({ expiresInDays: 0, maxUses: null })).toBe('有效期必须为 1–30 天');
    expect(inviteFormError({ expiresInDays: 31, maxUses: null })).toBe('有效期必须为 1–30 天');
  });

  it('rejects non-integer days', () => {
    expect(inviteFormError({ expiresInDays: 7.5, maxUses: null })).toBe('有效期必须为 1–30 天');
    expect(inviteFormError({ expiresInDays: 'abc', maxUses: null })).toBe('有效期必须为 1–30 天');
  });

  it('rejects a non-positive use limit', () => {
    expect(inviteFormError({ expiresInDays: 7, maxUses: 0 })).toBe('使用次数上限必须大于 0');
    expect(inviteFormError({ expiresInDays: 7, maxUses: -1 })).toBe('使用次数上限必须大于 0');
    expect(inviteFormError({ expiresInDays: 7, maxUses: 'x' })).toBe('使用次数上限必须大于 0');
  });
});

describe('invitePayload', () => {
  it('defaults the expiry and omits an empty use limit', () => {
    expect(invitePayload({ expiresInDays: '', maxUses: '' })).toEqual({
      expiresInDays: DEFAULT_INVITE_DAYS,
    });
    expect(invitePayload({ expiresInDays: 7, maxUses: null })).toEqual({ expiresInDays: 7 });
  });

  it('forwards valid values', () => {
    expect(invitePayload({ expiresInDays: '30', maxUses: '5' })).toEqual({
      expiresInDays: 30,
      maxUses: 5,
    });
  });
});
