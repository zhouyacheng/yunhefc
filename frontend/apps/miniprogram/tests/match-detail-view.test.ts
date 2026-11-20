import { describe, expect, it } from 'vitest';

import type { MatchDetail, MatchEvent } from '../miniprogram/types/api';
import {
  buildEventRows,
  buildMatchDetailHeader,
  eventDetailText,
} from '../miniprogram/utils/match-detail-view';

function detail(overrides: Partial<MatchDetail> = {}): MatchDetail {
  return {
    id: 1001,
    title: '2026赛季第5场',
    type: 0,
    typeLabel: 'OFFICIAL',
    startAt: '2026-12-03T20:00:00+08:00',
    endAt: '2026-12-03T22:00:00+08:00',
    venue: '聚龙足球场',
    season: '2026',
    homeTeamName: '云河FC',
    awayTeamName: '对手FC',
    scoreHome: 1,
    scoreAway: 0,
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
    signupDeadline: '2026-12-02T00:00:00+08:00',
    cancelLock: '2026-12-03T01:00:00+08:00',
    version: 3,
    derivedScore: null,
    ...overrides,
  };
}

function event(overrides: Partial<MatchEvent> = {}): MatchEvent {
  return {
    id: 1,
    type: 0,
    team: 0,
    minute: 23,
    half: 0,
    penalty: false,
    player: { id: 11, name: '张三' },
    ...overrides,
  };
}

describe('buildMatchDetailHeader', () => {
  it('uses Chinese labels instead of backend enum names', () => {
    const header = buildMatchDetailHeader(
      detail({ type: 1, typeLabel: 'INTERNAL', statusLabel: 'FINISHED' }),
      null,
    );
    expect(header.typeText).toBe('队内赛');
    expect(header.statusText).toBe('已结束');
    expect(header.typeTone).toBe('internal');
    expect(header.statusTone).toBe('finished');
  });

  it('prefers the event-derived score over the stored score', () => {
    expect(buildMatchDetailHeader(detail(), { scoreHome: 3, scoreAway: 1 }).scoreText).toBe(
      '3 : 1',
    );
    expect(
      buildMatchDetailHeader(detail({ derivedScore: { scoreHome: 2, scoreAway: 1 } }), null)
        .scoreText,
    ).toBe('2 : 1');
    expect(
      buildMatchDetailHeader(detail({ derivedScore: { scoreHome: 2, scoreAway: 1 } }), {
        scoreHome: 4,
        scoreAway: 1,
      }).scoreText,
    ).toBe('4 : 1');
    expect(buildMatchDetailHeader(detail({ derivedScore: null }), null).scoreText).toBe('1 : 0');
  });

  it('shows VS when no score exists', () => {
    const header = buildMatchDetailHeader(
      detail({ scoreHome: null, scoreAway: null, resultText: null, status: 0 }),
      null,
    );
    expect(header.scoreText).toBe('VS');
    expect(header.resultText).toBe('');
  });

  it('highlights the winner only for finished unequal scores', () => {
    expect(buildMatchDetailHeader(detail(), null)).toMatchObject({
      homeWinner: true,
      awayWinner: false,
    });
    expect(buildMatchDetailHeader(detail({ scoreHome: 0, scoreAway: 2 }), null)).toMatchObject({
      homeWinner: false,
      awayWinner: true,
    });
    expect(buildMatchDetailHeader(detail({ scoreHome: 2, scoreAway: 2 }), null)).toMatchObject({
      homeWinner: false,
      awayWinner: false,
    });
    expect(
      buildMatchDetailHeader(detail({ status: 0, scoreHome: 2, scoreAway: 1 }), null),
    ).toMatchObject({ homeWinner: false, awayWinner: false });
  });

  it('builds the C03 meta row', () => {
    const header = buildMatchDetailHeader(detail(), null);
    expect(header.dateText).toBe('2026/12/03 周四');
    expect(header.timeRangeText).toBe('20:00–22:00');
    expect(header.signupDeadlineText).toBe('2026/12/02 00:00');
    expect(header.cancelLockText).toBe('2026/12/03 01:00');
    expect(header.ordinalText).toBe('2026赛季第5场（累计9场）');
    expect(header.venue).toBe('聚龙足球场');
  });

  it('shows a placeholder when the cancel lock is missing', () => {
    expect(buildMatchDetailHeader(detail({ cancelLock: undefined }), null).cancelLockText).toBe(
      '—',
    );
  });

  it('flags cancelled and postponed matches with history counts', () => {
    const cancelled = buildMatchDetailHeader(detail({ status: 6 }), null);
    expect(cancelled.isCancelled).toBe(true);
    expect(cancelled.isPostponed).toBe(false);
    expect(cancelled.cancelledCountsText).toBe('历史报名：正式 10 · 外挂 2 · 候补 1');

    const postponed = buildMatchDetailHeader(detail({ status: 5 }), null);
    expect(postponed.isCancelled).toBe(false);
    expect(postponed.isPostponed).toBe(true);
  });
});

describe('buildEventRows', () => {
  const match = { homeTeamName: '云河FC', awayTeamName: '对手FC' };

  it('formats goals with assist and penalty', () => {
    const rows = buildEventRows(
      [
        event({
          type: 0,
          assistPlayer: { id: 12, name: '李四' },
          penalty: true,
          minute: 41,
          half: 1,
        }),
      ],
      match,
    );
    expect(rows).toEqual([
      {
        id: 1,
        timeText: "下半场 41'",
        typeText: '进球',
        detailText: '张三 · 助攻 李四 · 点球',
        teamText: '云河FC',
        side: 'home',
      },
    ]);
  });

  it('formats substitutions with in/out players', () => {
    const row = buildEventRows(
      [
        event({
          type: 2,
          team: 1,
          inPlayer: { id: 20, name: '王五' },
          outPlayer: { id: 21, name: '赵六' },
          player: null,
          minute: 60,
        }),
      ],
      match,
    )[0];
    expect(row).toMatchObject({
      timeText: "上半场 60'",
      typeText: '换人',
      detailText: '王五 换上 · 赵六 换下',
      teamText: '对手FC',
      side: 'away',
    });
  });

  it('falls back to dashes for missing players and handles empty input', () => {
    expect(eventDetailText(event({ player: null }))).toBe('—');
    expect(buildEventRows([], match)).toEqual([]);
    expect(buildEventRows(null, match)).toEqual([]);
  });
});
