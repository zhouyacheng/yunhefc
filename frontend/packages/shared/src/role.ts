/** 内置管理员角色编码；ADMIN 始终拥有全部权限（06 §21）。 */
export const ADMIN_ROLE_CODE = 'ADMIN';

/** ROL-06 权限目录项（05 §3.13）。 */
export interface PermissionView {
  id: number;
  code: string;
  name: string;
  resource: string;
  action: string;
  scope: string;
}

/** B08 权限勾选的 7 个展示分组，顺序与原型一致。 */
export const PERMISSION_GROUP_LABELS = [
  '比赛',
  '报名',
  '考勤',
  '事件',
  '阵容/分组',
  '球员/权限',
  '邀请',
] as const;

/** 后端 `permission.resource` 到展示分组的映射（02 §6.10）。 */
const RESOURCE_GROUP_LABELS: Readonly<Record<string, string>> = {
  match: '比赛',
  registration: '报名',
  attendance: '考勤',
  event: '事件',
  lineup: '阵容/分组',
  grouping: '阵容/分组',
  player: '球员/权限',
  role: '球员/权限',
  user: '球员/权限',
  audit: '球员/权限',
  settings: '球员/权限',
  invite: '邀请',
};

/**
 * 权限归属的展示分组：优先使用后端 `resource`，缺失时回退权限码首段。
 * 未知 resource 原样作为组名，避免新增权限码被静默丢弃。
 */
export function permissionGroupLabel(code: string, resource?: string | null): string {
  const key = resource?.trim() || code.split(':')[0] || '';
  return RESOURCE_GROUP_LABELS[key] ?? key;
}

export interface PermissionGroup {
  label: string;
  items: PermissionView[];
}

export function groupPermissions(items: readonly PermissionView[]): PermissionGroup[] {
  const groups = new Map<string, PermissionView[]>();
  for (const item of items) {
    const label = permissionGroupLabel(item.code, item.resource);
    const bucket = groups.get(label);
    if (bucket) {
      bucket.push(item);
    } else {
      groups.set(label, [item]);
    }
  }
  const known = PERMISSION_GROUP_LABELS.filter((label) => groups.has(label)).map((label) => ({
    label,
    items: groups.get(label) ?? [],
  }));
  const unknown = [...groups.entries()]
    .filter(([label]) => !(PERMISSION_GROUP_LABELS as readonly string[]).includes(label))
    .map(([label, groupItems]) => ({ label, items: groupItems }));
  return [...known, ...unknown];
}

/** ROL-02 role code：与后端 `RoleService.ROLE_CODE` 保持一致。 */
export const ROLE_CODE_PATTERN = /^[A-Z][A-Z0-9_]{1,31}$/;

export function roleCodeError(code: string): string | null {
  const value = code.trim();
  if (!value) {
    return '请输入角色编码';
  }
  if (!ROLE_CODE_PATTERN.test(value)) {
    return '角色编码仅限大写字母、数字和下划线，以字母开头，长度 2–32';
  }
  return null;
}

export function roleNameError(name: string): string | null {
  const value = name.trim();
  if (!value) {
    return '请输入角色名称';
  }
  if (value.length > 64) {
    return '角色名称最多 64 个字符';
  }
  return null;
}
