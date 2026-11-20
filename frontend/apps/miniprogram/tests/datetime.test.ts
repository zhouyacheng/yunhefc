import { describe, expect, it } from 'vitest';

import {
  formatDateYmd,
  formatDayStartTime,
  formatHourMinute,
  formatTimeRange,
} from '../miniprogram/utils/datetime';

describe('formatDayStartTime', () => {
  it.each([
    ['2026-12-03T20:00:00+08:00', '12月3日 周四 20:00'],
    ['2026-12-03T12:00:00Z', '12月3日 周四 20:00'],
    ['2026-12-03T17:00:00Z', '12月4日 周五 01:00'],
    ['2026-12-03T00:00:00+08:00', '12月3日 周四 00:00'],
    ['2026-01-05T09:05:00+08:00', '1月5日 周一 09:05'],
  ])('按上海时区格式化 %s', (value, expected) => {
    expect(formatDayStartTime(value)).toBe(expected);
  });

  it.each([null, undefined, '', 'not-a-date'])('无效值显示占位符：%s', (value) => {
    expect(formatDayStartTime(value)).toBe('—');
  });
});

describe('formatDateYmd / formatHourMinute', () => {
  it('按上海时区输出日期与时刻', () => {
    expect(formatDateYmd('2026-12-03T20:00:00+08:00')).toBe('2026-12-03');
    expect(formatHourMinute('2026-12-03T20:00:00+08:00')).toBe('20:00');
    expect(formatDateYmd('2026-12-03T17:00:00Z')).toBe('2026-12-04');
    expect(formatHourMinute('2026-12-03T17:00:00Z')).toBe('01:00');
  });

  it('无效值返回空串', () => {
    expect(formatDateYmd(null)).toBe('');
    expect(formatDateYmd('bad')).toBe('');
    expect(formatHourMinute('')).toBe('');
    expect(formatHourMinute(undefined)).toBe('');
  });
});

describe('formatTimeRange', () => {
  it('同日输出起止时刻', () => {
    expect(
      formatTimeRange('2026-12-06T20:00:00+08:00', '2026-12-06T22:00:00+08:00'),
    ).toBe('20:00–22:00');
  });

  it('跨天带出结束日期', () => {
    expect(
      formatTimeRange('2026-12-03T22:00:00+08:00', '2026-12-04T02:00:00+08:00'),
    ).toBe('22:00–12/04 02:00');
  });

  it('缺少或非法结束时间时只显示开始时刻', () => {
    expect(formatTimeRange('2026-12-06T20:00:00+08:00', null)).toBe('20:00');
    expect(formatTimeRange('2026-12-06T20:00:00+08:00', 'bad')).toBe('20:00');
  });

  it('非法开始时间返回空串', () => {
    expect(formatTimeRange(null, '2026-12-06T22:00:00+08:00')).toBe('');
  });
});
