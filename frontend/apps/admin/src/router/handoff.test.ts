import { describe, expect, it } from 'vitest';

import { readTicket, stripTicket } from './handoff';

describe('readTicket', () => {
  it('reads the ticket from the URL fragment', () => {
    expect(readTicket('#ticket=abc_-123')).toBe('abc_-123');
    expect(readTicket('#foo=1&ticket=abc')).toBe('abc');
  });

  it('ignores empty, missing and oversized tickets', () => {
    expect(readTicket('')).toBeUndefined();
    expect(readTicket('#foo=1')).toBeUndefined();
    expect(readTicket('#ticket=')).toBeUndefined();
    expect(readTicket(`#ticket=${'a'.repeat(129)}`)).toBeUndefined();
  });
});

describe('stripTicket', () => {
  it('drops the fragment but preserves path and query', () => {
    expect(
      stripTicket({ path: '/registrations', query: { match: '1002' }, hash: '#ticket=abc' }),
    ).toEqual({ path: '/registrations', query: { match: '1002' }, hash: '' });
  });

  it('preserves repeated query values', () => {
    expect(
      stripTicket({ path: '/events', query: { match: ['1002', '1003'] }, hash: '#ticket=abc' }),
    ).toEqual({ path: '/events', query: { match: ['1002', '1003'] }, hash: '' });
  });
});
