const SEASON_PATTERN = /^\d{4}$/;
const SHANGHAI_YEAR = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
});

/** 当前赛季（Asia/Shanghai 当前年份）。 */
export function currentShanghaiSeason(now = new Date()): string {
  const year = SHANGHAI_YEAR.formatToParts(now).find((part) => part.type === 'year')?.value;
  return year && SEASON_PATTERN.test(year) ? year : String(now.getUTCFullYear());
}

/** 解析 URL season；缺失或非法值回退当前年份。 */
export function parseTeamSeason(raw: unknown, now = new Date()): string {
  const value = Array.isArray(raw) ? raw[0] : raw;
  const season = typeof value === 'string' ? value.trim() : '';
  return SEASON_PATTERN.test(season) ? season : currentShanghaiSeason(now);
}

/** 默认年份不写入 URL，避免污染。 */
export function serializeTeamSeason(season: string, now = new Date()): Record<string, string> {
  return season === currentShanghaiSeason(now) ? {} : { season };
}

/** 选项过滤非法值、去重、倒序，并强制包含当前年份与已选赛季。 */
export function mergeSeasonOptions(
  items: readonly string[],
  selected: string,
  now = new Date(),
): string[] {
  const seasons = new Set<string>([currentShanghaiSeason(now), selected]);
  for (const item of items) {
    if (SEASON_PATTERN.test(item)) {
      seasons.add(item);
    }
  }
  return [...seasons].sort((a, b) => b.localeCompare(a));
}
