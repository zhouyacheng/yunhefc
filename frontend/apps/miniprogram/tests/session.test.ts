import { beforeEach, describe, expect, it } from 'vitest';
import { LEGAL_VERSION } from '../miniprogram/utils/legal';
import { ApiError, request } from '../miniprogram/utils/request';
import {
  bindPhone,
  clearBindTicket,
  clearSession,
  getBindTicket,
  getPermissions,
  getPlayer,
  getUser,
  hasPermission,
  loginWithPassword,
  loginWithWechat,
  logout,
  restore,
  restoreIfAgreed,
  sendSmsCode,
  sessionReady,
} from '../miniprogram/utils/session';
import { MATCH_STATUS_ANY, REGISTRATION_CREATE_OWN } from '../miniprogram/utils/permissions';

type WxOptions = {
  url: string;
  method: string;
  header: Record<string, string>;
  success: (res: { statusCode: number; data: unknown; header: Record<string, unknown> }) => void;
  fail: (err: { errMsg: string }) => void;
};

let responder: (options: WxOptions) => void;
let calls: WxOptions[];
let loginCalls: number;
let storedConsent: unknown;

beforeEach(() => {
  clearSession();
  calls = [];
  loginCalls = 0;
  responder = () => undefined;
  storedConsent = { version: LEGAL_VERSION, agreedAt: '2026-11-27T00:00:00.000Z' };
  (globalThis as unknown as { wx: unknown }).wx = {
    getStorageSync: () => storedConsent,
    login: (options: { success: (res: { code: string }) => void }) => {
      loginCalls += 1;
      options.success({ code: 'c1' });
    },
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

    const result = await loginWithWechat();

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

    await loginWithWechat();

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

  it('logs in with phone and password after fetching csrf', async () => {
    responder = (options) => {
      if (options.url.endsWith('/auth/csrf')) {
        options.success({
          statusCode: 200,
          data: { code: 0, data: { token: 'csrf-0', headerName: 'X-XSRF-TOKEN' } },
          header: { 'Set-Cookie': 'YHFC_C=s0; Path=/' },
        });
        return;
      }
      options.success({
        statusCode: 200,
        data: {
          code: 0,
          data: {
            user: { id: 9, name: '球员' },
            player: { id: 3, type: 1 },
            permissions: [REGISTRATION_CREATE_OWN],
            csrfToken: 'csrf-1',
          },
        },
        header: { 'Set-Cookie': 'YHFC_C=s1; Path=/' },
      });
    };

    await loginWithPassword('13900000000', 'abc12345');

    expect(getUser()?.id).toBe(9);
    expect(getPlayer()?.id).toBe(3);
    expect(hasPermission(REGISTRATION_CREATE_OWN)).toBe(true);
    const loginCall = calls.find((call) => call.url.endsWith('/auth/login'));
    expect(loginCall?.header['X-XSRF-TOKEN']).toBe('csrf-0');
    expect((await sessionReady())?.id).toBe(9);
  });

  it('keeps the restored session when password login is rejected', async () => {
    responder = respond({
      code: 0,
      data: { user: { id: 7, name: '球员' }, player: { id: 1, type: 1 } },
    });
    await restore();

    responder = (options) => {
      if (options.url.endsWith('/auth/csrf')) {
        options.success({
          statusCode: 200,
          data: { code: 0, data: { token: 'csrf-0', headerName: 'X-XSRF-TOKEN' } },
          header: {},
        });
        return;
      }
      options.success({
        statusCode: 401,
        data: { code: 40102, message: '手机号或密码错误' },
        header: {},
      });
    };

    await expect(loginWithPassword('13900000000', 'wrong-pass')).rejects.toBeInstanceOf(ApiError);

    expect(getUser()?.id).toBe(7);
  });

  it('does not resume silent login after a failed password login following logout', async () => {
    responder = respond({ code: 0, data: {} });
    await logout();

    responder = (options) => {
      if (options.url.endsWith('/auth/csrf')) {
        options.success({
          statusCode: 200,
          data: { code: 0, data: { token: 'csrf-0', headerName: 'X-XSRF-TOKEN' } },
          header: {},
        });
        return;
      }
      options.success({
        statusCode: 401,
        data: { code: 40102, message: '手机号或密码错误' },
        header: {},
      });
    };

    await expect(loginWithPassword('13900000000', 'wrong-pass')).rejects.toBeInstanceOf(ApiError);

    expect(await sessionReady()).toBeNull();
    expect(loginCalls).toBe(0);
  });

  it('discards the bind ticket when switching to password login', async () => {
    responder = respond({
      code: 0,
      data: { needBindPhone: true, bindTicket: 't1', user: null, csrfToken: null },
    });
    await loginWithWechat();

    clearBindTicket();

    expect(getBindTicket()).toBeNull();
  });

  it('skips silent wechat login before the user agrees to the agreements', async () => {
    storedConsent = '';
    responder = (options) => {
      options.success({
        statusCode: 401,
        data: { code: 40101, message: '未登录' },
        header: {},
      });
    };

    expect(await sessionReady()).toBeNull();
    expect(loginCalls).toBe(0);
  });

  it('does not restore or login when the agreement version changed', async () => {
    storedConsent = { version: '2026-01-01', agreedAt: '2026-01-01T00:00:00.000Z' };
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

    expect(await sessionReady()).toBeNull();
    expect(getUser()).toBeNull();
    expect(loginCalls).toBe(0);
    expect(calls.some((call) => call.url.endsWith('/me'))).toBe(false);
  });

  it('skips /me via restoreIfAgreed when consent is missing', async () => {
    storedConsent = '';

    expect(await restoreIfAgreed()).toBeNull();
    expect(calls).toHaveLength(0);
  });

  it('restores via restoreIfAgreed when consent is current', async () => {
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

    expect((await restoreIfAgreed())?.id).toBe(7);
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
    await loginWithWechat();

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
    await loginWithWechat();
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
    await loginWithWechat();

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

  it('does not silently sign in again after an explicit logout', async () => {
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
    await loginWithWechat();

    responder = respond({ code: 0, data: {} });
    await logout();
    const callsAfterLogout = calls.length;

    expect(await sessionReady()).toBeNull();
    expect(getUser()).toBeNull();
    expect(calls).toHaveLength(callsAfterLogout);
  });

  it('stores permissions returned by login', async () => {
    responder = respond({
      code: 0,
      data: {
        needBindPhone: false,
        bindTicket: null,
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        permissions: [REGISTRATION_CREATE_OWN],
        csrfToken: 'csrf-1',
      },
    });

    await loginWithWechat();

    expect(getPermissions()).toEqual([REGISTRATION_CREATE_OWN]);
    expect(hasPermission(REGISTRATION_CREATE_OWN)).toBe(true);
    expect(hasPermission(MATCH_STATUS_ANY)).toBe(false);
  });

  it('stores permissions returned by /me', async () => {
    responder = respond({
      code: 0,
      data: {
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        roles: [],
        permissions: [MATCH_STATUS_ANY],
        unreadCount: 0,
      },
    });

    await restore();

    expect(hasPermission(MATCH_STATUS_ANY)).toBe(true);
  });

  it('clears permissions on logout and 401', async () => {
    responder = respond({
      code: 0,
      data: {
        user: { id: 7, name: '球员' },
        player: { id: 1, type: 1 },
        roles: [],
        permissions: [MATCH_STATUS_ANY],
        unreadCount: 0,
      },
    });
    await restore();
    expect(hasPermission(MATCH_STATUS_ANY)).toBe(true);

    clearSession();
    expect(getPermissions()).toEqual([]);
    expect(hasPermission(MATCH_STATUS_ANY)).toBe(false);

    await restore();
    responder = respond({ code: 40101, message: '会话已过期' }, 401);
    await expect(request('GET', '/matches')).rejects.toBeInstanceOf(ApiError);
    expect(getPermissions()).toEqual([]);
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
