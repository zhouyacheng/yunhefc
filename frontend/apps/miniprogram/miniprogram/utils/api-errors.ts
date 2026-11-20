import { ApiError } from './request';

export const VERSION_CONFLICT = 40901;
export const DUPLICATE_REGISTRATION = 40902;
export const CAPACITY_FULL = 40903;
export const MATCH_STATE_CONFLICT = 40905;
export const GROUPING_MISMATCH = 42203;

/** 这些冲突说明服务端数据已变化，写操作后应静默重载。 */
export function shouldRefreshAfterConflict(error: unknown): boolean {
  return (
    error instanceof ApiError &&
    [VERSION_CONFLICT, DUPLICATE_REGISTRATION, CAPACITY_FULL, MATCH_STATE_CONFLICT].includes(
      error.code,
    )
  );
}

export function errorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message ? error.message : fallback;
}
