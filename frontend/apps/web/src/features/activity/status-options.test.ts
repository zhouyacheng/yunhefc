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

  it('已取消为终态，全部禁用', () => {
    expect(activityStatusOptions(3).every((option) => option.disabled)).toBe(true);
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

  it('禁用项与终态忽略，报名截止与已结束可回退', () => {
    expect(activityStatusChoice(activityStatusOptions(1), 1, 0)).toEqual({
      kind: 'change',
      status: 0,
    });
    expect(activityStatusChoice(activityStatusOptions(2), 2, 3)).toEqual({ kind: 'ignore' });
    expect(activityStatusChoice(activityStatusOptions(2), 2, 0)).toEqual({
      kind: 'change',
      status: 0,
    });
    expect(activityStatusChoice(activityStatusOptions(3), 3, 0)).toEqual({ kind: 'ignore' });
  });
});

describe('activityCancelReasonError', () => {
  it('要求非空且不超过 255 字', () => {
    expect(activityCancelReasonError('   ')).toBe('请输入取消原因');
    expect(activityCancelReasonError('x'.repeat(256))).toBe('取消原因不能超过 255 字');
    expect(activityCancelReasonError('  场地停业  ')).toBeNull();
  });
});
