/** 手机号规则；AUTH-11 与 C 端注册/登录共用。 */
export const PHONE_PATTERN = /^1\d{10}$/;

export interface User {
  id: number;
  name: string;
  phoneMasked: string;
  status: number;
  statusLabel: string;
  roles: string[];
  createdAt: string;
  wechatBound: boolean;
}

export interface Player {
  id: number;
  name: string;
  jersey?: number | null;
  type: number;
  typeLabel?: string;
  position: number;
  positionLabel?: string;
  secondaryPositions?: number[];
  region?: string | null;
  avatarUrl?: string | null;
  skill: number;
}

/** AUTH-04 登录响应。 */
export interface AuthResponse {
  user: User;
  player?: Player;
  permissions: string[];
  csrfToken: string;
}

/** AUTH-15 登录响应。 */
export interface BLoginResponse {
  user: User;
  permissions: string[];
  csrfToken: string;
}

/** AUTH-10 `GET /c/me`。 */
export interface MeView {
  user: User;
  player: Player;
  roles: string[];
  permissions: string[];
  unreadCount: number;
}

/** AUTH-17 `GET /b/me`。 */
export interface BMeView {
  user: User;
  permissions: string[];
  sections: string[];
}

export interface TeamRef {
  id: number;
  name: string;
  shortName: string;
}

export type InviteType = 0 | 1;
export type InviteStatus = 0 | 1 | 2;

/** INV-04 的 match 摘要子集；比赛被删除时后端返回 null。 */
export interface InviteMatchSummary {
  id: number;
  title: string;
  startAt: string;
  endAt: string;
  venue: string;
  status: number;
  homeTeamName?: string | null;
  awayTeamName?: string | null;
}

export interface Invite {
  id: number;
  type: InviteType;
  typeLabel?: string;
  /** 历史数据或球队缺失时后端可返回 null。 */
  team: TeamRef | null;
  matchId?: number | null;
  match?: InviteMatchSummary | null;
  /** 邀请人账号已删除时后端可返回 null。 */
  inviter: UserSummary | null;
  expiresAt: string;
  maxUses?: number | null;
  usedCount: number;
  remainingUses?: number | null;
  status: InviteStatus;
  statusLabel?: string;
  exhausted?: boolean;
  createdAt: string;
}

export interface Role {
  id: number;
  code: string;
  name: string;
  isDefault: boolean;
  permissions: string[];
}

export interface PlayerPublic {
  id: number;
  name: string;
  jersey?: number | null;
  position: number;
  positionLabel?: string;
  secondaryPositions?: number[];
  region?: string | null;
  avatarUrl?: string | null;
  skill: number;
  type: number;
  appearances?: number;
}

export interface PlayerAdmin extends PlayerPublic {
  /** 无账号的球员，账号派生字段同时为 null（见 05 §4 PlayerAdmin）。 */
  userId: number | null;
  phoneMasked: string | null;
  status: number | null;
  statusLabel?: string | null;
  admin: boolean;
  roles: string[];
  createdAt: string | null;
  wechatBound: boolean;
}

export interface PlayerStats {
  playerId: number;
  season: string;
  appearances: number;
  goals: number;
  assists: number;
  updatedAt?: string;
}

export interface TeamStats {
  season: string;
  items: PlayerStats[];
}

export interface UserSummary {
  id: number;
  name: string;
  phoneMasked: string;
  status: number;
  statusLabel: string;
  roles: string[];
  type: number;
  typeLabel: string;
}
