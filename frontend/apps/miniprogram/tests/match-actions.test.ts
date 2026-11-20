import { beforeEach, describe, expect, it, vi } from 'vitest';

import type { Attendance, EventCreate, MatchEvent, Registration } from '../miniprogram/types/api';

const mocks = vi.hoisted(() => ({
  signupMatch: vi.fn(),
  cancelMyRegistration: vi.fn(),
  checkInMatch: vi.fn(),
  changeMatchStatus: vi.fn(),
  createMatchEvent: vi.fn(),
  updateMatchEvent: vi.fn(),
}));

vi.mock('../miniprogram/utils/api-client', () => mocks);

import { ApiError } from '../miniprogram/utils/request';
import { consumeMyRegistrationsDirty } from '../miniprogram/utils/registration-signal';
import {
  cancel,
  changeStatus,
  checkIn,
  refreshedEventVersion,
  saveEvent,
  signup,
} from '../miniprogram/pages/match-detail/actions';

const CONFLICT = new ApiError(40901, '比赛信息已更新，请刷新后重试', 409);

function registration(status: number): Registration {
  return {
    id: 11,
    playerId: 2,
    type: 1,
    status,
    registeredAt: '2026-01-01T09:00:00Z',
    active: true,
  };
}

const ATTENDANCE: Attendance = {
  id: 9,
  playerId: 2,
  status: 0,
  source: 0,
  recordedAt: '2026-01-01T10:00:00Z',
};

const PAYLOAD: EventCreate = {
  type: 0,
  team: 0,
  playerId: 2,
  assistPlayerId: null,
  inPlayerId: null,
  outPlayerId: null,
  minute: 12,
  half: 0,
  penalty: false,
};

function eventFixture(version: number): MatchEvent {
  return {
    id: 31,
    type: 0,
    team: 0,
    minute: 12,
    half: 0,
    penalty: false,
    version,
  };
}

beforeEach(() => {
  for (const mock of Object.values(mocks)) {
    mock.mockReset();
  }
  consumeMyRegistrationsDirty();
});

describe('signup', () => {
  it('toasts 报名成功 and marks my registrations dirty', async () => {
    mocks.signupMatch.mockResolvedValue(registration(0));

    await expect(signup(7)).resolves.toEqual({ ok: true, patch: {}, toast: '报名成功' });
    expect(mocks.signupMatch).toHaveBeenCalledWith(7);
    expect(consumeMyRegistrationsDirty()).toBe(true);
  });

  it('toasts 已加入候补 for a waitlisted registration', async () => {
    mocks.signupMatch.mockResolvedValue(registration(1));

    await expect(signup(7)).resolves.toMatchObject({ toast: '已加入候补' });
  });

  it('reloads on a version conflict', async () => {
    mocks.signupMatch.mockRejectedValue(CONFLICT);

    await expect(signup(7)).resolves.toEqual({
      ok: false,
      error: '比赛信息已更新，请刷新后重试',
      reload: true,
      refreshEventId: null,
    });
    expect(consumeMyRegistrationsDirty()).toBe(false);
  });

  it('keeps the page without reload for other failures', async () => {
    mocks.signupMatch.mockRejectedValue(new Error('网络异常'));

    await expect(signup(7)).resolves.toEqual({
      ok: false,
      error: '网络异常',
      reload: false,
      refreshEventId: null,
    });
  });

  it('falls back to the action text for non-Error rejections', async () => {
    mocks.signupMatch.mockRejectedValue('boom');

    await expect(signup(7)).resolves.toMatchObject({ error: '报名失败，请稍后重试' });
  });
});

describe('cancel', () => {
  it('reports a plain cancellation as the post-reload message', async () => {
    mocks.cancelMyRegistration.mockResolvedValue({ promoted: false, cancelled: true });

    await expect(cancel({ matchId: 7, cancelledStatus: 0 })).resolves.toEqual({
      ok: true,
      patch: { actionMessage: '已取消报名' },
      toast: '',
    });
    expect(consumeMyRegistrationsDirty()).toBe(true);
  });

  it('uses the waitlist wording when a waitlisted registration is cancelled', async () => {
    mocks.cancelMyRegistration.mockResolvedValue({ promoted: false, cancelled: true });

    await expect(cancel({ matchId: 7, cancelledStatus: 1 })).resolves.toMatchObject({
      patch: { actionMessage: '已取消候补' },
    });
  });

  it('reports the promotion from REG-02', async () => {
    mocks.cancelMyRegistration.mockResolvedValue({ promoted: true, cancelled: true });

    await expect(cancel({ matchId: 7, cancelledStatus: 0 })).resolves.toMatchObject({
      patch: { actionMessage: '已取消报名，候补球员已递补为正式报名' },
    });
  });

  it('reloads on a conflict', async () => {
    mocks.cancelMyRegistration.mockRejectedValue(CONFLICT);

    await expect(cancel({ matchId: 7, cancelledStatus: 0 })).resolves.toEqual({
      ok: false,
      error: '比赛信息已更新，请刷新后重试',
      reload: true,
      refreshEventId: null,
    });
  });
});

describe('checkIn', () => {
  it('returns the attendance patch and toast', async () => {
    mocks.checkInMatch.mockResolvedValue(ATTENDANCE);

    const outcome = await checkIn(7);

    expect(mocks.checkInMatch).toHaveBeenCalledWith(7);
    expect(outcome).toMatchObject({ ok: true, toast: '签到成功' });
    if (outcome.ok) {
      expect(outcome.patch.attendance).toBe(ATTENDANCE);
      expect(outcome.patch.attendanceText).toContain('正常');
      expect(outcome.patch.attendanceText).toContain('自助签到');
    }
  });

  it('reloads on a conflict', async () => {
    mocks.checkInMatch.mockRejectedValue(CONFLICT);

    await expect(checkIn(7)).resolves.toMatchObject({ ok: false, reload: true });
  });
});

describe('changeStatus', () => {
  it('sends the manual status update with the optimistic-lock version', async () => {
    mocks.changeMatchStatus.mockResolvedValue(undefined);

    await expect(
      changeStatus({ matchId: 7, version: 4, status: 2, reason: '调整' }),
    ).resolves.toEqual({
      ok: true,
      patch: { statusOpen: false },
      toast: '状态已改为已锁定',
    });
    expect(mocks.changeMatchStatus).toHaveBeenCalledWith(7, {
      status: 2,
      version: 4,
      manualStatus: true,
      reason: '调整',
      startAt: undefined,
      endAt: undefined,
    });
  });

  it('passes the postpone window and cancel reason through', async () => {
    mocks.changeMatchStatus.mockResolvedValue(undefined);

    await changeStatus({
      matchId: 7,
      version: 4,
      status: 5,
      startAt: '2026-12-01T11:00:00Z',
      endAt: '2026-12-01T13:00:00Z',
    });
    expect(mocks.changeMatchStatus).toHaveBeenLastCalledWith(
      7,
      expect.objectContaining({
        status: 5,
        startAt: '2026-12-01T11:00:00Z',
        endAt: '2026-12-01T13:00:00Z',
      }),
    );

    await changeStatus({ matchId: 7, version: 4, status: 6, reason: '天气' });
    expect(mocks.changeMatchStatus).toHaveBeenLastCalledWith(
      7,
      expect.objectContaining({ status: 6, reason: '天气' }),
    );
  });

  it('reloads on a conflict', async () => {
    mocks.changeMatchStatus.mockRejectedValue(CONFLICT);

    await expect(
      changeStatus({ matchId: 7, version: 4, status: 6, reason: '天气' }),
    ).resolves.toMatchObject({ ok: false, reload: true });
  });
});

describe('saveEvent', () => {
  it('creates a new event when no event id is given', async () => {
    mocks.createMatchEvent.mockResolvedValue(eventFixture(1));

    await expect(
      saveEvent({ matchId: 7, payload: PAYLOAD, eventId: null, version: null }),
    ).resolves.toEqual({ ok: true, patch: { eventFormOpen: false }, toast: '事件已保存' });
    expect(mocks.createMatchEvent).toHaveBeenCalledWith(7, PAYLOAD);
    expect(mocks.updateMatchEvent).not.toHaveBeenCalled();
  });

  it('updates an existing event with its version', async () => {
    mocks.updateMatchEvent.mockResolvedValue(undefined);

    await saveEvent({ matchId: 7, payload: PAYLOAD, eventId: 31, version: 3 });

    expect(mocks.updateMatchEvent).toHaveBeenCalledWith(31, { ...PAYLOAD, version: 3 });
    expect(mocks.createMatchEvent).not.toHaveBeenCalled();
  });

  it('asks the page to re-read the event version after an update conflict', async () => {
    mocks.updateMatchEvent.mockRejectedValue(CONFLICT);

    await expect(
      saveEvent({ matchId: 7, payload: PAYLOAD, eventId: 31, version: 3 }),
    ).resolves.toEqual({
      ok: false,
      error: '比赛信息已更新，请刷新后重试',
      reload: true,
      refreshEventId: 31,
    });
  });

  it('does not ask for an event version when a create conflicts', async () => {
    mocks.createMatchEvent.mockRejectedValue(CONFLICT);

    await expect(
      saveEvent({ matchId: 7, payload: PAYLOAD, eventId: null, version: null }),
    ).resolves.toMatchObject({ ok: false, reload: true, refreshEventId: null });
  });
});

describe('refreshedEventVersion', () => {
  it('takes the reloaded version of the edited event', () => {
    const events = [
      { ...eventFixture(2), id: 42 },
      { ...eventFixture(6), id: 31 },
    ];
    expect(refreshedEventVersion(events, 31)).toBe(6);
  });

  it('returns null when the reloaded timeline no longer has the event', () => {
    expect(refreshedEventVersion([], 31)).toBeNull();
  });
});
