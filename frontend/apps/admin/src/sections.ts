import type { AdminSection } from '@yunhefc/permissions';

export const SECTION_LABELS: Record<AdminSection, string> = {
  dashboard: '工作台',
  matches: '比赛管理',
  registrations: '报名/考勤',
  events: '事件/比分',
  lineup: '阵容',
  grouping: '分组',
  players: '球员',
  roles: '角色权限',
  settings: '系统设置',
  audit: '操作日志',
  invites: '邀请管理',
};
