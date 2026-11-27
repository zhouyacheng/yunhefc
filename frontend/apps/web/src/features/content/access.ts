import { CONTENT_VIEW_ANY, hasPermission } from '@yunhefc/permissions';
import type { User } from '@yunhefc/shared';

import { isPending } from '../auth/user-status';

/** 动态与相册仅登录的正式球员与管理员可见（P-142）；首页与详情入口共用。 */
export function contentVisible(user: User | null, permissions: string[]): boolean {
  return user !== null && !isPending(user.status) && hasPermission(permissions, CONTENT_VIEW_ANY);
}
