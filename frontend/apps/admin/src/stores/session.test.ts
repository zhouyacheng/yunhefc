import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { api } from '../api/client';
import { useSessionStore } from './session';

vi.mock('../api/client', () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    setCsrfToken: vi.fn(),
    clearCsrfToken: vi.fn(),
  },
}));

const user = {
  id: 1001,
  name: '管理员',
  phoneMasked: '139****0001',
  status: 1,
  statusLabel: 'ACTIVE',
  roles: ['ADMIN', 'PLAYER'],
  createdAt: '2026-01-01T00:00:00Z',
  wechatBound: false,
};

const me = {
  user,
  permissions: ['match:view:any'],
  sections: ['matches'],
};

describe('admin session exchangeTicket', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('rotates the CSRF token and loads /me after a successful exchange', async () => {
    vi.mocked(api.post).mockResolvedValue({
      user,
      permissions: ['match:view:any'],
      csrfToken: 'rotated-token',
    });
    vi.mocked(api.get).mockResolvedValue(me);

    const session = useSessionStore();
    await session.exchangeTicket('one-time-ticket');

    expect(api.clearCsrfToken).toHaveBeenCalled();
    expect(api.post).toHaveBeenCalledWith(
      '/auth/admin-ticket/exchange',
      { ticket: 'one-time-ticket' },
      { redirectOnUnauthorized: false },
    );
    expect(api.setCsrfToken).toHaveBeenCalledWith('rotated-token');
    expect(api.get).toHaveBeenCalledWith('/me', { redirectOnUnauthorized: false });
    expect(session.isAuthenticated).toBe(true);
    expect(session.sections).toEqual(['matches']);
  });

  it('keeps an existing session when the ticket exchange fails', async () => {
    vi.mocked(api.post).mockRejectedValue(new Error('ticket expired'));

    const session = useSessionStore();
    session.user = user;

    await expect(session.exchangeTicket('expired-ticket')).rejects.toThrow('ticket expired');
    expect(session.user).toEqual(user);
    expect(session.isAuthenticated).toBe(true);
    expect(api.get).not.toHaveBeenCalled();
  });
});
