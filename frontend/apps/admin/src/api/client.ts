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
