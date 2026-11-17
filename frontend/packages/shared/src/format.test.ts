import { describe, expect, it } from 'vitest';

import { formatDateTimeRange, formatDateWithYear, formatTimeRange } from './format';

describe('formatDateWithYear', () => {
  it('输出上海日期与星期', () => {
    expect(formatDateWithYear('2026-11-18T11:30:00Z')).toBe('2026/11/18 周三');
  });

  it('按上海自然日跨日', () => {
    expect(formatDateWithYear('2078-12-01T16:00:00Z')).toBe('2078/12/02 周五');
  });

  it.each([null, '', 'bad'])('无效值显示占位符：%s', (value) => {
    expect(formatDateWithYear(value)).toBe('—');
  });
});

describe('formatTimeRange', () => {
  it('同日只显示起止时刻', () => {
    expect(formatTimeRange('2078-12-01T11:30:00Z', '2078-12-01T13:30:00Z')).toBe('19:30–21:30');
  });

  it('跨天补结束日期', () => {
    expect(formatTimeRange('2078-12-01T14:00:00Z', '2078-12-01T18:00:00Z')).toBe(
      '22:00–12/02 02:00',
    );
  });

  it('上海午夜输出 00:00', () => {
    expect(formatTimeRange('2078-12-01T16:00:00Z', '2078-12-01T18:00:00Z')).toBe('00:00–02:00');
  });

  it.each([null, '', 'bad', '2078-12-01T11:00:00Z'])(
    '结束时间无效或早于开始时间时只显示开始时间：%s',
    (endAt) => {
      expect(formatTimeRange('2078-12-01T12:00:00Z', endAt)).toBe('20:00');
    },
  );

  it.each([null, 'bad'])('开始时间无效时显示占位符：%s', (startAt) => {
    expect(formatTimeRange(startAt, '2078-12-01T14:00:00Z')).toBe('—');
  });
});

describe('formatDateTimeRange', () => {
  it('同日只显示结束时刻', () => {
    expect(formatDateTimeRange('2078-12-01T12:00:00Z', '2078-12-01T14:00:00Z')).toBe(
      '2078/12/01 20:00 ~ 22:00',
    );
  });

  it('跨天补结束日期', () => {
    expect(formatDateTimeRange('2078-12-01T14:00:00Z', '2078-12-01T18:00:00Z')).toBe(
      '2078/12/01 22:00 ~ 12/02 02:00',
    );
  });

  it('上海午夜输出 00:00', () => {
    expect(formatDateTimeRange('2078-12-01T16:00:00Z', '2078-12-01T18:00:00Z')).toBe(
      '2078/12/02 00:00 ~ 02:00',
    );
  });

  it('跨年补结束日期', () => {
    expect(formatDateTimeRange('2078-12-31T14:00:00Z', '2078-12-31T18:00:00Z')).toBe(
      '2078/12/31 22:00 ~ 01/01 02:00',
    );
  });

  it('按上海自然日判断跨天，而非 UTC 日期', () => {
    expect(formatDateTimeRange('2078-12-01T17:00:00Z', '2078-12-02T03:00:00Z')).toBe(
      '2078/12/02 01:00 ~ 11:00',
    );
  });

  it.each([null, '', 'bad', '2078-12-01T11:00:00Z'])(
    '结束时间无效或早于开始时间时只显示开始时间：%s',
    (endAt) => {
      expect(formatDateTimeRange('2078-12-01T12:00:00Z', endAt)).toBe('2078/12/01 20:00');
    },
  );

  it.each([null, 'bad'])('开始时间无效时显示占位符：%s', (startAt) => {
    expect(formatDateTimeRange(startAt, '2078-12-01T14:00:00Z')).toBe('—');
  });
});
