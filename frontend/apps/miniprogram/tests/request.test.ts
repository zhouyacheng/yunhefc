import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  ApiError,
  extractSessionCookie,
  request,
  resetAuth,
  setCsrf,
} from '../miniprogram/utils/request';

type WxOptions = {
  url: string;
  method: string;
  header: Record<string, string>;
  success: (res: { statusCode: number; data: unknown; header: Record<string, unknown> }) => void;
  fail: (err: { errMsg: string }) => void;
};

let responder: (options: WxOptions) => void;

beforeEach(() => {
  resetAuth();
  (globalThis as unknown as { wx: unknown }).wx = {
    request: (options: WxOptions) => responder(options),
  };
});

afterEach(() => {
  resetAuth();
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

  it('clears auth state on 401', async () => {
    responder = (options) =>
      options.success({
        statusCode: 200,
        data: { code: 0, data: {} },
        header: { 'Set-Cookie': 'YHFC_C=s1' },
      });
    await request('GET', '/auth/csrf');

    responder = (options) =>
      options.success({ statusCode: 401, data: { code: 40101 }, header: {} });
    await expect(request('GET', '/me')).rejects.toBeInstanceOf(ApiError);

    responder = (options) => {
      expect(options.header.Cookie).toBeUndefined();
      options.success({ statusCode: 200, data: { code: 0, data: {} }, header: {} });
    };
    await request('GET', '/me');
  });
});
