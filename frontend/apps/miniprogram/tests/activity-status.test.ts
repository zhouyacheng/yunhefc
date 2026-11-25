import { describe, expect, it } from 'vitest';

import { activityStatusOptions } from '../miniprogram/utils/activity-status';

describe('activityStatusOptions', () => {
  it('报名中可流转到报名截止、已结束与已取消', () => {
    const options = activityStatusOptions(0);
    expect(options.map((option) => [option.status, option.disabled])).toEqual([
      [0, false],
      [1, false],
      [2, false],
      [3, false],
    ]);
  });

  it('报名截止可恢复报名中、结束或取消', () => {
    const options = activityStatusOptions(1);
    expect(options.every((option) => !option.disabled)).toBe(true);
  });

  it('已结束可重新开放为报名中/报名截止，但不可取消', () => {
    const options = activityStatusOptions(2);
    expect(options.map((option) => [option.status, option.disabled])).toEqual([
      [0, false],
      [1, false],
      [2, false],
      [3, true],
    ]);
    expect(options[3]?.reason).toBe('已结束的活动不可取消');
  });

  it('已取消可恢复为报名中/报名截止，但不可结束', () => {
    const options = activityStatusOptions(3);
    expect(options.map((option) => [option.status, option.disabled])).toEqual([
      [0, false],
      [1, false],
      [2, true],
      [3, false],
    ]);
    expect(options[2]?.reason).toBe('已取消的活动不可结束');
  });
});
