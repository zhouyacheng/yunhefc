/**
 * el-date-picker 按浏览器本地时区解释 Date，而管理端统一按 Asia/Shanghai 展示。
 * 这些函数把真实时刻与「上海墙钟」互相映射，保证非 UTC+8 浏览器也显示同一时间。
 */
export const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;

/** 未来第 N 天的上海整点时刻（真实 UTC 时刻）。 */
export function shanghaiDayAt(daysAhead: number, hour: number): Date {
  const shanghaiNow = new Date(Date.now() + SHANGHAI_OFFSET_MS);
  return new Date(
    Date.UTC(
      shanghaiNow.getUTCFullYear(),
      shanghaiNow.getUTCMonth(),
      shanghaiNow.getUTCDate() + daysAhead,
      hour - 8,
      0,
      0,
    ),
  );
}

/** 接口时刻 → picker 本地墙钟 Date。 */
export function toPickerDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) {
    return null;
  }
  return new Date(instant.getTime() + SHANGHAI_OFFSET_MS + instant.getTimezoneOffset() * 60_000);
}

/** picker 本地墙钟 Date → 真实 UTC 时刻。 */
export function fromPickerDate(value: Date | null): Date | null {
  if (!value) {
    return null;
  }
  return new Date(value.getTime() - SHANGHAI_OFFSET_MS - value.getTimezoneOffset() * 60_000);
}
