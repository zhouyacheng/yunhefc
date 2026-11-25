// MP10 活动筛选的唯一编码口径：范围/类型/状态/时间选项与 feed key 互转集中在此。
import type { ActivityStatus, ActivityType } from '../types/api';
import type { ActivityScope } from './api-client';
import { normalizeTimeRange, timeRangeEntries, timeRangeParams } from './time-filter';
import type { MonthRange } from './time-filter';

export interface ActivityFilters extends MonthRange {
  scope: ActivityScope;
  type: ActivityType | '';
  status: ActivityStatus | '';
}

export interface ActivityScopeOption {
  key: ActivityScope;
  label: string;
}

export interface ActivityFilterOption<T> {
  value: T | '';
  label: string;
}

/** MP10 不提供「历史」（P-116）；已结束活动=已结束+已取消（P-126），历史活动经「全部」查看。 */
export const ACTIVITY_SCOPES: ActivityScopeOption[] = [
  { key: 'latest', label: '最近活动' },
  { key: 'finished', label: '已结束活动' },
  { key: 'all', label: '全部' },
];

export const ACTIVITY_TYPE_OPTIONS: ActivityFilterOption<ActivityType>[] = [
  { value: '', label: '全部类型' },
  { value: 0, label: '聚餐' },
  { value: 1, label: '年会' },
  { value: 2, label: '其他' },
];

export const ACTIVITY_STATUS_OPTIONS: ActivityFilterOption<ActivityStatus>[] = [
  { value: '', label: '全部状态' },
  { value: 0, label: '报名中' },
  { value: 1, label: '报名截止' },
  { value: 2, label: '已结束' },
  { value: 3, label: '已取消' },
];

export const DEFAULT_ACTIVITY_FILTERS: ActivityFilters = {
  scope: 'latest',
  type: '',
  status: '',
  month: '',
  startDate: '',
  endDate: '',
};

/** 有效日期范围优先于月份；两者互斥，非法值回落为空。 */
function normalizeActivityTime(filters: ActivityFilters): ActivityFilters {
  const time = normalizeTimeRange(filters);
  return {
    ...filters,
    month: time.month ?? '',
    startDate: time.startDate ?? '',
    endDate: time.endDate ?? '',
  };
}

/** key = 仅含非空条件的查询串（如 `scope=latest&month=2026-11`），同时用作 PagedFeed 的 key。 */
export function activityFeedKey(filters: ActivityFilters): string {
  const normalized = normalizeActivityTime(filters);
  const entries = [`scope=${normalized.scope}`];
  if (normalized.type !== '') {
    entries.push(`type=${normalized.type}`);
  }
  if (normalized.status !== '') {
    entries.push(`status=${normalized.status}`);
  }
  entries.push(...timeRangeEntries(normalized));
  return entries.join('&');
}

export const DEFAULT_ACTIVITY_KEY = activityFeedKey(DEFAULT_ACTIVITY_FILTERS);

/** 范围选项下标 ↔ 值映射集中在此，页面不再硬编码选项顺序。 */
export function activityScopeAt(index: number): ActivityScope {
  return ACTIVITY_SCOPES[index]?.key ?? DEFAULT_ACTIVITY_FILTERS.scope;
}

export function activityScopeIndex(scope: ActivityScope): number {
  const index = ACTIVITY_SCOPES.findIndex((option) => option.key === scope);
  return index >= 0 ? index : 0;
}

/** 页面控件状态；typeIndex/statusIndex 对应下拉选项下标。 */
export interface ActivityFilterState {
  scope: ActivityScope;
  typeIndex: number;
  statusIndex: number;
  month: string;
  startDate: string;
  endDate: string;
}

export function activityFiltersFromState(state: ActivityFilterState): ActivityFilters {
  return {
    scope: state.scope,
    type: ACTIVITY_TYPE_OPTIONS[state.typeIndex]?.value ?? '',
    status: ACTIVITY_STATUS_OPTIONS[state.statusIndex]?.value ?? '',
    month: state.month,
    startDate: state.startDate,
    endDate: state.endDate,
  };
}

function parseScope(value: string): ActivityScope {
  return ACTIVITY_SCOPES.some((option) => option.key === value)
    ? (value as ActivityScope)
    : DEFAULT_ACTIVITY_FILTERS.scope;
}

function parseCode<T extends number>(value: string, options: { value: T | '' }[]): T | '' {
  if (value === '') {
    return '';
  }
  const code = Number(value);
  return options.some((option) => option.value === code) ? (code as T) : '';
}

function parseParams(key: string): Record<string, string> {
  const params: Record<string, string> = {};
  for (const part of key.split('&')) {
    const index = part.indexOf('=');
    if (index > 0) {
      params[part.slice(0, index)] = part.slice(index + 1);
    }
  }
  return params;
}

export function parseActivityFeedKey(key: string): ActivityFilters {
  const params = parseParams(key);
  return normalizeActivityTime({
    scope: parseScope(params.scope ?? ''),
    type: parseCode(params.type ?? '', ACTIVITY_TYPE_OPTIONS),
    status: parseCode(params.status ?? '', ACTIVITY_STATUS_OPTIONS),
    month: params.month ?? '',
    startDate: params.startDate ?? '',
    endDate: params.endDate ?? '',
  });
}

/** ACT-01 参数：空值不下发。 */
export function activityListQuery(filters: ActivityFilters): {
  scope: ActivityScope;
  type?: ActivityType;
  status?: ActivityStatus;
  month?: string;
  startDate?: string;
  endDate?: string;
} {
  const normalized = normalizeActivityTime(filters);
  const query: ReturnType<typeof activityListQuery> = {
    scope: normalized.scope,
    ...timeRangeParams(normalized),
  };
  if (normalized.type !== '') {
    query.type = normalized.type;
  }
  if (normalized.status !== '') {
    query.status = normalized.status;
  }
  return query;
}
