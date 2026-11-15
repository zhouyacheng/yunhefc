import { describe, expect, it } from 'vitest';

import { capacityDefaults, DEFAULT_CAPACITY_DEFAULTS, matchStatusCommandOptions } from './index';

describe('capacityDefaults', () => {
  it('maps SET-01 registration limits to the create form', () => {
    expect(capacityDefaults({ maxPlayers: 12, maxGuests: 3, maxWaitlist: 1 })).toEqual({
      capacity: 12,
      guestCapacity: 3,
      waitlistCapacity: 1,
    });
  });

  it('falls back to 30/4/2 when settings are unavailable', () => {
    expect(capacityDefaults(null)).toEqual(DEFAULT_CAPACITY_DEFAULTS);
    expect(capacityDefaults(undefined)).toEqual(DEFAULT_CAPACITY_DEFAULTS);
  });

  it('returns a fresh object so callers cannot mutate the shared default', () => {
    const defaults = capacityDefaults(null);
    defaults.capacity = 99;
    expect(DEFAULT_CAPACITY_DEFAULTS.capacity).toBe(30);
  });
});

describe('matchStatusCommandOptions', () => {
  const ALL = [0, 1, 2, 3, 4, 5, 6];

  it('lists all seven targets with labels', () => {
    const options = matchStatusCommandOptions({
      currentStatus: 0,
      canEditAny: true,
      canChangeStatus: true,
    });
    expect(options.map((option) => option.status)).toEqual(ALL);
    expect(options.map((option) => option.label)).toEqual([
      '报名中',
      '报名截止',
      '已锁定',
      '进行中',
      '已结束',
      '已延期',
      '已取消',
    ]);
  });

  it('keeps 0–4 enabled from every source status', () => {
    for (const currentStatus of ALL) {
      const options = matchStatusCommandOptions({
        currentStatus,
        canEditAny: true,
        canChangeStatus: true,
      });
      for (const status of [0, 1, 2, 3, 4]) {
        expect(options[status]?.disabled, `current=${currentStatus} target=${status}`).toBe(false);
      }
    }
  });

  it('requires match:edit:any for postponing', () => {
    const withoutEdit = matchStatusCommandOptions({
      currentStatus: 0,
      canEditAny: false,
      canChangeStatus: true,
    });
    expect(withoutEdit[5]).toMatchObject({ disabled: true, reason: '需要编辑比赛权限' });

    const withEdit = matchStatusCommandOptions({
      currentStatus: 0,
      canEditAny: true,
      canChangeStatus: true,
    });
    expect(withEdit[5]).toMatchObject({ disabled: false, reason: '' });
  });

  it('disables postponing unless the source status is 0–3', () => {
    for (const currentStatus of [0, 1, 2, 3]) {
      expect(
        matchStatusCommandOptions({ currentStatus, canEditAny: true, canChangeStatus: true })[5]
          ?.disabled,
      ).toBe(false);
    }
    for (const currentStatus of [4, 5, 6]) {
      expect(
        matchStatusCommandOptions({ currentStatus, canEditAny: true, canChangeStatus: true })[5],
      ).toMatchObject({ disabled: true, reason: '当前状态不可延期' });
    }
  });

  it('disables cancelling from finished or cancelled status', () => {
    for (const currentStatus of [0, 1, 2, 3, 5]) {
      expect(
        matchStatusCommandOptions({ currentStatus, canEditAny: true, canChangeStatus: true })[6]
          ?.disabled,
      ).toBe(false);
    }
    for (const currentStatus of [4, 6]) {
      expect(
        matchStatusCommandOptions({ currentStatus, canEditAny: true, canChangeStatus: true })[6],
      ).toMatchObject({ disabled: true, reason: '当前状态不可取消' });
    }
  });

  it('requires match:status:any for plain statuses and cancelling', () => {
    const withoutStatus = matchStatusCommandOptions({
      currentStatus: 0,
      canEditAny: true,
      canChangeStatus: false,
    });
    for (const status of [0, 1, 2, 3, 4, 6]) {
      expect(withoutStatus[status]).toMatchObject({
        disabled: true,
        reason: '需要状态管理权限',
      });
    }
    expect(withoutStatus[5]?.disabled).toBe(false);
  });

  it('covers the full current × target matrix', () => {
    for (const currentStatus of ALL) {
      const options = matchStatusCommandOptions({
        currentStatus,
        canEditAny: true,
        canChangeStatus: true,
      });
      for (const target of ALL) {
        const expected =
          target === 5
            ? !(currentStatus >= 0 && currentStatus <= 3)
            : target === 6
              ? currentStatus === 4 || currentStatus === 6
              : false;
        expect(options[target]?.disabled, `current=${currentStatus} target=${target}`).toBe(
          expected,
        );
      }
    }
  });
});
