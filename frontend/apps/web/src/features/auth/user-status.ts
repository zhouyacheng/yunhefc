import { USER_STATUS } from '@yunhefc/shared';

/** app_user.status：0-待审核；1-正常；2-已注销。 */
export { USER_STATUS };

export function isPending(status: number): boolean {
  return status === USER_STATUS.PENDING;
}
