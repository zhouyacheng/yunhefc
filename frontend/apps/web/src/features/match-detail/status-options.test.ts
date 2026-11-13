import { describe, expect, it } from 'vitest';

import {
  cancelReasonError,
  isoToShanghaiLocal,
  postponeFormError,
  shanghaiLocalToIso,
  statusOptions,
} from './status-options';

describe('statusOptions', () => {
  it('shows all seven statuses', () => {
    const options = statusOptions({ currentStatus: 0, canEditAny: true });
    expect(options.map((option) => option.status)).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });

  it('keeps 0–4 enabled from terminal and current states', () => {
    for (const currentStatus of [0, 1, 2, 3, 4, 5, 6]) {
      const options = statusOptions({ currentStatus, canEditAny: true });
      for (const status of [0, 1, 2, 3, 4]) {
        expect(options[status]?.disabled).toBe(false);
      }
    }
  });

  it('requires match:edit:any for postponing', () => {
    const withoutEdit = statusOptions({ currentStatus: 0, canEditAny: false });
    expect(withoutEdit[5]).toMatchObject({ disabled: true, reason: '需要编辑比赛权限' });

    const withEdit = statusOptions({ currentStatus: 0, canEditAny: true });
    expect(withEdit[5]).toMatchObject({ disabled: false, reason: '' });
  });

  it('disables postponing unless the source status is 0–3', () => {
    for (const currentStatus of [0, 1, 2, 3]) {
      expect(statusOptions({ currentStatus, canEditAny: true })[5]?.disabled).toBe(false);
    }
    for (const currentStatus of [4, 5, 6]) {
      expect(statusOptions({ currentStatus, canEditAny: true })[5]).toMatchObject({
        disabled: true,
        reason: '当前状态不可延期',
      });
    }
  });

  it('disables cancelling from finished or cancelled status', () => {
    for (const currentStatus of [0, 1, 2, 3, 5]) {
      expect(statusOptions({ currentStatus, canEditAny: true })[6]?.disabled).toBe(false);
    }
    for (const currentStatus of [4, 6]) {
      expect(statusOptions({ currentStatus, canEditAny: true })[6]).toMatchObject({
        disabled: true,
        reason: '已结束或已取消的比赛不可取消',
      });
    }
  });
});

describe('Shanghai wall-clock conversion', () => {
  it('converts a Shanghai wall clock to the matching UTC instant', () => {
    expect(shanghaiLocalToIso('2026-11-14T20:00')).toBe('2026-11-14T12:00:00.000Z');
    expect(shanghaiLocalToIso('2026-11-14T20:00:30')).toBe('2026-11-14T12:00:30.000Z');
  });

  it('converts an ISO instant back to the Shanghai wall clock', () => {
    expect(isoToShanghaiLocal('2026-11-14T12:00:00.000Z')).toBe('2026-11-14T20:00');
    expect(isoToShanghaiLocal('2026-11-14T16:30:00.000Z')).toBe('2026-11-15T00:30');
  });

  it('round-trips a valid wall clock', () => {
    const local = '2026-02-28T19:05';
    expect(isoToShanghaiLocal(shanghaiLocalToIso(local))).toBe(local);
  });

  it('rejects malformed or impossible values', () => {
    expect(shanghaiLocalToIso('2026-02-30T20:00')).toBeNull();
    expect(shanghaiLocalToIso('2026-13-01T20:00')).toBeNull();
    expect(shanghaiLocalToIso('2026-11-14')).toBeNull();
    expect(isoToShanghaiLocal('not-a-date')).toBe('');
    expect(isoToShanghaiLocal(null)).toBe('');
  });

  it('accepts a leap day', () => {
    expect(shanghaiLocalToIso('2028-02-29T20:00')).toBe('2028-02-29T12:00:00.000Z');
  });
});

describe('postponeFormError', () => {
  it('requires both times', () => {
    expect(postponeFormError('', '2026-11-14T22:00')).toBe('请选择开始与结束时间');
    expect(postponeFormError('2026-11-14T20:00', 'bad')).toBe('请选择开始与结束时间');
  });

  it('accepts a valid 2–4 hour window after 10:00', () => {
    expect(postponeFormError('2026-11-14T20:00', '2026-11-14T22:00')).toBeNull();
  });

  it('rejects a window shorter than two hours', () => {
    expect(postponeFormError('2026-11-14T20:00', '2026-11-14T21:30')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
  });

  it('rejects a window longer than four hours', () => {
    expect(postponeFormError('2026-11-14T20:00', '2026-11-15T01:00')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
  });

  it('rejects a start at or before 10:00 Shanghai', () => {
    expect(postponeFormError('2026-11-14T10:00', '2026-11-14T12:00')).toBe(
      '开赛时间必须晚于当天 10:00',
    );
  });
});

describe('cancelReasonError', () => {
  it('requires a non-blank reason', () => {
    expect(cancelReasonError('   ')).toBe('请输入取消原因');
  });

  it('accepts a normal reason', () => {
    expect(cancelReasonError('场地原因')).toBeNull();
  });

  it('rejects more than 200 characters', () => {
    expect(cancelReasonError('雨'.repeat(201))).toBe('取消原因不能超过 200 字');
  });
});
