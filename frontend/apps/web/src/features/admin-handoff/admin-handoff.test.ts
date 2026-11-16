import { describe, expect, it, vi } from 'vitest';

import { adminUrl, openAdmin } from './admin-handoff';

describe('adminUrl', () => {
  it('normalizes the base and joins path and query', () => {
    expect(
      adminUrl({ path: '/registrations', query: { match: 1002 } }, 'http://localhost:5274/admin'),
    ).toBe('http://localhost:5274/admin/registrations?match=1002');
    expect(adminUrl({ path: 'events', query: { match: 1002 } }, '/admin/')).toBe(
      '/admin/events?match=1002',
    );
  });

  it('keeps the bare base when no target is given', () => {
    expect(adminUrl({}, '/admin/')).toBe('/admin/');
  });
});

describe('openAdmin', () => {
  it('navigates with the issued ticket in the URL fragment', async () => {
    const navigate = vi.fn();
    const issueTicket = vi.fn().mockResolvedValue({ ticket: 'abc_-123', expiresIn: 60 });

    await openAdmin(
      { path: '/registrations', query: { match: 1002 } },
      { adminBase: '/admin/', issueTicket, navigate },
    );

    expect(issueTicket).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith('/admin/registrations?match=1002#ticket=abc_-123');
  });

  it('falls back to the plain admin URL when ticket issuance fails', async () => {
    const navigate = vi.fn();
    const issueTicket = vi.fn().mockRejectedValue(new Error('no session'));

    await openAdmin(
      { path: '/events', query: { match: 1002 } },
      { adminBase: '/admin/', issueTicket, navigate },
    );

    expect(navigate).toHaveBeenCalledWith('/admin/events?match=1002');
  });
});
