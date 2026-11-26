import type { User } from '@yunhefc/shared';
import { describe, expect, it } from 'vitest';

import { contentVisible } from './access';

function user(status: number): User {
  return {
    id: 2,
    name: '张伟',
    phoneMasked: '139****0002',
    status,
    statusLabel: status === 0 ? 'PENDING' : 'ACTIVE',
    roles: ['PLAYER'],
    createdAt: '2026-01-01T00:00:00+08:00',
    wechatBound: true,
  };
}

describe('contentVisible', () => {
  it('requires a logged-in, non-pending account with content:view:any', () => {
    expect(contentVisible(null, ['content:view:any'])).toBe(false);
    expect(contentVisible(user(0), ['content:view:any'])).toBe(false);
    expect(contentVisible(user(1), ['activity:signup:own'])).toBe(false);
    expect(contentVisible(user(1), ['content:view:any'])).toBe(true);
  });
});
