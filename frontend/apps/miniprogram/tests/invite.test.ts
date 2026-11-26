import { describe, expect, it } from 'vitest';

import type { InviteLanding, MatchDetail } from '../miniprogram/types/api';
import {
  DEFAULT_INVITE_DAYS,
  buildInviteMatchView,
  inviteBindPath,
  inviteFormError,
  inviteInvalidText,
  inviteLandingPath,
  inviteLandingTokenFromQuery,
  inviteLoginPath,
  invitePayload,
  inviteTokenFromQuery,
  isInviteUsable,
  matchIdOf,
  matchInviteHint,
  remainingUsesText,
} from '../miniprogram/utils/invite';

const NOW = Date.parse('2026-11-26T20:00:00+08:00');

function invite(patch: Partial<InviteLanding> = {}): InviteLanding {
  return {
    id: 1,
    type: 1,
    typeLabel: 'MATCH',
    team: { id: 1, name: '云河FC', shortName: '云河' },
    matchId: 9,
    match: {
      id: 9,
      title: '报名满员联调赛',
      startAt: '2026-11-28T00:08:00+08:00',
      endAt: '2026-11-28T02:08:00+08:00',
      venue: '聚龙足球场',
      status: 0,
      homeTeamName: '云河FC',
      awayTeamName: '测试队',
    },
    inviter: { id: 2, name: '测试门将01' },
    expiresAt: '2026-12-03T14:37:00+08:00',
    maxUses: null,
    usedCount: 0,
    remainingUses: null,
    status: 0,
    statusLabel: 'ACTIVE',
    exhausted: false,
    createdAt: '2026-11-26T14:37:00+08:00',
    ...patch,
  };
}

function detail(patch: Partial<MatchDetail> = {}): MatchDetail {
  return {
    id: 9,
    title: '报名满员联调赛',
    type: 1,
    typeLabel: 'INTERNAL',
    startAt: '2026-11-28T00:08:00+08:00',
    endAt: '2026-11-28T02:08:00+08:00',
    venue: '聚龙足球场',
    season: '2026',
    homeTeamName: '云河FC',
    awayTeamName: '测试队',
    scoreHome: null,
    scoreAway: null,
    status: 0,
    statusLabel: 'SIGNUP_OPEN',
    totalMatchNo: 6,
    seasonMatchNo: 6,
    capacity: 30,
    guestCapacity: 4,
    waitlistCapacity: 2,
    confirmedCount: 30,
    guestCount: 0,
    waitlistCount: 0,
    signupDeadline: '2026-11-27T00:08:00+08:00',
    cancelLock: '2026-11-27T00:38:00+08:00',
    version: 0,
    ...patch,
  };
}

describe('inviteFormError', () => {
  it('accepts the defaults (7 days, unlimited uses)', () => {
    expect(inviteFormError({ expiresInDays: 7, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: '', maxUses: '' })).toBeNull();
  });

  it('accepts the inclusive day range and string inputs', () => {
    expect(inviteFormError({ expiresInDays: 1, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: 30, maxUses: null })).toBeNull();
    expect(inviteFormError({ expiresInDays: '14', maxUses: '3' })).toBeNull();
  });

  it('rejects days outside 1–30 and non-integers', () => {
    expect(inviteFormError({ expiresInDays: 0, maxUses: null })).toBe('有效期必须为 1–30 天');
    expect(inviteFormError({ expiresInDays: 31, maxUses: null })).toBe('有效期必须为 1–30 天');
    expect(inviteFormError({ expiresInDays: 'abc', maxUses: null })).toBe('有效期必须为 1–30 天');
  });

  it('rejects a non-positive use limit', () => {
    expect(inviteFormError({ expiresInDays: 7, maxUses: 0 })).toBe('使用次数上限必须大于 0');
    expect(inviteFormError({ expiresInDays: 7, maxUses: 'x' })).toBe('使用次数上限必须大于 0');
  });
});

describe('invitePayload', () => {
  it('defaults the expiry and omits an empty use limit', () => {
    expect(invitePayload({ expiresInDays: '', maxUses: '' })).toEqual({
      expiresInDays: DEFAULT_INVITE_DAYS,
    });
    expect(invitePayload({ expiresInDays: 7, maxUses: null })).toEqual({ expiresInDays: 7 });
  });

  it('forwards valid values', () => {
    expect(invitePayload({ expiresInDays: '30', maxUses: '5' })).toEqual({
      expiresInDays: 30,
      maxUses: 5,
    });
  });
});

describe('invite routing helpers', () => {
  it('reads only the inviteToken query and trims it', () => {
    expect(inviteTokenFromQuery({ inviteToken: ' abc ' })).toBe('abc');
    expect(inviteTokenFromQuery({})).toBe('');
    expect(inviteTokenFromQuery({ token: 'ignored' })).toBe('');
  });

  it('reads the landing token from the token query', () => {
    expect(inviteLandingTokenFromQuery({ token: ' abc ' })).toBe('abc');
    expect(inviteLandingTokenFromQuery({})).toBe('');
    expect(inviteLandingTokenFromQuery({ inviteToken: 'ignored' })).toBe('');
  });

  it('builds the landing path with an encoded token', () => {
    expect(inviteLandingPath('abc/def')).toBe('/pages/invite/index?token=abc%2Fdef');
  });

  it('carries the token through login and phone binding, or falls back to plain pages', () => {
    expect(inviteLoginPath('abc')).toBe('/pages/login/index?inviteToken=abc');
    expect(inviteLoginPath('')).toBe('/pages/login/index');
    expect(inviteBindPath('abc')).toBe('/pages/phone-bind/index?inviteToken=abc');
    expect(inviteBindPath('')).toBe('/pages/phone-bind/index');
  });

  it('resolves the match id from matchId or the nested match summary', () => {
    expect(matchIdOf(invite())).toBe(9);
    expect(matchIdOf(invite({ matchId: null }))).toBe(9);
    expect(matchIdOf(invite({ matchId: null, match: null }))).toBeNull();
  });
});

describe('invite state', () => {
  it('marks only active, non-exhausted invites usable', () => {
    expect(isInviteUsable(invite())).toBe(true);
    expect(isInviteUsable(invite({ status: 1 }))).toBe(false);
    expect(isInviteUsable(invite({ status: 2 }))).toBe(false);
    expect(isInviteUsable(invite({ exhausted: true }))).toBe(false);
  });

  it('explains the invalid reason in Chinese', () => {
    expect(inviteInvalidText(invite({ status: 2 }))).toBe('该邀请已过期');
    expect(inviteInvalidText(invite({ status: 1 }))).toBe('该邀请已被撤销');
    expect(inviteInvalidText(invite({ exhausted: true }))).toBe('该邀请的使用次数已用完');
    expect(inviteInvalidText(null)).toBe('该邀请已失效');
  });

  it('shows unlimited uses instead of a remaining count', () => {
    expect(remainingUsesText(invite())).toBe('不限');
    expect(remainingUsesText(invite({ maxUses: 5, usedCount: 2, remainingUses: 3 }))).toBe('3 次');
  });
});

describe('matchInviteHint', () => {
  it('checks the deadline before capacity', () => {
    expect(matchInviteHint(detail(), NOW)).toBe('full');
    expect(matchInviteHint(detail({ confirmedCount: 1 }), NOW)).toBe('open');
    expect(matchInviteHint(detail({ signupDeadline: '2026-11-26T00:00:00+08:00' }), NOW)).toBe(
      'closed',
    );
    expect(matchInviteHint(null, NOW)).toBeNull();
  });
});

describe('buildInviteMatchView', () => {
  it('matches C07 facts when match detail is available', () => {
    const view = buildInviteMatchView(invite(), detail(), NOW);
    expect(view.title).toBe('报名满员联调赛');
    expect(view.dateText).toBe('2026/11/28 周六');
    expect(view.timeRangeText).toBe('00:08–02:08');
    expect(view.facts.map((row) => [row.label, row.value])).toEqual([
      ['报名截止', '2026/11/27 00:08'],
      ['报名锁定', '2026/11/27 00:38'],
      ['赛季场次', '2026赛季第6场（累计6场）'],
      ['场地', '聚龙足球场'],
      ['名额', '30 / 30'],
      ['邀请人', '测试门将01'],
      ['有效期至', '2026/12/03 14:37'],
      ['剩余次数', '不限'],
    ]);
    expect(view.hintText).toBe('名额已满，接受后可进入比赛查看候补');
  });

  it('keeps the INV-01 summary when match detail fails', () => {
    const view = buildInviteMatchView(invite(), null, NOW);
    expect(view.title).toBe('报名满员联调赛');
    expect(view.dateText).toBe('2026/11/28 周六');
    expect(view.facts.find((row) => row.label === '报名截止')?.value).toBe('—');
    expect(view.facts.find((row) => row.label === '名额')?.value).toBe('—');
    expect(view.facts.find((row) => row.label === '场地')?.value).toBe('聚龙足球场');
    expect(view.hintText).toBe('');
  });

  it('shows the closed hint without a capacity hint', () => {
    const view = buildInviteMatchView(
      invite(),
      detail({ signupDeadline: '2026-11-26T00:00:00+08:00' }),
      NOW,
    );
    expect(view.hintText).toBe('报名已截止');
  });
});
