import type { MatchSummary } from '@yunhefc/shared';
import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  currentShanghaiSeason,
  groupMatches,
  mergeSeasons,
  optionLabel,
  seasonLabel,
} from './selector';

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
  it('包含标题与状态', () => {
    expect(optionLabel(match(1, '2026'))).toContain('比赛1');
    expect(optionLabel(match(1, '2026'))).toContain('已锁定');
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

  it('选中项不在当前页时置顶且不重复', () => {
    const selected = match(9, '2025');
    const groups = groupMatches([match(1, '2026')], selected);
    expect(groups[0]).toEqual({
      key: 'pinned-9',
      label: '当前选择 · 2025 赛季',
      items: [selected],
    });
    expect(groups.map((group) => group.key)).toEqual(['pinned-9', 'season-2026']);
    expect(new Set(groups.map((group) => group.key)).size).toBe(groups.length);
    expect(groups.flatMap((group) => group.items).filter((item) => item.id === 9)).toHaveLength(1);
  });

  it('选中项在当前页时不置顶', () => {
    const selected = match(1, '2026');
    const groups = groupMatches([selected, match(2, '2026')], selected);
    expect(groups).toHaveLength(1);
    expect(groups[0]?.key).toBe('season-2026');
    expect(groups[0]?.items).toHaveLength(2);
  });
});
