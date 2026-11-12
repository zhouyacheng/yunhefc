import { describe, expect, it } from 'vitest';

import { matchTagType, scoreText } from './presentation';

describe('matchTagType', () => {
  it('maps match statuses to Vant tag types', () => {
    expect(matchTagType(0)).toBe('primary');
    expect(matchTagType(1)).toBe('primary');
    expect(matchTagType(2)).toBe('primary');
    expect(matchTagType(null)).toBe('primary');
    expect(matchTagType(undefined)).toBe('primary');
    expect(matchTagType(3)).toBe('warning');
    expect(matchTagType(4)).toBe('success');
    expect(matchTagType(5)).toBe('danger');
    expect(matchTagType(6)).toBe('danger');
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
