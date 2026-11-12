import { describe, expect, it } from 'vitest';

import {
  type InviteLandingState,
  inviteInvalidText,
  inviteLandingState,
  inviteTypeText,
  matchInviteHint,
  remainingUsesText,
} from './invite-state';
import type { InviteLandingView } from './types';

function view(overrides: Partial<InviteLandingView> = {}): InviteLandingView {
  return {
    id: 5001,
    type: 0,
    typeLabel: 'TEAM',
    team: { id: 1, name: '云河FC', shortName: '云河FC' },
    matchId: null,
    match: null,
    inviter: { id: 1002, name: '测试后卫01' },
    expiresAt: '2026-11-19T00:00:00Z',
    maxUses: null,
    usedCount: 0,
    remainingUses: null,
    status: 0,
    statusLabel: 'ACTIVE',
    exhausted: false,
    createdAt: '2026-11-12T00:00:00Z',
    ...overrides,
  };
}

function state(invite: InviteLandingView | null, member = false): InviteLandingState {
  return inviteLandingState({ invite, member });
}

describe('invite landing state', () => {
  it('treats a missing invite as not found', () => {
    expect(state(null)).toBe('notFound');
  });

  it.each([1, 2])('treats status %i as invalid', (status) => {
    expect(state(view({ status }))).toBe('invalid');
  });

  it('treats an exhausted active invite as invalid', () => {
    expect(state(view({ exhausted: true }))).toBe('invalid');
  });

  it('shows a usable team invite to a non-member', () => {
    expect(state(view())).toBe('team');
  });

  it('shows the member state to an existing member', () => {
    expect(state(view(), true)).toBe('member');
  });

  it('always treats a match invite as a match, even for members', () => {
    expect(state(view({ type: 1 }), false)).toBe('match');
    expect(state(view({ type: 1 }), true)).toBe('match');
  });
});

describe('invite copy', () => {
  it('maps invite types to Chinese labels', () => {
    expect(inviteTypeText(0)).toBe('入队邀请');
    expect(inviteTypeText(1)).toBe('比赛邀请');
  });

  it('explains each invalid reason', () => {
    expect(inviteInvalidText(view({ status: 2 }))).toBe('该邀请已过期');
    expect(inviteInvalidText(view({ status: 1 }))).toBe('该邀请已被撤销');
    expect(inviteInvalidText(view({ exhausted: true }))).toBe('该邀请的使用次数已用完');
    expect(inviteInvalidText(view())).toBe('该邀请已失效');
    expect(inviteInvalidText(null)).toBe('该邀请已失效');
  });

  it('prefers the status reason over exhausted', () => {
    expect(inviteInvalidText(view({ status: 2, exhausted: true }))).toBe('该邀请已过期');
  });

  it('renders unlimited and remaining uses', () => {
    expect(remainingUsesText(view({ maxUses: null, usedCount: 3 }))).toBe('不限');
    expect(remainingUsesText(view({ maxUses: 5, usedCount: 2, remainingUses: 3 }))).toBe('3 次');
    expect(remainingUsesText(view({ maxUses: 5, usedCount: 5, remainingUses: 0 }))).toBe('0 次');
    expect(remainingUsesText(view({ maxUses: 5, usedCount: 2, remainingUses: null }))).toBe('3 次');
  });
});

describe('match invite hint', () => {
  const detail = {
    confirmedCount: 3,
    capacity: 10,
    signupDeadline: '2026-11-12T10:00:00Z',
  };

  it('returns null without match detail', () => {
    expect(matchInviteHint(null, Date.parse('2026-11-12T09:00:00Z'))).toBeNull();
  });

  it('closes at the signup deadline', () => {
    expect(matchInviteHint(detail, Date.parse('2026-11-12T10:00:00Z'))).toBe('closed');
  });

  it('reports a full match', () => {
    expect(
      matchInviteHint({ ...detail, confirmedCount: 10 }, Date.parse('2026-11-12T09:00:00Z')),
    ).toBe('full');
  });

  it('stays open before the deadline with room', () => {
    expect(matchInviteHint(detail, Date.parse('2026-11-12T09:00:00Z'))).toBe('open');
  });
});
