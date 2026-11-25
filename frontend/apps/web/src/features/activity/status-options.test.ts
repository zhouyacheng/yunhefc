import { describe, expect, it } from 'vitest';

import {
  activityCancelReasonError,
  activityStatusChoice,
  activityStatusOptions,
} from './status-options';

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

describe('activityStatusChoice', () => {
  it('按合法集返回切换、取消或忽略', () => {
    const options = activityStatusOptions(0);
    expect(activityStatusChoice(options, 0, 0)).toEqual({ kind: 'ignore' });
    expect(activityStatusChoice(options, 0, 1)).toEqual({ kind: 'change', status: 1 });
    expect(activityStatusChoice(options, 0, 2)).toEqual({ kind: 'change', status: 2 });
    expect(activityStatusChoice(options, 0, 3)).toEqual({ kind: 'cancel' });
    expect(activityStatusChoice(options, 0, 9)).toEqual({ kind: 'ignore' });
  });

  it('禁用项忽略，报名截止/已结束/已取消均可回退', () => {
    expect(activityStatusChoice(activityStatusOptions(1), 1, 0)).toEqual({
      kind: 'change',
      status: 0,
    });
    expect(activityStatusChoice(activityStatusOptions(2), 2, 3)).toEqual({ kind: 'ignore' });
    expect(activityStatusChoice(activityStatusOptions(2), 2, 0)).toEqual({
      kind: 'change',
      status: 0,
    });
    expect(activityStatusChoice(activityStatusOptions(3), 3, 2)).toEqual({ kind: 'ignore' });
    expect(activityStatusChoice(activityStatusOptions(3), 3, 1)).toEqual({
      kind: 'change',
      status: 1,
    });
  });
});

describe('activityCancelReasonError', () => {
  it('可选且不超过 255 字', () => {
    expect(activityCancelReasonError('   ')).toBeNull();
    expect(activityCancelReasonError('x'.repeat(256))).toBe('取消原因不能超过 255 字');
    expect(activityCancelReasonError('  场地停业  ')).toBeNull();
  });
});
