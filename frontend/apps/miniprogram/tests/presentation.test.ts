import { describe, expect, it } from 'vitest';

import {
  matchStatusText,
  matchTypeText,
  queuePercent,
  resultTone,
  scoreText,
  seasonMatchText,
  statusTone,
  typeTone,
  winnerSide,
} from '../miniprogram/utils/presentation';

describe('match presentation', () => {
  it('maps status tones', () => {
    expect([0, 1, 2, 3, 4, 5, 6].map(statusTone)).toEqual([
      'open',
      'closed',
      'locked',
      'live',
      'finished',
      'postponed',
      'cancelled',
    ]);
    expect(statusTone(9)).toBe('closed');
    expect(statusTone(null)).toBe('closed');
  });

  it('maps type tones', () => {
    expect([0, 1, 2, 3].map(typeTone)).toEqual([
      'external',
      'internal',
      'training',
      'friendly',
    ]);
    expect(typeTone(9)).toBe('external');
  });

  it('maps result tones by text', () => {
    expect(resultTone('胜')).toBe('win');
    expect(resultTone('平')).toBe('draw');
    expect(resultTone('负')).toBe('loss');
    expect(resultTone(null)).toBe('muted');
    expect(resultTone('其他')).toBe('muted');
  });

  it('only highlights a winner for finished matches with unequal scores', () => {
    expect(winnerSide({ status: 4, scoreHome: 3, scoreAway: 1 })).toBe(0);
    expect(winnerSide({ status: 4, scoreHome: 1, scoreAway: 3 })).toBe(1);
    expect(winnerSide({ status: 4, scoreHome: 2, scoreAway: 2 })).toBeNull();
    expect(winnerSide({ status: 0, scoreHome: 3, scoreAway: 1 })).toBeNull();
    expect(winnerSide({ status: 4, scoreHome: null, scoreAway: 1 })).toBeNull();
  });

  it('clamps and rounds queue percentages', () => {
    expect(queuePercent(5, 10)).toBe(50);
    expect(queuePercent(15, 10)).toBe(100);
    expect(queuePercent(-1, 10)).toBe(0);
    expect(queuePercent(1, 0)).toBe(0);
    expect(queuePercent(2, 3)).toBe(67);
  });

  it('formats scores', () => {
    expect(scoreText({ scoreHome: null, scoreAway: null })).toBe('VS');
    expect(scoreText({ scoreHome: 2, scoreAway: 1 })).toBe('2 : 1');
    expect(scoreText({ scoreHome: 2, scoreAway: null })).toBe('2 : 0');
  });

  it('maps labels with the same wording as Web', () => {
    expect([0, 1, 2, 3, 4, 5, 6].map(matchStatusText)).toEqual([
      '报名中',
      '报名截止',
      '已锁定',
      '进行中',
      '已结束',
      '已延期',
      '已取消',
    ]);
    expect(matchStatusText(9)).toBe('');
    expect([0, 1, 2, 3].map(matchTypeText)).toEqual([
      '对外比赛',
      '队内赛',
      '训练赛',
      '友谊赛',
    ]);
    expect(
      seasonMatchText({ season: '2026', seasonMatchNo: 5, totalMatchNo: 5 }),
    ).toBe('2026赛季第5场（累计5场）');
  });
});
