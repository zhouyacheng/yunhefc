import { describe, expect, it } from 'vitest';

import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  checkInWindowState,
  emptyPage,
  eventEditWindowOpen,
  eventEntryOpen,
  eventHalfText,
  eventTypeText,
  groupingTeamName,
  isInternalMatchType,
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

  it('recognizes internal and training matches and maps grouping team names', () => {
    expect(isInternalMatchType(1)).toBe(true);
    expect(isInternalMatchType(2)).toBe(true);
    expect(isInternalMatchType(0)).toBe(false);
    expect(isInternalMatchType(undefined)).toBe(false);
    expect(groupingTeamName({ homeTeamName: '橙队', awayTeamName: '兰队' }, 'A')).toBe('橙队');
    expect(groupingTeamName({ homeTeamName: '橙队', awayTeamName: '兰队' }, 'B')).toBe('兰队');
  });

  it('maps attendance codes to display text', () => {
    expect(ATTENDANCE_STATUS_TEXT[0]).toBe('正常');
    expect(ATTENDANCE_STATUS_TEXT[2]).toBe('临时到场');
    expect(ATTENDANCE_SOURCE_TEXT[0]).toBe('自助签到');
    expect(ATTENDANCE_SOURCE_TEXT[2]).toBe('系统');
  });
});

describe('event helpers', () => {
  it('maps 04 §2.4 event codes to display text', () => {
    expect(eventTypeText(0)).toBe('进球');
    expect(eventTypeText(2)).toBe('换人');
    expect(eventTypeText(4)).toBe('红牌');
    expect(eventHalfText(1)).toBe('下半场');
  });

  it('opens event entry only for in-progress or finished matches', () => {
    expect(eventEntryOpen(3)).toBe(true);
    expect(eventEntryOpen(4)).toBe(true);
    expect(eventEntryOpen(2)).toBe(false);
    expect(eventEntryOpen(6)).toBe(false);
  });

  it('allows player edits within 24 hours after the match ends', () => {
    const endAt = '2026-11-15T14:00:00Z';
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-16T14:00:00Z'))).toBe(true);
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-16T14:00:01Z'))).toBe(false);
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
