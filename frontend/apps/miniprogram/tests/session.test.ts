import { beforeEach, describe, expect, it } from 'vitest';
import {
  bindPhone,
  clearSession,
  getBindTicket,
  getUser,
  login,
  sendSmsCode,
} from '../miniprogram/utils/session';

type WxOptions = {
  url: string;
  method: string;
  header: Record<string, string>;
  success: (res: { statusCode: number; data: unknown; header: Record<string, unknown> }) => void;
  fail: (err: { errMsg: string }) => void;
};

let responder: (options: WxOptions) => void;
let calls: WxOptions[];

beforeEach(() => {
  clearSession();
  calls = [];
  responder = () => undefined;
  (globalThis as unknown as { wx: unknown }).wx = {
    login: (options: { success: (res: { code: string }) => void }) =>
      options.success({ code: 'c1' }),
    request: (options: WxOptions) => {
      calls.push(options);
      responder(options);
    },
  };
});

function respond(body: unknown, statusCode = 200) {
  return (options: WxOptions) => options.success({ statusCode, data: body, header: {} });
}

describe('session', () => {
  it('keeps a bind ticket when the mini program account is unbound', async () => {
    responder = respond({
      code: 0,
      data: { needBindPhone: true, bindTicket: 't1', user: null, csrfToken: null },
    });

    const result = await login();

    expect(result.needBindPhone).toBe(true);
    expect(getBindTicket()).toBe('t1');
    expect(getUser()).toBeNull();
  });

  it('stores the user when already bound', async () => {
    responder = respond({
      code: 0,
      data: {
        needBindPhone: false,
        bindTicket: null,
        user: { id: 7, name: '球员' },
        csrfToken: 'csrf-1',
      },
    });

    await login();

    expect(getBindTicket()).toBeNull();
    expect(getUser()?.id).toBe(7);
  });

  it('rejects binding without a ticket', async () => {
    await expect(
      bindPhone({ phone: '13900000000', code: '123456', name: '', position: 2 }),
    ).rejects.toThrow('登录已过期');
  });

  it('fetches csrf before sending sms code', async () => {
    responder = (options) => {
      if (options.url.endsWith('/auth/csrf')) {
        options.success({
          statusCode: 200,
          data: { code: 0, data: { token: 't', headerName: 'X-XSRF-TOKEN' } },
          header: {},
        });
        return;
      }
      options.success({ statusCode: 200, data: { code: 0, data: { expiresIn: 300 } }, header: {} });
    };

    await sendSmsCode('13900000000');

    expect(calls.map((call) => call.url.split('/api/v1/c')[1])).toEqual([
      '/auth/csrf',
      '/auth/sms-codes',
    ]);
    expect(calls[1].header['X-XSRF-TOKEN']).toBe('t');
  });
});
