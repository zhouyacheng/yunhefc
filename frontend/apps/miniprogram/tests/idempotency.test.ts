import { describe, expect, it } from 'vitest';
import { newIdempotencyKey } from '../miniprogram/utils/idempotency';

describe('newIdempotencyKey', () => {
  it('produces an opaque key within the backend limit', () => {
    const key = newIdempotencyKey(1_700_000_000_000, () => 0.5);

    expect(key).toMatch(/^mp-[0-9a-z]+-[0-9a-z]+-[0-9a-z]+$/);
    expect(key.length).toBeLessThanOrEqual(64);
  });

  it('stays unique for repeated calls in the same millisecond', () => {
    const keys = new Set([
      newIdempotencyKey(1_700_000_000_000, () => 0.1),
      newIdempotencyKey(1_700_000_000_000, () => 0.1),
      newIdempotencyKey(1_700_000_000_000, () => 0.1),
    ]);

    expect(keys.size).toBe(3);
  });

  it('only uses characters accepted by the backend', () => {
    for (let index = 0; index < 20; index += 1) {
      expect(newIdempotencyKey()).toMatch(/^[\x21-\x7e]{1,64}$/);
    }
  });
});
