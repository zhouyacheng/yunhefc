import { describe, expect, it, vi } from 'vitest';

import {
  ApiError,
  ERROR_CODES,
  createApiClient,
  isApiError,
  isObjectForbidden,
  isSectionForbidden,
  isUnauthorized,
  isVersionConflict,
  newIdempotencyKey,
  safeRedirectPath,
} from './index';

type Handler = (url: string, init: RequestInit, call: number) => Promise<Response>;

function json(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json', ...headers },
  });
}

function envelope<T>(data: T, requestId = 'req-1') {
  return { code: 0, message: 'success', data, requestId };
}

function harness(handler: Handler, options: { getCurrentPath?: () => string } = {}) {
  const calls: Array<{ url: string; init: RequestInit }> = [];
  const fetchMock: typeof fetch = async (input, init = {}) => {
    const url = String(input);
    calls.push({ url, init });
    return handler(url, init, calls.length);
  };
  const onUnauthorized = vi.fn();
  const client = createApiClient({
    namespace: 'c',
    fetch: fetchMock,
    onUnauthorized,
    getCurrentPath: options.getCurrentPath ?? (() => '/matches?page=2'),
  });
  return { client, calls, onUnauthorized };
}

describe('envelope', () => {
  it('unwraps data and sends credentials', async () => {
    const payload = { items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 };
    const { client, calls } = harness(async () => json(envelope(payload)));

    await expect(client.get('/matches')).resolves.toEqual(payload);
    expect(calls).toHaveLength(1);
    expect(calls[0]?.url).toBe('/api/v1/c/matches');
    expect(calls[0]?.init.credentials).toBe('include');
  });

  it('serializes query parameters and drops empty values', async () => {
    const { client, calls } = harness(async () => json(envelope(null)));

    await client.get('/matches', {
      query: { page: 2, pageSize: 20, status: 0, scope: undefined, venue: null, sort: '' },
    });

    expect(calls[0]?.url).toBe('/api/v1/c/matches?page=2&pageSize=20&status=0');
  });

  it('throws ApiError with status, code, message and requestId', async () => {
    const { client } = harness(async () =>
      json({ code: 42201, message: '业务规则不满足', data: null, requestId: 'req-9' }, 422),
    );

    const error = await client.get('/matches').catch((caught: unknown) => caught);

    expect(error).toBeInstanceOf(ApiError);
    expect(isApiError(error)).toBe(true);
    expect(error).toMatchObject({
      status: 422,
      code: 42201,
      message: '业务规则不满足',
      requestId: 'req-9',
    });
  });

  it('falls back to the X-Request-Id header when the body omits requestId', async () => {
    const { client, calls } = harness(async () =>
      json({ code: 50001, message: '服务端错误', data: null }, 500, {
        'X-Request-Id': 'req-header',
      }),
    );
    const error = await client.get('/matches').catch((caught: unknown) => caught);

    expect(calls).toHaveLength(1);
    expect(error).toMatchObject({ code: 50001, requestId: 'req-header' });
  });
});

describe('401 handling', () => {
  it('clears the session token and redirects to login with a safe redirect', async () => {
    const { client, calls, onUnauthorized } = harness(async (url) => {
      if (url.endsWith('/auth/csrf')) {
        return json(envelope({ token: 'fresh', headerName: 'X-XSRF-TOKEN' }));
      }
      if (url.endsWith('/matches')) {
        return json({ code: 40101, message: '未登录或会话失效', data: null, requestId: 'r' }, 401);
      }
      return json(envelope(null));
    });

    client.setCsrfToken('stale');
    await expect(client.get('/matches')).rejects.toBeInstanceOf(ApiError);

    expect(onUnauthorized).toHaveBeenCalledWith('/login?redirect=%2Fmatches%3Fpage%3D2');

    await client.post('/anything', { ok: true });
    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/c/matches',
      '/api/v1/c/auth/csrf',
      '/api/v1/c/anything',
    ]);
  });

  it('does not redirect for credential failures', async () => {
    const { client, onUnauthorized } = harness(async () =>
      json({ code: 40102, message: '手机号或密码错误', data: null, requestId: 'r' }, 401),
    );

    const error = await client.post('/auth/login', {}).catch((caught: unknown) => caught);

    expect(isUnauthorized(error)).toBe(false);
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('treats an empty 401 body as unauthenticated', async () => {
    const { client, onUnauthorized } = harness(async () => new Response(null, { status: 401 }));

    const error = await client.get('/matches').catch((caught: unknown) => caught);

    expect(isUnauthorized(error)).toBe(true);
    expect(onUnauthorized).toHaveBeenCalledWith('/login?redirect=%2Fmatches%3Fpage%3D2');
  });

  it('does not report an empty error body as success', async () => {
    const { client } = harness(
      async () => new Response(null, { status: 500, statusText: 'Internal Server Error' }),
    );

    const error = await client.get('/matches').catch((caught: unknown) => caught);

    expect(error).toMatchObject({
      status: 500,
      code: 500,
      message: 'Internal Server Error',
    });
  });
});

describe('CSRF', () => {
  it('fetches the token once before the first write and reuses it', async () => {
    const { client, calls } = harness(async (url) => {
      if (url.endsWith('/auth/csrf')) {
        return json(envelope({ token: 'csrf-1', headerName: 'X-XSRF-TOKEN' }));
      }
      return json(envelope({ ok: true }));
    });

    await client.post('/auth/login', { phone: '1', password: '2' });
    await client.post('/anything', {});

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/c/auth/csrf',
      '/api/v1/c/auth/login',
      '/api/v1/c/anything',
    ]);
    expect(new Headers(calls[1]?.init.headers).get('X-XSRF-TOKEN')).toBe('csrf-1');
    expect(new Headers(calls[2]?.init.headers).get('X-XSRF-TOKEN')).toBe('csrf-1');
  });

  it('retries a rejected write once after fetching a missing token', async () => {
    let csrfAttempts = 0;
    const { client, calls } = harness(async (url) => {
      if (url.endsWith('/auth/csrf')) {
        csrfAttempts += 1;
        if (csrfAttempts === 1) {
          return json({ code: 50001, message: '暂时不可用', data: null }, 500);
        }
        return json(envelope({ token: 'csrf-late', headerName: 'X-XSRF-TOKEN' }));
      }
      const token = new Headers(calls.at(-1)?.init.headers).get('X-XSRF-TOKEN');
      if (!token) {
        return json({ code: 40301, message: '无权限', data: null }, 403);
      }
      return json(envelope({ ok: true }));
    });

    await expect(client.post('/anything', {})).resolves.toEqual({ ok: true });

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/c/auth/csrf',
      '/api/v1/c/anything',
      '/api/v1/c/auth/csrf',
      '/api/v1/c/anything',
    ]);
  });

  it('refreshes a stale token once and retries the write', async () => {
    const { client, calls } = harness(async (url) => {
      if (url.endsWith('/auth/csrf')) {
        return json(envelope({ token: 'csrf-fresh', headerName: 'X-XSRF-TOKEN' }));
      }
      const token = new Headers(calls.at(-1)?.init.headers).get('X-XSRF-TOKEN');
      return token === 'csrf-stale'
        ? json({ code: 40301, message: '无权限', data: null }, 403)
        : json(envelope({ ok: true }));
    });

    client.setCsrfToken('csrf-stale');
    await expect(client.post('/anything', {})).resolves.toEqual({ ok: true });

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/c/anything',
      '/api/v1/c/auth/csrf',
      '/api/v1/c/anything',
    ]);
    expect(new Headers(calls[2]?.init.headers).get('X-XSRF-TOKEN')).toBe('csrf-fresh');
  });

  it('retries a genuine 403 only once', async () => {
    const { client, calls } = harness(async (url) => {
      if (url.endsWith('/auth/csrf')) {
        return json(envelope({ token: 'csrf-fresh', headerName: 'X-XSRF-TOKEN' }));
      }
      return json({ code: 40301, message: '无权限', data: null }, 403);
    });

    client.setCsrfToken('csrf-stale');
    await expect(client.post('/anything', {})).rejects.toBeInstanceOf(ApiError);

    expect(calls.map((call) => call.url)).toEqual([
      '/api/v1/c/anything',
      '/api/v1/c/auth/csrf',
      '/api/v1/c/anything',
    ]);
  });
});

describe('403 classification', () => {
  it('separates section admission from object-level denial', async () => {
    const section = await harness(async () =>
      json({ code: 40302, message: '无后台分区权限', data: null }, 403),
    )
      .client.get('/me')
      .catch((caught: unknown) => caught);
    const object = await harness(async () =>
      json({ code: 40301, message: '无权限', data: null }, 403),
    )
      .client.get('/me')
      .catch((caught: unknown) => caught);

    expect(isSectionForbidden(section)).toBe(true);
    expect(isObjectForbidden(section)).toBe(false);
    expect(isObjectForbidden(object)).toBe(true);
    expect(isSectionForbidden(object)).toBe(false);
  });
});

describe('write helpers', () => {
  it('sends an explicit Idempotency-Key and normalizes 40901', async () => {
    const { client, calls } = harness(async () =>
      json({ code: ERROR_CODES.VERSION_CONFLICT, message: '版本冲突', data: null }, 409),
    );
    client.setCsrfToken('csrf-1');

    const error = await client
      .post('/matches/1/status', { status: 3, version: 1 }, { idempotencyKey: 'idem-1' })
      .catch((caught: unknown) => caught);

    expect(new Headers(calls[0]?.init.headers).get('Idempotency-Key')).toBe('idem-1');
    expect(isVersionConflict(error)).toBe(true);
  });

  it('generates UUID idempotency keys', () => {
    expect(newIdempotencyKey()).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/,
    );
  });

  it('sends a JSON body and Idempotency-Key on DELETE', async () => {
    const { client, calls } = harness(async () => json(envelope({ ok: true })));
    client.setCsrfToken('csrf-1');

    await client.delete('/events/1', { version: 2 }, { idempotencyKey: 'idem-2' });

    expect(calls).toHaveLength(1);
    expect(calls[0]?.init.body).toBe('{"version":2}');
    expect(new Headers(calls[0]?.init.headers).get('Content-Type')).toBe('application/json');
    expect(new Headers(calls[0]?.init.headers).get('Idempotency-Key')).toBe('idem-2');
  });
});

describe('safeRedirectPath', () => {
  it('keeps same-origin paths inside the app base', () => {
    expect(safeRedirectPath('/matches?page=2')).toBe('/matches?page=2');
    expect(
      safeRedirectPath('/admin/matches?page=2', { basePath: '/admin/', loginPath: '/admin/login' }),
    ).toBe('/admin/matches?page=2');
  });

  it('rejects absolute URLs, protocol-relative URLs and login paths', () => {
    expect(safeRedirectPath('https://evil.example/steal')).toBeUndefined();
    expect(safeRedirectPath('//evil.example/steal')).toBeUndefined();
    expect(safeRedirectPath('/\\evil.example')).toBeUndefined();
    expect(safeRedirectPath('/login')).toBeUndefined();
    expect(
      safeRedirectPath('/login', { basePath: '/admin/', loginPath: '/admin/login' }),
    ).toBeUndefined();
    expect(safeRedirectPath('/matches', { basePath: '/admin/' })).toBeUndefined();
  });
});
