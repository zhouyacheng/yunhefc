import { describe, expect, it } from 'vitest';

import {
  DEFAULT_ACTIVITY_PAGE_SIZE,
  fromActivityForm,
  normalizeActivityPageSize,
  parseActivityScope,
  parseActivityStatus,
  parseActivityType,
  toActivityRequest,
} from './query';

describe('parseActivityScope', () => {
  it('只接受 latest/history/all', () => {
    expect(parseActivityScope('latest')).toBe('latest');
    expect(parseActivityScope('history')).toBe('history');
    expect(parseActivityScope('all')).toBe('all');
    expect(parseActivityScope('')).toBe('all');
    expect(parseActivityScope('career')).toBe('all');
  });
});

describe('parseActivityType/Status', () => {
  it('空值表示不筛选', () => {
    expect(parseActivityType('')).toBeNull();
    expect(parseActivityStatus('')).toBeNull();
  });

  it('接受白名单枚举码', () => {
    expect(parseActivityType('0')).toBe(0);
    expect(parseActivityType('2')).toBe(2);
    expect(parseActivityStatus('3')).toBe(3);
  });

  it('非法枚举码回退为不筛选', () => {
    expect(parseActivityType('9')).toBeNull();
    expect(parseActivityStatus('-1')).toBeNull();
    expect(parseActivityType('dinner')).toBeNull();
  });
});

describe('fromActivityForm', () => {
  it('表单字符串转为筛选条件', () => {
    expect(fromActivityForm({ scope: 'history', type: '1', status: '0' })).toEqual({
      scope: 'history',
      type: 1,
      status: 0,
    });
  });
});

describe('toActivityRequest', () => {
  it('全部范围下发分页与筛选', () => {
    expect(toActivityRequest({ scope: 'all', type: 0, status: 2, page: 2, pageSize: 20 })).toEqual({
      scope: 'all',
      type: 0,
      status: 2,
      page: 2,
      pageSize: 20,
    });
  });

  it('不筛选时不带 type/status', () => {
    expect(
      toActivityRequest({ scope: 'history', type: null, status: null, page: 1, pageSize: 10 }),
    ).toEqual({ scope: 'history', page: 1, pageSize: 10 });
  });

  it('最近活动同样下发分页参数', () => {
    expect(
      toActivityRequest({ scope: 'latest', type: null, status: null, page: 3, pageSize: 20 }),
    ).toEqual({ scope: 'latest', page: 3, pageSize: 20 });
  });
});

describe('normalizeActivityPageSize', () => {
  it('白名单外的页大小回退默认值', () => {
    expect(normalizeActivityPageSize(50)).toBe(50);
    expect(normalizeActivityPageSize(15)).toBe(DEFAULT_ACTIVITY_PAGE_SIZE);
  });
});
