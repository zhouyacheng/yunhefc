import { describe, expect, it } from 'vitest';

import {
  checkInWindowState,
  eventEditWindowOpen,
  eventEntryOpen,
} from '../miniprogram/utils/match-window';

describe('checkInWindowState', () => {
  const startAt = '2026-11-14T20:00:00+08:00';

  it('opens four hours before kickoff', () => {
    expect(checkInWindowState(startAt, Date.parse('2026-11-14T15:59:59+08:00'))).toBe('before');
    expect(checkInWindowState(startAt, Date.parse('2026-11-14T16:00:00+08:00'))).toBe('open');
  });

  it('closes at the end of the Shanghai match day', () => {
    expect(checkInWindowState(startAt, Date.parse('2026-11-14T23:59:59+08:00'))).toBe('open');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T00:00:00+08:00'))).toBe('after');
  });

  it('treats invalid kickoff as closed', () => {
    expect(checkInWindowState('not-a-date', Date.now())).toBe('after');
  });
});

describe('eventEntryOpen', () => {
  it('allows only in-progress and finished matches', () => {
    for (const status of [3, 4]) {
      expect(eventEntryOpen(status)).toBe(true);
    }
    for (const status of [0, 1, 2, 5, 6]) {
      expect(eventEntryOpen(status)).toBe(false);
    }
  });
});

describe('eventEditWindowOpen', () => {
  const endAt = '2026-11-14T22:00:00+08:00';

  it('stays open through 24 hours after the match', () => {
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-15T21:59:59+08:00'))).toBe(true);
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-15T22:00:00+08:00'))).toBe(true);
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-15T22:00:01+08:00'))).toBe(false);
  });

  it('treats invalid end time as closed', () => {
    expect(eventEditWindowOpen('not-a-date', Date.now())).toBe(false);
  });
});
