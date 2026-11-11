export type MatchType = 0 | 1 | 2 | 3;
export type MatchStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6;
export type MatchSide = 0 | 1;

/** 05 §4 MatchSummary。 */
export interface MatchSummary {
  id: number;
  title: string;
  type: MatchType;
  typeLabel: string;
  startAt: string;
  endAt: string;
  venue: string;
  round?: number | null;
  season: string;
  homeTeamName: string;
  awayTeamName: string;
  ourSide?: MatchSide | null;
  ourSideLabel?: string | null;
  scoreHome?: number | null;
  scoreAway?: number | null;
  resultText?: string | null;
  status: MatchStatus;
  statusLabel: string;
  totalMatchNo: number;
  seasonMatchNo: number;
  confirmedCount: number;
  guestCount: number;
  waitlistCount: number;
}

export interface DerivedScore {
  scoreHome: number | null;
  scoreAway: number | null;
}

/** 05 §4 MatchDetail。 */
export interface MatchDetail extends MatchSummary {
  format: string;
  competition?: string | null;
  signupDeadline: string;
  cancelLock: string;
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
  version: number;
  derivedScore: DerivedScore;
  myRegistration?: Registration | null;
}

/** 05 §4 MatchAdmin（GET /b/matches 列表项为 MatchAdmin）。 */
export interface MatchAdmin extends MatchDetail {
  includeGuests: boolean;
  manualStatus: boolean;
  statusUpdatedAt?: string | null;
  statusUpdatedBy?: number | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
}

export interface Score {
  scoreHome: number | null;
  scoreAway: number | null;
  derived?: DerivedScore;
}

/** MAT-05 请求体；赛季/类型/我方主客不可改。 */
export interface MatchUpdate {
  title?: string;
  startAt?: string;
  endAt?: string;
  venue?: string;
  competition?: string | null;
  round?: number | null;
  homeTeamName?: string;
  awayTeamName?: string;
  capacity?: number;
  guestCapacity?: number;
  waitlistCapacity?: number;
  includeGuests?: boolean;
  version: number;
}

export interface Registration {
  id: number;
  playerId: number;
  type: number;
  typeLabel?: string;
  status: number;
  statusLabel?: string;
  registeredAt: string;
  cancelledAt?: string | null;
  active: boolean;
}

export interface RegistrationRow {
  id: number;
  player: { id: number; name: string; jersey?: number | null; position: number };
  type: number;
  typeLabel?: string;
  status: number;
  statusLabel?: string;
  registeredAt: string;
  cancelledAt?: string | null;
}

export interface Attendance {
  id: number;
  playerId: number;
  status: number;
  statusLabel?: string;
  source: number;
  sourceLabel?: string;
  recordedAt: string;
}

export interface MyRegistration {
  registration: RegistrationRow;
  match: MatchSummary;
}

export interface PromotionResult {
  promotedPlayerId?: number | null;
  promoted: boolean;
}

export const MATCH_TYPE_TEXT: Readonly<Record<MatchType, string>> = {
  0: '对外比赛',
  1: '队内赛',
  2: '训练赛',
  3: '友谊赛',
};

export const MATCH_STATUS_TEXT: Readonly<Record<MatchStatus, string>> = {
  0: '报名中',
  1: '报名截止',
  2: '已锁定',
  3: '进行中',
  4: '已结束',
  5: '已延期',
  6: '已取消',
};

export function matchTypeText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (MATCH_TYPE_TEXT[code as MatchType] ?? '');
}

export function matchStatusText(code: number | null | undefined): string {
  return code === null || code === undefined ? '' : (MATCH_STATUS_TEXT[code as MatchStatus] ?? '');
}
