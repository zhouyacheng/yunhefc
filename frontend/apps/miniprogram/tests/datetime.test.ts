import { describe, expect, it } from 'vitest';

import {
  currentShanghaiMonth,
  formatDateWithYear,
  formatDateYmd,
  formatDateTime,
  formatDateTimeWithYear,
  formatDayStartTime,
  formatDayTimeRange,
  formatHourMinute,
  formatTimeRange,
  formatYearDate,
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

  it('formatDateTime 组合日期与时刻', () => {
    expect(formatDateTime('2026-12-03T20:00:00+08:00')).toBe('2026/12/03 20:00');
    expect(formatDateTime(null)).toBe('');
  });
});

describe('formatTimeRange', () => {
  it('同日输出起止时刻', () => {
    expect(formatTimeRange('2026-12-06T20:00:00+08:00', '2026-12-06T22:00:00+08:00')).toBe(
      '20:00–22:00',
    );
  });

  it('跨天带出结束日期', () => {
    expect(formatTimeRange('2026-12-03T22:00:00+08:00', '2026-12-04T02:00:00+08:00')).toBe(
      '22:00–12/04 02:00',
    );
  });

  it('缺少或非法结束时间时只显示开始时刻', () => {
    expect(formatTimeRange('2026-12-06T20:00:00+08:00', null)).toBe('20:00');
    expect(formatTimeRange('2026-12-06T20:00:00+08:00', 'bad')).toBe('20:00');
  });

  it('非法开始时间返回空串', () => {
    expect(formatTimeRange(null, '2026-12-06T22:00:00+08:00')).toBe('');
  });
});

describe('formatYearDate', () => {
  it('只输出上海日期，不带时分与星期', () => {
    expect(formatYearDate('2026-12-03T20:00:00+08:00')).toBe('2026/12/03');
    expect(formatYearDate('2026-12-03T17:00:00Z')).toBe('2026/12/04');
  });

  it('无效值显示占位符', () => {
    expect(formatYearDate(null)).toBe('—');
    expect(formatYearDate('bad')).toBe('—');
  });
});

describe('formatDateWithYear / formatDateTimeWithYear', () => {
  it('按上海时区输出带年份的日期与时刻', () => {
    expect(formatDateWithYear('2026-12-03T20:00:00+08:00')).toBe('2026/12/03 周四');
    expect(formatDateWithYear('2026-12-03T17:00:00Z')).toBe('2026/12/04 周五');
    expect(formatDateTimeWithYear('2026-12-03T20:00:00+08:00')).toBe('2026/12/03 20:00');
    expect(formatDateTimeWithYear('2026-12-03T17:00:00Z')).toBe('2026/12/04 01:00');
  });

  it('无效值显示占位符', () => {
    expect(formatDateWithYear(null)).toBe('—');
    expect(formatDateWithYear('bad')).toBe('—');
    expect(formatDateTimeWithYear('')).toBe('—');
    expect(formatDateTimeWithYear(undefined)).toBe('—');
  });
});

describe('formatDayTimeRange', () => {
  it('拼接日期与同日时段', () => {
    expect(formatDayTimeRange('2026-12-05T18:00:00+08:00', '2026-12-05T21:00:00+08:00')).toBe(
      '12月5日 周六 18:00–21:00',
    );
    expect(formatDayTimeRange('2026-12-05T18:00:00+08:00', null)).toBe('12月5日 周六 18:00');
  });

  it('跨天结束带月日', () => {
    expect(formatDayTimeRange('2026-12-05T22:00:00+08:00', '2026-12-06T02:00:00+08:00')).toBe(
      '12月5日 周六 22:00–12/06 02:00',
    );
  });

  it('无效开始时间返回占位符', () => {
    expect(formatDayTimeRange(null, '2026-12-05T21:00:00+08:00')).toBe('—');
    expect(formatDayTimeRange('bad', null)).toBe('—');
  });
});

describe('currentShanghaiMonth', () => {
  it('按上海时区跨月', () => {
    expect(currentShanghaiMonth(Date.UTC(2026, 10, 30, 17))).toBe('2026-12');
    expect(currentShanghaiMonth(Date.UTC(2026, 10, 30, 15))).toBe('2026-11');
    expect(currentShanghaiMonth(new Date(Date.UTC(2026, 0, 1, 0)))).toBe('2026-01');
  });
});
