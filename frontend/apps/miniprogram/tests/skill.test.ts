import { describe, expect, it } from 'vitest';

import { starParts } from '../miniprogram/utils/skill';

const parts = (skill: number | null | undefined) => starParts(skill).map((star) => star.part);

describe('starParts', () => {
  it('splits a skill into five full/half/empty stars', () => {
    expect(parts(4.5)).toEqual(['full', 'full', 'full', 'full', 'half']);
    expect(parts(3.49)).toEqual(['full', 'full', 'full', 'empty', 'empty']);
    expect(parts(5)).toEqual(['full', 'full', 'full', 'full', 'full']);
  });

  it('renders empty stars for missing or zero skill', () => {
    expect(parts(null)).toEqual(['empty', 'empty', 'empty', 'empty', 'empty']);
    expect(parts(Number.NaN)).toEqual(['empty', 'empty', 'empty', 'empty', 'empty']);
  });

  it('always returns five marks with stable keys', () => {
    expect(starParts(4.5)).toHaveLength(5);
    expect(starParts(4.5).map((star) => star.key)).toEqual([0, 1, 2, 3, 4]);
  });
});
