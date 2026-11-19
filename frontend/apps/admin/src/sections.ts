import type { AdminSection } from '@yunhefc/permissions';

export type AdminNavGroup = {
  label: string;
  sections: readonly AdminSection[];
};

// 02 §2.4：运营 / 战术 / 球队 / 系统；组内顺序以设计文档为准。
export const ADMIN_NAV_GROUPS = [
  { label: '运营', sections: ['matches', 'registrations', 'events'] },
  { label: '战术', sections: ['lineup', 'grouping'] },
  { label: '球队', sections: ['players', 'roles'] },
  { label: '系统', sections: ['invites', 'settings', 'audit'] },
] as const satisfies readonly AdminNavGroup[];

export const SECTION_LABELS: Record<AdminSection, string> = {
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
