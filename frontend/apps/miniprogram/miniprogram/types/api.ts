export interface Page<T> {
  items: T[];
  total: number;
  page: number;
  pageSize: number;
  totalPages: number;
}

export interface AuthUser {
  id: number;
  name: string;
  phoneMasked: string;
  status: number;
  statusLabel: string;
  roles: string[];
  createdAt: string;
  wechatBound: boolean;
}

export interface PlayerView {
  id: number;
  name: string;
  jersey: number | null;
  type: number;
  typeLabel: string;
  position: number;
  positionLabel: string;
  secondaryPositions?: number[];
  region?: string | null;
  skill: number;
  avatarUrl: string | null;
}

export interface MiniLoginResult {
  needBindPhone: boolean;
  bindTicket: string | null;
  user: AuthUser | null;
  player: PlayerView | null;
  permissions: string[] | null;
  csrfToken: string | null;
}

/** C 端比赛详情里的当前用户报名；小程序目前只读 status。 */
export interface RegistrationView {
  status: number;
}

export interface MatchSummary {
  id: number;
  title: string;
  type: number;
  typeLabel: string;
  startAt: string;
  endAt: string;
  venue: string;
  season: string;
  homeTeamName: string;
  awayTeamName: string;
  scoreHome: number | null;
  scoreAway: number | null;
  resultText?: string | null;
  status: number;
  statusLabel: string;
  totalMatchNo: number;
  seasonMatchNo: number;
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
  confirmedCount: number;
  guestCount: number;
  waitlistCount: number;
}

/** 计分事件推导比分；无计分事件时为 null。 */
export interface DerivedScore {
  scoreHome: number | null;
  scoreAway: number | null;
}

export interface MatchDetail extends MatchSummary {
  signupDeadline: string;
  cancelLock?: string;
  version?: number;
  derivedScore?: DerivedScore | null;
  myRegistration?: RegistrationView | null;
}

export interface EventPlayer {
  id: number;
  name: string;
}

export interface MatchEvent {
  id: number;
  type: number;
  team: number;
  player?: EventPlayer | null;
  assistPlayer?: EventPlayer | null;
  inPlayer?: EventPlayer | null;
  outPlayer?: EventPlayer | null;
  minute: number;
  half: number;
  penalty: boolean;
}

export interface EventTimeline {
  items: MatchEvent[];
  derivedScore?: DerivedScore | null;
}

export interface PlayerPublic {
  id: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel: string;
  secondaryPositions?: number[];
  region?: string | null;
  skill: number;
  type: number;
  appearances: number;
}

export interface Notification {
  id: number;
  type: number;
  typeLabel: string;
  title: string;
  body: string;
  refType?: string | null;
  refId?: number | null;
  readAt?: string | null;
  createdAt: string;
}

export interface NotificationReadResult {
  readAt: string;
}

export interface NotificationUnreadCount {
  count: number;
}

export interface NotificationReadAllResult {
  updated: number;
}

export interface RegistrationSummary {
  id: number;
  type: number;
  status: number;
  statusLabel?: string;
  registeredAt: string;
  cancelledAt?: string | null;
}

export interface MyRegistration {
  registration: RegistrationSummary;
  match: MatchSummary;
}

export interface AvatarUploadView {
  objectKey: string;
  signedUrl: string;
}
