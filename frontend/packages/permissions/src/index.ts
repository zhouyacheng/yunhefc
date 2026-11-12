/**
 * 05 §5.1 权限码目录。后端 `AUTH-10` / `AUTH-17` 返回的 permissions 与 sections 是唯一权威，
 * 前端只用于导航与渲染判断。
 */
export const ATTENDANCE_CHECKIN_OWN = 'attendance:checkin:own' as const;
export const ATTENDANCE_MARK_ANY = 'attendance:mark:any' as const;
export const ATTENDANCE_VIEW_ANY = 'attendance:view:any' as const;
export const AUDIT_VIEW_ANY = 'audit:view:any' as const;
export const EVENT_CREATE_OWN = 'event:create:own' as const;
export const EVENT_DELETE_ANY = 'event:delete:any' as const;
export const EVENT_EDIT_ANY = 'event:edit:any' as const;
export const EVENT_EDIT_OWN = 'event:edit:own' as const;
export const GROUPING_GENERATE_ANY = 'grouping:generate:any' as const;
export const GROUPING_VIEW_ANY = 'grouping:view:any' as const;
export const INVITE_CREATE_MATCH = 'invite:create:match' as const;
export const INVITE_MANAGE_ANY = 'invite:manage:any' as const;
export const LINEUP_EDIT_ANY = 'lineup:edit:any' as const;
export const LINEUP_VIEW_ANY = 'lineup:view:any' as const;
export const MATCH_CREATE_ANY = 'match:create:any' as const;
export const MATCH_EDIT_ANY = 'match:edit:any' as const;
export const MATCH_STATUS_ANY = 'match:status:any' as const;
export const MATCH_VIEW_ANY = 'match:view:any' as const;
export const PLAYER_EDIT_ANY = 'player:edit:any' as const;
export const PLAYER_EDIT_OWN = 'player:edit:own' as const;
export const PLAYER_VIEW_ANY = 'player:view:any' as const;
export const REGISTRATION_CREATE_OWN = 'registration:create:own' as const;
export const REGISTRATION_REMOVE_ANY = 'registration:remove:any' as const;
export const ROLE_MANAGE_ANY = 'role:manage:any' as const;
export const ROLE_VIEW_ANY = 'role:view:any' as const;
export const SETTINGS_MANAGE_ANY = 'settings:manage:any' as const;
export const SETTINGS_VIEW_ANY = 'settings:view:any' as const;
export const USER_APPROVE_ANY = 'user:approve:any' as const;

export const PERMISSIONS = {
  ATTENDANCE_CHECKIN_OWN,
  ATTENDANCE_MARK_ANY,
  ATTENDANCE_VIEW_ANY,
  AUDIT_VIEW_ANY,
  EVENT_CREATE_OWN,
  EVENT_DELETE_ANY,
  EVENT_EDIT_ANY,
  EVENT_EDIT_OWN,
  GROUPING_GENERATE_ANY,
  GROUPING_VIEW_ANY,
  INVITE_CREATE_MATCH,
  INVITE_MANAGE_ANY,
  LINEUP_EDIT_ANY,
  LINEUP_VIEW_ANY,
  MATCH_CREATE_ANY,
  MATCH_EDIT_ANY,
  MATCH_STATUS_ANY,
  MATCH_VIEW_ANY,
  PLAYER_EDIT_ANY,
  PLAYER_EDIT_OWN,
  PLAYER_VIEW_ANY,
  REGISTRATION_CREATE_OWN,
  REGISTRATION_REMOVE_ANY,
  ROLE_MANAGE_ANY,
  ROLE_VIEW_ANY,
  SETTINGS_MANAGE_ANY,
  SETTINGS_VIEW_ANY,
  USER_APPROVE_ANY,
} as const;

export type PermissionCode = (typeof PERMISSIONS)[keyof typeof PERMISSIONS];
export type PermissionGrant = Iterable<string> | null | undefined;

export const ALL_PERMISSIONS = Object.values(PERMISSIONS) as readonly PermissionCode[];

/** 仅 C 端使用、不构成后台准入的权限（与后端 BackendPermissions 保持一致）。 */
const C_ONLY_PERMISSIONS: ReadonlySet<string> = new Set([
  ATTENDANCE_CHECKIN_OWN,
  EVENT_CREATE_OWN,
  EVENT_EDIT_OWN,
  PLAYER_EDIT_OWN,
  REGISTRATION_CREATE_OWN,
  INVITE_CREATE_MATCH,
]);

export const ADMIN_SECTIONS = [
  'matches',
  'registrations',
  'events',
  'lineup',
  'grouping',
  'players',
  'roles',
  'settings',
  'audit',
  'invites',
] as const;

export type AdminSection = (typeof ADMIN_SECTIONS)[number];

export function isAdminSection(value: string): value is AdminSection {
  return (ADMIN_SECTIONS as readonly string[]).includes(value);
}

/** 05 §5.1 B 端分区权限映射。 */
export const ADMIN_SECTION_PERMISSIONS: Record<AdminSection, readonly PermissionCode[]> = {
  matches: [MATCH_VIEW_ANY],
  registrations: [ATTENDANCE_VIEW_ANY],
  events: [EVENT_EDIT_ANY],
  lineup: [LINEUP_VIEW_ANY],
  grouping: [GROUPING_VIEW_ANY],
  players: [PLAYER_VIEW_ANY],
  roles: [ROLE_VIEW_ANY],
  settings: [SETTINGS_VIEW_ANY],
  audit: [AUDIT_VIEW_ANY],
  invites: [INVITE_MANAGE_ANY],
};

export function hasPermission(
  granted: PermissionGrant,
  required: PermissionCode | readonly PermissionCode[],
): boolean {
  if (!granted) {
    return false;
  }
  const pool = granted instanceof Set ? granted : new Set(granted);
  const requiredList = Array.isArray(required) ? required : [required];
  return requiredList.every((code) => pool.has(code));
}

export function hasBackendPermission(granted: PermissionGrant): boolean {
  if (!granted) {
    return false;
  }
  for (const code of granted) {
    if (!C_ONLY_PERMISSIONS.has(code)) {
      return true;
    }
  }
  return false;
}

export function canAccessSection(granted: PermissionGrant, section: AdminSection): boolean {
  return hasPermission(granted, ADMIN_SECTION_PERMISSIONS[section]);
}
