import { describe, expect, it } from 'vitest';

import type { Invite } from './auth';
import {
  inviteMatchTitle,
  inviteQuotaText,
  inviteStatusText,
  inviteStatusTone,
  inviteTypeText,
} from './index';

const NOW = Date.parse('2026-11-13T12:00:00Z');

type StatusInput = Pick<Invite, 'status' | 'expiresAt' | 'exhausted'>;

function invite(overrides: Partial<StatusInput> = {}): StatusInput {
  return {
    status: 0,
    expiresAt: '2026-11-20T12:00:00Z',
    exhausted: false,
    ...overrides,
  };
}

describe('invite display helpers', () => {
  it('maps invite types to Chinese text', () => {
    expect(inviteTypeText(0)).toBe('入队邀请');
    expect(inviteTypeText(1)).toBe('比赛邀请');
    expect(inviteTypeText(undefined)).toBe('—');
  });

  it('prefers revoked over every other state', () => {
    expect(inviteStatusText(invite({ status: 1, exhausted: true }), NOW)).toBe('已撤销');
    expect(inviteStatusTone(invite({ status: 1, exhausted: true }), NOW)).toBe('info');
  });

  it('treats status 2 or a past expiresAt as expired', () => {
    expect(inviteStatusText(invite({ status: 2 }), NOW)).toBe('已过期');
    expect(inviteStatusText(invite({ expiresAt: '2026-11-12T12:00:00Z' }), NOW)).toBe('已过期');
    expect(inviteStatusTone(invite({ status: 2 }), NOW)).toBe('warning');
  });

  it('reports exhausted only after expiry checks', () => {
    expect(inviteStatusText(invite({ exhausted: true }), NOW)).toBe('已用尽');
    expect(inviteStatusTone(invite({ exhausted: true }), NOW)).toBe('warning');
  });

  it('falls back to active', () => {
    expect(inviteStatusText(invite(), NOW)).toBe('有效');
    expect(inviteStatusTone(invite(), NOW)).toBe('success');
  });

  it('renders quota with unlimited maxUses', () => {
    expect(inviteQuotaText({ usedCount: 2, maxUses: 5 })).toBe('2 / 5');
    expect(inviteQuotaText({ usedCount: 0, maxUses: null })).toBe('0 / 不限');
  });

  it('reads the match title defensively', () => {
    expect(inviteMatchTitle({ match: { title: '橙队VS兰队' } })).toBe('橙队VS兰队');
    expect(inviteMatchTitle({ match: null })).toBe('—');
    expect(inviteMatchTitle({})).toBe('—');
  });
});
