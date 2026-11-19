import type { MatchSummary } from '@yunhefc/shared';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  currentShanghaiSeason,
  groupMatches,
  matchFitsFilters,
  mergeSeasons,
  optionLabel,
  seasonLabel,
} from './selector';
import type { MatchFilters } from './types';

function match(
  id: number,
  season: string,
  title = `比赛${id}`,
  status: MatchSummary['status'] = 2,
): MatchSummary {
  return {
    id,
    title,
    type: 1,
    typeLabel: '队内赛',
    startAt: '2026-11-17T12:00:00Z',
    endAt: '2026-11-17T14:00:00Z',
    venue: '球场',
    season,
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    ourSide: null,
    ourSideLabel: null,
    scoreHome: null,
    scoreAway: null,
    resultText: null,
    status,
    statusLabel: '已锁定',
    totalMatchNo: id,
    seasonMatchNo: id,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 0,
    guestCount: 0,
    waitlistCount: 0,
  };
}

afterEach(() => {
  vi.useRealTimers();
});

describe('currentShanghaiSeason', () => {
  it('按 Asia/Shanghai 跨年', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-12-31T15:59:00Z'));
    expect(currentShanghaiSeason()).toBe('2026');
    vi.setSystemTime(new Date('2026-12-31T16:00:00Z'));
    expect(currentShanghaiSeason()).toBe('2027');
  });
});

describe('mergeSeasons', () => {
  it('去空、只保留四位年份、去重并倒序', () => {
    expect(mergeSeasons(['2025', '', '2026', null, 'abc', '2026'])).toEqual(['2026', '2025']);
  });
});

describe('seasonLabel', () => {
  it('区分赛季与未分配', () => {
    expect(seasonLabel('2026')).toBe('2026 赛季');
    expect(seasonLabel('')).toBe('未分配赛季');
    expect(seasonLabel(null)).toBe('未分配赛季');
  });
});

describe('optionLabel', () => {
  it('依次展示场次、标题、时间与状态', () => {
    expect(optionLabel(match(1, '2026'))).toBe(
      '第1场（累计1场） · 比赛1 · 2026/11/17 20:00 · 已锁定',
    );
  });
});

function filters(overrides: Partial<MatchFilters> = {}): MatchFilters {
  return { season: '', monthRange: null, status: '', ...overrides };
}

describe('matchFitsFilters', () => {
  it('赛季与状态空串表示不限，非空精确比较', () => {
    expect(matchFitsFilters(match(1, '2026'), filters())).toBe(true);
    expect(matchFitsFilters(match(1, '2026'), filters({ season: '2026' }))).toBe(true);
    expect(matchFitsFilters(match(1, '2026'), filters({ season: '2025' }))).toBe(false);
    expect(matchFitsFilters(match(1, '2026', '比赛1', 2), filters({ status: 2 }))).toBe(true);
    expect(matchFitsFilters(match(1, '2026', '比赛1', 2), filters({ status: 4 }))).toBe(false);
  });

  it('月份按上海自然月边界', () => {
    const marchEdge = { ...match(1, '2026'), startAt: '2026-03-31T15:59:59Z' };
    expect(matchFitsFilters(marchEdge, filters({ monthRange: ['2026-03', '2026-03'] }))).toBe(true);
    const aprilStart = { ...marchEdge, startAt: '2026-03-31T16:00:00Z' };
    expect(matchFitsFilters(aprilStart, filters({ monthRange: ['2026-03', '2026-03'] }))).toBe(
      false,
    );
    expect(matchFitsFilters(aprilStart, filters({ monthRange: ['2026-04', '2026-04'] }))).toBe(
      true,
    );
  });

  it('支持跨月与反序范围', () => {
    const item = { ...match(1, '2026'), startAt: '2026-05-10T12:00:00Z' };
    expect(matchFitsFilters(item, filters({ monthRange: ['2026-03', '2026-06'] }))).toBe(true);
    expect(matchFitsFilters(item, filters({ monthRange: ['2026-06', '2026-03'] }))).toBe(true);
    expect(matchFitsFilters(item, filters({ monthRange: ['2026-06', '2026-07'] }))).toBe(false);
  });

  it('非法月份范围视为不限', () => {
    const item = { ...match(1, '2026'), startAt: '2026-05-10T12:00:00Z' };
    expect(matchFitsFilters(item, filters({ monthRange: ['2026-13', '2026-01'] }))).toBe(true);
  });

  it('startAt 非法时仅在存在月份条件时判不匹配', () => {
    const broken = { ...match(1, '2026'), startAt: 'not-a-date' };
    expect(matchFitsFilters(broken, filters({ monthRange: ['2026-03', '2026-03'] }))).toBe(false);
    expect(matchFitsFilters(broken, filters())).toBe(true);
  });
});

describe('groupMatches', () => {
  it('按 items 首次出现顺序分组', () => {
    const groups = groupMatches([match(1, '2026'), match(2, '2025'), match(3, '2026')], null);
    expect(groups.map((group) => group.label)).toEqual(['2026 赛季', '2025 赛季']);
    expect(groups[0]?.items.map((item) => item.id)).toEqual([1, 3]);
    expect(groups[1]?.items.map((item) => item.id)).toEqual([2]);
  });

  it('空 season 进入未分配赛季组', () => {
    const groups = groupMatches([match(1, '')], null);
    expect(groups).toEqual([
      { key: 'season-none', label: '未分配赛季', items: [expect.objectContaining({ id: 1 })] },
    ]);
  });

  it('选中项不在当前页时不出现在选项', () => {
    const selected = match(9, '2025');
    const groups = groupMatches([match(1, '2026')], selected);
    expect(groups.map((group) => group.key)).toEqual(['season-2026']);
    expect(groups.flatMap((group) => group.items).some((item) => item.id === 9)).toBe(false);
  });

  it('选中项在当前页时从选项中排除', () => {
    const selected = match(1, '2026');
    const groups = groupMatches([selected, match(2, '2026')], selected);
    expect(groups).toHaveLength(1);
    expect(groups[0]?.key).toBe('season-2026');
    expect(groups[0]?.items.map((item) => item.id)).toEqual([2]);
  });
});
