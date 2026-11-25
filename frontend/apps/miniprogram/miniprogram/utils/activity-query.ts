// MP10 活动筛选的唯一编码口径：范围/类型/状态选项与 feed key 互转集中在此。
import type { ActivityStatus, ActivityType } from '../types/api';
import type { ActivityScope } from './api-client';

export interface ActivityFilters {
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

/** MP10 只提供最近活动/全部（P-116）；历史活动经「全部」查看，服务端仍支持 scope=history。 */
export const ACTIVITY_SCOPES: ActivityScopeOption[] = [
  { key: 'latest', label: '最近活动' },
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
};

/** key = `scope|type|status`。 */
export function activityFeedKey(filters: ActivityFilters): string {
  return `${filters.scope}|${filters.type}|${filters.status}`;
}

export const DEFAULT_ACTIVITY_KEY = activityFeedKey(DEFAULT_ACTIVITY_FILTERS);

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

export function parseActivityFeedKey(key: string): ActivityFilters {
  const [scope, type, status] = key.split('|');
  return {
    scope: parseScope(scope),
    type: parseCode(type, ACTIVITY_TYPE_OPTIONS),
    status: parseCode(status, ACTIVITY_STATUS_OPTIONS),
  };
}

/** ACT-01 参数：空值不下发。 */
export function activityListQuery(filters: ActivityFilters): {
  scope: ActivityScope;
  type?: ActivityType;
  status?: ActivityStatus;
} {
  return {
    scope: filters.scope,
    type: filters.type === '' ? undefined : filters.type,
    status: filters.status === '' ? undefined : filters.status,
  };
}
