import { SHANGHAI_OFFSET_MS } from './shanghai';

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
const WEEKDAYS_LONG = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

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

function dayLabel(date: Date): string {
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日 ${WEEKDAYS[date.getUTCDay()]}`;
}

/** 展示文案：按 Asia/Shanghai 输出 `M月D日 周X HH:mm`，无效值返回占位符 `—`。 */
export function formatDayStartTime(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  return `${dayLabel(date)} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
}

/** 展示文案：`M月D日 周X HH:mm–HH:mm`；与 packages/shared 的 formatDayTimeRange 保持一致。 */
export function formatDayTimeRange(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string {
  const date = shanghaiDate(startAt);
  const start = formatHourMinute(startAt);
  if (!date || !start) {
    return '—';
  }
  const head = dayLabel(date);
  const end = formatHourMinute(endAt);
  if (!end) {
    return `${head} ${start}`;
  }
  const startDate = formatDateYmd(startAt);
  const endDate = formatDateYmd(endAt);
  if (!endDate || startDate === endDate) {
    return `${head} ${start}–${end}`;
  }
  return `${head} ${start}–${formatMonthDay(endAt)} ${end}`;
}

/** 当前年月（Asia/Shanghai），输出 `YYYY-MM`。 */
export function currentShanghaiMonth(now: number | Date = Date.now()): string {
  const time = typeof now === 'number' ? now : now.getTime();
  const date = new Date(time + SHANGHAI_OFFSET_MS);
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}`;
}

/** 当前自然日（Asia/Shanghai），输出 `YYYY-MM-DD`。 */
export function currentShanghaiDate(now: number | Date = Date.now()): string {
  return `${currentShanghaiMonth(now)}-${pad(
    new Date((typeof now === 'number' ? now : now.getTime()) + SHANGHAI_OFFSET_MS).getUTCDate(),
  )}`;
}

/** 展示文案：`11月26日 星期四`；无效值返回占位符 `—`。 */
export function formatMonthDayWeekday(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  return `${date.getUTCMonth() + 1}月${date.getUTCDate()}日 ${WEEKDAYS_LONG[date.getUTCDay()]}`;
}

/** 月份标题：`2026年11月`。 */
export function formatMonthLabel(month: string): string {
  const [year = '', monthOfYear = ''] = month.split('-');
  return year && monthOfYear ? `${year}年${Number(monthOfYear)}月` : '—';
}

/** 月份前后切换；越界返回原值，限制在 2000-01 至 2100-12。 */
export function shiftMonth(month: string, delta: number): string {
  const [year = 0, monthOfYear = 1] = month.split('-').map(Number);
  const date = new Date(Date.UTC(year, monthOfYear - 1 + delta, 1));
  const nextYear = date.getUTCFullYear();
  if (nextYear < 2000 || nextYear > 2100) {
    return month;
  }
  return `${nextYear}-${pad(date.getUTCMonth() + 1)}`;
}

/** 展示文案：按 Asia/Shanghai 输出 `YYYY/MM/DD`（不含时分与星期），无效值返回占位符 `—`。 */
export function formatYearDate(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  return `${date.getUTCFullYear()}/${pad(date.getUTCMonth() + 1)}/${pad(date.getUTCDate())}`;
}

/** 展示文案：按 Asia/Shanghai 输出 `YYYY/MM/DD 周X`，无效值返回占位符 `—`。 */
export function formatDateWithYear(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  const month = pad(date.getUTCMonth() + 1);
  const day = pad(date.getUTCDate());
  return `${date.getUTCFullYear()}/${month}/${day} ${WEEKDAYS[date.getUTCDay()]}`;
}

/** 展示文案：按 Asia/Shanghai 输出 `YYYY/MM/DD HH:mm`，无效值返回占位符 `—`。 */
export function formatDateTimeWithYear(value: string | null | undefined): string {
  const date = shanghaiDate(value);
  if (!date) {
    return '—';
  }
  const month = pad(date.getUTCMonth() + 1);
  const day = pad(date.getUTCDate());
  return `${date.getUTCFullYear()}/${month}/${day} ${pad(date.getUTCHours())}:${pad(date.getUTCMinutes())}`;
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

/** 展示文案：按 Asia/Shanghai 输出 `YYYY/MM/DD HH:mm`；无效值返回空串。 */
export function formatDateTime(value: string | null | undefined): string {
  const date = formatDateYmd(value);
  return date ? `${date.replace(/-/g, '/')} ${formatHourMinute(value)}` : '';
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
