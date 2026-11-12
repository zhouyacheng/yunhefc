import { describe, expect, it } from 'vitest';

import {
  ADMIN_SECTION_PERMISSIONS,
  ADMIN_SECTIONS,
  ALL_PERMISSIONS,
  PERMISSIONS,
  canAccessSection,
  hasBackendPermission,
  hasPermission,
} from './index';

describe('permission catalogue', () => {
  it('exposes the 28 permission codes from 05 §5.1', () => {
    expect(ALL_PERMISSIONS).toHaveLength(28);
    expect(new Set(ALL_PERMISSIONS).size).toBe(28);
    expect(ALL_PERMISSIONS).toContain('attendance:checkin:own');
    expect(ALL_PERMISSIONS).toContain('user:approve:any');
  });

  it('uses resource:action:scope for every code', () => {
    for (const code of ALL_PERMISSIONS) {
      expect(code).toMatch(/^[a-z]+:[a-z]+:[a-z]+$/);
    }
  });
});

describe('hasPermission', () => {
  it('accepts an array', () => {
    expect(hasPermission(['match:view:any'], PERMISSIONS.MATCH_VIEW_ANY)).toBe(true);
  });

  it('accepts a Set', () => {
    expect(hasPermission(new Set(['match:view:any']), PERMISSIONS.MATCH_VIEW_ANY)).toBe(true);
  });

  it('returns false when the code is missing or the grant list is empty', () => {
    expect(hasPermission([], PERMISSIONS.MATCH_VIEW_ANY)).toBe(false);
    expect(hasPermission(null, PERMISSIONS.MATCH_VIEW_ANY)).toBe(false);
    expect(hasPermission(undefined, PERMISSIONS.MATCH_VIEW_ANY)).toBe(false);
  });

  it('requires all of several permissions when given a list', () => {
    expect(
      hasPermission(
        ['match:view:any', 'match:edit:any'],
        [PERMISSIONS.MATCH_VIEW_ANY, PERMISSIONS.MATCH_EDIT_ANY],
      ),
    ).toBe(true);
    expect(
      hasPermission(['match:view:any'], [PERMISSIONS.MATCH_VIEW_ANY, PERMISSIONS.MATCH_EDIT_ANY]),
    ).toBe(false);
  });
});

describe('admin sections', () => {
  it('defines every backend section and maps it to 05 §5.1', () => {
    expect(ADMIN_SECTIONS).toHaveLength(10);
    expect(ADMIN_SECTIONS).not.toContain('dashboard');
    expect(Object.keys(ADMIN_SECTION_PERMISSIONS)).toEqual([...ADMIN_SECTIONS]);
    expect(ADMIN_SECTION_PERMISSIONS.matches).toEqual(['match:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.registrations).toEqual(['attendance:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.events).toEqual(['event:edit:any']);
    expect(ADMIN_SECTION_PERMISSIONS.lineup).toEqual(['lineup:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.grouping).toEqual(['grouping:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.players).toEqual(['player:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.roles).toEqual(['role:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.settings).toEqual(['settings:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.audit).toEqual(['audit:view:any']);
    expect(ADMIN_SECTION_PERMISSIONS.invites).toEqual(['invite:manage:any']);
  });

  it('checks the section permission for every section', () => {
    expect(canAccessSection(['attendance:view:any'], 'registrations')).toBe(true);
    expect(canAccessSection(['attendance:view:any'], 'matches')).toBe(false);
  });

  it('separates backend permissions from C-only permissions', () => {
    expect(hasBackendPermission(['registration:create:own'])).toBe(false);
    expect(hasBackendPermission(['match:view:any'])).toBe(true);
    expect(hasBackendPermission([])).toBe(false);
  });
});
