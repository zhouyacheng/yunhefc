import { describe, expect, it } from 'vitest';

import type { AuthUser, MatchDetail, MatchSummary, PlayerView } from '../miniprogram/types/api';
import { buildHero, HOME_FEATURES } from '../miniprogram/utils/home-view';
import { buildMatchCard, queuesOf } from '../miniprogram/utils/match-view';

function summary(overrides: Partial<MatchSummary> = {}): MatchSummary {
  return {
    id: 1,
    title: '比赛',
    type: 1,
    typeLabel: 'INTERNAL',
    startAt: '2026-12-06T20:00:00+08:00',
    endAt: '2026-12-06T22:00:00+08:00',
    venue: '聚龙足球场',
    season: '2026',
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    scoreHome: 2,
    scoreAway: 1,
    resultText: '胜',
    status: 4,
    statusLabel: 'FINISHED',
    totalMatchNo: 9,
    seasonMatchNo: 5,
    capacity: 20,
    guestCapacity: 4,
    waitlistCapacity: 3,
    confirmedCount: 10,
    guestCount: 2,
    waitlistCount: 1,
    ...overrides,
  };
}

function detail(overrides: Partial<MatchDetail> = {}): MatchDetail {
  return {
    ...summary(),
    signupDeadline: '2026-12-06T00:00:00+08:00',
    cancelLock: '2026-12-06T01:00:00+08:00',
    version: 1,
    ...overrides,
  };
}

const user = { id: 7, status: 1 } as unknown as AuthUser;
const formalPlayer = { id: 1, type: 1 } as unknown as PlayerView;

describe('home view', () => {
  it('builds the hero card and anonymous CTA from match data', () => {
    const hero = buildHero(detail(), null, null, null, Date.parse('2026-12-06T12:00:00+08:00'));

    expect(hero.metaText).toBe('12月6日 周日 20:00 · 聚龙足球场');
    expect(hero.ordinalText).toBe('2026赛季第5场（累计9场）');
    expect(hero.scoreText).toBe('2 : 1');
    expect(hero.homeWinner).toBe(true);
    expect(hero.awayWinner).toBe(false);
    expect(hero.queues.map((queue) => queue.percent)).toEqual([50, 50, 33]);
    expect(hero.cta).toMatchObject({ label: '登录后报名', action: 'login', disabled: false });
  });

  it('builds the CTA from the current user registration', () => {
    const openMatch = detail({
      status: 0,
      myRegistration: {
        id: 1,
        playerId: 1,
        type: 1,
        status: 0,
        registeredAt: '2026-12-01T10:00:00+08:00',
        active: true,
      },
      signupDeadline: '2026-12-07T00:00:00+08:00',
    });

    const hero = buildHero(
      openMatch,
      user,
      formalPlayer,
      ['registration:create:own'],
      Date.parse('2026-12-06T12:00:00+08:00'),
    );

    expect(hero.cta).toMatchObject({
      label: '查看详情',
      action: 'view-detail',
      reason: '已报名',
    });
  });

  it('maps the shared eligibility decision for viewers without signup permission', () => {
    const openMatch = detail({ status: 0, signupDeadline: '2026-12-07T00:00:00+08:00' });

    const hero = buildHero(
      openMatch,
      user,
      formalPlayer,
      [],
      Date.parse('2026-12-06T12:00:00+08:00'),
    );

    expect(hero.cta).toMatchObject({
      label: '没有报名权限',
      action: 'none',
      disabled: true,
    });
  });

  it('maps pending accounts to the shared blocked reason', () => {
    const openMatch = detail({ status: 0, signupDeadline: '2026-12-07T00:00:00+08:00' });

    const hero = buildHero(
      openMatch,
      { ...user, status: 0 } as AuthUser,
      formalPlayer,
      ['registration:create:own'],
      Date.parse('2026-12-06T12:00:00+08:00'),
    );

    expect(hero.cta).toEqual({
      label: '账号待审核，通过后可报名',
      action: 'none',
      disabled: true,
      reason: '',
    });
  });

  it('maps a full formal pool to the waitlist CTA with its hint', () => {
    const fullMatch = detail({
      status: 0,
      signupDeadline: '2026-12-07T00:00:00+08:00',
      confirmedCount: 20,
      waitlistCount: 0,
    });

    const hero = buildHero(
      fullMatch,
      user,
      formalPlayer,
      ['registration:create:own'],
      Date.parse('2026-12-06T12:00:00+08:00'),
    );

    expect(hero.cta).toEqual({
      label: '加入候补',
      action: 'register',
      disabled: false,
      reason: '正式名额已满，报名将进入候补',
    });
  });

  it('builds a recent match card', () => {
    const recent = buildMatchCard(summary());

    expect(recent.dateText).toBe('12月6日 周日 20:00');
    expect(recent.resultText).toBe('胜');
    expect(recent.resultTone).toBe('win');
    expect(recent.typeText).toBe('队内赛');
    expect(recent.statusText).toBe('已结束');
    expect(recent.queues.map((queue) => queue.label)).toEqual(['正式', '外挂', '候补']);
  });

  it('keeps the phase-2 feature titles in sync with C01', () => {
    expect(HOME_FEATURES.map((feature) => feature.title)).toEqual([
      'AI 身价',
      '投票',
      '公众号提醒',
      '资产与荣誉',
    ]);
  });

  it('labels queue rows in formal, guest, waitlist order', () => {
    expect(queuesOf(summary())).toEqual([
      { key: 'formal', label: '正式', count: 10, capacity: 20, percent: 50 },
      { key: 'guest', label: '外挂', count: 2, capacity: 4, percent: 50 },
      { key: 'waitlist', label: '候补', count: 1, capacity: 3, percent: 33 },
    ]);
  });
});
