import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Page, RegistrationRow } from '../miniprogram/types/api';
import { emptyScheduleFilters } from '../miniprogram/utils/schedule-query';

const mocks = vi.hoisted(() => ({
  request: vi.fn(),
  ensureCsrf: vi.fn(async () => undefined),
  uploadFile: vi.fn(),
}));

vi.mock('../miniprogram/utils/request', () => ({
  request: mocks.request,
  ensureCsrf: mocks.ensureCsrf,
  uploadFile: mocks.uploadFile,
  ApiError: class ApiError extends Error {},
}));

import {
  bindPhoneWithTicket,
  cancelActivitySignup,
  cancelMyRegistration,
  changeMatchStatus,
  checkInMatch,
  createMatchEvent,
  fetchActivity,
  fetchFullRoster,
  fetchLeaderboards,
  fetchLedgerSummary,
  fetchMatchDetail,
  fetchMe,
  fetchTeamStats,
  fetchUnreadNotificationCount,
  listActivities,
  listActivitySignups,
  listLedgerCategories,
  listLedgerEntries,
  listMatchSeasons,
  listMatches,
  listMyRegistrations,
  listNotifications,
  listTeamPlayers,
  listRecentMatches,
  logoutRequest,
  markNotificationRead,
  miniLogin,
  saveMyPlayer,
  sendBindSmsCode,
  signupActivity,
  signupMatch,
  updateMatchEvent,
  uploadAvatar,
} from '../miniprogram/utils/api-client';

function page<T>(items: T[], totalPages = 1): Page<T> {
  return { items, total: items.length, page: 1, pageSize: items.length, totalPages };
}

beforeEach(() => {
  mocks.request.mockReset();
  mocks.ensureCsrf.mockClear();
  mocks.uploadFile.mockReset();
});

describe('listMatches', () => {
  it('owns scope/sort/pagination and appends the encoded filter key', () => {
    mocks.request.mockResolvedValue(page([]));
    void listMatches();
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/matches?scope=all&sort=startAt:desc&page=1&pageSize=10',
    );

    mocks.request.mockClear();
    void listMatches({
      filters: { ...emptyScheduleFilters(), status: 4 },
      page: 3,
      pageSize: 20,
    });
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/matches?scope=all&sort=startAt:desc&page=3&pageSize=20&status=4',
    );
  });

  it('clamps invalid page numbers to the first page', () => {
    mocks.request.mockResolvedValue(page([]));
    void listMatches({ page: 0 });
    expect(mocks.request.mock.calls[0]?.[1]).toContain('page=1');
  });

  it('keeps the home hero query window', async () => {
    mocks.request.mockResolvedValue(page([]));
    await listRecentMatches();
    expect(mocks.request).toHaveBeenCalledWith('GET', '/matches?sort=startAt:desc&pageSize=4');
  });
});

describe('pagination', () => {
  it('walks every roster page and keeps both registration pools', async () => {
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path === '/matches/7/attendance?page=1&pageSize=100') {
        return Promise.resolve(page([]));
      }
      if (path === '/matches/7/registrations?status=0&page=1&pageSize=100') {
        return Promise.resolve(page([{ id: 1 } as RegistrationRow], 2));
      }
      if (path === '/matches/7/registrations?status=0&page=2&pageSize=100') {
        return Promise.resolve(page([{ id: 2 } as RegistrationRow]));
      }
      if (path === '/matches/7/registrations?status=1&page=1&pageSize=100') {
        return Promise.resolve(page([{ id: 3 } as RegistrationRow]));
      }
      return Promise.reject(new Error(`unexpected ${path}`));
    });

    const roster = await fetchFullRoster(7);
    expect(roster.confirmed.map((row) => row.id)).toEqual([1, 2]);
    expect(roster.waitlist.map((row) => row.id)).toEqual([3]);
    expect(roster.attendance).toEqual([]);
    expect(roster.attendanceError).toBe('');
    expect(mocks.request).toHaveBeenCalledTimes(4);
  });

  it('degrades attendance failures instead of blocking the roster', async () => {
    mocks.request.mockImplementation((_method: string, path: string) => {
      if (path.startsWith('/matches/7/attendance')) {
        return Promise.reject(new Error('考勤加载失败'));
      }
      return Promise.resolve(page([]));
    });

    const roster = await fetchFullRoster(7);
    expect(roster.attendanceError).toBe('考勤加载失败');
    expect(roster.confirmed).toEqual([]);

    mocks.request.mockImplementation((_method: string, path: string) =>
      path.startsWith('/matches/7/attendance') ? Promise.reject('boom') : Promise.resolve(page([])),
    );
    const fallback = await fetchFullRoster(7);
    expect(fallback.attendanceError).toBe('考勤加载失败，名单未显示考勤');
  });
});

describe('idempotent writes', () => {
  it('sends CSRF plus a fresh idempotency key per registration write', async () => {
    mocks.request.mockResolvedValue({ registration: { id: 1 } });
    await signupMatch(5);
    await signupMatch(5);

    expect(mocks.ensureCsrf).toHaveBeenCalledTimes(2);
    const first = mocks.request.mock.calls[0];
    const second = mocks.request.mock.calls[1];
    expect(first?.slice(0, 2)).toEqual(['POST', '/matches/5/registrations']);
    expect(first?.[3]?.idempotencyKey).toMatch(/^mp-/);
    expect(second?.[3]?.idempotencyKey).not.toBe(first?.[3]?.idempotencyKey);
  });

  it('keys every high-risk match write', async () => {
    const writes: [string, () => Promise<unknown>][] = [
      ['DELETE /matches/5/registrations/me', () => cancelMyRegistration(5)],
      ['POST /matches/5/check-in', () => checkInMatch(5)],
      ['POST /matches/5/status', () => changeMatchStatus(5, { status: 4, version: 2 })],
      [
        'POST /matches/5/events',
        () => createMatchEvent(5, { type: 1, team: 0, minute: 1, half: 1, penalty: false }),
      ],
      ['PATCH /events/9', () => updateMatchEvent(9, { minute: 2, version: 1 })],
    ];
    mocks.request.mockResolvedValue({});
    for (const [expected, run] of writes) {
      mocks.request.mockClear();
      await run();
      const [method, path, , options] = mocks.request.mock.calls[0] ?? [];
      expect(`${method} ${path}`).toBe(expected);
      expect(options?.idempotencyKey).toMatch(/^mp-/);
    }
  });

  it('leaves GETs without options so they stay cacheable reads', async () => {
    mocks.request.mockResolvedValue({ id: 9 });
    await fetchMatchDetail(9);
    expect(mocks.request.mock.calls[0]).toEqual(['GET', '/matches/9']);
  });
});

describe('auth handshake', () => {
  it('logs in and binds without pre-fetching CSRF', async () => {
    mocks.request.mockResolvedValue({ needBindPhone: true });
    await miniLogin('code-1');
    expect(mocks.request).toHaveBeenCalledWith('POST', '/auth/mini/login', { code: 'code-1' });
    expect(mocks.ensureCsrf).not.toHaveBeenCalled();

    mocks.request.mockResolvedValue({ user: { id: 1 } });
    await bindPhoneWithTicket('ticket', {
      phone: '13900000000',
      code: '123456',
      name: '张三',
      position: 0,
    });
    expect(mocks.request).toHaveBeenLastCalledWith('POST', '/auth/mini/bind-phone', {
      bindTicket: 'ticket',
      phone: '13900000000',
      code: '123456',
      name: '张三',
      position: 0,
    });
    expect(mocks.ensureCsrf).not.toHaveBeenCalled();
  });

  it('fetches CSRF for logout and sms and reads the identity', async () => {
    mocks.request.mockResolvedValue({});
    await logoutRequest();
    await sendBindSmsCode('13900000000');
    await fetchMe();

    expect(mocks.ensureCsrf).toHaveBeenCalledTimes(2);
    expect(mocks.request).toHaveBeenLastCalledWith('GET', '/me');
    expect(mocks.request).toHaveBeenCalledWith('POST', '/auth/sms-codes', {
      phone: '13900000000',
      purpose: 'BIND',
    });
  });
});

describe('error normalization', () => {
  it('keeps the original error so pages keep their own fallback copy', async () => {
    const failure = new Error('未登录');
    mocks.request.mockRejectedValue(failure);
    await expect(markNotificationRead(3)).rejects.toBe(failure);
  });
});

describe('notifications, me and team queries', () => {
  it('builds notification and registration feeds', async () => {
    mocks.request.mockResolvedValue(page([]));
    await listNotifications({ page: 2, pageSize: 10, unread: true });
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/notifications?page=2&pageSize=10&unread=true',
    );

    await listMyRegistrations({ scope: 'history', page: 1, pageSize: 10 });
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/me/registrations?scope=history&page=1&pageSize=10',
    );
  });

  it('returns the unread badge count', async () => {
    mocks.request.mockResolvedValue({ count: 4 });
    await expect(fetchUnreadNotificationCount()).resolves.toBe(4);
    expect(mocks.request).toHaveBeenCalledWith('GET', '/notifications/unread-count');
  });

  it('normalizes non-paged team collections to items', async () => {
    mocks.request.mockResolvedValue({ items: ['2025', '2026'] });
    await expect(listMatchSeasons()).resolves.toEqual(['2025', '2026']);

    mocks.request.mockResolvedValue({ items: [{ id: 1 }], total: 1 });
    await expect(listTeamPlayers({ all: true, season: '2026' })).resolves.toEqual([{ id: 1 }]);
    expect(mocks.request).toHaveBeenCalledWith('GET', '/team/players?all=true&season=2026');

    mocks.request.mockResolvedValue({ season: '2026', items: [] });
    await fetchTeamStats('2026');
    expect(mocks.request).toHaveBeenCalledWith('GET', '/team/stats?season=2026');
  });

  it('requests leaderboards per season and career scope', async () => {
    mocks.request.mockResolvedValue({ season: '2026', scope: 'season', items: [] });
    await fetchLeaderboards({ season: '2026', scope: 'season' });
    expect(mocks.request).toHaveBeenLastCalledWith(
      'GET',
      '/team/leaderboards?season=2026&scope=season',
    );

    await fetchLeaderboards({ season: '2026', scope: 'all' });
    expect(mocks.request).toHaveBeenLastCalledWith('GET', '/team/leaderboards?scope=all');
  });

  it('hides the upload adapter behind the client', async () => {
    mocks.uploadFile.mockResolvedValue({ objectKey: 'k', signedUrl: 'u' });
    await uploadAvatar('/tmp/a.jpg');
    expect(mocks.ensureCsrf).toHaveBeenCalled();
    expect(mocks.uploadFile).toHaveBeenCalledWith('/files/avatar', '/tmp/a.jpg');
  });
});

describe('profile save', () => {
  it('patches the player without an idempotency key', async () => {
    mocks.request.mockResolvedValue({ id: 1 });
    await saveMyPlayer({ name: '张三', region: '上海', position: 0, secondaryPositions: [] });
    expect(mocks.ensureCsrf).toHaveBeenCalled();
    expect(mocks.request.mock.calls[0]?.slice(0, 2)).toEqual(['PATCH', '/me/player']);
    expect(mocks.request.mock.calls[0]?.[3]).toBeUndefined();
  });
});

describe('activity client', () => {
  it('lists activities with scope, filters and default pagination', async () => {
    mocks.request.mockResolvedValue(page([]));
    await listActivities({ scope: 'latest', type: 0, status: 1 });
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/activities?type=0&status=1&scope=latest&page=1&pageSize=10',
    );
  });

  it('reads detail and signup roster without idempotency options', async () => {
    mocks.request.mockResolvedValue({ id: 5 });
    await fetchActivity(5);
    expect(mocks.request).toHaveBeenLastCalledWith('GET', '/activities/5');

    mocks.request.mockResolvedValue(page([]));
    await listActivitySignups(5, { page: 2, pageSize: 20 });
    expect(mocks.request).toHaveBeenLastCalledWith(
      'GET',
      '/activities/5/signups?page=2&pageSize=20',
    );
  });

  it('signs up and cancels with fresh idempotency keys', async () => {
    mocks.request.mockResolvedValue({ signup: { id: 11 } });
    await expect(signupActivity(5, { withFamily: true, familyNote: '两位' })).resolves.toEqual({
      id: 11,
    });
    const [method, path, body, options] = mocks.request.mock.calls[0] ?? [];
    expect(`${method} ${path}`).toBe('POST /activities/5/signups');
    expect(body).toEqual({ withFamily: true, familyNote: '两位' });
    expect(options?.idempotencyKey).toMatch(/^mp-/);

    mocks.request.mockResolvedValue({ cancelled: true });
    await expect(cancelActivitySignup(5)).resolves.toEqual({ cancelled: true });
    const cancel = mocks.request.mock.calls[1] ?? [];
    expect(`${cancel[0]} ${cancel[1]}`).toBe('DELETE /activities/5/signups/me');
    expect(cancel[3]?.idempotencyKey).toMatch(/^mp-/);
  });
});

describe('ledger client', () => {
  it('lists entries with direction, category and ISO range', async () => {
    mocks.request.mockResolvedValue(page([]));
    await listLedgerEntries({
      type: 1,
      categoryId: 3,
      from: '2026-11-01T00:00:00+08:00',
      to: '2026-11-30T23:59:59+08:00',
      page: 2,
      pageSize: 20,
    });
    expect(mocks.request).toHaveBeenCalledWith(
      'GET',
      '/ledger/entries?type=1&categoryId=3&from=2026-11-01T00%3A00%3A00%2B08%3A00&to=2026-11-30T23%3A59%3A59%2B08%3A00&page=2&pageSize=20',
    );
  });

  it('reads summary with the same filter and unwraps enabled categories', async () => {
    mocks.request.mockResolvedValue({ income: 0, expense: 0, balance: 0 });
    await fetchLedgerSummary({ type: 0 });
    expect(mocks.request).toHaveBeenLastCalledWith('GET', '/ledger/summary?type=0');

    mocks.request.mockResolvedValue({ items: [{ id: 1 }] });
    await expect(listLedgerCategories()).resolves.toEqual([{ id: 1 }]);
    expect(mocks.request).toHaveBeenLastCalledWith('GET', '/ledger/categories');
  });
});
