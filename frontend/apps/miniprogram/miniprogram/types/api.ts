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

export interface MatchDetail extends MatchSummary {
  signupDeadline: string;
  cancelLock?: string;
  version?: number;
  myRegistration?: RegistrationView | null;
}

export interface MatchDetailView extends MatchDetail {
  timeText: string;
}

export interface MatchEvent {
  id: number;
  type: number;
  typeLabel: string;
  minute: number;
  half: number;
  playerName: string | null;
  assistPlayerName: string | null;
}

export interface PlayerPublic {
  id: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel: string;
  skill: number;
  type: number;
  appearances: number;
}
