import { describe, expect, it } from 'vitest';

import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  checkInWindowState,
  emptyPage,
  isPage,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
  readLabel,
} from './index';

describe('Page helper', () => {
  it('recognizes the 05 pagination shape', () => {
    expect(isPage({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 })).toBe(true);
    expect(isPage({ items: [] })).toBe(false);
    expect(isPage(null)).toBe(false);
  });

  it('builds an empty page', () => {
    expect(emptyPage(20)).toEqual({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0 });
  });
});

describe('enum helpers', () => {
  it('reads the sibling label field', () => {
    expect(readLabel({ status: 4, statusLabel: 'FINISHED' }, 'status')).toBe('FINISHED');
    expect(readLabel({ status: 4 }, 'status')).toBeUndefined();
  });

  it('maps 04 §2.4 match codes to display text', () => {
    expect(matchStatusText(2)).toBe('已锁定');
    expect(matchTypeText(1)).toBe('队内赛');
    expect(matchStatusText(undefined)).toBe('');
  });

  it('maps match statuses to a shared tone', () => {
    expect(matchStatusTone(6)).toBe('danger');
    expect(matchStatusTone(4)).toBe('success');
    expect(matchStatusTone(3)).toBe('warning');
    expect(matchStatusTone(0)).toBe('neutral');
  });

  it('maps attendance codes to display text', () => {
    expect(ATTENDANCE_STATUS_TEXT[0]).toBe('正常');
    expect(ATTENDANCE_STATUS_TEXT[2]).toBe('临时到场');
    expect(ATTENDANCE_SOURCE_TEXT[0]).toBe('自助签到');
    expect(ATTENDANCE_SOURCE_TEXT[2]).toBe('系统');
  });
});

describe('check-in window', () => {
  // 2026-11-15 20:00 Asia/Shanghai = 12:00Z；窗口 16:00–23:59 本地。
  const startAt = '2026-11-15T12:00:00Z';

  it('opens four hours before start and closes at end of match day', () => {
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T07:59:59Z'))).toBe('before');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T08:00:00Z'))).toBe('open');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T15:59:59Z'))).toBe('open');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T16:00:00Z'))).toBe('after');
  });

  it('handles an early-morning match whose window starts the previous day', () => {
    // 2026-11-16 01:00 Asia/Shanghai = 17:00Z，窗口从 2026-11-15 21:00 本地开始。
    const earlyStart = '2026-11-15T17:00:00Z';
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T12:59:59Z'))).toBe('before');
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T13:00:00Z'))).toBe('open');
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T15:00:00Z'))).toBe('open');
  });
});
