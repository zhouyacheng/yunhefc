import { BASE_URL } from './config';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

interface Envelope<T> {
  code: number;
  message: string;
  data: T;
  requestId?: string;
}

export class ApiError extends Error {
  constructor(
    readonly code: number,
    message: string,
    readonly statusCode: number,
    readonly requestId?: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

// wx.request 不会自动保存/回放 Set-Cookie，因此在内存维护一个 Cookie jar。
// 冷启动后 jar 清空，重新 wx.login 即可恢复登录态。
let cookieHeader = '';
let csrfToken = '';
let csrfHeaderName = 'X-XSRF-TOKEN';
let unauthorizedHandler: (() => void) | null = null;

/** 由会话模块注册：401 时清理用户/球员/会话缓存，避免首页保留已登录状态。 */
export function setUnauthorizedHandler(handler: (() => void) | null): void {
  unauthorizedHandler = handler;
}

export function setCsrf(token: string | null | undefined, headerName?: string | null): void {
  csrfToken = token || '';
  if (headerName) {
    csrfHeaderName = headerName;
  }
}

export function resetAuth(): void {
  cookieHeader = '';
  csrfToken = '';
}

export function extractSessionCookie(header: Record<string, unknown>): string {
  const raw = header['Set-Cookie'] ?? header['set-cookie'];
  if (!raw) {
    return '';
  }
  const list = Array.isArray(raw) ? raw : [raw];
  for (const entry of list) {
    const text = String(entry);
    const matched = text.match(/YHFC_C=([^;]*)/);
    if (!matched) {
      continue;
    }
    if (/Max-Age=0/i.test(text)) {
      return '';
    }
    return `YHFC_C=${matched[1]}`;
  }
  return '';
}

export function request<T>(method: HttpMethod, path: string, data?: unknown): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    const header: Record<string, string> = { 'Content-Type': 'application/json' };
    if (cookieHeader) {
      header.Cookie = cookieHeader;
    }
    if (csrfToken && method !== 'GET') {
      header[csrfHeaderName] = csrfToken;
    }

    wx.request({
      url: BASE_URL + path,
      method,
      data: data as unknown as WechatMiniprogram.IAnyObject,
      header,
      success: (res) => {
        const nextCookie = extractSessionCookie((res.header || {}) as Record<string, unknown>);
        if (nextCookie) {
          cookieHeader = nextCookie;
        }
        const statusCode = res.statusCode;
        const body = res.data as Envelope<T> | undefined;
        if (statusCode >= 200 && statusCode < 300 && body && body.code === 0) {
          resolve(body.data);
          return;
        }
        const code = body && typeof body.code === 'number' ? body.code : statusCode;
        if (statusCode === 401) {
          resetAuth();
          unauthorizedHandler?.();
        }
        reject(
          new ApiError(
            code,
            (body && body.message) || '请求失败',
            statusCode,
            body && body.requestId,
          ),
        );
      },
      fail: (error) => {
        reject(new ApiError(-1, error.errMsg || '网络异常', -1));
      },
    });
  });
}

/** 写请求前按需获取 CSRF；小程序端的 Cookie jar 会带上 csrf 会话。 */
export async function ensureCsrf(): Promise<void> {
  if (csrfToken) {
    return;
  }
  const data = await request<{ token: string; headerName: string }>('GET', '/auth/csrf');
  setCsrf(data.token, data.headerName);
}
