import type { QueryParams } from '@yunhefc/api-client';
import type { ActivityStatus, ActivityType } from '@yunhefc/shared';

import { ACTIVITY_STATUS_TEXT, ACTIVITY_TYPE_TEXT } from './presentation';

export type ActivityScope = 'latest' | 'finished' | 'all';

export const DEFAULT_ACTIVITY_PAGE_SIZE = 10;
export const ACTIVITY_PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

export interface ActivityFilters {
  scope: ActivityScope;
  type: ActivityType | null;
  status: ActivityStatus | null;
}

export interface ActivityQuery extends ActivityFilters {
  page: number;
  pageSize: number;
}

/** 表单里的原始字符串；空字符串表示不筛选。 */
export interface ActivityForm {
  scope: string;
  type: string;
  status: string;
}

/** C11 不提供「历史」范围（P-127），历史活动经「全部」查看。 */
export const ACTIVITY_SCOPE_OPTIONS: ReadonlyArray<{ value: ActivityScope; label: string }> = [
  { value: 'latest', label: '最近活动' },
  { value: 'finished', label: '已结束活动' },
  { value: 'all', label: '全部' },
];

export const ACTIVITY_TYPE_OPTIONS: ReadonlyArray<{ value: ActivityType; label: string }> = (
  [0, 1, 2] as const
).map((value) => ({ value, label: ACTIVITY_TYPE_TEXT[value] ?? '' }));

export const ACTIVITY_STATUS_OPTIONS: ReadonlyArray<{ value: ActivityStatus; label: string }> = (
  [0, 1, 2, 3] as const
).map((value) => ({ value, label: ACTIVITY_STATUS_TEXT[value] ?? '' }));

const TYPE_CODES = new Set(ACTIVITY_TYPE_OPTIONS.map((option) => option.value));
const STATUS_CODES = new Set(ACTIVITY_STATUS_OPTIONS.map((option) => option.value));
const SCOPES = new Set<ActivityScope>(['latest', 'finished', 'all']);

export function parseActivityScope(value: string): ActivityScope {
  return SCOPES.has(value as ActivityScope) ? (value as ActivityScope) : 'all';
}

export function parseActivityType(value: string): ActivityType | null {
  if (value === '') {
    return null;
  }
  const code = Number(value);
  return TYPE_CODES.has(code as ActivityType) ? (code as ActivityType) : null;
}

export function parseActivityStatus(value: string): ActivityStatus | null {
  if (value === '') {
    return null;
  }
  const code = Number(value);
  return STATUS_CODES.has(code as ActivityStatus) ? (code as ActivityStatus) : null;
}

export function fromActivityForm(form: ActivityForm): ActivityFilters {
  return {
    scope: parseActivityScope(form.scope),
    type: parseActivityType(form.type),
    status: parseActivityStatus(form.status),
  };
}

function isSupportedPageSize(value: number): value is (typeof ACTIVITY_PAGE_SIZE_OPTIONS)[number] {
  return (ACTIVITY_PAGE_SIZE_OPTIONS as readonly number[]).includes(value);
}

export function normalizeActivityPageSize(value: number): number {
  return isSupportedPageSize(value) ? value : DEFAULT_ACTIVITY_PAGE_SIZE;
}

/** ACT-01 查询参数；「最近活动」同为分页列表，仅由服务端限定报名中。 */
export function toActivityRequest(query: ActivityQuery): QueryParams {
  const params: QueryParams = { scope: query.scope };
  if (query.type !== null) {
    params.type = query.type;
  }
  if (query.status !== null) {
    params.status = query.status;
  }
  params.page = query.page;
  params.pageSize = query.pageSize;
  return params;
}
