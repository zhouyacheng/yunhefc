import { describe, expect, it } from 'vitest';

import {
  activityCancelState,
  activityCapacityPercent,
  activityCta,
  activityFamilyText,
  activitySignupCardText,
  activitySignupState,
  activitySignupText,
  activityStatusTone,
  formatActivityCost,
  type ActivityGateInput,
} from './presentation';

const NOW = Date.parse('2026-12-01T12:00:00+08:00');

function gate(overrides: Partial<ActivityGateInput> = {}): ActivityGateInput {
  return {
    status: 0,
    signupDeadline: '2026-12-04T20:00:00+08:00',
    capacity: 20,
    signupCount: 3,
    signupOpen: true,
    ...overrides,
  };
}

describe('activityCta（列表与详情共用矩阵）', () => {
  it('报名中未满显示报名', () => {
    expect(activityCta(gate(), NOW)).toEqual({ visible: true, label: '报名', disabled: false });
  });

  it('已取消/已结束只显示徽标', () => {
    expect(activityCta(gate({ status: 3 }), NOW)).toEqual({
      visible: false,
      label: '已取消',
      disabled: true,
    });
    expect(activityCta(gate({ status: 2 }), NOW)).toEqual({
      visible: false,
      label: '已结束',
      disabled: true,
    });
  });

  it('报名截止状态或已过截止时间显示已截止', () => {
    expect(activityCta(gate({ status: 1 }), NOW)).toMatchObject({
      label: '已截止',
      disabled: true,
    });
    expect(activityCta(gate({ signupDeadline: '2026-12-01T11:59:00+08:00' }), NOW)).toMatchObject({
      label: '已截止',
      disabled: true,
    });
  });

  it('报名中且满员显示已满', () => {
    expect(activityCta(gate({ signupCount: 20 }), NOW)).toMatchObject({
      label: '已满',
      disabled: true,
    });
  });

  it('后端 signupOpen=false 时保守禁用', () => {
    expect(activityCta(gate({ signupOpen: false }), NOW)).toEqual({
      visible: true,
      label: '报名',
      disabled: true,
    });
  });
});

describe('activitySignupState（详情报名态派生）', () => {
  it('已报名隐藏报名按钮', () => {
    expect(
      activitySignupState({
        ...gate(),
        authenticated: true,
        pending: false,
        mySignupStatus: 0,
        now: NOW,
      }),
    ).toMatchObject({ visible: false, action: 'none' });
  });

  it('未登录引导登录', () => {
    expect(
      activitySignupState({
        ...gate(),
        authenticated: false,
        pending: false,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toEqual({
      visible: true,
      label: '登录后报名',
      action: 'login',
      disabled: false,
      reason: '',
    });
  });

  it('待审核账号不可报名', () => {
    expect(
      activitySignupState({
        ...gate(),
        authenticated: true,
        pending: true,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toMatchObject({ visible: false, reason: '账号待审核，通过后可报名' });
  });

  it('可报名时返回 signup 动作', () => {
    expect(
      activitySignupState({
        ...gate(),
        authenticated: true,
        pending: false,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toMatchObject({ label: '报名', action: 'signup', disabled: false });
  });

  it('已满禁用并给出原因', () => {
    expect(
      activitySignupState({
        ...gate({ signupCount: 20 }),
        authenticated: true,
        pending: false,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toMatchObject({ label: '已满', action: 'none', disabled: true, reason: '名额已满' });
  });

  it('已取消/已结束活动对访客也不渲染按钮', () => {
    for (const status of [2, 3] as const) {
      expect(
        activitySignupState({
          ...gate({ status }),
          authenticated: false,
          pending: false,
          mySignupStatus: null,
          now: NOW,
        }),
      ).toMatchObject({ visible: false, action: 'none' });
    }
  });

  it('访客在满员或已截止时看到禁用文案而不是登录引导', () => {
    expect(
      activitySignupState({
        ...gate({ signupCount: 20 }),
        authenticated: false,
        pending: false,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toMatchObject({ label: '已满', action: 'none', disabled: true });
    expect(
      activitySignupState({
        ...gate({ status: 1 }),
        authenticated: false,
        pending: false,
        mySignupStatus: null,
        now: NOW,
      }),
    ).toMatchObject({ label: '已截止', action: 'none', disabled: true });
  });

  it('已取消活动不渲染按钮（已报名）', () => {
    expect(
      activitySignupState({
        ...gate({ status: 3 }),
        authenticated: true,
        pending: false,
        mySignupStatus: 1,
        now: NOW,
      }),
    ).toMatchObject({ visible: false, action: 'none' });
  });
});

describe('activityCancelState', () => {
  it('报名中未过截止可取消', () => {
    expect(activityCancelState({ ...gate(), mySignupStatus: 0, now: NOW })).toEqual({
      visible: true,
      label: '取消报名',
      disabled: false,
      reason: '',
    });
  });

  it('未报名不显示取消', () => {
    expect(activityCancelState({ ...gate(), mySignupStatus: null, now: NOW }).visible).toBe(false);
  });

  it('已过截止或活动取消禁用取消', () => {
    expect(
      activityCancelState({
        ...gate({ signupDeadline: '2026-12-01T11:59:00+08:00' }),
        mySignupStatus: 0,
        now: NOW,
      }),
    ).toMatchObject({ disabled: true, reason: '报名已截止，无法取消' });
    expect(
      activityCancelState({ ...gate({ status: 3 }), mySignupStatus: 0, now: NOW }),
    ).toMatchObject({ disabled: true, reason: '活动已取消' });
  });
});

describe('展示格式化', () => {
  it('hero 人数文案只展示人数/上限', () => {
    expect(activitySignupText(12, 20)).toBe('已报 12 / 20 人');
  });

  it('报名卡人数文案明确已报名与上限', () => {
    expect(activitySignupCardText(12, 20)).toBe('已报名 12 / 20 人');
  });

  it('人数进度按上限计算并夹在 0–100', () => {
    expect(activityCapacityPercent(12, 20)).toBe(60);
    expect(activityCapacityPercent(30, 20)).toBe(100);
    expect(activityCapacityPercent(2, 0)).toBe(0);
  });

  it('人均消费与家属摘要', () => {
    expect(formatActivityCost(120)).toBe('¥120.00');
    expect(formatActivityCost(null)).toBe('—');
    expect(activityFamilyText({ withFamily: true, familyNote: '带 1 位家属' })).toBe(
      '携带家属（带 1 位家属）',
    );
    expect(activityFamilyText({ withFamily: true, familyNote: null })).toBe('携带家属');
    expect(activityFamilyText({ withFamily: false, familyNote: null })).toBe('不携带家属');
  });

  it('状态色调映射', () => {
    expect(activityStatusTone(0)).toBe('open');
    expect(activityStatusTone(1)).toBe('closed');
    expect(activityStatusTone(2)).toBe('finished');
    expect(activityStatusTone(3)).toBe('cancelled');
  });
});
