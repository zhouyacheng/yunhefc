import { describe, expect, it } from 'vitest';

import {
  ACTIVITY_SCOPES,
  ACTIVITY_STATUS_OPTIONS,
  ACTIVITY_TYPE_OPTIONS,
  activityFeedKey,
  activityListQuery,
  DEFAULT_ACTIVITY_FILTERS,
  DEFAULT_ACTIVITY_KEY,
  parseActivityFeedKey,
} from '../miniprogram/utils/activity-query';

describe('活动筛选编码', () => {
  it('默认 key 可往返', () => {
    expect(DEFAULT_ACTIVITY_KEY).toBe('latest||');
    expect(parseActivityFeedKey(DEFAULT_ACTIVITY_KEY)).toEqual(DEFAULT_ACTIVITY_FILTERS);
    expect(activityListQuery(DEFAULT_ACTIVITY_FILTERS)).toEqual({
      scope: 'latest',
      type: undefined,
      status: undefined,
    });
  });

  it('筛选 key 与请求参数保持同一口径', () => {
    const filters = { scope: 'latest' as const, type: 0 as const, status: 1 as const };
    expect(activityFeedKey(filters)).toBe('latest|0|1');
    expect(parseActivityFeedKey(activityFeedKey(filters))).toEqual(filters);
    expect(activityListQuery(filters)).toEqual({ scope: 'latest', type: 0, status: 1 });
  });

  it('非法片段回落到默认值', () => {
    expect(parseActivityFeedKey('')).toEqual(DEFAULT_ACTIVITY_FILTERS);
    expect(parseActivityFeedKey('bogus|9|x')).toEqual(DEFAULT_ACTIVITY_FILTERS);
    // P-116：MP10 不再提供历史范围，旧 key 回落缺省最近活动。
    expect(parseActivityFeedKey('history||')).toEqual(DEFAULT_ACTIVITY_FILTERS);
  });

  it('选项顺序与 02 一致', () => {
    expect(ACTIVITY_SCOPES.map((option) => option.label)).toEqual([
      '最近活动',
      '已结束活动',
      '全部',
    ]);
    expect(ACTIVITY_TYPE_OPTIONS.map((option) => option.label)).toEqual([
      '全部类型',
      '聚餐',
      '年会',
      '其他',
    ]);
    expect(ACTIVITY_STATUS_OPTIONS.map((option) => option.label)).toEqual([
      '全部状态',
      '报名中',
      '报名截止',
      '已结束',
      '已取消',
    ]);
  });
});
