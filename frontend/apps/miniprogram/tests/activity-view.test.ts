import { describe, expect, it } from 'vitest';

import type {
  ActivitySignup,
  ActivityStatus,
  ActivitySummary,
  ActivityType,
} from '../miniprogram/types/api';
import {
  activityAction,
  activityCanCancel,
  activityCapacityPercent,
  activityCostText,
  activityCountText,
  activitySignupOpen,
  activityStatusText,
  activityStatusTone,
  activityTimeText,
  activityTypeText,
  buildActivityCard,
  clampGalleryIndex,
  familySummary,
  withoutFailedImage,
} from '../miniprogram/utils/activity-view';

const NOW = Date.parse('2026-12-01T12:00:00+08:00');

function activity(patch: Partial<ActivitySummary> = {}): ActivitySummary {
  return {
    id: 1,
    title: '年终聚餐',
    type: 0,
    // 服务端 label 为英文枚举名，展示文案由本地映射决定。
    typeLabel: 'DINNER',
    startAt: '2026-12-05T18:00:00+08:00',
    endAt: '2026-12-05T21:00:00+08:00',
    signupDeadline: '2026-12-03T23:59:00+08:00',
    capacity: 20,
    familyAllowed: true,
    costPerPerson: 120,
    location: '聚龙酒家',
    locationImageUrls: [],
    description: null,
    status: 0,
    statusLabel: 'SIGNUP_OPEN',
    signupCount: 3,
    signupOpen: true,
    version: 0,
    mySignup: null,
    ...patch,
  };
}

function signup(patch: Partial<ActivitySignup> = {}): ActivitySignup {
  return {
    id: 9,
    activityId: 1,
    playerId: 2,
    playerName: '张三',
    playerNo: 7,
    avatarUrl: null,
    withFamily: false,
    familyNote: null,
    status: 0,
    statusLabel: '已报名',
    signedUpAt: '2026-12-01T10:00:00+08:00',
    ...patch,
  };
}

describe('activityAction', () => {
  it('报名中且未满时展示可用的「报名」', () => {
    expect(activityAction(activity(), NOW)).toEqual({
      visible: true,
      disabled: false,
      label: '报名',
      reason: '',
    });
  });

  it('报名截止或已过截止禁用「已截止」', () => {
    expect(activityAction(activity({ status: 1 }), NOW).label).toBe('已截止');
    expect(activityAction(activity({ signupDeadline: '2026-11-30T23:59:00+08:00' }), NOW)).toEqual({
      visible: true,
      disabled: true,
      label: '已截止',
      reason: '报名已截止',
    });
  });

  it('满员禁用「已满」', () => {
    expect(activityAction(activity({ capacity: 3 }), NOW)).toEqual({
      visible: true,
      disabled: true,
      label: '已满',
      reason: '报名人数已满',
    });
  });

  it('多个禁用条件同时命中时取首个：已截止先于已满', () => {
    const pastAndFull = activity({
      signupDeadline: '2026-11-30T23:59:00+08:00',
      capacity: 3,
    });
    expect(activityAction(pastAndFull, NOW).label).toBe('已截止');
  });

  it('已取消与已结束只显示徽标，不渲染按钮', () => {
    expect(activityAction(activity({ status: 3, statusLabel: '已取消' }), NOW)).toEqual({
      visible: false,
      disabled: true,
      label: '已取消',
      reason: '活动已取消',
    });
    expect(activityAction(activity({ status: 2, statusLabel: '已结束' }), NOW)).toEqual({
      visible: false,
      disabled: true,
      label: '已结束',
      reason: '活动已结束',
    });
  });

  it('已取消优先于满员与截止', () => {
    const cancelled = activity({ status: 3, capacity: 3, signupCount: 3 });
    expect(activityAction(cancelled, NOW).label).toBe('已取消');
  });

  it('服务端 signupOpen=false 时按已截止兜底', () => {
    const stale = activity({ signupOpen: false });
    expect(activityAction(stale, NOW).label).toBe('已截止');
  });
});

describe('activitySignupOpen', () => {
  it('仅在报名中、未过截止且未满员时为 true', () => {
    expect(activitySignupOpen(activity(), NOW)).toBe(true);
    expect(activitySignupOpen(activity({ status: 1 }), NOW)).toBe(false);
    expect(activitySignupOpen(activity({ signupDeadline: '2026-11-30T23:59:00+08:00' }), NOW)).toBe(
      false,
    );
    expect(activitySignupOpen(activity({ capacity: 3 }), NOW)).toBe(false);
    expect(activitySignupOpen(activity({ capacity: null, signupCount: 99 }), NOW)).toBe(true);
  });
});

describe('活动展示字段', () => {
  it('类型与状态按本地映射输出中文，忽略服务端英文 label', () => {
    expect(activityTypeText(0)).toBe('聚餐');
    expect(activityTypeText(1)).toBe('年会');
    expect(activityTypeText(2)).toBe('其他');
    expect(activityStatusText(0)).toBe('报名中');
    expect(activityStatusText(1)).toBe('报名截止');
    expect(activityStatusText(2)).toBe('已结束');
    expect(activityStatusText(3)).toBe('已取消');
    expect(activityTypeText(9 as ActivityType)).toBe('—');
    expect(activityStatusText(9 as ActivityStatus)).toBe('—');
  });

  it('人数按上限展示，无上限只显示已报人数', () => {
    expect(activityCountText(activity())).toBe('3 / 20 人');
    expect(activityCountText(activity({ capacity: null }))).toBe('3 人报名');
    expect(activityCapacityPercent(activity())).toBe(15);
    expect(activityCapacityPercent(activity({ capacity: null }))).toBe(0);
  });

  it('单张图片失败只移除该张', () => {
    const urls = ['a.png', 'b.png', 'c.png'];
    expect(withoutFailedImage(urls, 1)).toEqual(['a.png', 'c.png']);
    expect(withoutFailedImage(urls, 9)).toEqual(urls);
    expect(withoutFailedImage(urls, -1)).toEqual(urls);
  });

  it('图片增删后轮播索引收敛到合法范围', () => {
    expect(clampGalleryIndex(0, 0)).toBe(0);
    expect(clampGalleryIndex(2, 0)).toBe(0);
    expect(clampGalleryIndex(2, 3)).toBe(2);
    expect(clampGalleryIndex(4, 3)).toBe(2);
    expect(clampGalleryIndex(-1, 3)).toBe(0);
  });

  it('时间拼接日期与时段', () => {
    expect(activityTimeText(activity())).toBe('12月5日 周六 18:00–21:00');
    expect(activityTimeText(activity({ endAt: null }))).toBe('12月5日 周六 18:00');
  });

  it('人均消费与状态色位', () => {
    expect(activityCostText(activity())).toBe('¥120.00 / 人');
    expect(activityCostText(activity({ costPerPerson: null }))).toBe('—');
    expect(activityStatusTone(0)).toBe('open');
    expect(activityStatusTone(1)).toBe('closed');
    expect(activityStatusTone(2)).toBe('finished');
    expect(activityStatusTone(3)).toBe('cancelled');
  });

  it('卡片视图带出首个命中按钮与管理标记', () => {
    const card = buildActivityCard(
      activity({ locationImageUrls: ['https://cdn/a.png', 'https://cdn/b.png'] }),
      NOW,
    );
    expect(card).toMatchObject({
      id: 1,
      title: '年终聚餐',
      typeText: '聚餐',
      statusText: '报名中',
      statusTone: 'open',
      timeText: '12月5日 周六 18:00–21:00',
      locationText: '聚龙酒家',
      countText: '3 / 20 人',
      imageUrl: 'https://cdn/a.png',
      actionLabel: '报名',
      actionVisible: true,
      actionDisabled: false,
    });
  });
});

describe('activityCanCancel', () => {
  it('仅本人有效报名且活动报名中、未过截止时可取消', () => {
    expect(activityCanCancel(activity({ mySignup: signup() }), NOW)).toBe(true);
    expect(activityCanCancel(activity(), NOW)).toBe(false);
    expect(activityCanCancel(activity({ mySignup: signup({ status: 1 }) }), NOW)).toBe(false);
    expect(activityCanCancel(activity({ mySignup: signup(), status: 1 }), NOW)).toBe(false);
    expect(
      activityCanCancel(
        activity({ mySignup: signup(), signupDeadline: '2026-11-30T23:59:00+08:00' }),
        NOW,
      ),
    ).toBe(false);
  });
});

describe('familySummary', () => {
  it('未报名或已取消不展示家属摘要', () => {
    expect(familySummary(null)).toBe('');
    expect(familySummary(signup({ status: 1 }))).toBe('');
  });

  it('本人报名与携带家属的文案', () => {
    expect(familySummary(signup())).toBe('仅本人');
    expect(familySummary(signup({ withFamily: true }))).toBe('携带家属');
    expect(familySummary(signup({ withFamily: true, familyNote: '  两位  ' }))).toBe(
      '携带家属 · 两位',
    );
  });
});
