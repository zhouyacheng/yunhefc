/** 05 §2.1 统一响应 envelope。 */
export interface ApiEnvelope<T> {
  code: number;
  message: string;
  data: T;
  requestId: string;
}

/** 05 §2.2 逻辑分页结构。 */
export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export function isPage<T>(value: unknown): value is Page<T> {
  if (value === null || typeof value !== 'object') {
    return false;
  }
  const candidate = value as Partial<Page<T>>;
  return (
    Array.isArray(candidate.items) &&
    typeof candidate.total === 'number' &&
    typeof candidate.page === 'number' &&
    typeof candidate.pageSize === 'number' &&
    typeof candidate.totalPages === 'number'
  );
}

export function emptyPage<T>(pageSize = 10): Page<T> {
  return { items: [], total: 0, page: 1, pageSize, totalPages: 0 };
}

/** 读取 05 §2.8 的 `<field>Label` 只读派生字段。 */
export function readLabel(item: object | null | undefined, field: string): string | undefined {
  if (!item) {
    return undefined;
  }
  const label = (item as Record<string, unknown>)[`${field}Label`];
  return typeof label === 'string' ? label : undefined;
}
