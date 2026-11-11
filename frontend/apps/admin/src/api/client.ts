import { createApiClient } from '@yunhefc/api-client';

const basePath = import.meta.env.BASE_URL;

export const api = createApiClient({
  namespace: 'b',
  baseUrl: '/api/v1/b',
  basePath,
  loginPath: `${basePath}login`,
});

/** 公开读接口（EVT-01）在 /c 命名空间；后台页面复用同一会话读取。 */
export const publicApi = createApiClient({
  namespace: 'c',
  baseUrl: '/api/v1/c',
  basePath,
  loginPath: `${basePath}login`,
});
