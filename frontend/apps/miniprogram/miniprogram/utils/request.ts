import { BASE_URL } from './config';

type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

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

// wx.request 不会自动保存/回放 Set-Cookie，因此维护一个 Cookie jar。
// jar 写入本地存储：密码登录没有 mp_openid 无法靠 wx.login 续登，冷启动需靠会话 Cookie 恢复。
// 只存不透明会话 ID，不存密码；退出登录、服务端清除或会话失效的 401 即清除（登录凭证错误除外）。
const SESSION_STORAGE_KEY = 'yhfc.c.session';

let cookieHeader = '';
let cookieHydrated = false;
let csrfToken = '';
let csrfHeaderName = 'X-XSRF-TOKEN';
let unauthorizedHandler: (() => void) | null = null;

function readStoredCookie(): string {
  try {
    return (wx.getStorageSync(SESSION_STORAGE_KEY) as string | undefined) || '';
  } catch {
    return '';
  }
}

function writeStoredCookie(value: string): void {
  try {
    if (value) {
      wx.setStorageSync(SESSION_STORAGE_KEY, value);
    } else {
      wx.removeStorageSync(SESSION_STORAGE_KEY);
    }
  } catch {
    // 存储不可用时退化为内存会话，不影响登录本身。
  }
}

function ensureCookieHydrated(): void {
  if (!cookieHydrated) {
    cookieHydrated = true;
    cookieHeader = readStoredCookie();
  }
}

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
  // 标记已 hydrate，避免清空后又被旧存储值恢复。
  cookieHydrated = true;
  writeStoredCookie('');
}

/** 提取会话 Cookie：响应未携带 `YHFC_C` 返回 null；显式清除（Max-Age=0）返回空串。 */
export function extractSessionCookie(header: Record<string, unknown>): string | null {
  const raw = header['Set-Cookie'] ?? header['set-cookie'];
  if (!raw) {
    return null;
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
  return null;
}

function authHeader(includeCsrf: boolean): Record<string, string> {
  const header: Record<string, string> = {};
  if (cookieHeader) {
    header.Cookie = cookieHeader;
  }
  if (includeCsrf && csrfToken) {
    header[csrfHeaderName] = csrfToken;
  }
  return header;
}

function handleUnauthorized(): void {
  resetAuth();
  unauthorizedHandler?.();
}

export interface RequestOptions {
  headers?: Record<string, string>;
  /** 高风险写接口的幂等键；GET 忽略。 */
  idempotencyKey?: string;
  /** 凭证校验接口（如登录）的 401 不应清掉已有会话与本地持久化。 */
  skipSessionResetOn401?: boolean;
}

export function request<T>(
  method: HttpMethod,
  path: string,
  data?: unknown,
  options?: RequestOptions,
): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    ensureCookieHydrated();
    const header = authHeader(method !== 'GET');
    header['Content-Type'] = 'application/json';
    if (options?.headers) {
      Object.assign(header, options.headers);
    }
    if (method !== 'GET' && options?.idempotencyKey) {
      header['Idempotency-Key'] = options.idempotencyKey;
    }

    wx.request({
      url: BASE_URL + path,
      // 官方类型/文档未列 PATCH，但运行时支持；保留 05 的 PATCH 契约，真机验收覆盖。
      method: method as unknown as
        'GET' | 'POST' | 'PUT' | 'DELETE' | 'OPTIONS' | 'HEAD' | 'TRACE' | 'CONNECT',
      data: data as unknown as WechatMiniprogram.IAnyObject,
      header,
      success: (res) => {
        const nextCookie = extractSessionCookie((res.header || {}) as Record<string, unknown>);
        if (nextCookie !== null) {
          cookieHeader = nextCookie;
          writeStoredCookie(nextCookie);
        }
        const statusCode = res.statusCode;
        const body = res.data as Envelope<T> | undefined;
        if (statusCode >= 200 && statusCode < 300 && body && body.code === 0) {
          resolve(body.data);
          return;
        }
        const code = body && typeof body.code === 'number' ? body.code : statusCode;
        if (statusCode === 401 && !options?.skipSessionResetOn401) {
          handleUnauthorized();
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

/** multipart 上传；复用 Cookie/CSRF，401 与普通请求一致清理会话。 */
export function uploadFile<T>(path: string, filePath: string, name = 'file'): Promise<T> {
  return new Promise<T>((resolve, reject) => {
    ensureCookieHydrated();
    wx.uploadFile({
      url: BASE_URL + path,
      filePath,
      name,
      header: authHeader(true),
      success: (res) => {
        let body: Envelope<T> | undefined;
        try {
          body = JSON.parse(res.data) as Envelope<T>;
        } catch {
          body = undefined;
        }
        const statusCode = res.statusCode;
        if (statusCode >= 200 && statusCode < 300 && body && body.code === 0) {
          resolve(body.data);
          return;
        }
        const code = body && typeof body.code === 'number' ? body.code : statusCode;
        if (statusCode === 401) {
          handleUnauthorized();
        }
        reject(
          new ApiError(
            code,
            (body && body.message) || '上传失败',
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
