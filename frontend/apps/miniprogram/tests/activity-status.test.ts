import { describe, expect, it } from 'vitest';

import { activityStatusOptions } from '../miniprogram/utils/activity-status';
import { activityEditable, activityStatusChangeable } from '../miniprogram/utils/activity-view';

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

  it('已取消为终态，全部禁用', () => {
    expect(activityStatusOptions(3).every((option) => option.disabled)).toBe(true);
  });
});

describe('activityEditable', () => {
  it('仅报名中与报名截止可编辑', () => {
    expect(activityEditable(0)).toBe(true);
    expect(activityEditable(1)).toBe(true);
    expect(activityEditable(2)).toBe(false);
    expect(activityEditable(3)).toBe(false);
  });
});

describe('activityStatusChangeable', () => {
  it('仅已取消不可流转，已结束可重新开放', () => {
    expect(activityStatusChangeable(0)).toBe(true);
    expect(activityStatusChangeable(1)).toBe(true);
    expect(activityStatusChangeable(2)).toBe(true);
    expect(activityStatusChangeable(3)).toBe(false);
  });
});
