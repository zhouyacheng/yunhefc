const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

/** 后端统一返回 +08:00；用 UTC 读取偏移后的时间，避免设备时区影响。 */
function shanghaiDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const time = Date.parse(value);
  if (Number.isNaN(time)) {
    return null;
  }
  return new Date(time + SHANGHAI_OFFSET_MS);
}

/** 展示文案：按 Asia/Shanghai 输出 `M月D日 周X HH:mm`，无效值返回占位符 `—`。 */
export function formatDayStartTime(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  const month = date.getUTCMonth() + 1;
  const day = date.getUTCDate();
  const weekday = WEEKDAYS[date.getUTCDay()];
  return `${month}月${day}日 ${weekday} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

/**
 * 数据字段：按 Asia/Shanghai 输出 `YYYY-MM-DD`；无效值返回空串，由调用方决定兜底文案。
 */
export function formatDateYmd(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '';
  }
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`;
}

/** 数据字段：按 Asia/Shanghai 输出 `HH:mm`；无效值返回空串，由调用方决定兜底文案。 */
export function formatHourMinute(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '';
  }
  return `${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

/** 数据字段：按 Asia/Shanghai 输出 `YYYY-MM-DD HH:mm`；无效值返回空串。 */
export function formatDateTime(value: string | null | undefined): string {
  const date = formatDateYmd(value);
  return date ? `${date} ${formatHourMinute(value)}` : '';
}

function formatMonthDay(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  return date ? `${pad(date.getUTCMonth() + 1)}/${pad(date.getUTCDate())}` : '';
}

/** 比赛时段：同日 19:30–21:30；跨天 22:00–12/04 02:00。 */
export function formatTimeRange(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string {
  const startTime = formatHourMinute(startAt);
  if (!startTime) {
    return '';
  }
  const endTime = formatHourMinute(endAt);
  if (!endTime) {
    return startTime;
  }
  const startDate = formatDateYmd(startAt);
  const endDate = formatDateYmd(endAt);
  if (!startDate || !endDate || startDate === endDate) {
    return `${startTime}–${endTime}`;
  }
  return `${startTime}–${formatMonthDay(endAt)} ${endTime}`;
}
