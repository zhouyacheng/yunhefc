/** 05 §2.3 错误码。 */
export const ERROR_CODES = {
  OK: 0,
  VALIDATION_FAILED: 40001,
  UNAUTHENTICATED: 40101,
  INVALID_CREDENTIALS: 40102,
  ACCOUNT_LOCKED: 40103,
  ACCOUNT_DISABLED: 40104,
  FORBIDDEN: 40301,
  ADMIN_SECTION_FORBIDDEN: 40302,
  ACCOUNT_PENDING: 40303,
  NOT_FOUND: 40401,
  VERSION_CONFLICT: 40901,
  DUPLICATE_JERSEY: 40904,
  MATCH_STATE_CONFLICT: 40905,
  IDEMPOTENCY_KEY_REUSED: 40906,
  PHONE_EXISTS: 40907,
  INVITE_UNAVAILABLE: 40910,
  GROUPING_FINGERPRINT_CONFLICT: 40911,
  LAST_ADMIN_PROTECTED: 40913,
  IDEMPOTENCY_IN_PROGRESS: 40915,
  RATE_LIMITED: 42901,
  BUSINESS_RULE_VIOLATION: 42201,
  CONFIRM_REQUIRED: 42202,
  GROUPING_MISMATCH: 42203,
  INTERNAL_ERROR: 50001,
} as const;

export interface ApiErrorInit {
  status: number;
  code: number;
  message: string;
  requestId?: string;
  data?: unknown;
  method?: string;
  url?: string;
  cause?: unknown;
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: number;
  readonly requestId?: string;
  readonly data?: unknown;
  readonly method?: string;
  readonly url?: string;

  constructor(init: ApiErrorInit) {
    super(init.message, init.cause === undefined ? undefined : { cause: init.cause });
    this.name = 'ApiError';
    this.status = init.status;
    this.code = init.code;
    this.requestId = init.requestId;
    this.data = init.data;
    this.method = init.method;
    this.url = init.url;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function isUnauthorized(error: unknown): boolean {
  return isApiError(error) && error.code === ERROR_CODES.UNAUTHENTICATED;
}

/** 40302：后台分区准入失败，由路由守卫渲染 403。 */
export function isSectionForbidden(error: unknown): boolean {
  return isApiError(error) && error.code === ERROR_CODES.ADMIN_SECTION_FORBIDDEN;
}

/** 40301：对象级/方法级权限不足，由页面行内处理。 */
export function isObjectForbidden(error: unknown): boolean {
  return isApiError(error) && error.code === ERROR_CODES.FORBIDDEN;
}

/** 40901：version 乐观锁冲突。 */
export function isVersionConflict(error: unknown): boolean {
  return isApiError(error) && error.code === ERROR_CODES.VERSION_CONFLICT;
}
