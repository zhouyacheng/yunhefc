import { afterEach, describe, expect, it, vi } from 'vitest';

import { buildShareCoverData, renderShareCover } from '../miniprogram/utils/match-invite-cover';
import { stubShareCanvas } from './support/share-cover-fakes';

const MATCH_DETAIL = {
  title: '报名满员联调赛',
  startAt: '2026-11-28T00:08:00+08:00',
  endAt: '2026-11-28T02:08:00+08:00',
  venue: '聚龙足球场',
  confirmedCount: 30,
  capacity: 30,
  signupDeadline: '2026-11-27T00:08:00+08:00',
};

const INVITE = { expiresAt: '2026-12-03T14:37:00+08:00' };

describe('buildShareCoverData', () => {
  it('formats the match facts for the 5:4 cover', () => {
    const data = buildShareCoverData(MATCH_DETAIL, INVITE);
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

describe('renderShareCover', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('keeps the match cover palette green', async () => {
    const { canvas, fills, stops } = stubShareCanvas();
    await renderShareCover(canvas, buildShareCoverData(MATCH_DETAIL, INVITE));

    expect(fills).toEqual(expect.arrayContaining(['#dcfce7', '#16a34a', '#ffffff']));
    expect(fills).not.toContain('#fde68a');
    expect(fills).not.toContain('#fbbf24');
    expect(fills).not.toContain('#f3ead7');
    expect(stops).toEqual(
      expect.arrayContaining([
        { kind: 'linear', offset: 0, color: '#0b1220' },
        { kind: 'linear', offset: 0.58, color: '#111c33' },
        { kind: 'linear', offset: 1, color: '#0f2a24' },
        { kind: 'radial', offset: 0, color: 'rgba(22, 163, 74, 0.45)' },
        { kind: 'radial', offset: 1, color: 'rgba(22, 163, 74, 0)' },
      ]),
    );
  });
});
