import { describe, expect, it } from 'vitest';

import { activityStatusCanTransit, activityStatusDisabledReason } from './activity';

describe('activityStatusCanTransit', () => {
  it('allows 0↔1 and both ended/cancelled to reopen', () => {
    expect(activityStatusCanTransit(0, 1)).toBe(true);
    expect(activityStatusCanTransit(1, 0)).toBe(true);
    expect(activityStatusCanTransit(0, 2)).toBe(true);
    expect(activityStatusCanTransit(0, 3)).toBe(true);
    expect(activityStatusCanTransit(2, 0)).toBe(true);
    expect(activityStatusCanTransit(2, 1)).toBe(true);
    expect(activityStatusCanTransit(3, 0)).toBe(true);
    expect(activityStatusCanTransit(3, 1)).toBe(true);
  });

  it('rejects transitions outside ACT-10', () => {
    expect(activityStatusCanTransit(2, 3)).toBe(false);
    expect(activityStatusCanTransit(3, 2)).toBe(false);
    expect(activityStatusCanTransit(0, 0)).toBe(false);
    expect(activityStatusCanTransit(2, 2)).toBe(false);
  });
});

describe('activityStatusDisabledReason', () => {
  it('returns undefined for legal transitions', () => {
    expect(activityStatusDisabledReason(0, 1)).toBeUndefined();
    expect(activityStatusDisabledReason(3, 0)).toBeUndefined();
  });

  it('explains same-status and forbidden end/cancel transitions', () => {
    expect(activityStatusDisabledReason(1, 1)).toBe('当前已是该状态');
    expect(activityStatusDisabledReason(2, 3)).toBe('已结束的活动不可取消');
    expect(activityStatusDisabledReason(3, 2)).toBe('已取消的活动不可结束');
  });
});
