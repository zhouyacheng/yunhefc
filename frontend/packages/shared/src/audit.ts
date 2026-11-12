/** AUD-01 响应中的操作人（05 §3.11）；有用户 ID 时 name 仍可能为 null。 */
export interface AuditActor {
  id: number | null;
  name: string | null;
}

/** AUD-01 操作日志行；快照已由后端脱敏。 */
export interface AuditLog {
  id: number;
  actor: AuditActor | null;
  action: string;
  targetType: string;
  targetId: number | null;
  beforeRedacted: unknown;
  afterRedacted: unknown;
  requestId: string | null;
  ipMasked: string | null;
  createdAt: string;
}

/** 38 个已使用的动作码中文标签；未知码按原值展示。 */
export const AUDIT_ACTION_TEXT: Readonly<Record<string, string>> = {
  ACCOUNT_REGISTER: '账号注册',
  PASSWORD_RESET: '重置密码',
  PHONE_CHANGE: '更换手机号',
  WECHAT_UNBIND: '解绑微信',
  WECHAT_BIND: '绑定微信',
  USER_APPROVE: '审核用户',
  PLAYER_CONVERT: '外挂转正',
  USER_DISABLE: '停用用户',
  ROLE_CREATE: '新建角色',
  ROLE_RENAME: '重命名角色',
  ROLE_DELETE: '删除角色',
  ROLE_PERMISSIONS_UPDATE: '修改角色权限',
  USER_ROLES_UPDATE: '分配用户角色',
  INVITE_ACCEPT: '接受邀请',
  INVITE_CREATE: '创建邀请',
  INVITE_REVOKE: '撤销邀请',
  INVITE_EXPIRE: '邀请过期',
  MATCH_CREATE: '创建比赛',
  MATCH_UPDATE: '编辑比赛',
  MATCH_STATUS_CHANGE: '修改比赛状态',
  MATCH_POSTPONE: '延期比赛',
  MATCH_RESCHEDULE: '重新排期',
  MATCH_CANCEL: '取消比赛',
  MATCH_AUTO_STATUS_CHANGE: '自动流转比赛状态',
  REGISTRATION_REMOVE: '移除报名',
  ATTENDANCE_MARK: '标记考勤',
  ATTENDANCE_TEMP: '补录临时到场',
  EVENT_CREATE: '录入事件',
  EVENT_UPDATE: '编辑事件',
  EVENT_DELETE: '删除事件',
  SCORE_UPDATE: '更新比分',
  LINEUP_SAVE: '保存阵容',
  'grouping.generate': '生成分组',
  'grouping.adjust': '调整分组',
  'grouping.save': '保存分组',
  'grouping.clear': '清除分组',
  PLAYER_UPDATE: '编辑球员',
  SETTINGS_UPDATE: '修改系统设置',
};

/** 11 个对象类型中文标签；未知码按原值展示。 */
export const AUDIT_TARGET_TYPE_TEXT: Readonly<Record<string, string>> = {
  USER: '用户',
  ROLE: '角色',
  PLAYER: '球员',
  INVITE: '邀请',
  MATCH: '比赛',
  REGISTRATION: '报名',
  ATTENDANCE: '考勤',
  EVENT: '事件',
  LINEUP: '阵容',
  team_match: '队内赛',
  SETTINGS: '系统设置',
};

export function auditActionText(code: string): string {
  return AUDIT_ACTION_TEXT[code] ?? code;
}

export function auditTargetTypeText(code: string): string {
  return AUDIT_TARGET_TYPE_TEXT[code] ?? code;
}

/** 展开行快照为空（null 或空对象）时统一按「无变更快照」展示。 */
export function isEmptySnapshot(value: unknown): boolean {
  if (value === null || value === undefined) {
    return true;
  }
  if (typeof value === 'object') {
    return Object.keys(value as Record<string, unknown>).length === 0;
  }
  return false;
}
