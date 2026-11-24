import { createApiClient } from '@yunhefc/api-client';

const basePath = import.meta.env.BASE_URL;

export const api = createApiClient({
  namespace: 'b',
  baseUrl: '/api/v1/b',
  basePath,
  loginPath: `${basePath}login`,
});

/** 公开读接口（EVT-01）在 /c 命名空间；同域后必须 omit，避免误带 C 端会话。 */
export const publicApi = createApiClient({
  namespace: 'c',
  baseUrl: '/api/v1/c',
  credentials: 'omit',
  basePath,
  loginPath: `${basePath}login`,
});

/**
 * 需要当前后台会话的 C 端只读接口（如 LED-08）：共享同一 Session Cookie，
 * 未登录时不跳 C 端登录页，由调用方按普通错误处理。
 */
export const sessionReadApi = createApiClient({
  namespace: 'c',
  baseUrl: '/api/v1/c',
  basePath,
  loginPath: `${basePath}login`,
  onUnauthorized: () => undefined,
});
