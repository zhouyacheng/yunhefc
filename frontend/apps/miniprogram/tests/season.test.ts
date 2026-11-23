import { describe, expect, it } from 'vitest';

import { currentSeason, mergeSeasonOptions } from '../miniprogram/utils/season';

describe('currentSeason', () => {
  it('uses the Asia/Shanghai year', () => {
    // 2026-12-31T16:30Z 已是上海 2027-01-01。
    expect(currentSeason(Date.parse('2026-12-31T16:30:00Z'))).toBe('2027');
    expect(currentSeason(Date.parse('2026-06-01T00:00:00Z'))).toBe('2026');
  });
});

describe('mergeSeasonOptions', () => {
  const now = Date.parse('2026-06-01T00:00:00Z');

  it('filters invalid values, de-duplicates, sorts desc and keeps current + selected', () => {
    expect(mergeSeasonOptions(['2024', '2024', 'abc', '2023-x', '2026'], '2022', now)).toEqual([
      '2026',
      '2024',
      '2022',
    ]);
  });

  it('keeps the current year even when absent from match seasons', () => {
    expect(mergeSeasonOptions([], '2024', now)).toEqual(['2026', '2024']);
  });
});
