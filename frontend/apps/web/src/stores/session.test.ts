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

  it('registers with the invite token, then refreshes permissions', async () => {
    postMock.mockResolvedValueOnce({ user, player, csrfToken: 'csrf-register' });
    getMock.mockResolvedValueOnce(me);

    const store = useSessionStore();
    await store.register({
      phone: '13910000021',
      code: '123456',
      password: 'Passw0rd!',
      name: '测试待审核01',
      position: 2,
      inviteToken: 'B3-INVITE-TEAM',
    });

    expect(api.post).toHaveBeenCalledWith('/auth/register', {
      phone: '13910000021',
      code: '123456',
      password: 'Passw0rd!',
      name: '测试待审核01',
      position: 2,
      inviteToken: 'B3-INVITE-TEAM',
    });
    expect(api.setCsrfToken).toHaveBeenCalledWith('csrf-register');
    expect(api.get).toHaveBeenCalledWith('/me', { redirectOnUnauthorized: false });
    expect(store.user).toEqual(user);
    expect(store.player).toEqual(player);
    expect(store.permissions).toEqual(me.permissions);
    expect(store.loaded).toBe(true);
  });

  it('registers via WeChat without a phone and hydrates permissions', async () => {
    const wechatUser: User = {
      ...user,
      phoneMasked: null,
      status: 0,
      statusLabel: 'PENDING',
      wechatBound: true,
    };
    postMock.mockResolvedValueOnce({ user: wechatUser, player, csrfToken: 'csrf-wechat' });
    getMock.mockResolvedValueOnce({ ...me, user: wechatUser, permissions: [] });

    const store = useSessionStore();
    await store.registerViaWechat({ name: '微信球员', position: 2, inviteToken: 'tok' });

    expect(api.post).toHaveBeenCalledWith('/auth/wechat/bind-phone', {
      name: '微信球员',
      position: 2,
      inviteToken: 'tok',
    });
    expect(api.setCsrfToken).toHaveBeenCalledWith('csrf-wechat');
    expect(store.user?.phoneMasked).toBeNull();
    expect(store.user?.wechatBound).toBe(true);
    expect(store.user?.status).toBe(0);
  });

  it('binds a phone and refreshes the session', async () => {
    const boundUser: User = { ...user, phoneMasked: '139****0061' };
    postMock.mockResolvedValueOnce(boundUser);
    getMock.mockResolvedValueOnce({ ...me, user: boundUser });

    const store = useSessionStore();
    await store.bindPhone({ phone: '13900000061', code: '123456' });

    expect(api.post).toHaveBeenCalledWith('/me/phone', {
      phone: '13900000061',
      code: '123456',
    });
    expect(store.user?.phoneMasked).toBe('139****0061');
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
