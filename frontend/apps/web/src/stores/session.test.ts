import type { MeView, Player, User } from '@yunhefc/shared';
import { createPinia, setActivePinia } from 'pinia';
import { beforeEach, describe, expect, it, vi, type Mock } from 'vitest';

import { api } from '../api/client';
import { useSessionStore } from './session';

vi.mock('../api/client', () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    setCsrfToken: vi.fn(),
    clearCsrfToken: vi.fn(),
  },
}));

const user: User = {
  id: 1002,
  name: '测试后卫01',
  phoneMasked: '139****0002',
  status: 1,
  statusLabel: 'ACTIVE',
  roles: ['PLAYER'],
  createdAt: '2026-11-01T00:00:00.000Z',
  wechatBound: false,
};

const player: Player = {
  id: 1002,
  name: '测试后卫01',
  jersey: 22,
  type: 1,
  typeLabel: 'PLAYER',
  position: 1,
  positionLabel: 'DEFENDER',
  skill: 4,
  avatarUrl: null,
};

const me: MeView = {
  user,
  player,
  roles: ['PLAYER'],
  permissions: ['registration:create:own'],
  unreadCount: 7,
};

const getMock = vi.mocked(api.get) as Mock;
const postMock = vi.mocked(api.post) as Mock;

describe('session store', () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
  });

  it('stores unreadCount when refreshing /me', async () => {
    getMock.mockResolvedValueOnce(me);

    const store = useSessionStore();
    await store.refresh();

    expect(store.unreadCount).toBe(7);
    expect(store.isAuthenticated).toBe(true);
    expect(store.loaded).toBe(true);
    expect(api.get).toHaveBeenCalledWith('/me', { redirectOnUnauthorized: false });
  });

  it('defaults unreadCount to zero when a login response omits it', async () => {
    postMock.mockResolvedValueOnce({
      user,
      player,
      permissions: [],
      csrfToken: 'csrf-1',
    });

    const store = useSessionStore();
    await store.login('13910000002', 'Passw0rd!');

    expect(store.unreadCount).toBe(0);
    expect(store.isAuthenticated).toBe(true);
  });

  it('clears unreadCount with the rest of the session', async () => {
    getMock.mockResolvedValueOnce(me);

    const store = useSessionStore();
    await store.refresh();
    store.clearSession();

    expect(store.unreadCount).toBe(0);
    expect(store.isAuthenticated).toBe(false);
  });
});
