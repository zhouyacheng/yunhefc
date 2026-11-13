import { describe, expect, it } from 'vitest';

import type { Invite } from './auth';
import { inviteMatchTitle, inviteQuotaText, inviteStatusMeta, inviteTypeText } from './index';

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
    expect(inviteStatusMeta(invite({ status: 1, exhausted: true }), NOW)).toEqual({
      text: '已撤销',
      tone: 'info',
    });
  });

  it('treats status 2 or a past expiresAt as expired', () => {
    expect(inviteStatusMeta(invite({ status: 2 }), NOW)).toEqual({
      text: '已过期',
      tone: 'warning',
    });
    expect(inviteStatusMeta(invite({ expiresAt: '2026-11-12T12:00:00Z' }), NOW).text).toBe(
      '已过期',
    );
  });

  it('reports exhausted only after expiry checks', () => {
    expect(inviteStatusMeta(invite({ exhausted: true }), NOW)).toEqual({
      text: '已用尽',
      tone: 'warning',
    });
  });

  it('falls back to active', () => {
    expect(inviteStatusMeta(invite(), NOW)).toEqual({ text: '有效', tone: 'success' });
  });

  it('renders quota with unlimited maxUses', () => {
    expect(inviteQuotaText({ usedCount: 2, maxUses: 5 })).toBe('2 / 5');
    expect(inviteQuotaText({ usedCount: 0, maxUses: null })).toBe('0 / 不限');
  });

  it('reads the match title defensively', () => {
    expect(
      inviteMatchTitle({
        match: {
          id: 1005,
          title: '橙队VS兰队',
          startAt: '2026-11-20T12:00:00Z',
          endAt: '2026-11-20T14:00:00Z',
          venue: '聚龙足球场',
          status: 0,
        },
      }),
    ).toBe('橙队VS兰队');
    expect(inviteMatchTitle({ match: null })).toBe('—');
    expect(inviteMatchTitle({})).toBe('—');
  });
});
