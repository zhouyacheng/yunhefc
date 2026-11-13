import { describe, expect, it } from 'vitest';

import { capacityDefaults, DEFAULT_CAPACITY_DEFAULTS } from './index';

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
