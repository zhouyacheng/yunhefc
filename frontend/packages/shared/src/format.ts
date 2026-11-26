const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

const yearDateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const monthDayLongFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: 'long',
  day: 'numeric',
});

const weekdayFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  weekday: 'short',
});

const weekdayLongFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  weekday: 'long',
});

const dayKeyFormatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

const monthDayFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: '2-digit',
  day: '2-digit',
});

/** 接口返回 ISO-8601（含 +08:00 偏移），展示统一按 Asia/Shanghai。 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date);
}

/** 月日与星期：11/18 周三。 */
export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return `${monthDayFormatter.format(date)} ${weekdayFormatter.format(date)}`;
}

/** 月日、星期与开始时刻：12月3日 周四 20:00。 */
export function formatDayStartTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return `${monthDayLongFormatter.format(date)} ${weekdayFormatter.format(date)} ${timeFormatter.format(date)}`;
}

/** 月日、星期与时段：12月3日 周四 20:00–22:00；跨天 22:00–12/02 02:00。 */
export function formatDayTimeRange(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string {
  const head = formatDayStartTime(startAt);
  if (head === '—' || !endAt) {
    return head;
  }
  const start = new Date(startAt as string);
  const end = new Date(endAt);
  if (Number.isNaN(end.getTime()) || end.getTime() < start.getTime()) {
    return head;
  }
  const endText =
    dayKeyFormatter.format(start) === dayKeyFormatter.format(end)
      ? timeFormatter.format(end)
      : `${monthDayFormatter.format(end)} ${timeFormatter.format(end)}`;
  return `${head}–${endText}`;
}

/** 日期键：YYYY-MM-DD（Asia/Shanghai），用于流水按日分组。 */
export function formatDayKey(value: string | null | undefined): string {
  if (!value) {
    return '';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : dayKeyFormatter.format(date);
}

/** 月日与完整星期：11月26日 星期四。 */
export function formatMonthDayWeekday(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return `${monthDayLongFormatter.format(date)} ${weekdayLongFormatter.format(date)}`;
}

/** 时刻：08:06（24 小时制）。 */
export function formatTimeOfDay(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : timeFormatter.format(date);
}

/** 仅日期：2026/12/02。 */
export function formatYearDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : yearDateFormatter.format(date);
}

/** 带年份与星期：2026/12/02 周三。 */
export function formatDateWithYear(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return `${yearDateFormatter.format(date)} ${weekdayFormatter.format(date)}`;
}

/** 比赛时段：同日 19:30–21:30；跨天 22:00–12/02 02:00。 */
export function formatTimeRange(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string {
  if (!startAt) {
    return '—';
  }
  const start = new Date(startAt);
  if (Number.isNaN(start.getTime())) {
    return '—';
  }
  const startText = timeFormatter.format(start);
  if (!endAt) {
    return startText;
  }
  const end = new Date(endAt);
  if (Number.isNaN(end.getTime()) || end.getTime() < start.getTime()) {
    return startText;
  }
  const endText = timeFormatter.format(end);
  if (dayKeyFormatter.format(start) === dayKeyFormatter.format(end)) {
    return `${startText}–${endText}`;
  }
  return `${startText}–${monthDayFormatter.format(end)} ${endText}`;
}

/** 同日：YYYY/MM/DD HH:mm ~ HH:mm；跨天：YYYY/MM/DD HH:mm ~ MM/DD HH:mm。 */
export function formatDateTimeRange(
  startAt: string | null | undefined,
  endAt: string | null | undefined,
): string {
  if (!startAt) {
    return '—';
  }
  const start = new Date(startAt);
  if (Number.isNaN(start.getTime())) {
    return '—';
  }
  const startText = formatDateTime(startAt);
  if (!endAt) {
    return startText;
  }
  const end = new Date(endAt);
  if (Number.isNaN(end.getTime()) || end.getTime() < start.getTime()) {
    return startText;
  }
  if (dayKeyFormatter.format(start) === dayKeyFormatter.format(end)) {
    return `${startText} ~ ${timeFormatter.format(end)}`;
  }
  return `${startText} ~ ${monthDayFormatter.format(end)} ${timeFormatter.format(end)}`;
}

function groupDigits(value: string): string {
  return value.replace(/\B(?=(\d{3})+(?!\d))/g, ',');
}

/** 金额展示：固定两位小数并千分位，不带货币符号。 */
export function formatAmount(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const [integer = '0', fraction = '00'] = Math.abs(value).toFixed(2).split('.');
  return `${groupDigits(integer)}.${fraction}`;
}

/** 金额展示：负数用 U+2212，统一为 −¥1,234.50。 */
export function formatMoney(value: number): string {
  if (!Number.isFinite(value)) {
    return '—';
  }
  const sign = value < 0 ? '−' : '';
  return `${sign}¥${formatAmount(value)}`;
}
