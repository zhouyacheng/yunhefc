import type { MatchSummary } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT, formatDateTime } from '@yunhefc/shared';

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;

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
  return `${match.title} · ${formatDateTime(match.startAt)} · ${MATCH_STATUS_TEXT[match.status]}`;
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
