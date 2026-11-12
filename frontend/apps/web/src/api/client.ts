import { createApiClient } from '@yunhefc/api-client';

import { clearSessionHint } from '../stores/session-hint';

const basePath = import.meta.env.BASE_URL;

export const api = createApiClient({
  namespace: 'c',
  baseUrl: '/api/v1/c',
  basePath,
  loginPath: `${basePath}login`,
  onUnauthorized: (loginUrl) => {
    // 任何写请求的 40101 都会整页跳登录；同时清掉本地提示，避免下次公开页再探测。
    clearSessionHint();
    window.location.assign(loginUrl);
  },
});
