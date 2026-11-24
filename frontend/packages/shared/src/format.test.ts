import { describe, expect, it } from 'vitest';

import {
  formatAmount,
  formatDate,
  formatDayStartTime,
  formatDayTimeRange,
  formatDateTimeRange,
  formatDateWithYear,
  formatMoney,
  formatTimeRange,
} from './format';

describe('formatDayStartTime', () => {
  it('输出月日、星期与开始时刻', () => {
    expect(formatDayStartTime('2026-12-03T12:00:00Z')).toBe('12月3日 周四 20:00');
  });

  it('按上海自然日跨日', () => {
    expect(formatDayStartTime('2078-12-01T16:00:00Z')).toBe('12月2日 周五 00:00');
  });

  it.each([null, '', 'bad'])('无效值显示占位符：%s', (value) => {
    expect(formatDayStartTime(value)).toBe('—');
  });
});

describe('formatDayTimeRange', () => {
  it('同日拼接日期、星期与起止时刻', () => {
    expect(formatDayTimeRange('2078-12-01T11:30:00Z', '2078-12-01T13:30:00Z')).toBe(
      '12月1日 周四 19:30–21:30',
    );
  });

  it('跨天补结束日期', () => {
    expect(formatDayTimeRange('2078-12-01T14:00:00Z', '2078-12-01T18:00:00Z')).toBe(
      '12月1日 周四 22:00–12/02 02:00',
    );
  });

  it.each([null, undefined, ''])('无结束时间只显示开始：%s', (endAt) => {
    expect(formatDayTimeRange('2078-12-01T11:30:00Z', endAt)).toBe('12月1日 周四 19:30');
  });

  it.each([null, '', 'bad'])('无效开始时间显示占位符：%s', (value) => {
    expect(formatDayTimeRange(value, '2078-12-01T13:30:00Z')).toBe('—');
  });
});

describe('formatDate', () => {
  it('月日与星期之间保留空格', () => {
    expect(formatDate('2026-11-18T11:30:00Z')).toBe('11/18 周三');
  });

  it.each([null, '', 'bad'])('无效值显示占位符：%s', (value) => {
    expect(formatDate(value)).toBe('—');
  });
});

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

describe('金额格式化', () => {
  it('固定两位小数并千分位', () => {
    expect(formatAmount(0)).toBe('0.00');
    expect(formatAmount(88.5)).toBe('88.50');
    expect(formatAmount(1234.5)).toBe('1,234.50');
    expect(formatAmount(-1234.5)).toBe('1,234.50');
    expect(formatAmount(Number.NaN)).toBe('—');
  });

  it('负数统一使用 U+2212 与货币符号', () => {
    expect(formatMoney(0)).toBe('¥0.00');
    expect(formatMoney(1234.5)).toBe('¥1,234.50');
    expect(formatMoney(-500)).toBe('−¥500.00');
    expect(formatMoney(Number.POSITIVE_INFINITY)).toBe('—');
  });
});
