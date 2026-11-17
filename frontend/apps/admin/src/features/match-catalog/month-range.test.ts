import { describe, expect, it } from 'vitest';

import {
  buildMonthRange,
  formatMonthRange,
  isSingleMonth,
  monthText,
  parseMonthRange,
  remapMonthRange,
} from './month-range';

describe('parseMonthRange', () => {
  it('解析并升序归一化', () => {
    expect(parseMonthRange(['2026-03', '2026-01'])).toEqual({ start: 1, end: 3 });
    expect(parseMonthRange(['2026-05', '2026-05'])).toEqual({ start: 5, end: 5 });
  });

  it('空值或非法月份返回 null', () => {
    expect(parseMonthRange(null)).toBeNull();
    expect(parseMonthRange(['2026-00', '2026-01'])).toBeNull();
    expect(parseMonthRange(['2026-01', '2026-13'])).toBeNull();
    expect(parseMonthRange(['2026-1', '2026-03'])).toBeNull();
  });
});

describe('buildMonthRange', () => {
  it('用赛季年份构建，反向输入自动升序', () => {
    expect(buildMonthRange('2026', 3, 1)).toEqual(['2026-01', '2026-03']);
    expect(buildMonthRange('2026', 12, 12)).toEqual(['2026-12', '2026-12']);
  });

  it('非法赛季或月号返回 null', () => {
    expect(buildMonthRange('', 1, 3)).toBeNull();
    expect(buildMonthRange('2026-01', 1, 3)).toBeNull();
    expect(buildMonthRange('2026', 0, 3)).toBeNull();
    expect(buildMonthRange('2026', 1, 13)).toBeNull();
    expect(buildMonthRange('2026', 1.5, 3)).toBeNull();
  });
});

describe('remapMonthRange', () => {
  it('只替换年份、保留月号', () => {
    expect(remapMonthRange(['2026-01', '2026-03'], '2025')).toEqual(['2025-01', '2025-03']);
  });

  it('空范围或空赛季返回 null', () => {
    expect(remapMonthRange(null, '2025')).toBeNull();
    expect(remapMonthRange(['2026-01', '2026-03'], '')).toBeNull();
  });

  it('重复重映射幂等', () => {
    const once = remapMonthRange(['2026-01', '2026-03'], '2025');
    expect(remapMonthRange(once, '2025')).toEqual(once);
  });
});

describe('monthText', () => {
  it('输出中文月份', () => {
    expect(monthText(1)).toBe('1月');
    expect(monthText(12)).toBe('12月');
    expect(monthText(0)).toBe('');
    expect(monthText(13)).toBe('');
  });
});

describe('formatMonthRange', () => {
  it('空、单月与跨月文案', () => {
    expect(formatMonthRange(null)).toBe('');
    expect(formatMonthRange(['2026-03', '2026-03'])).toBe('3月');
    expect(formatMonthRange(['2026-01', '2026-03'])).toBe('1月 至 3月');
  });
});

describe('isSingleMonth', () => {
  it('识别单月范围', () => {
    expect(isSingleMonth(['2026-03', '2026-03'])).toBe(true);
    expect(isSingleMonth(['2026-01', '2026-03'])).toBe(false);
    expect(isSingleMonth(null)).toBe(false);
  });
});
