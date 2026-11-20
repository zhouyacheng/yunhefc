import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError, request } from '../miniprogram/utils/request';
import {
  bindPhone,
  clearSession,
  getBindTicket,
  getPlayer,
  getUser,
  login,
  restore,
  sendSmsCode,
  sessionReady,
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
        player: { id: 1, type: 1 },
        csrfToken: 'csrf-1',
      },
    });

    await login();

    expect(getBindTicket()).toBeNull();
    expect(getUser()?.id).toBe(7);
    expect(getPlayer()?.type).toBe(1);
  });

  it('restores user and player from /me', async () => {
    responder = respond({
      code: 0,
      data: {
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        roles: [],
        permissions: [],
        unreadCount: 0,
      },
    });

    const user = await restore();

    expect(user?.id).toBe(7);
    expect(getPlayer()?.type).toBe(1);
  });

  it('runs the launch session once for concurrent callers', async () => {
    responder = respond({
      code: 0,
      data: { user: { id: 7, name: '球员' }, player: { id: 1, type: 1 } },
    });

    const [first, second] = await Promise.all([sessionReady(), sessionReady()]);

    expect(first?.id).toBe(7);
    expect(second?.id).toBe(7);
    expect(calls.filter((call) => call.url.endsWith('/me'))).toHaveLength(1);
  });

  it('returns null and keeps the bind ticket when binding is required', async () => {
    responder = (options) => {
      if (options.url.endsWith('/me')) {
        options.success({
          statusCode: 401,
          data: { code: 40101, message: '未登录' },
          header: {},
        });
        return;
      }
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            needBindPhone: true,
            bindTicket: 't1',
            user: null,
            player: null,
            csrfToken: null,
          },
        },
        header: {},
      });
    };

    const user = await sessionReady();

    expect(user).toBeNull();
    expect(getBindTicket()).toBe('t1');
    expect(getUser()).toBeNull();
  });

  it('refreshes the ready promise after a successful login', async () => {
    responder = (options) => {
      if (options.url.endsWith('/me')) {
        options.success({
          statusCode: 401,
          data: { code: 40101, message: '未登录' },
          header: {},
        });
        return;
      }
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            needBindPhone: true,
            bindTicket: 't1',
            user: null,
            player: null,
            csrfToken: null,
          },
        },
        header: {},
      });
    };
    expect(await sessionReady()).toBeNull();

    responder = respond({
      code: 0,
      data: {
        needBindPhone: false,
        bindTicket: null,
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        csrfToken: 'csrf-1',
      },
    });
    await login();

    expect((await sessionReady())?.id).toBe(7);
  });

  it('clears the cached user when an authenticated request returns 401', async () => {
    responder = respond({
      code: 0,
      data: {
        needBindPhone: false,
        bindTicket: null,
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        csrfToken: 'csrf-1',
      },
    });
    await login();
    expect(getUser()?.id).toBe(7);

    responder = respond({ code: 40101, message: '会话已过期' }, 401);
    await expect(request('GET', '/matches')).rejects.toBeInstanceOf(ApiError);

    expect(getUser()).toBeNull();
    expect(getPlayer()).toBeNull();
  });

  it('keeps the player returned by bind-phone', async () => {
    responder = respond({
      code: 0,
      data: {
        needBindPhone: true,
        bindTicket: 't1',
        user: null,
        player: null,
        csrfToken: null,
      },
    });
    await login();

    responder = respond({
      code: 0,
      data: {
        user: { id: 8, name: '新球员' },
        player: { id: 2, type: 0 },
        csrfToken: 'csrf-2',
      },
    });
    await bindPhone({ phone: '13900000000', code: '123456', name: '新球员', position: 2 });

    expect(getUser()?.id).toBe(8);
    expect(getPlayer()?.type).toBe(0);
    expect((await sessionReady())?.id).toBe(8);
  });

  it('clears the cached player on logout', async () => {
    responder = respond({
      code: 0,
      data: { user: { id: 7, name: '球员' }, player: { id: 1, type: 1 } },
    });
    await restore();

    clearSession();

    expect(getUser()).toBeNull();
    expect(getPlayer()).toBeNull();
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
