import { describe, expect, it } from 'vitest';

import {
  currentShanghaiSeason,
  mergeSeasonOptions,
  parseTeamSeason,
  serializeTeamSeason,
} from './query';

describe('currentShanghaiSeason', () => {
  it('按 Asia/Shanghai 跨年', () => {
    expect(currentShanghaiSeason(new Date('2025-12-31T15:59:59Z'))).toBe('2025');
    expect(currentShanghaiSeason(new Date('2025-12-31T16:00:00Z'))).toBe('2026');
  });
});

describe('parseTeamSeason', () => {
  it('接受四位年份并 trim', () => {
    expect(parseTeamSeason('2025')).toBe('2025');
    expect(parseTeamSeason(' 2025 ')).toBe('2025');
  });

  it('数组取首值', () => {
    expect(parseTeamSeason(['2024', '2023'])).toBe('2024');
  });

  it('非法或缺失回退当前年份', () => {
    const now = new Date('2026-06-01T00:00:00Z');
    expect(parseTeamSeason('26', now)).toBe('2026');
    expect(parseTeamSeason('abcd', now)).toBe('2026');
    expect(parseTeamSeason('', now)).toBe('2026');
    expect(parseTeamSeason(undefined, now)).toBe('2026');
    expect(parseTeamSeason(['bad'], now)).toBe('2026');
  });
});

describe('serializeTeamSeason', () => {
  const now = new Date('2026-06-01T00:00:00Z');

  it('默认年份不写 URL', () => {
    expect(serializeTeamSeason('2026', now)).toEqual({});
  });

  it('历史赛季写入 URL', () => {
    expect(serializeTeamSeason('2025', now)).toEqual({ season: '2025' });
  });
});

describe('mergeSeasonOptions', () => {
  const now = new Date('2026-06-01T00:00:00Z');

  it('过滤非法值、去重并倒序', () => {
    expect(mergeSeasonOptions(['2025', '2025', 'bad', '2026', '2024'], '2026', now)).toEqual([
      '2026',
      '2025',
      '2024',
    ]);
  });

  it('接口为空时仍包含当前年份与已选赛季', () => {
    expect(mergeSeasonOptions([], '2023', now)).toEqual(['2026', '2023']);
  });
});
