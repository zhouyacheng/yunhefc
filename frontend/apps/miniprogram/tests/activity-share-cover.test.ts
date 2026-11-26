import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  ACTIVITY_SHARE_COVER_FALLBACK,
  buildActivityShareCoverData,
  renderActivityShareCover,
} from '../miniprogram/utils/activity-share-cover';
import { generateShareCover } from '../miniprogram/utils/share-cover';
import { stubShareCanvas } from './support/share-cover-fakes';

const BASE_ACTIVITY = {
  title: '周末聚餐',
  startAt: '2026-11-28T18:00:00+08:00',
  endAt: '2026-11-28T21:00:00+08:00' as string | null,
  location: '聚龙足球场',
  signupCount: 12,
  capacity: 50,
  signupDeadline: '2026-11-27T20:00:00+08:00',
  costPerPerson: 50 as number | null,
  signupOpen: true,
};

describe('buildActivityShareCoverData', () => {
  it('formats the activity facts for the 5:4 cover', () => {
    const data = buildActivityShareCoverData(BASE_ACTIVITY);
    expect(data).toEqual({
      title: '周末聚餐',
      dateText: '2026/11/28 周六',
      timeRangeText: '18:00–21:00',
      locationText: '地点 · 聚龙足球场',
      quotaText: '已报名 12 / 50 人',
      deadlineText: '报名截止 2026/11/27 20:00',
      costText: '人均 ¥50.00',
      hintText: '查看活动详情，报名参加',
    });
  });

  it('drops the signup call to action once signup is closed or full', () => {
    const closed = buildActivityShareCoverData({
      ...BASE_ACTIVITY,
      signupOpen: false,
    });
    expect(closed.quotaText).toBe('已报名 12 / 50 人');
    expect(closed.hintText).toBe('查看活动详情');

    const full = buildActivityShareCoverData({
      ...BASE_ACTIVITY,
      signupCount: 50,
      signupOpen: false,
    });
    expect(full.quotaText).toBe('已报名 50 / 50 人');
    expect(full.hintText).toBe('查看活动详情');
  });

  it('handles a missing end time and cost', () => {
    const data = buildActivityShareCoverData({
      ...BASE_ACTIVITY,
      endAt: null,
      costPerPerson: null,
    });
    expect(data.timeRangeText).toBe('18:00');
    expect(data.costText).toBe('');
  });

  it('falls back to placeholders when optional text is empty', () => {
    const data = buildActivityShareCoverData({
      ...BASE_ACTIVITY,
      title: '',
      startAt: '',
      endAt: '',
      location: '',
      signupCount: 0,
      capacity: 0,
      signupDeadline: '',
      costPerPerson: null,
      signupOpen: false,
    });
    expect(data.title).toBe('活动邀请');
    expect(data.dateText).toBe('—');
    expect(data.timeRangeText).toBe('—');
    expect(data.locationText).toBe('地点 · 待定');
    expect(data.quotaText).toBe('已报名 0 / 0 人');
    expect(data.deadlineText).toBe('报名截止 —');
    expect(data.costText).toBe('');
    expect(data.hintText).toBe('查看活动详情');
  });

  it('keeps the static fallback cover path', () => {
    expect(ACTIVITY_SHARE_COVER_FALLBACK).toBe('/assets/share-activity-invite.png');
  });
});

describe('renderActivityShareCover', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('draws the activity facts onto the share cover', async () => {
    const { drawn } = stubShareCanvas();
    const path = await generateShareCover(
      '#activity-share-cover',
      (canvas) => renderActivityShareCover(canvas, buildActivityShareCoverData(BASE_ACTIVITY)),
      ACTIVITY_SHARE_COVER_FALLBACK,
    );

    expect(path).toBe('/tmp/share-cover.png');
    expect(drawn).toEqual(
      expect.arrayContaining([
        '活动邀请',
        '云河FC',
        '周末聚餐',
        '2026/11/28 周六',
        '18:00–21:00',
        '地点 · 聚龙足球场',
        '已报名 12 / 50 人 · 报名截止 2026/11/27 20:00',
        '人均 ¥50.00',
        '查看活动详情，报名参加',
      ]),
    );
  });

  it('truncates a long title to two lines', async () => {
    const title = '周末聚餐'.repeat(12);
    const { drawn } = stubShareCanvas();
    await generateShareCover(
      '#activity-share-cover',
      (canvas) =>
        renderActivityShareCover(canvas, buildActivityShareCoverData({ ...BASE_ACTIVITY, title })),
      ACTIVITY_SHARE_COVER_FALLBACK,
    );

    // 页头两项（标签、品牌）之后是标题行；两行拼接应还原完整标题。
    expect(drawn.slice(2, 4).join('')).toBe(title);
  });

  it('omits the cost line when cost is not set', async () => {
    const { drawn } = stubShareCanvas();
    await generateShareCover(
      '#activity-share-cover',
      (canvas) =>
        renderActivityShareCover(
          canvas,
          buildActivityShareCoverData({ ...BASE_ACTIVITY, costPerPerson: null }),
        ),
      ACTIVITY_SHARE_COVER_FALLBACK,
    );

    expect(drawn.some((text) => text.startsWith('人均'))).toBe(false);
  });
});
