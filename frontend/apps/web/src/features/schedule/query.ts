import type { QueryParams } from '@yunhefc/api-client';
import type { MatchStatus, MatchType } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from '@yunhefc/shared';

export const DEFAULT_PAGE_SIZE = 10;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
const DATE_PATTERN = /^\d{4}-(0[1-9]|1[0-2])-(0[1-9]|[12]\d|3[01])$/;
/** URL 中表示“明确不限月份”的哨兵；不会下发到 MAT-02。 */
export const MONTH_ALL = 'all';
// 与下拉选项共用同一份枚举键，避免新增状态时前端校验与选项漂移。
const TYPE_CODES = new Set(Object.keys(MATCH_TYPE_TEXT).map(Number));
const STATUS_CODES = new Set(Object.keys(MATCH_STATUS_TEXT).map(Number));
const SHANGHAI_MONTH = new Intl.DateTimeFormat('en-US', {
  timeZone: 'Asia/Shanghai',
  year: 'numeric',
  month: '2-digit',
});

/** 已生效的筛选条件；自定义日期范围与 month 互斥。 */
export interface ScheduleFilters {
  type: MatchType | null;
  venue: string;
  status: MatchStatus | null;
  /** YYYY-MM；null 表示不按月份筛选。 */
  month: string | null;
  /** true 表示“明确不限月份”（URL month=all 或已选自定义范围）；false 且 month=null 为默认态。 */
  monthAll: boolean;
  /** YYYY-MM-DD，与 endDate 成对。 */
  startDate: string | null;
  endDate: string | null;
}

export interface ScheduleQuery extends ScheduleFilters {
  page: number;
  pageSize: number;
}

/** 表单里的原始字符串；空字符串表示不筛选。 */
export interface ScheduleForm {
  type: string;
  venue: string;
  status: string;
  month: string;
  startDate: string;
  endDate: string;
}

function firstValue(value: unknown): string {
  const raw = Array.isArray(value) ? value[0] : value;
  return typeof raw === 'string' ? raw.trim() : '';
}

function parseInteger(value: unknown): number | null {
  const text = firstValue(value);
  return /^\d+$/.test(text) ? Number(text) : null;
}

function parseEnum(value: unknown, allowed: ReadonlySet<number>): number | null {
  const code = parseInteger(value);
  return code !== null && allowed.has(code) ? code : null;
}

function parseMonth(value: unknown): string | null {
  const text = firstValue(value);
  return MONTH_PATTERN.test(text) ? text : null;
}

/** 严格校验 YYYY-MM-DD，并用 UTC 回读拒绝 2026-02-30 之类的非法日期。 */
function parseDate(value: unknown): string | null {
  const text = firstValue(value);
  if (!DATE_PATTERN.test(text)) {
    return null;
  }
  const [year = 0, month = 0, day = 0] = text.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 1, day));
  return date.getUTCFullYear() === year &&
    date.getUTCMonth() === month - 1 &&
    date.getUTCDate() === day
    ? text
    : null;
}

/** 当前年月（Asia/Shanghai），避免浏览器本地时区在月末预填错月。 */
export function currentShanghaiMonth(now = new Date()): string {
  const parts = SHANGHAI_MONTH.formatToParts(now);
  const year = parts.find((part) => part.type === 'year')?.value ?? '';
  const month = parts.find((part) => part.type === 'month')?.value ?? '';
  return `${year}-${month}`;
}

function parsePageSize(value: unknown): number {
  const size = parseInteger(value);
  return size !== null && isSupportedPageSize(size) ? size : DEFAULT_PAGE_SIZE;
}

function isSupportedPageSize(value: number): boolean {
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(value);
}

/** 统一时间筛选不变量：有效范围优先；其次月份；两者都无时仅保留“明确不限”标记。 */
function normalizeFilters(filters: ScheduleFilters): ScheduleFilters {
  const startDate = parseDate(filters.startDate);
  const endDate = parseDate(filters.endDate);
  const hasRange = startDate !== null && endDate !== null && startDate <= endDate;
  if (hasRange) {
    return {
      type: filters.type,
      venue: filters.venue.trim(),
      status: filters.status,
      month: null,
      monthAll: true,
      startDate,
      endDate,
    };
  }
  if (filters.month) {
    return {
      type: filters.type,
      venue: filters.venue.trim(),
      status: filters.status,
      month: filters.month,
      monthAll: false,
      startDate: null,
      endDate: null,
    };
  }
  return {
    type: filters.type,
    venue: filters.venue.trim(),
    status: filters.status,
    month: null,
    monthAll: filters.monthAll,
    startDate: null,
    endDate: null,
  };
}

/** 类型/地点/状态等非时间筛选键值。 */
function baseFilterEntries(filters: ScheduleFilters): Array<[string, string | number]> {
  const normalized = normalizeFilters(filters);
  const entries: Array<[string, string | number]> = [];
  if (normalized.type !== null) {
    entries.push(['type', normalized.type]);
  }
  if (normalized.venue) {
    entries.push(['venue', normalized.venue]);
  }
  if (normalized.status !== null) {
    entries.push(['status', normalized.status]);
  }
  return entries;
}

/** MAT-02 请求筛选键值；month=all 与默认态都不下发时间参数。 */
function requestFilterEntries(filters: ScheduleFilters): Array<[string, string | number]> {
  const normalized = normalizeFilters(filters);
  const entries = baseFilterEntries(normalized);
  if (normalized.startDate !== null && normalized.endDate !== null) {
    entries.push(['startDate', normalized.startDate], ['endDate', normalized.endDate]);
  } else if (normalized.month) {
    entries.push(['month', normalized.month]);
  }
  return entries;
}

/** 从 route.query 解析；非法值一律忽略并回退默认值。 */
export function parseScheduleQuery(raw: Record<string, unknown>): ScheduleQuery {
  const startDate = parseDate(raw.startDate);
  const endDate = parseDate(raw.endDate);
  const hasRange = startDate !== null && endDate !== null && startDate <= endDate;
  const monthRaw = firstValue(raw.month);
  const month = hasRange ? null : parseMonth(monthRaw);
  const filters = normalizeFilters({
    type: parseEnum(raw.type, TYPE_CODES) as MatchType | null,
    venue: firstValue(raw.venue),
    status: parseEnum(raw.status, STATUS_CODES) as MatchStatus | null,
    month,
    monthAll: hasRange || (!month && monthRaw === MONTH_ALL),
    startDate: hasRange ? startDate : null,
    endDate: hasRange ? endDate : null,
  });
  return {
    ...filters,
    page: Math.max(1, parseInteger(raw.page) ?? 1),
    pageSize: parsePageSize(raw.pageSize),
  };
}

/** 只序列化非默认值，避免空参数污染 URL；month=all 仅存在于前端 URL。 */
export function serializeScheduleQuery(query: ScheduleQuery): Record<string, string> {
  const normalized = normalizeFilters(query);
  const result = Object.fromEntries(
    requestFilterEntries(normalized).map(([key, value]) => [key, String(value)]),
  );
  if (normalized.startDate === null && normalized.month === null && normalized.monthAll) {
    result.month = MONTH_ALL;
  }
  if (query.page !== 1) {
    result.page = String(query.page);
  }
  if (query.pageSize !== DEFAULT_PAGE_SIZE) {
    result.pageSize = String(query.pageSize);
  }
  return result;
}

/** MAT-02 固定 scope/sort，只发送非空筛选。 */
export function toMatchListQuery(query: ScheduleQuery): QueryParams {
  const params: QueryParams = {
    scope: 'all',
    sort: 'startAt:desc',
    page: query.page,
    pageSize: query.pageSize,
  };
  for (const [key, value] of requestFilterEntries(query)) {
    params[key] = value;
  }
  return params;
}

/** 默认态预填当月；明确不限或已选范围时月份留空。 */
export function toScheduleForm(query: ScheduleFilters, now = new Date()): ScheduleForm {
  return {
    type: query.type === null ? '' : String(query.type),
    venue: query.venue,
    status: query.status === null ? '' : String(query.status),
    month:
      query.month ?? (query.monthAll || query.startDate !== null ? '' : currentShanghaiMonth(now)),
    startDate: query.startDate ?? '',
    endDate: query.endDate ?? '',
  };
}

export function fromScheduleForm(form: ScheduleForm): ScheduleFilters {
  const startDate = parseDate(form.startDate);
  const endDate = parseDate(form.endDate);
  const hasRange = startDate !== null && endDate !== null && startDate <= endDate;
  const month = hasRange ? null : parseMonth(form.month);
  return normalizeFilters({
    type: parseEnum(form.type, TYPE_CODES) as MatchType | null,
    venue: form.venue,
    status: parseEnum(form.status, STATUS_CODES) as MatchStatus | null,
    month,
    monthAll: hasRange || month === null,
    startDate: hasRange ? startDate : null,
    endDate: hasRange ? endDate : null,
  });
}

export function withScheduleFilters(query: ScheduleQuery, filters: ScheduleFilters): ScheduleQuery {
  return { ...normalizeFilters(filters), page: 1, pageSize: query.pageSize };
}

export function withPage(query: ScheduleQuery, page: number): ScheduleQuery {
  return { ...query, page: Number.isFinite(page) ? Math.max(1, Math.trunc(page)) : 1 };
}

/** 改页大小会重排列表，回到第 1 页。 */
export function withPageSize(query: ScheduleQuery, pageSize: number): ScheduleQuery {
  return {
    ...query,
    page: 1,
    pageSize: isSupportedPageSize(pageSize) ? pageSize : DEFAULT_PAGE_SIZE,
  };
}

/** 清空全部筛选并回到第 1 页；页大小不属于筛选条件，保持不变。 */
export function resetScheduleQuery(pageSize = DEFAULT_PAGE_SIZE): ScheduleQuery {
  return {
    type: null,
    venue: '',
    status: null,
    month: null,
    monthAll: false,
    startDate: null,
    endDate: null,
    page: 1,
    pageSize: isSupportedPageSize(pageSize) ? pageSize : DEFAULT_PAGE_SIZE,
  };
}
