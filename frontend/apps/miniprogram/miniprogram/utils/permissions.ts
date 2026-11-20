// 05 §5.1 权限码；只用于小程序入口渲染，服务端仍是权威校验。
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

/** app_user.status：0-待审核；1-正常；2-已注销。 */
export function isPending(status: number | null | undefined): boolean {
  return status === 0;
}
