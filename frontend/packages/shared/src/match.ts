export type MatchType = 0 | 1 | 2 | 3;
export type MatchStatus = 0 | 1 | 2 | 3 | 4 | 5 | 6;

export const MATCH_STATUS_FINISHED: MatchStatus = 4;
export const MATCH_STATUS_POSTPONED: MatchStatus = 5;
export const MATCH_STATUS_CANCELLED: MatchStatus = 6;
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

/**
 * 05 §4 MatchAdmin。后端 MAT-09/MAT-10 的 MatchAdminView 未返回
 * derivedScore/myRegistration，故按实际响应声明为可选。
 */
export type MatchAdmin = Omit<MatchDetail, 'derivedScore' | 'myRegistration'> & {
  includeGuests: boolean;
  manualStatus: boolean;
  statusUpdatedAt?: string | null;
  statusUpdatedBy?: number | null;
  createdBy: number;
  createdAt: string;
  updatedAt: string;
  derivedScore?: DerivedScore;
  myRegistration?: Registration | null;
};

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

export interface RosterPlayer {
  id: number;
  name: string;
  jersey?: number | null;
  position: number;
  positionLabel?: string;
}

export interface RegistrationRow {
  id: number;
  player: RosterPlayer;
  type: number;
  typeLabel?: string;
  status: number;
  statusLabel?: string;
  registeredAt: string;
  cancelledAt?: string | null;
}

/** 05 §4 AttendanceRow：比赛全队考勤行，含球员展示字段与来源。 */
export interface AttendanceRow {
  id: number;
  player: RosterPlayer;
  status: number;
  statusLabel?: string;
  source: number;
  sourceLabel?: string;
  recordedAt: string;
}

export interface RemovalResult extends PromotionResult {
  removed: boolean;
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

/** 05 §4 REG-04 本人报名与考勤；未报名或无考勤记录时对应字段为 null。 */
export interface MyMatchRegistration {
  registration?: Registration | null;
  attendance?: Attendance | null;
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

/** 跨端统一的状态语气，由各组件库映射到自己的颜色类型。 */
export type MatchStatusTone = 'neutral' | 'success' | 'warning' | 'danger';

export function matchStatusTone(code: number | null | undefined): MatchStatusTone {
  if (code === 5 || code === 6) {
    return 'danger';
  }
  if (code === 4) {
    return 'success';
  }
  if (code === 3) {
    return 'warning';
  }
  return 'neutral';
}

export const POSITION_TEXT: Readonly<Record<number, string>> = {
  0: '门将',
  1: '后卫',
  2: '中场',
  3: '前锋',
};

export function positionText(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : (POSITION_TEXT[code] ?? '—');
}

export const REGISTRATION_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '已确认',
  1: '候补中',
  2: '已取消',
  3: '已移除',
};

export const ATTENDANCE_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '正常',
  1: '缺勤',
  2: '临时到场',
};

export const ATTENDANCE_SOURCE_TEXT: Readonly<Record<number, string>> = {
  0: '自助签到',
  1: '管理员',
  2: '系统',
};

export type CheckInWindowState = 'open' | 'before' | 'after';

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const DAY_MS = 24 * 60 * 60 * 1000;
const CHECK_IN_OPENS_BEFORE_MS = 4 * 60 * 60 * 1000;

/**
 * 签到窗口：开赛前 4 小时至比赛当天 23:59（Asia/Shanghai）。
 * 仅用于按钮启用与禁用说明；服务端仍按同一规则强校验。
 */
export function checkInWindowState(startAt: string, now: number = Date.now()): CheckInWindowState {
  const start = Date.parse(startAt);
  if (Number.isNaN(start)) {
    return 'after';
  }
  if (now < start - CHECK_IN_OPENS_BEFORE_MS) {
    return 'before';
  }
  // Asia/Shanghai 固定 UTC+8：比赛当天结束 = 次日 00:00（本地）。
  const localDayStart =
    Math.floor((start + SHANGHAI_OFFSET_MS) / DAY_MS) * DAY_MS - SHANGHAI_OFFSET_MS;
  return now < localDayStart + DAY_MS ? 'open' : 'after';
}
