import { describe, expect, it } from 'vitest';

import { formatDateTimeRange } from './format';

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
