import { describe, expect, it } from 'vitest';
import {
  ATTENDANCE_VIEW_ANY,
  MATCH_STATUS_ANY,
  REGISTRATION_CREATE_OWN,
  hasPermission,
  isPending,
} from '../miniprogram/utils/permissions';

describe('hasPermission', () => {
  it('matches exact permission codes only', () => {
    expect(hasPermission([REGISTRATION_CREATE_OWN], REGISTRATION_CREATE_OWN)).toBe(true);
    expect(hasPermission([ATTENDANCE_VIEW_ANY], MATCH_STATUS_ANY)).toBe(false);
    expect(hasPermission(null, MATCH_STATUS_ANY)).toBe(false);
  });
});

describe('isPending', () => {
  it('treats only status 0 as pending', () => {
    expect(isPending(0)).toBe(true);
    expect(isPending(1)).toBe(false);
    expect(isPending(null)).toBe(false);
  });
});
