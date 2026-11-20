import { describe, expect, it } from 'vitest';

import { adminEntryUrl } from '../miniprogram/utils/admin-entry';

describe('adminEntryUrl', () => {
  it('puts the one-time ticket in the fragment', () => {
    expect(adminEntryUrl('ticket-1')).toBe('https://www.yunhefc.com/admin/#ticket=ticket-1');
    expect(adminEntryUrl('a b')).toBe('https://www.yunhefc.com/admin/#ticket=a%20b');
  });

  it('falls back to the plain admin entry when the ticket is missing', () => {
    expect(adminEntryUrl(null)).toBe('https://www.yunhefc.com/admin/');
  });
});
