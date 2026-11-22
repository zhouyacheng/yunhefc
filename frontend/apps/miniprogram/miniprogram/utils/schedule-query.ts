// 比赛列表筛选模型：与 Web frontend/apps/web/src/features/schedule/query.ts 口径一致；
// 小程序没有 URL，因此只保留表单/筛选/请求串，不移植 URL 解析与 month=all。
// 筛选值、归一化与编码在 match-query；本文件只负责表单转换与选项。
import { currentShanghaiMonth } from './datetime';
import {
  DEFAULT_PAGE_SIZE,
  filterQueryString,
  normalize,
  type ScheduleFilters,
} from './match-query';
import { MATCH_STATUS_TEXT, MATCH_TYPE_TEXT } from './presentation';

export { DEFAULT_PAGE_SIZE };
export type { ScheduleFilters };

const TYPE_CODES = new Set(Object.keys(MATCH_TYPE_TEXT).map(Number));
const STATUS_CODES = new Set(Object.keys(MATCH_STATUS_TEXT).map(Number));

/** 表单里的原始字符串；空字符串表示不筛选。 */
export interface ScheduleForm {
  type: string;
  venue: string;
  status: string;
  month: string;
  startDate: string;
  endDate: string;
}

export interface SelectOption {
  value: string;
  label: string;
}

export const TYPE_OPTIONS: readonly SelectOption[] = [
  { value: '', label: '全部类型' },
  ...Object.entries(MATCH_TYPE_TEXT).map(([value, label]) => ({ value, label })),
];

export const STATUS_OPTIONS: readonly SelectOption[] = [
  { value: '', label: '全部状态' },
  ...Object.entries(MATCH_STATUS_TEXT).map(([value, label]) => ({ value, label })),
];

/** 小程序没有 URL 持久化：默认态由本方与 `toScheduleForm` 表达，因此不移植 Web 的 `monthAll` 哨兵。 */
export function emptyScheduleFilters(): ScheduleFilters {
  return {
    type: null,
    venue: '',
    status: null,
    month: null,
    startDate: null,
    endDate: null,
  };
}

function parseCode(value: string, allowed: ReadonlySet<number>): number | null {
  if (!/^\d+$/.test(value)) {
    return null;
  }
  const code = Number(value);
  return allowed.has(code) ? code : null;
}

/** 默认态预填当前上海月份，其余为空；首屏由 emptyScheduleFilters 发起查询。 */
export function toScheduleForm(
  filters: ScheduleFilters,
  now: number | Date = Date.now(),
): ScheduleForm {
  return {
    type: filters.type === null ? '' : String(filters.type),
    venue: filters.venue,
    status: filters.status === null ? '' : String(filters.status),
    month: filters.month ?? (filters.startDate !== null ? '' : currentShanghaiMonth(now)),
    startDate: filters.startDate ?? '',
    endDate: filters.endDate ?? '',
  };
}

export function fromScheduleForm(form: ScheduleForm): ScheduleFilters {
  return normalize({
    type: parseCode(form.type, TYPE_CODES),
    venue: form.venue,
    status: parseCode(form.status, STATUS_CODES),
    month: form.month || null,
    startDate: form.startDate.trim() ? form.startDate.trim() : null,
    endDate: form.endDate.trim() ? form.endDate.trim() : null,
  });
}

/** 稳定、已编码的筛选串，作为 PagedFeed 的 key；编码实现见 match-query。 */
export const scheduleFeedKey = filterQueryString;

export function sameForm(a: ScheduleForm, b: ScheduleForm): boolean {
  return (
    a.type === b.type &&
    a.venue === b.venue &&
    a.status === b.status &&
    a.month === b.month &&
    a.startDate === b.startDate &&
    a.endDate === b.endDate
  );
}
