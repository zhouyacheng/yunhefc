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

/** PLR-03 自助编辑资料；avatarUrl 只接受 FIL-01 返回的 objectKey。 */
export interface PlayerProfileUpdate {
  name: string;
  region: string;
  position: number;
  secondaryPositions: number[];
  avatarUrl?: string;
}

/** AUTH-21 绑定手机号：已有账号忽略 name/position，新账号必填。 */
export interface BindPhonePayload {
  phone: string;
  code: string;
  name: string;
  position: number;
}

export interface MiniLoginResult {
  needBindPhone: boolean;
  bindTicket: string | null;
  user: AuthUser | null;
  player: PlayerView | null;
  permissions: string[] | null;
  csrfToken: string | null;
}

/** MAT-03/MAT-12 返回的当前用户报名。 */
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

export interface RegistrationPlayer {
  id: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel?: string;
  skill: number;
}

/** REG-05 名单行。 */
export interface RegistrationRow {
  id: number;
  player: RegistrationPlayer;
  type: number;
  typeLabel?: string;
  status: number;
  statusLabel?: string;
  registeredAt: string;
  cancelledAt?: string | null;
}

export interface AttendancePlayer {
  id: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel?: string;
}

/** REG-04 本人考勤。 */
export interface Attendance {
  id: number;
  playerId: number;
  status: number;
  statusLabel?: string;
  source: number;
  sourceLabel?: string;
  recordedAt: string;
}

/** ATT-03 全队考勤行。 */
export interface AttendanceRow {
  id: number;
  player: AttendancePlayer;
  status: number;
  statusLabel?: string;
  source: number;
  sourceLabel?: string;
  recordedAt: string;
}

export interface MyMatchRegistration {
  registration?: Registration | null;
  attendance?: Attendance | null;
}

export interface PromotionResult {
  promotedPlayerId?: number | null;
  promoted: boolean;
}

export interface CancellationResult extends PromotionResult {
  cancelled: boolean;
}

export interface MatchStatusUpdate {
  status: number;
  version: number;
  manualStatus?: boolean;
  reason?: string;
  startAt?: string;
  endAt?: string;
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
  ourSide?: number | null;
  ourSideLabel?: string | null;
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
  format?: string;
  competition?: string | null;
  signupDeadline: string;
  cancelLock: string;
  version: number;
  derivedScore?: DerivedScore | null;
  myRegistration?: Registration | null;
}

export interface EventFormPlayer {
  id: number;
  name: string;
  jersey?: number | null;
  position?: number;
  positionLabel?: string;
}

export interface EventPlayer {
  id: number;
  name: string;
  jersey?: number | null;
  position?: number;
  positionLabel?: string;
}

export interface MatchEvent {
  id: number;
  type: number;
  typeLabel?: string;
  team: number;
  teamLabel?: string;
  player?: EventPlayer | null;
  assistPlayer?: EventPlayer | null;
  inPlayer?: EventPlayer | null;
  outPlayer?: EventPlayer | null;
  minute: number;
  half: number;
  halfLabel?: string;
  penalty: boolean;
  createdBy?: number | null;
  updatedAt?: string;
  version: number;
}

export interface EventCreate {
  type: number;
  team: number;
  playerId?: number | null;
  assistPlayerId?: number | null;
  inPlayerId?: number | null;
  outPlayerId?: number | null;
  minute: number;
  half: number;
  penalty: boolean;
}

export type EventUpdate = Partial<EventCreate> & { version: number };

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
  avatarUrl?: string | null;
  skill: number;
  type: number;
  appearances: number;
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

/** STA-03 排行榜口径：赛季或生涯累计。 */
export type LeaderboardScope = 'season' | 'all';

/** 排行榜累计口径：生涯累计含当前赛季，历史累计不含当前赛季。 */
export interface LeaderboardTotals {
  appearances: number;
  goals: number;
  assists: number;
  ownGoals: number;
  internalAppearances: number;
  internalWins: number;
  winRate: number | null;
  goalsPerMatch: number;
  assistsPerMatch: number;
}

/** STA-03 LeaderboardEntry（05 §3.14）；排序与名次由前端按 02 §5.11 计算。 */
export interface LeaderboardEntry {
  playerId: number;
  name: string;
  jersey: number | null;
  position: number;
  positionLabel?: string;
  avatarUrl: string | null;
  appearances: number;
  goals: number;
  assists: number;
  ownGoals: number;
  internalAppearances: number;
  internalWins: number;
  /** 0–100 百分比，1 位小数；分母为 0 时为 null。 */
  winRate: number | null;
  /** 2 位小数；无出场为 0。 */
  goalsPerMatch: number;
  assistsPerMatch: number;
  /** 生涯累计（当前赛季 + 历史累计，不晚于当前自然年；未来赛季不计）；scope=all 时为 null。 */
  career: LeaderboardTotals | null;
  /** 历史累计（早于当前自然年，不含当前赛季）；scope=all 时为 null。 */
  history: LeaderboardTotals | null;
}

/** STA-03 响应；生涯累计时 season 为 null。 */
export interface Leaderboard {
  season: string | null;
  scope: LeaderboardScope;
  items: LeaderboardEntry[];
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

export interface MyRegistration {
  registration: RegistrationRow;
  match: MatchSummary;
}

export interface FormationSlot {
  slotKey: string;
  role: number;
  roleLabel: string;
  x: number;
  y: number;
}

export interface FormationTemplate {
  id: number;
  name: string;
  isPreset: boolean;
  baseTemplateId?: number | null;
  slots: FormationSlot[];
}

export interface LineupPlayer {
  id: number;
  name: string;
  jersey?: number | null;
  position: number;
  positionLabel?: string | null;
}

export interface LineupStarter {
  slotKey: string;
  player?: LineupPlayer | null;
}

/** LIN-01 只读阵容；对手方 starters 仅空槽位、bench 为空。 */
export interface LineupView {
  side: number;
  formation: FormationTemplate;
  starters: LineupStarter[];
  bench: LineupPlayer[];
}

export interface GroupedPlayer {
  playerId: number;
  name: string;
  jersey?: number | null;
  position: number;
  manualScore?: number | null;
  autoScore?: number | null;
  finalScore?: number | null;
}

export interface GroupingView {
  teams: { A: GroupedPlayer[]; B: GroupedPlayer[] };
  diff: number;
  warnings: string[];
  savedAt?: string | null;
}

export interface AvatarUploadView {
  objectKey: string;
  signedUrl: string;
}

// ---- M15 队内活动（05 §3.16）：列表与详情均不含报名名单 ----

/** 0-聚餐；1-年会；2-其他。 */
export type ActivityType = 0 | 1 | 2;

/** 0-报名中；1-报名截止；2-已结束；3-已取消。 */
export type ActivityStatus = 0 | 1 | 2 | 3;

/** 0-已报名；1-已取消。 */
export type ActivitySignupStatus = 0 | 1;

export interface ActivitySignup {
  id: number;
  activityId: number;
  playerId: number;
  playerName: string;
  playerNo: number | null;
  avatarUrl: string | null;
  withFamily: boolean;
  familyNote: string | null;
  status: ActivitySignupStatus;
  statusLabel: string;
  signedUpAt: string;
}

export interface ActivitySummary {
  id: number;
  title: string;
  type: ActivityType;
  typeLabel: string;
  startAt: string;
  endAt: string | null;
  signupDeadline: string;
  /** 只计报名人数，不含家属；null 为不限。 */
  capacity: number | null;
  costPerPerson: number | null;
  location: string;
  locationImageUrl: string | null;
  description: string | null;
  status: ActivityStatus;
  statusLabel: string;
  signupCount: number;
  signupOpen: boolean;
  /** 仅登录时返回。 */
  mySignup?: ActivitySignup | null;
}

export type ActivityDetail = ActivitySummary;

// ---- M16 账本（05 §3.17）：C 端只读 ----

/** 0-收入；1-支出。 */
export type LedgerType = 0 | 1;

/** 分类方向：0-收入；1-支出；null-通用。 */
export type LedgerDirection = LedgerType | null;

export interface LedgerCategory {
  id: number;
  name: string;
  direction: LedgerDirection;
  directionLabel: string | null;
  sortNo: number;
  isActive: boolean;
  entryCount: number;
}

export interface LedgerEntry {
  id: number;
  type: LedgerType;
  typeLabel: string;
  categoryId: number;
  categoryName: string;
  categoryDirection: LedgerDirection;
  amount: number;
  description: string;
  occurredAt: string;
  playerId: number | null;
  playerName: string | null;
  activityId: number | null;
  activityTitle: string | null;
  version: number;
}

/** 余额 = 收入 − 支出，无期初余额。 */
export interface LedgerSummary {
  income: number;
  expense: number;
  balance: number;
}
