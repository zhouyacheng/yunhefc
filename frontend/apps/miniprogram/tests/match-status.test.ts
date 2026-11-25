import { describe, expect, it, vi } from 'vitest';

import {
  buildCancelSubmission,
  buildPostponeSubmission,
  cancelReasonError,
  isoToShanghaiLocal,
  postponeFormError,
  shanghaiLocalFields,
  shanghaiLocalToIso,
  statusChoice,
  statusCurrentLabel,
  statusOptions,
  statusSheetInitial,
} from '../miniprogram/utils/match-status';

describe('statusOptions', () => {
  it('shows all seven statuses', () => {
    expect(
      statusOptions({ currentStatus: 0, canEditAny: true }).map((option) => option.status),
    ).toEqual([0, 1, 2, 3, 4, 5, 6]);
  });

  it('keeps 0–4 enabled from every source state', () => {
    for (const currentStatus of [0, 1, 2, 3, 4, 5, 6]) {
      const options = statusOptions({ currentStatus, canEditAny: true });
      for (const status of [0, 1, 2, 3, 4]) {
        expect(options[status]?.disabled).toBe(false);
      }
    }
  });

  it('requires match:edit:any for postponing', () => {
    expect(statusOptions({ currentStatus: 0, canEditAny: false })[5]).toMatchObject({
      disabled: true,
      reason: '需要编辑比赛权限',
    });
  });

  it('disables postponing unless the source status is 0–3', () => {
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

  it('rejects malformed or impossible values', () => {
    expect(shanghaiLocalToIso('2026-02-30T20:00')).toBeNull();
    expect(shanghaiLocalToIso('2026-13-01T20:00')).toBeNull();
    expect(isoToShanghaiLocal('not-a-date')).toBe('');
    expect(isoToShanghaiLocal(null)).toBe('');
  });

  it('accepts a leap day', () => {
    expect(shanghaiLocalToIso('2028-02-29T20:00')).toBe('2028-02-29T12:00:00.000Z');
  });
});

describe('shanghaiLocalFields', () => {
  it('splits a Shanghai instant into date and time fields', () => {
    expect(shanghaiLocalFields('2026-11-14T12:00:00.000Z')).toEqual(['2026-11-14', '20:00']);
  });

  it('falls back to the supplied instant when the value is missing or invalid', () => {
    expect(shanghaiLocalFields('', '2026-11-15T02:30:00.000Z')).toEqual(['2026-11-15', '10:30']);
    expect(shanghaiLocalFields('bad', '2026-11-15T02:30:00.000Z')).toEqual(['2026-11-15', '10:30']);
  });

  it('uses the current time when no fallback is supplied', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-11-15T02:30:00.000Z'));
    try {
      expect(shanghaiLocalFields('')).toEqual(['2026-11-15', '10:30']);
    } finally {
      vi.useRealTimers();
    }
  });
});

describe('statusSheetInitial', () => {
  const options = statusOptions({ currentStatus: 1, canEditAny: true });
  const NOW = '2026-11-15T02:30:00.000Z';

  it('opens on the list with the current label and the match times', () => {
    const initial = statusSheetInitial({
      initialStartAt: '2026-11-14T12:00:00.000Z',
      initialEndAt: '2026-11-14T14:00:00.000Z',
      options,
      currentStatus: 1,
      now: NOW,
    });

    expect(initial).toEqual({
      mode: 'list',
      currentLabel: '报名截止',
      startDate: '2026-11-14',
      startTime: '20:00',
      endDate: '2026-11-14',
      endTime: '22:00',
    });
  });

  it('falls back to now when the match has no start time', () => {
    const initial = statusSheetInitial({
      initialStartAt: '',
      initialEndAt: '',
      options,
      currentStatus: 0,
      now: NOW,
    });

    expect(initial.startDate).toBe('2026-11-15');
    expect(initial.startTime).toBe('10:30');
    expect(initial.endDate).toBe('2026-11-15');
    expect(initial.endTime).toBe('10:30');
    expect(initial.currentLabel).toBe('报名中');
  });

  it('labels an unknown current status with a dash', () => {
    const initial = statusSheetInitial({
      initialStartAt: '',
      initialEndAt: '',
      options,
      currentStatus: 99,
      now: NOW,
    });

    expect(initial.currentLabel).toBe('—');
  });
});

describe('statusCurrentLabel', () => {
  it('returns the matching label or null', () => {
    const options = statusOptions({ currentStatus: 0, canEditAny: true });
    expect(statusCurrentLabel(options, 4)).toBe('已结束');
    expect(statusCurrentLabel(options, 99)).toBeNull();
  });
});

describe('statusChoice', () => {
  const options = statusOptions({ currentStatus: 0, canEditAny: true });

  it('ignores disabled options, the current status and out-of-range indices', () => {
    const finished = statusOptions({ currentStatus: 4, canEditAny: true });
    expect(statusChoice(finished, 4, 5)).toEqual({ kind: 'ignore' });
    expect(statusChoice(options, 0, 0)).toEqual({ kind: 'ignore' });
    expect(statusChoice(options, 0, 99)).toEqual({ kind: 'ignore' });
  });

  it('routes postponing and cancelling to their forms', () => {
    expect(statusChoice(options, 0, 5)).toEqual({ kind: 'postpone' });
    expect(statusChoice(options, 0, 6)).toEqual({ kind: 'cancel' });
  });

  it('changes directly to any other status', () => {
    expect(statusChoice(options, 0, 3)).toEqual({ kind: 'change', status: 3 });
  });
});

describe('buildPostponeSubmission', () => {
  it('converts a valid window to ISO instants', () => {
    expect(
      buildPostponeSubmission({
        startDate: '2026-11-14',
        startTime: '20:00',
        endDate: '2026-11-14',
        endTime: '22:00',
      }),
    ).toEqual({
      ok: true,
      startAt: '2026-11-14T12:00:00.000Z',
      endAt: '2026-11-14T14:00:00.000Z',
    });
  });

  it('returns the same validation message for an invalid window', () => {
    expect(
      buildPostponeSubmission({
        startDate: '2026-11-14',
        startTime: '20:00',
        endDate: '2026-11-14',
        endTime: '21:00',
      }),
    ).toEqual({ ok: false, error: '开始/结束时间相差必须为 2–4 小时' });
    expect(
      buildPostponeSubmission({ startDate: '', startTime: '', endDate: '', endTime: '' }),
    ).toEqual({ ok: false, error: '请选择开始与结束时间' });
  });
});

describe('buildCancelSubmission', () => {
  it('trims a valid reason and rejects a blank one when required', () => {
    expect(buildCancelSubmission('  场地原因  ')).toEqual({ ok: true, reason: '场地原因' });
    expect(buildCancelSubmission('   ')).toEqual({ ok: false, error: '请输入取消原因' });
  });

  it('allows a blank reason when not required', () => {
    expect(buildCancelSubmission('   ', 255, false)).toEqual({ ok: true, reason: '' });
    expect(buildCancelSubmission('x'.repeat(256), 255, false)).toEqual({
      ok: false,
      error: '取消原因不能超过 255 字',
    });
  });
});

describe('postponeFormError', () => {
  it('requires both times', () => {
    expect(postponeFormError('', '2026-11-14T22:00')).toBe('请选择开始与结束时间');
    expect(postponeFormError('2026-11-14T20:00', 'bad')).toBe('请选择开始与结束时间');
  });

  it('accepts a valid 2–4 hour window after 01:00', () => {
    expect(postponeFormError('2026-11-14T20:00', '2026-11-14T22:00')).toBeNull();
  });

  it('rejects windows outside 2–4 hours and starts at or before 01:00', () => {
    expect(postponeFormError('2026-11-14T20:00', '2026-11-14T21:30')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
    expect(postponeFormError('2026-11-14T20:00', '2026-11-15T01:00')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
    expect(postponeFormError('2026-11-14T01:00', '2026-11-14T03:00')).toBe(
      '开赛时间必须晚于当天 01:00',
    );
  });
});

describe('cancelReasonError', () => {
  it('requires a non-blank reason within 200 characters', () => {
    expect(cancelReasonError('   ')).toBe('请输入取消原因');
    expect(cancelReasonError('场地原因')).toBeNull();
    expect(cancelReasonError('雨'.repeat(201))).toBe('取消原因不能超过 200 字');
  });
});
