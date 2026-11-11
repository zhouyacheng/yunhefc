import { createApiClient } from '@yunhefc/api-client';

const basePath = import.meta.env.BASE_URL;

export const api = createApiClient({
  namespace: 'b',
  baseUrl: '/api/v1/b',
  basePath,
  loginPath: `${basePath}login`,
});
