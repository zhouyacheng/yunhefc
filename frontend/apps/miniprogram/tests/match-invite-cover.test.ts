import { describe, expect, it } from 'vitest';

import { buildShareCoverData, fitText, wrapText } from '../miniprogram/utils/match-invite-cover';

const measure = (text: string): number => text.length * 10;

describe('buildShareCoverData', () => {
  it('formats the match facts for the 5:4 cover', () => {
    const data = buildShareCoverData(
      {
        title: '报名满员联调赛',
        startAt: '2026-11-28T00:08:00+08:00',
        endAt: '2026-11-28T02:08:00+08:00',
        venue: '聚龙足球场',
        confirmedCount: 30,
        capacity: 30,
        signupDeadline: '2026-11-27T00:08:00+08:00',
      },
      { expiresAt: '2026-12-03T14:37:00+08:00' },
    );
    expect(data).toEqual({
      title: '报名满员联调赛',
      dateText: '2026/11/28 周六',
      timeRangeText: '00:08–02:08',
      venue: '聚龙足球场',
      quotaText: '30 / 30',
      deadlineText: '2026/11/27 00:08',
      expiresText: '邀请有效期至 2026/12/03 14:37',
    });
  });

  it('falls back to placeholders when optional text is empty', () => {
    const data = buildShareCoverData(
      {
        title: '',
        startAt: '',
        endAt: '',
        venue: '',
        confirmedCount: 0,
        capacity: 0,
        signupDeadline: '',
      },
      { expiresAt: '' },
    );
    expect(data.title).toBe('比赛邀请');
    expect(data.dateText).toBe('—');
    expect(data.timeRangeText).toBe('—');
    expect(data.venue).toBe('待定');
    expect(data.deadlineText).toBe('—');
    expect(data.expiresText).toBe('邀请有效期至 —');
  });
});

describe('fitText', () => {
  it('keeps text that fits and truncates with an ellipsis otherwise', () => {
    expect(fitText(measure, 'ABCD', 40)).toBe('ABCD');
    expect(fitText(measure, 'ABCDE', 40)).toBe('ABC…');
    expect(fitText(measure, '报名满员联调赛', 60)).toBe('报名满员联…');
  });
});

describe('wrapText', () => {
  it('wraps by measured width and caps the line count', () => {
    expect(wrapText(measure, 'ABCDEFGH', 40, 2)).toEqual(['ABCD', 'EFGH']);
    expect(wrapText(measure, 'ABCDEFGHIJ', 40, 2)).toEqual(['ABCD', 'EFG…']);
  });

  it('returns a single line when it fits', () => {
    expect(wrapText(measure, '报名满员', 80, 2)).toEqual(['报名满员']);
  });

  it('returns no lines when the line budget is zero', () => {
    expect(wrapText(measure, '报名满员', 80, 0)).toEqual([]);
  });
});
