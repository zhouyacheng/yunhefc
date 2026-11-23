// 05 §5.1 权限码；只用于小程序入口渲染，服务端仍是权威校验。
export const ACTIVITY_SIGNUP_OWN = 'activity:signup:own' as const;
export const ATTENDANCE_CHECKIN_OWN = 'attendance:checkin:own' as const;
export const ATTENDANCE_VIEW_ANY = 'attendance:view:any' as const;
export const EVENT_CREATE_OWN = 'event:create:own' as const;
export const EVENT_EDIT_ANY = 'event:edit:any' as const;
export const EVENT_EDIT_OWN = 'event:edit:own' as const;
export const GROUPING_VIEW_ANY = 'grouping:view:any' as const;
export const LINEUP_VIEW_ANY = 'lineup:view:any' as const;
export const MATCH_EDIT_ANY = 'match:edit:any' as const;
export const MATCH_STATUS_ANY = 'match:status:any' as const;
export const REGISTRATION_CREATE_OWN = 'registration:create:own' as const;

export function hasPermission(
  permissions: readonly string[] | null | undefined,
  code: string,
): boolean {
  return Boolean(permissions && permissions.includes(code));
}

/** 仅 C 端使用、不构成后台准入的权限（与 `packages/permissions` 的 C_ONLY_PERMISSIONS 一致）。 */
const C_ONLY_PERMISSIONS: ReadonlySet<string> = new Set([
  ACTIVITY_SIGNUP_OWN,
  ATTENDANCE_CHECKIN_OWN,
  EVENT_CREATE_OWN,
  EVENT_EDIT_OWN,
  'player:edit:own',
  REGISTRATION_CREATE_OWN,
  'invite:create:match',
]);

/** 含任一后台权限时展示「管理后台」入口；与 C 端 `hasBackendPermission` 同口径。 */
export function hasBackendPermission(permissions: readonly string[] | null | undefined): boolean {
  return Boolean(permissions && permissions.some((code) => !C_ONLY_PERMISSIONS.has(code)));
}

/** app_user.status：0-待审核；1-正常；2-已注销。 */
export function isPending(status: number | null | undefined): boolean {
  return status === 0;
}
