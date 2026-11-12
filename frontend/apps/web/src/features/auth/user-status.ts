/** app_user.status：0-待审核；1-正常；2-已注销。 */
export const USER_STATUS = {
  PENDING: 0,
  ACTIVE: 1,
  DISABLED: 2,
} as const;

export function isPending(status: number): boolean {
  return status === USER_STATUS.PENDING;
}
