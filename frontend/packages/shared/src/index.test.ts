import { describe, expect, it } from 'vitest';

import type { PermissionView } from './index';
import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  checkInWindowState,
  emptyPage,
  eventEditWindowOpen,
  eventEntryOpen,
  eventHalfText,
  eventTypeText,
  groupPermissions,
  groupingTeamName,
  isInternalMatchType,
  isPage,
  matchCancellable,
  matchCapacityError,
  matchEditable,
  matchPostponable,
  matchReschedulable,
  matchScheduleError,
  matchScorePairError,
  matchSeasonError,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
  notificationTarget,
  PERMISSION_GROUP_LABELS,
  permissionGroupLabel,
  phoneError,
  playerApprovable,
  playerConvertible,
  playerDisableable,
  playerProfileError,
  playerSort,
  playerStatusText,
  playerStatusTone,
  playerTypeText,
  readLabel,
  REGISTRATION_STATUS,
  roleCodeError,
  roleNameError,
} from './index';

describe('Page helper', () => {
  it('recognizes the 05 pagination shape', () => {
    expect(isPage({ items: [], total: 0, page: 1, pageSize: 10, totalPages: 0 })).toBe(true);
    expect(isPage({ items: [] })).toBe(false);
    expect(isPage(null)).toBe(false);
  });

  it('builds an empty page', () => {
    expect(emptyPage(20)).toEqual({ items: [], total: 0, page: 1, pageSize: 20, totalPages: 0 });
  });
});

describe('enum helpers', () => {
  it('reads the sibling label field', () => {
    expect(readLabel({ status: 4, statusLabel: 'FINISHED' }, 'status')).toBe('FINISHED');
    expect(readLabel({ status: 4 }, 'status')).toBeUndefined();
  });

  it('maps 04 §2.4 match codes to display text', () => {
    expect(matchStatusText(2)).toBe('已锁定');
    expect(matchTypeText(1)).toBe('队内赛');
    expect(matchStatusText(undefined)).toBe('');
  });

  it('maps match statuses to a shared tone', () => {
    expect(matchStatusTone(6)).toBe('danger');
    expect(matchStatusTone(4)).toBe('success');
    expect(matchStatusTone(3)).toBe('warning');
    expect(matchStatusTone(0)).toBe('neutral');
  });

  it('recognizes internal and training matches and maps grouping team names', () => {
    expect(isInternalMatchType(1)).toBe(true);
    expect(isInternalMatchType(2)).toBe(true);
    expect(isInternalMatchType(0)).toBe(false);
    expect(isInternalMatchType(undefined)).toBe(false);
    expect(groupingTeamName({ homeTeamName: '橙队', awayTeamName: '兰队' }, 'A')).toBe('橙队');
    expect(groupingTeamName({ homeTeamName: '橙队', awayTeamName: '兰队' }, 'B')).toBe('兰队');
  });

  it('exposes registration status codes matching 04 §2.4', () => {
    expect(REGISTRATION_STATUS).toEqual({
      CONFIRMED: 0,
      WAITLISTED: 1,
      CANCELLED: 2,
      REMOVED: 3,
    });
  });

  it('maps attendance codes to display text', () => {
    expect(ATTENDANCE_STATUS_TEXT[0]).toBe('正常');
    expect(ATTENDANCE_STATUS_TEXT[2]).toBe('临时到场');
    expect(ATTENDANCE_SOURCE_TEXT[0]).toBe('自助签到');
    expect(ATTENDANCE_SOURCE_TEXT[2]).toBe('系统');
  });
});

describe('event helpers', () => {
  it('maps 04 §2.4 event codes to display text', () => {
    expect(eventTypeText(0)).toBe('进球');
    expect(eventTypeText(2)).toBe('换人');
    expect(eventTypeText(4)).toBe('红牌');
    expect(eventHalfText(1)).toBe('下半场');
  });

  it('opens event entry only for in-progress or finished matches', () => {
    expect(eventEntryOpen(3)).toBe(true);
    expect(eventEntryOpen(4)).toBe(true);
    expect(eventEntryOpen(2)).toBe(false);
    expect(eventEntryOpen(6)).toBe(false);
  });

  it('allows player edits within 24 hours after the match ends', () => {
    const endAt = '2026-11-15T14:00:00Z';
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-16T14:00:00Z'))).toBe(true);
    expect(eventEditWindowOpen(endAt, Date.parse('2026-11-16T14:00:01Z'))).toBe(false);
  });
});

describe('match management rules', () => {
  it('requires a 2–4 hour duration', () => {
    expect(matchScheduleError('2026-12-01T12:00:00Z', '2026-12-01T10:00:00Z')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
    expect(matchScheduleError('2026-12-01T12:00:00Z', '2026-12-01T13:59:59Z')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
    expect(matchScheduleError('2026-12-01T12:00:00Z', '2026-12-01T14:00:00Z')).toBeNull();
    expect(matchScheduleError('2026-12-01T12:00:00Z', '2026-12-01T16:00:00Z')).toBeNull();
    expect(matchScheduleError('2026-12-01T12:00:00Z', '2026-12-01T16:00:01Z')).toBe(
      '开始/结束时间相差必须为 2–4 小时',
    );
  });

  it('rejects a start at or before 10:00 Asia/Shanghai', () => {
    // 02:00Z = 10:00 北京时间；09:59 与 10:00:01 是边界。
    expect(matchScheduleError('2026-12-01T02:00:00Z', '2026-12-01T04:00:00Z')).toBe(
      '开赛时间必须晚于当天 10:00',
    );
    expect(matchScheduleError('2026-12-01T01:59:00Z', '2026-12-01T03:59:00Z')).toBe(
      '开赛时间必须晚于当天 10:00',
    );
    expect(matchScheduleError('2026-12-01T02:00:01Z', '2026-12-01T04:00:01Z')).toBeNull();
  });

  it('requires capacity to cover confirmed counts', () => {
    expect(
      matchCapacityError({ capacity: 9, guestCapacity: 4, confirmedCount: 10, guestCount: 4 }),
    ).toBe('正式名额不得低于已确认正式人数');
    expect(
      matchCapacityError({ capacity: 10, guestCapacity: 3, confirmedCount: 10, guestCount: 4 }),
    ).toBe('外挂名额不得低于已确认外挂人数');
    expect(
      matchCapacityError({ capacity: 10, guestCapacity: 4, confirmedCount: 10, guestCount: 4 }),
    ).toBeNull();
  });

  it('only allows editing before terminal or postponed states', () => {
    expect([0, 1, 2, 3].every((status) => matchEditable(status))).toBe(true);
    expect(matchEditable(4)).toBe(false);
    expect(matchEditable(5)).toBe(false);
    expect(matchEditable(6)).toBe(false);
  });

  it('limits postpone and reschedule to their own states', () => {
    expect([0, 1, 2, 3].every((status) => matchPostponable(status))).toBe(true);
    expect(matchPostponable(5)).toBe(false);
    expect(matchPostponable(6)).toBe(false);
    expect(matchReschedulable(5)).toBe(true);
    expect(matchReschedulable(0)).toBe(false);
  });

  it('rejects cancelling terminal matches', () => {
    expect([0, 1, 2, 3, 5].every((status) => matchCancellable(status))).toBe(true);
    expect(matchCancellable(4)).toBe(false);
    expect(matchCancellable(6)).toBe(false);
    expect(matchCancellable(null)).toBe(false);
    expect(matchCancellable(undefined)).toBe(false);
  });

  it('validates a four-digit season and paired scores', () => {
    expect(matchSeasonError('2026')).toBeNull();
    expect(matchSeasonError('')).toBeNull();
    expect(matchSeasonError('26')).toBe('赛季必须为四位年份');
    expect(matchSeasonError('20266')).toBe('赛季必须为四位年份');
    expect(matchScorePairError(1, 0)).toBeNull();
    expect(matchScorePairError(null, null)).toBeNull();
    expect(matchScorePairError(1, null)).toBe('比分必须成对填写');
    expect(matchScorePairError(undefined, 0)).toBe('比分必须成对填写');
  });
});

describe('notification target', () => {
  it('routes MATCH notifications with a refId to the match detail', () => {
    expect(notificationTarget({ refType: 'MATCH', refId: 1005 })).toEqual({
      kind: 'match',
      id: 1005,
    });
  });

  it('treats USER and unknown ref types as read-only', () => {
    expect(notificationTarget({ refType: 'USER', refId: 1002 })).toEqual({ kind: 'none' });
    expect(notificationTarget({ refType: 'OTHER', refId: 1002 })).toEqual({ kind: 'none' });
  });

  it('treats missing or invalid refs as read-only', () => {
    expect(notificationTarget({ refType: 'MATCH', refId: null })).toEqual({ kind: 'none' });
    expect(notificationTarget({ refType: 'MATCH', refId: undefined })).toEqual({ kind: 'none' });
    expect(notificationTarget({ refType: null, refId: 1005 })).toEqual({ kind: 'none' });
    expect(notificationTarget({ refType: 'MATCH', refId: 0 })).toEqual({ kind: 'none' });
    expect(notificationTarget({ refType: 'MATCH', refId: -1 })).toEqual({ kind: 'none' });
  });
});

describe('player management rules', () => {
  const profile = {
    name: '测试球员',
    jersey: 21,
    region: '上海',
    position: 1,
    secondaryPositions: [2],
    skill: 4,
  };

  it('maps player type and account status to display text', () => {
    expect(playerTypeText(0)).toBe('外挂');
    expect(playerTypeText(1)).toBe('正式');
    expect(playerStatusText(0)).toBe('待审核');
    expect(playerStatusText(1)).toBe('正常');
    expect(playerStatusText(2)).toBe('已注销');
    expect(playerStatusText(undefined)).toBe('—');
    expect(playerStatusTone(1)).toBe('success');
    expect(playerStatusTone(0)).toBe('warning');
    expect(playerStatusTone(2)).toBe('danger');
  });

  it('maps table sort events to the PLR-05 whitelist', () => {
    expect(playerSort('jersey', 'ascending')).toBe('jersey:asc');
    expect(playerSort('skill', 'descending')).toBe('skill:desc');
    expect(playerSort('region', 'ascending')).toBe('region:asc');
    expect(playerSort('name', 'ascending')).toBe('jersey:asc');
    expect(playerSort('jersey', null)).toBe('jersey:asc');
    expect(playerSort(undefined, 'descending')).toBe('jersey:asc');
  });

  it('validates the PLR-06 profile form', () => {
    expect(playerProfileError(profile)).toBeNull();
    expect(playerProfileError({ ...profile, name: '  ' })).toBe('请输入姓名');
    expect(playerProfileError({ ...profile, name: '球'.repeat(33) })).toBe('姓名最多 32 个字符');
    expect(playerProfileError({ ...profile, jersey: null })).toBeNull();
    expect(playerProfileError({ ...profile, jersey: 65536 })).toBe('号码必须为 0–65535 的整数');
    expect(playerProfileError({ ...profile, region: '沪'.repeat(65) })).toBe('地区最多 64 个字符');
    expect(playerProfileError({ ...profile, position: 4 })).toBe('请选择主位置');
    expect(playerProfileError({ ...profile, secondaryPositions: [1, 2, 3, 0] })).toBe(
      '副位置最多 3 个',
    );
    expect(playerProfileError({ ...profile, secondaryPositions: [4] })).toBe('副位置必须为 0–3');
    expect(playerProfileError({ ...profile, secondaryPositions: [2, 2] })).toBe('副位置不能重复');
    expect(playerProfileError({ ...profile, secondaryPositions: [1] })).toBe(
      '副位置不能包含主位置',
    );
    expect(playerProfileError({ ...profile, skill: 0 })).toBe('星级必须为 1–5');
    expect(playerProfileError({ ...profile, skill: 6 })).toBe('星级必须为 1–5');
  });

  it('limits lifecycle actions to their source states', () => {
    expect(playerApprovable({ status: 0 })).toBe(true);
    expect(playerApprovable({ status: 1 })).toBe(false);
    expect(playerConvertible({ status: 1, type: 0 })).toBe(true);
    expect(playerConvertible({ status: 1, type: 1 })).toBe(false);
    expect(playerConvertible({ status: 0, type: 0 })).toBe(false);
    expect(playerDisableable({ status: 1 })).toBe(true);
    expect(playerDisableable({ status: 2 })).toBe(false);
  });

  it('validates AUTH-11 phone format', () => {
    expect(phoneError('13910000001')).toBeNull();
    expect(phoneError(' 13910000001 ')).toBeNull();
    expect(phoneError('23910000001')).toBe('手机号格式不正确');
    expect(phoneError('1391000000')).toBe('手机号格式不正确');
  });
});

describe('check-in window', () => {
  // 2026-11-15 20:00 Asia/Shanghai = 12:00Z；窗口 16:00–23:59 本地。
  const startAt = '2026-11-15T12:00:00Z';

  it('opens four hours before start and closes at end of match day', () => {
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T07:59:59Z'))).toBe('before');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T08:00:00Z'))).toBe('open');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T15:59:59Z'))).toBe('open');
    expect(checkInWindowState(startAt, Date.parse('2026-11-15T16:00:00Z'))).toBe('after');
  });

  it('handles an early-morning match whose window starts the previous day', () => {
    // 2026-11-16 01:00 Asia/Shanghai = 17:00Z，窗口从 2026-11-15 21:00 本地开始。
    const earlyStart = '2026-11-15T17:00:00Z';
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T12:59:59Z'))).toBe('before');
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T13:00:00Z'))).toBe('open');
    expect(checkInWindowState(earlyStart, Date.parse('2026-11-15T15:00:00Z'))).toBe('open');
  });
});

describe('role permissions', () => {
  const CODES = [
    'attendance:checkin:own',
    'attendance:mark:any',
    'attendance:view:any',
    'audit:view:any',
    'event:create:own',
    'event:delete:any',
    'event:edit:any',
    'event:edit:own',
    'grouping:generate:any',
    'grouping:view:any',
    'invite:create:match',
    'invite:manage:any',
    'lineup:edit:any',
    'lineup:view:any',
    'match:create:any',
    'match:edit:any',
    'match:status:any',
    'match:view:any',
    'player:edit:any',
    'player:edit:own',
    'player:view:any',
    'registration:create:own',
    'registration:remove:any',
    'role:manage:any',
    'role:view:any',
    'settings:manage:any',
    'settings:view:any',
    'user:approve:any',
  ];

  function permission(code: string, resource = code.split(':')[0] ?? ''): PermissionView {
    return { id: CODES.indexOf(code) + 1, code, name: code, resource, action: '', scope: '' };
  }

  it('maps the 28 permission codes into the 7 B08 groups', () => {
    expect(CODES).toHaveLength(28);

    const groups = groupPermissions(CODES.map((code) => permission(code)));

    expect(groups.map((group) => group.label)).toEqual([...PERMISSION_GROUP_LABELS]);
    expect(groups.reduce((count, group) => count + group.items.length, 0)).toBe(28);
    expect(groups.find((group) => group.label === '比赛')?.items).toHaveLength(4);
    expect(groups.find((group) => group.label === '球员/权限')?.items).toHaveLength(9);
  });

  it('falls back to the resource for unknown permission codes and keeps it last', () => {
    const groups = groupPermissions([
      permission('match:view:any'),
      permission('custom:do:any'),
      permission('custom:view:any'),
    ]);

    expect(permissionGroupLabel('custom:do:any', 'custom')).toBe('custom');
    expect(permissionGroupLabel('custom:do:any')).toBe('custom');
    // resource 优先于 code 首段：未知 code 仍按后端 resource 归组。
    expect(permissionGroupLabel('custom:do:any', 'audit')).toBe('球员/权限');
    expect(permissionGroupLabel('audit:view:any', 'audit')).toBe('球员/权限');
    expect(groups.map((group) => group.label)).toEqual(['比赛', 'custom']);
    expect(groups[1]?.items.map((item) => item.code)).toEqual(['custom:do:any', 'custom:view:any']);
  });

  it('validates role code and name like ROL-02', () => {
    expect(roleCodeError('COACH')).toBeNull();
    expect(roleCodeError(' E2E_123 ')).toBeNull();
    expect(roleCodeError('')).toBe('请输入角色编码');
    expect(roleCodeError('A')).toBe('角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32');
    expect(roleCodeError('coach')).toBe(
      '角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32',
    );
    expect(roleCodeError('1COACH')).toBe(
      '角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32',
    );
    expect(roleCodeError('_COACH')).toBe(
      '角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32',
    );
    expect(roleCodeError('A'.repeat(33))).toBe(
      '角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32',
    );

    expect(roleNameError('教练')).toBeNull();
    expect(roleNameError('角'.repeat(64))).toBeNull();
    expect(roleNameError('  ')).toBe('请输入角色名称');
    expect(roleNameError('角'.repeat(65))).toBe('角色名称最多 64 个字符');
  });
});
