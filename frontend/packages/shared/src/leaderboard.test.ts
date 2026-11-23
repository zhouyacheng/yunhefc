import { describe, expect, it } from 'vitest';

import { isZeroOrPlaceholder, leaderboardMainLabel } from './leaderboard';

describe('leaderboardMainLabel', () => {
  it('按窗口返回当前赛季、历史赛季或生涯累计', () => {
    expect(leaderboardMainLabel('season', '2026', '2026')).toBe('当前赛季');
    expect(leaderboardMainLabel('season', '2025', '2026')).toBe('2025 赛季');
    expect(leaderboardMainLabel('all', '2026', '2026')).toBe('生涯累计');
  });
});

describe('isZeroOrPlaceholder', () => {
  it('识别 0、破折号与正常值', () => {
    expect(isZeroOrPlaceholder('0')).toBe(true);
    expect(isZeroOrPlaceholder('—')).toBe(true);
    expect(isZeroOrPlaceholder('0.00')).toBe(false);
    expect(isZeroOrPlaceholder('1')).toBe(false);
    expect(isZeroOrPlaceholder(null)).toBe(false);
  });
});
