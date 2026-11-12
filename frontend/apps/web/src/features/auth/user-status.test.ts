import { describe, expect, it } from 'vitest';

import { USER_STATUS, isPending } from './user-status';

describe('user status', () => {
  it('matches the database codes', () => {
    expect(USER_STATUS).toEqual({ PENDING: 0, ACTIVE: 1, DISABLED: 2 });
  });

  it('identifies pending accounts only', () => {
    expect(isPending(USER_STATUS.PENDING)).toBe(true);
    expect(isPending(USER_STATUS.ACTIVE)).toBe(false);
    expect(isPending(USER_STATUS.DISABLED)).toBe(false);
  });
});
