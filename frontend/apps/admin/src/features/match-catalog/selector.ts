import type { MatchSummary } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT, formatDateTime } from '@yunhefc/shared';

import type { MatchFilters } from './types';

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const MONTH_KEY_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

export interface MatchOptionGroup {
  key: string;
  label: string;
  items: MatchSummary[];
}

/** 赛季按 Asia/Shanghai 当前年份；与筛选默认值保持一致。 */
export function currentShanghaiSeason(now: number = Date.now()): string {
  return String(new Date(now + SHANGHAI_OFFSET_MS).getUTCFullYear());
}

export function mergeSeasons(values: ReadonlyArray<string | null | undefined>): string[] {
  return [...new Set(values.filter((value): value is string => Boolean(value)))]
    .filter((value) => /^\d{4}$/.test(value))
    .sort((a, b) => b.localeCompare(a));
}

export function seasonLabel(value: string | null | undefined): string {
  return value ? `${value} 赛季` : '未分配赛季';
}

export function optionLabel(match: MatchSummary): string {
  return `第${match.seasonMatchNo}场（累计${match.totalMatchNo}场） · ${match.title} · ${formatDateTime(match.startAt)} · ${MATCH_STATUS_TEXT[match.status]}`;
}

/**
 * 比赛是否满足当前筛选。月份按 Asia/Shanghai 自然月比较；
 * 非法 monthRange 与 source 对齐视为不下发月份条件。
 */
export function matchFitsFilters(match: MatchSummary, filters: MatchFilters): boolean {
  if (filters.season !== '' && match.season !== filters.season) {
    return false;
  }
  if (filters.status !== '' && match.status !== filters.status) {
    return false;
  }
  const range = filters.monthRange;
  if (!range) {
    return true;
  }
  const [first, second] = range;
  if (!MONTH_KEY_PATTERN.test(first) || !MONTH_KEY_PATTERN.test(second)) {
    return true;
  }
  const start = first <= second ? first : second;
  const end = first <= second ? second : first;
  const time = Date.parse(match.startAt);
  if (Number.isNaN(time)) {
    return false;
  }
  const month = new Date(time + SHANGHAI_OFFSET_MS).toISOString().slice(0, 7);
  return month >= start && month <= end;
}

/**
 * 当前页选项按赛季分组；当前已选比赛不出现在下拉选项中。
 * 组顺序沿用 items 的首次出现顺序，不额外重排。
 */
export function groupMatches(
  items: readonly MatchSummary[],
  selected: MatchSummary | null,
): MatchOptionGroup[] {
  const groups: MatchOptionGroup[] = [];
  const selectedId = selected?.id ?? null;
  const bySeason = new Map<string, MatchSummary[]>();
  for (const match of items) {
    if (match.id === selectedId) {
      continue;
    }
    const key = match.season || '';
    const list = bySeason.get(key) ?? [];
    list.push(match);
    bySeason.set(key, list);
  }
  for (const [value, list] of bySeason.entries()) {
    groups.push({
      key: `season-${value || 'none'}`,
      label: seasonLabel(value),
      items: list,
    });
  }
  return groups;
}
