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

export interface MatchSummary {
  id: number;
  title: string;
  typeLabel: string;
  startAt: string;
  endAt: string;
  venue: string;
  season: string;
  homeTeamName: string;
  awayTeamName: string;
  scoreHome: number | null;
  scoreAway: number | null;
  status: number;
  statusLabel: string;
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
}

export interface MatchDetail extends MatchSummary {
  signupDeadline?: string;
  cancelLock?: string;
  version?: number;
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
