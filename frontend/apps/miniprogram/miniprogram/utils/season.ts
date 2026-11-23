import { currentShanghaiMonth } from './datetime';

/** 与 Web currentShanghaiSeason 一致：Asia/Shanghai 当前年份。 */
export function currentSeason(now: number | Date = Date.now()): string {
  const time = typeof now === 'number' ? now : now.getTime();
  return currentShanghaiMonth(time).slice(0, 4) || String(new Date(time).getFullYear());
}

/** 与 Web mergeSeasonOptions 一致：过滤非法值、去重、倒序，并包含当前与已选赛季。 */
export function mergeSeasonOptions(
  items: readonly string[],
  selected: string,
  now: number | Date = Date.now(),
): string[] {
  const seasons = new Set<string>([currentSeason(now), selected]);
  for (const item of items) {
    if (/^\d{4}$/.test(item)) {
      seasons.add(item);
    }
  }
  return [...seasons].sort((a, b) => b.localeCompare(a));
}
