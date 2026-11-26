import { describe, expect, it } from 'vitest';

import { buildShareCoverData } from '../miniprogram/utils/match-invite-cover';

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
