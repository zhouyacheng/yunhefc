import type { QueryParams } from '@yunhefc/api-client';
import type { MatchStatus, MatchType } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from '@yunhefc/shared';

export const DEFAULT_PAGE_SIZE = 10;
export const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

const SEASON_PATTERN = /^\d{4}$/;
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;
// 与下拉选项共用同一份枚举键，避免新增状态时前端校验与选项漂移。
const TYPE_CODES = new Set(Object.keys(MATCH_TYPE_TEXT).map(Number));
const STATUS_CODES = new Set(Object.keys(MATCH_STATUS_TEXT).map(Number));

/** 已生效的筛选条件；month 与 season 互斥。 */
export interface ScheduleFilters {
  type: MatchType | null;
  venue: string;
  status: MatchStatus | null;
  season: string | null;
  month: string | null;
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
  season: string;
  month: string;
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

function parseSeason(value: unknown): string | null {
  const text = firstValue(value);
  return SEASON_PATTERN.test(text) ? text : null;
}

function parseMonth(value: unknown): string | null {
  const text = firstValue(value);
  return MONTH_PATTERN.test(text) ? text : null;
}

function parsePageSize(value: unknown): number {
  const size = parseInteger(value);
  return size !== null && isSupportedPageSize(size) ? size : DEFAULT_PAGE_SIZE;
}

function isSupportedPageSize(value: number): boolean {
  return (PAGE_SIZE_OPTIONS as readonly number[]).includes(value);
}

function normalizeFilters(filters: ScheduleFilters): ScheduleFilters {
  return {
    type: filters.type,
    venue: filters.venue.trim(),
    status: filters.status,
    season: filters.month ? null : filters.season,
    month: filters.month,
  };
}

/** URL 与请求共用的筛选键值；month 优先，空值不出现。 */
function activeFilterEntries(filters: ScheduleFilters): Array<[string, string | number]> {
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
  if (normalized.month) {
    entries.push(['month', normalized.month]);
  } else if (normalized.season) {
    entries.push(['season', normalized.season]);
  }
  return entries;
}

/** 从 route.query 解析；非法值一律忽略并回退默认值。 */
export function parseScheduleQuery(raw: Record<string, unknown>): ScheduleQuery {
  const month = parseMonth(raw.month);
  return {
    type: parseEnum(raw.type, TYPE_CODES) as MatchType | null,
    venue: firstValue(raw.venue),
    status: parseEnum(raw.status, STATUS_CODES) as MatchStatus | null,
    season: month ? null : parseSeason(raw.season),
    month,
    page: Math.max(1, parseInteger(raw.page) ?? 1),
    pageSize: parsePageSize(raw.pageSize),
  };
}

/** 只序列化非默认值，避免空参数污染 URL。 */
export function serializeScheduleQuery(query: ScheduleQuery): Record<string, string> {
  const result = Object.fromEntries(
    activeFilterEntries(query).map(([key, value]) => [key, String(value)]),
  );
  if (query.page !== 1) {
    result.page = String(query.page);
  }
  if (query.pageSize !== DEFAULT_PAGE_SIZE) {
    result.pageSize = String(query.pageSize);
  }
  return result;
}

/** MAT-02 固定 scope/sort，只发送非空筛选；month 优先于 season。 */
export function toMatchListQuery(query: ScheduleQuery): QueryParams {
  const params: QueryParams = {
    scope: 'all',
    sort: 'startAt:desc',
    page: query.page,
    pageSize: query.pageSize,
  };
  for (const [key, value] of activeFilterEntries(query)) {
    params[key] = value;
  }
  return params;
}

export function toScheduleForm(query: ScheduleFilters): ScheduleForm {
  return {
    type: query.type === null ? '' : String(query.type),
    venue: query.venue,
    status: query.status === null ? '' : String(query.status),
    season: query.season ?? '',
    month: query.month ?? '',
  };
}

export function fromScheduleForm(form: ScheduleForm): ScheduleFilters {
  return normalizeFilters({
    type: parseEnum(form.type, TYPE_CODES) as MatchType | null,
    venue: form.venue,
    status: parseEnum(form.status, STATUS_CODES) as MatchStatus | null,
    season: parseSeason(form.season),
    month: parseMonth(form.month),
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
    season: null,
    month: null,
    page: 1,
    pageSize: isSupportedPageSize(pageSize) ? pageSize : DEFAULT_PAGE_SIZE,
  };
}
