import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  ApiError,
  extractSessionCookie,
  request,
  resetAuth,
  setCsrf,
  setUnauthorizedHandler,
  uploadFile,
} from '../miniprogram/utils/request';

type WxOptions = {
  url: string;
  method: string;
  header: Record<string, string>;
  success: (res: { statusCode: number; data: unknown; header: Record<string, unknown> }) => void;
  fail: (err: { errMsg: string }) => void;
};

type WxUploadOptions = {
  url: string;
  filePath: string;
  name: string;
  header: Record<string, string>;
  success: (res: { statusCode: number; data: string }) => void;
  fail: (err: { errMsg: string }) => void;
};

let responder: (options: WxOptions) => void;
let uploadResponder: (options: WxUploadOptions) => void;

beforeEach(() => {
  resetAuth();
  uploadResponder = () => undefined;
  (globalThis as unknown as { wx: unknown }).wx = {
    request: (options: WxOptions) => responder(options),
    uploadFile: (options: WxUploadOptions) => uploadResponder(options),
  };
});

afterEach(() => {
  resetAuth();
  setUnauthorizedHandler(null);
});

describe('extractSessionCookie', () => {
  it('extracts YHFC_C from string and array headers', () => {
    expect(extractSessionCookie({ 'Set-Cookie': 'YHFC_C=abc; Path=/api/v1/c' })).toBe('YHFC_C=abc');
    expect(extractSessionCookie({ 'Set-Cookie': ['other=1', 'YHFC_C=xyz; Path=/'] })).toBe(
      'YHFC_C=xyz',
    );
  });

  it('returns empty when missing or cleared', () => {
    expect(extractSessionCookie({})).toBe('');
    expect(extractSessionCookie({ 'Set-Cookie': 'other=1' })).toBe('');
    expect(extractSessionCookie({ 'Set-Cookie': 'YHFC_C=abc; Max-Age=0' })).toBe('');
  });
});

describe('request', () => {
  it('unwraps successful envelope', async () => {
    responder = (options) =>
      options.success({ statusCode: 200, data: { code: 0, data: { ok: true } }, header: {} });
    await expect(request<{ ok: boolean }>('GET', '/ping')).resolves.toEqual({ ok: true });
  });

  it('rejects with ApiError on business error', async () => {
    responder = (options) =>
      options.success({
        statusCode: 422,
        data: { code: 42201, message: '业务规则不满足', requestId: 'r-1' },
        header: {},
      });
    await expect(request('POST', '/x', {})).rejects.toMatchObject({
      code: 42201,
      statusCode: 422,
      requestId: 'r-1',
    });
  });

  it('rejects with ApiError on network failure', async () => {
    responder = (options) => options.fail({ errMsg: 'request:fail' });
    await expect(request('GET', '/ping')).rejects.toBeInstanceOf(ApiError);
  });

  it('keeps cookie jar and sends csrf header on writes', async () => {
    const seen: WxOptions[] = [];
    responder = (options) => {
      seen.push(options);
      options.success({
        statusCode: 200,
        data: { code: 0, data: {} },
        header: { 'Set-Cookie': 'YHFC_C=s1; Path=/api/v1/c' },
      });
    };
    await request('GET', '/auth/csrf');
    setCsrf('t-1');
    await request('POST', '/auth/sms-codes', { phone: '13900000000' });

    expect(seen[0].header.Cookie).toBeUndefined();
    expect(seen[1].header.Cookie).toBe('YHFC_C=s1');
    expect(seen[1].header['X-XSRF-TOKEN']).toBe('t-1');
  });

  it('uploads multipart with cookie/CSRF and unwraps the envelope', async () => {
    setCsrf('t-1');
    uploadResponder = (options) => {
      expect(options.url).toContain('/files/avatar');
      expect(options.name).toBe('file');
      expect(options.header['X-XSRF-TOKEN']).toBe('t-1');
      options.success({
        statusCode: 200,
        data: JSON.stringify({ code: 0, data: { objectKey: 'k', signedUrl: 'u' } }),
      });
    };

    await expect(uploadFile('/files/avatar', '/tmp/a.png')).resolves.toEqual({
      objectKey: 'k',
      signedUrl: 'u',
    });
  });

  it('notifies the session when an upload returns 401', async () => {
    let cleared = 0;
    setUnauthorizedHandler(() => {
      cleared += 1;
    });
    uploadResponder = (options) => {
      options.success({
        statusCode: 401,
        data: JSON.stringify({ code: 40101, message: '未登录' }),
      });
    };

    await expect(uploadFile('/files/avatar', '/tmp/a.png')).rejects.toBeInstanceOf(ApiError);
    expect(cleared).toBe(1);
  });

  it('sends an idempotency key and custom headers on writes', async () => {
    const seen: WxOptions[] = [];
    setCsrf('t-1');
    responder = (options) => {
      seen.push(options);
      options.success({ statusCode: 200, data: { code: 0, data: {} }, header: {} });
    };

    await request('POST', '/matches/1/registrations', undefined, {
      idempotencyKey: 'idem-1',
      headers: { 'X-Test': 'yes' },
    });

    expect(seen[0].header['Idempotency-Key']).toBe('idem-1');
    expect(seen[0].header['X-Test']).toBe('yes');
    expect(seen[0].header['X-XSRF-TOKEN']).toBe('t-1');
  });

  it('does not send an idempotency key on GET', async () => {
    const seen: WxOptions[] = [];
    responder = (options) => {
      seen.push(options);
      options.success({ statusCode: 200, data: { code: 0, data: {} }, header: {} });
    };

    await request('GET', '/matches', undefined, { idempotencyKey: 'idem-1' });

    expect(seen[0].header['Idempotency-Key']).toBeUndefined();
  });

  it('sends csrf on DELETE', async () => {
    const seen: WxOptions[] = [];
    setCsrf('t-2');
    responder = (options) => {
      seen.push(options);
      options.success({ statusCode: 200, data: { code: 0, data: {} }, header: {} });
    };

    await request('DELETE', '/matches/1/registrations/me', undefined, { idempotencyKey: 'idem-2' });

    expect(seen[0].header['X-XSRF-TOKEN']).toBe('t-2');
    expect(seen[0].header['Idempotency-Key']).toBe('idem-2');
  });

  it('clears auth state and notifies the session on 401', async () => {
    responder = (options) =>
      options.success({
        statusCode: 200,
        data: { code: 0, data: {} },
        header: { 'Set-Cookie': 'YHFC_C=s1' },
      });
    await request('GET', '/auth/csrf');

    let cleared = 0;
    setUnauthorizedHandler(() => {
      cleared += 1;
    });
    responder = (options) =>
      options.success({ statusCode: 401, data: { code: 40101 }, header: {} });
    await expect(request('GET', '/me')).rejects.toBeInstanceOf(ApiError);
    expect(cleared).toBe(1);

    responder = (options) => {
      expect(options.header.Cookie).toBeUndefined();
      options.success({ statusCode: 200, data: { code: 0, data: {} }, header: {} });
    };
    await request('GET', '/me');
  });
});
