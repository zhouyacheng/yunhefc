import { createApiClient } from '@yunhefc/api-client';

const basePath = import.meta.env.BASE_URL;

export const api = createApiClient({
  namespace: 'c',
  baseUrl: '/api/v1/c',
  basePath,
  loginPath: `${basePath}login`,
});
