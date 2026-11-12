import type { ApiEnvelope } from '@yunhefc/shared';

import { ApiError, ERROR_CODES } from './errors';
import { buildLoginUrl } from './redirect';

export type Namespace = 'c' | 'b';
export type QueryValue = string | number | boolean | null | undefined;
export type QueryParams = Record<string, QueryValue>;

export interface ApiClientOptions {
  /** C 端为 `c`，B 端为 `b`；决定 `/api/v1/{c|b}` 与默认登录页。 */
  namespace: Namespace;
  baseUrl?: string;
  fetch?: typeof fetch;
  /** 应用挂载前缀，admin 传 `/admin/`。 */
  basePath?: string;
  loginPath?: string;
  getCurrentPath?: () => string;
  /** 默认整页跳转；测试或路由守卫可替换。 */
  onUnauthorized?: (loginUrl: string) => void;
}

export interface RequestOptions {
  query?: QueryParams;
  headers?: Record<string, string>;
  idempotencyKey?: string;
  signal?: AbortSignal;
  /** 401 时是否跳转登录；静默探测会话（守卫内）传 false。 */
  redirectOnUnauthorized?: boolean;
}

const WRITE_METHODS = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function defaultLoginPath(namespace: Namespace): string {
  return namespace === 'b' ? '/admin/login' : '/login';
}

function defaultBasePath(namespace: Namespace): string {
  return namespace === 'b' ? '/admin/' : '/';
}

function defaultCurrentPath(): string {
  if (typeof window === 'undefined') {
    return '';
  }
  return `${window.location.pathname}${window.location.search}`;
}

export class ApiClient {
  readonly baseUrl: string;

  private readonly namespace: Namespace;
  private readonly basePath: string;
  private readonly loginPath: string;
  private readonly fetchImpl: typeof fetch;
  private readonly getCurrentPath: () => string;
  private readonly onUnauthorized: (loginUrl: string) => void;

  private csrfToken: string | null = null;
  private csrfHeaderName = 'X-XSRF-TOKEN';

  constructor(options: ApiClientOptions) {
    this.namespace = options.namespace;
    this.baseUrl = (options.baseUrl ?? `/api/v1/${options.namespace}`).replace(/\/$/, '');
    this.basePath = options.basePath ?? defaultBasePath(options.namespace);
    this.loginPath = options.loginPath ?? defaultLoginPath(options.namespace);
    this.fetchImpl = options.fetch ?? globalThis.fetch.bind(globalThis);
    this.getCurrentPath = options.getCurrentPath ?? defaultCurrentPath;
    this.onUnauthorized =
      options.onUnauthorized ??
      ((loginUrl) => {
        if (typeof window !== 'undefined') {
          window.location.assign(loginUrl);
        }
      });
  }

  get namespaceName(): Namespace {
    return this.namespace;
  }

  /** 登录/CSRF 响应体中的 token 只存内存。 */
  setCsrfToken(token: string | null, headerName?: string): void {
    this.csrfToken = token;
    if (headerName) {
      this.csrfHeaderName = headerName;
    }
  }

  clearCsrfToken(): void {
    this.csrfToken = null;
  }

  get<T>(path: string, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('GET', path, options);
  }

  post<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('POST', path, { ...options, body });
  }

  /** multipart 上传：不设置 Content-Type，由浏览器生成 boundary。 */
  postForm<T>(path: string, formData: FormData, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('POST', path, { ...options, body: formData });
  }

  put<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('PUT', path, { ...options, body });
  }

  patch<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('PATCH', path, { ...options, body });
  }

  delete<T>(path: string, body?: unknown, options: RequestOptions = {}): Promise<T> {
    return this.request<T>('DELETE', path, { ...options, body });
  }

  async request<T>(
    method: string,
    path: string,
    options: RequestOptions & { body?: unknown } = {},
  ): Promise<T> {
    const url = this.buildUrl(path, options.query);
    const isWrite = WRITE_METHODS.has(method.toUpperCase());

    if (isWrite && !this.csrfToken) {
      // 首次写请求前取一次；失败时照常发送，下面按 403 补取并重试一次。
      await this.fetchCsrfToken().catch(() => undefined);
    }

    let response = await this.send(method, url, options);

    // token 可能已过期或被其他标签页/会话轮换：403 时重取一次并重试一次。
    if (isWrite && response.status === 403) {
      this.clearCsrfToken();
      await this.fetchCsrfToken().catch(() => undefined);
      if (this.csrfToken) {
        response = await this.send(method, url, options);
      }
    }

    return this.unwrap<T>(response, method, url, options);
  }

  private buildUrl(path: string, query?: QueryParams): string {
    const normalized = path.startsWith('/') ? path : `/${path}`;
    const url = `${this.baseUrl}${normalized}`;
    if (!query) {
      return url;
    }
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === null || value === '') {
        continue;
      }
      params.set(key, String(value));
    }
    const serialized = params.toString();
    return serialized ? `${url}?${serialized}` : url;
  }

  private async send(
    method: string,
    url: string,
    options: RequestOptions & { body?: unknown },
  ): Promise<Response> {
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (this.csrfToken) {
      headers.set(this.csrfHeaderName, this.csrfToken);
    }
    if (options.idempotencyKey) {
      headers.set('Idempotency-Key', options.idempotencyKey);
    }

    let body: BodyInit | undefined;
    if (options.body instanceof FormData) {
      // boundary 必须由浏览器生成，不能手工指定 Content-Type。
      headers.delete('Content-Type');
      body = options.body;
    } else if (options.body !== undefined) {
      headers.set('Content-Type', 'application/json');
      body = JSON.stringify(options.body);
    }

    try {
      return await this.fetchImpl(url, {
        method,
        credentials: 'include',
        headers,
        body,
        signal: options.signal,
      });
    } catch (cause) {
      throw new ApiError({
        status: 0,
        code: ERROR_CODES.OK,
        message: '网络连接失败',
        method,
        url,
        cause,
      });
    }
  }

  private async fetchCsrfToken(): Promise<void> {
    const url = `${this.baseUrl}/auth/csrf`;
    let response: Response;
    try {
      response = await this.fetchImpl(url, {
        method: 'GET',
        credentials: 'include',
        headers: { Accept: 'application/json' },
      });
    } catch (cause) {
      throw new ApiError({
        status: 0,
        code: ERROR_CODES.OK,
        message: '无法获取 CSRF token',
        method: 'GET',
        url,
        cause,
      });
    }
    const envelope = await this.readEnvelope<{ token: string; headerName?: string }>(
      response,
      'GET',
      url,
    );
    const data = envelope.data;
    if (!data?.token) {
      throw new ApiError({
        status: response.status,
        code: ERROR_CODES.INTERNAL_ERROR,
        message: 'CSRF 响应缺少 token',
        method: 'GET',
        url,
      });
    }
    this.setCsrfToken(data.token, data.headerName);
  }

  private async unwrap<T>(
    response: Response,
    method: string,
    url: string,
    options: RequestOptions,
  ): Promise<T> {
    let envelope: (Partial<ApiEnvelope<T>> & { data: T }) | undefined;
    try {
      envelope = await this.readEnvelope<T>(response, method, url);
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) {
        this.handleUnauthorized(options);
      }
      throw error;
    }

    const code = typeof envelope.code === 'number' ? envelope.code : response.status;
    if (!response.ok || code !== ERROR_CODES.OK) {
      if (code === ERROR_CODES.UNAUTHENTICATED) {
        this.handleUnauthorized(options);
      }
      throw new ApiError({
        status: response.status,
        code,
        message: envelope.message ?? response.statusText ?? '请求失败',
        requestId: envelope.requestId,
        data: envelope.data,
        method,
        url,
      });
    }
    return envelope.data;
  }

  private async readEnvelope<T>(
    response: Response,
    method: string,
    url: string,
  ): Promise<Partial<ApiEnvelope<T>> & { data: T }> {
    const requestId = response.headers.get('X-Request-Id') ?? undefined;
    const text = await response.text();
    let body: unknown;
    if (text) {
      try {
        body = JSON.parse(text);
      } catch (cause) {
        throw new ApiError({
          status: response.status,
          code: response.status,
          message: '响应不是合法的 JSON',
          requestId,
          method,
          url,
          cause,
        });
      }
    }
    const envelope =
      body === undefined || body === null
        ? ({
            code: response.status === 401 ? ERROR_CODES.UNAUTHENTICATED : response.status,
            message: response.ok ? 'success' : response.statusText || '请求失败',
            data: undefined,
          } as Partial<ApiEnvelope<T>> & { data: T })
        : (body as Partial<ApiEnvelope<T>> & { data: T });
    return { ...envelope, requestId: envelope.requestId ?? requestId };
  }

  private handleUnauthorized(options: RequestOptions): void {
    this.clearCsrfToken();
    if (options.redirectOnUnauthorized === false) {
      return;
    }
    const loginUrl = buildLoginUrl(this.loginPath, this.getCurrentPath(), {
      basePath: this.basePath,
      loginPath: this.loginPath,
    });
    this.onUnauthorized(loginUrl);
  }
}

export function createApiClient(options: ApiClientOptions): ApiClient {
  return new ApiClient(options);
}
