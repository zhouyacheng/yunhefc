const dateTimeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  hour12: false,
});

const dateFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: '2-digit',
  day: '2-digit',
  weekday: 'short',
});

const timeFormatter = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
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

/** 接口返回 UTC，展示统一按 Asia/Shanghai。 */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateTimeFormatter.format(date);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : dateFormatter.format(date);
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
