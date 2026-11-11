export interface User {
  id: number;
  name: string;
  phoneMasked: string;
  status: number;
  statusLabel: string;
  roles: string[];
  createdAt: string;
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

export interface Invite {
  id: number;
  type: InviteType;
  typeLabel?: string;
  team: TeamRef;
  matchId?: number | null;
  match?: unknown;
  inviter: User;
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
  secondaryPositions?: number[];
  region?: string | null;
  avatarUrl?: string | null;
  skill: number;
  type: number;
  appearances?: number;
}

export interface PlayerAdmin extends PlayerPublic {
  userId: number;
  phoneMasked: string;
  status: number;
  statusLabel?: string;
  admin: boolean;
  roles: string[];
  createdAt: string;
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
