import { describe, expect, it } from 'vitest';

import {
  queuePercent,
  resolveDisplayedScore,
  resultTone,
  scoreText,
  statusTone,
  winnerSide,
} from './presentation';

describe('statusTone', () => {
  it('maps match statuses to prototype badge tones', () => {
    expect(statusTone(0)).toBe('open');
    expect(statusTone(1)).toBe('closed');
    expect(statusTone(2)).toBe('locked');
    expect(statusTone(3)).toBe('live');
    expect(statusTone(4)).toBe('finished');
    expect(statusTone(5)).toBe('postponed');
    expect(statusTone(6)).toBe('cancelled');
    expect(statusTone(null)).toBe('closed');
    expect(statusTone(undefined)).toBe('closed');
  });
});

describe('resultTone', () => {
  it('maps result text to win/draw/loss tones', () => {
    expect(resultTone('胜')).toBe('win');
    expect(resultTone('平')).toBe('draw');
    expect(resultTone('负')).toBe('loss');
    expect(resultTone('0:0')).toBe('muted');
    expect(resultTone(null)).toBe('muted');
  });
});

describe('winnerSide', () => {
  it('only highlights a winner for finished matches with unequal scores', () => {
    expect(winnerSide({ status: 4, scoreHome: 2, scoreAway: 1 })).toBe(0);
    expect(winnerSide({ status: 4, scoreHome: 0, scoreAway: 3 })).toBe(1);
    expect(winnerSide({ status: 4, scoreHome: 1, scoreAway: 1 })).toBeNull();
    expect(winnerSide({ status: 3, scoreHome: 2, scoreAway: 1 })).toBeNull();
    expect(winnerSide({ status: 4, scoreHome: null, scoreAway: 1 })).toBeNull();
  });
});

describe('queuePercent', () => {
  it('clamps queue usage to 0-100', () => {
    expect(queuePercent(0, 30)).toBe(0);
    expect(queuePercent(15, 30)).toBe(50);
    expect(queuePercent(40, 30)).toBe(100);
    expect(queuePercent(3, 0)).toBe(0);
  });
});

describe('resolveDisplayedScore', () => {
  it('prefers the event derived score when present', () => {
    expect(
      resolveDisplayedScore({ scoreHome: 3, scoreAway: 2 }, { scoreHome: 1, scoreAway: 0 }),
    ).toEqual({ scoreHome: 1, scoreAway: 0 });
  });

  it('falls back to the server final score without a derived score', () => {
    expect(resolveDisplayedScore({ scoreHome: 3, scoreAway: 2 }, null)).toEqual({
      scoreHome: 3,
      scoreAway: 2,
    });
  });

  it('returns null when both sources are missing', () => {
    expect(resolveDisplayedScore(null, undefined)).toBeNull();
  });
});

describe('scoreText', () => {
  it('shows VS until a stored score exists', () => {
    expect(scoreText({ scoreHome: null, scoreAway: null })).toBe('VS');
    expect(scoreText({ scoreHome: undefined, scoreAway: undefined })).toBe('VS');
  });

  it('formats stored scores', () => {
    expect(scoreText({ scoreHome: 2, scoreAway: 1 })).toBe('2 : 1');
    expect(scoreText({ scoreHome: 2, scoreAway: null })).toBe('2 : 0');
    expect(scoreText({ scoreHome: 0, scoreAway: 0 })).toBe('0 : 0');
  });
});
