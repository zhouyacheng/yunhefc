// C 端 typed client：页面所有请求的唯一入口（认证握手见下方认证段）。
// 页面只表达意图；路径、分页、幂等键与 CSRF 留在实现内。错误按 ApiError 原样抛出，
// 页面各自保留提示文案与冲突刷新语义。
import type {
  ActivityDetail,
  ActivitySignup,
  ActivityStatus,
  ActivitySummary,
  ActivityType,
  Attendance,
  AttendanceRow,
  AuthUser,
  AvatarUploadView,
  BindPhonePayload,
  CancellationResult,
  EventCreate,
  EventTimeline,
  EventUpdate,
  GroupingView,
  Leaderboard,
  LeaderboardScope,
  LedgerCategory,
  LedgerEntry,
  LedgerSummary,
  LedgerType,
  LineupView,
  MatchDetail,
  MatchEvent,
  MatchStatusUpdate,
  MatchSummary,
  MiniLoginResult,
  MyMatchRegistration,
  MyRegistration,
  Notification,
  NotificationReadAllResult,
  NotificationReadResult,
  NotificationUnreadCount,
  Page,
  PlayerProfileUpdate,
  PlayerPublic,
  PlayerView,
  Registration,
  RegistrationRow,
  TeamStats,
} from '../types/api';
import { errorMessage } from './api-errors';
import { newIdempotencyKey } from './idempotency';
import { ensureCsrf, request, uploadFile } from './request';
import { DEFAULT_PAGE_SIZE, filterQueryString, type ScheduleFilters } from './match-query';

const ROSTER_PAGE_SIZE = 100;

type QueryValue = string | number | undefined;

function queryString(query: Record<string, QueryValue>): string {
  const parts = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`);
  return parts.length ? `?${parts.join('&')}` : '';
}

/** 布尔参数在 query string 里只能写成字符串。 */
function flag(enabled: boolean | undefined): 'true' | undefined {
  return enabled ? 'true' : undefined;
}

/** 高风险写接口：CSRF + 每次操作新幂等键。 */
async function highRiskWrite<T>(
  method: 'POST' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  await ensureCsrf();
  return request<T>(method, path, body, { idempotencyKey: newIdempotencyKey() });
}

/** 普通写接口：只需 CSRF，不占用幂等键。 */
async function plainWrite<T>(
  method: 'POST' | 'PATCH' | 'DELETE',
  path: string,
  body?: unknown,
): Promise<T> {
  await ensureCsrf();
  return request<T>(method, path, body);
}

async function fetchAllPages<T>(path: string, query: Record<string, QueryValue>): Promise<T[]> {
  const first = await request<Page<T>>(
    'GET',
    `${path}${queryString({ ...query, page: 1, pageSize: ROSTER_PAGE_SIZE })}`,
  );
  const items = [...first.items];
  for (let page = 2; page <= first.totalPages; page += 1) {
    const next = await request<Page<T>>(
      'GET',
      `${path}${queryString({ ...query, page, pageSize: ROSTER_PAGE_SIZE })}`,
    );
    items.push(...next.items);
  }
  return items;
}

// ---- 认证（会话编排在 session.ts，这里只负责端点） ----

export interface MeView {
  user: AuthUser;
  player?: PlayerView | null;
  permissions?: string[] | null;
}

export interface BindPhoneResult extends MeView {
  csrfToken: string;
}

/** AUTH-10 当前身份。 */
export function fetchMe(): Promise<MeView> {
  return request<MeView>('GET', '/me');
}

/** AUTH-20 小程序登录；免 CSRF，登录前不预取 token。 */
export function miniLogin(code: string): Promise<MiniLoginResult> {
  return request<MiniLoginResult>('POST', '/auth/mini/login', { code });
}

/** AUTH-21 绑定手机号；免 CSRF，凭一次性 bindTicket。 */
export function bindPhoneWithTicket(
  bindTicket: string,
  payload: BindPhonePayload,
): Promise<BindPhoneResult> {
  return request<BindPhoneResult>('POST', '/auth/mini/bind-phone', { bindTicket, ...payload });
}

/** AUTH-05 退出登录。 */
export function logoutRequest(): Promise<void> {
  return plainWrite<void>('POST', '/auth/logout');
}

/** AUTH-02 绑定手机号短信验证码。 */
export function sendBindSmsCode(phone: string): Promise<void> {
  return plainWrite<void>('POST', '/auth/sms-codes', { phone, purpose: 'BIND' });
}

export interface AdminTicketView {
  ticket: string;
  expiresIn: number;
}

/** AUTH-18 C→B 交接票据；60 秒内一次性兑换，无后台权限返回 40301。 */
export function issueAdminTicket(): Promise<AdminTicketView> {
  return plainWrite<AdminTicketView>('POST', '/auth/admin-ticket');
}

// ---- 比赛 ----

export interface MatchListQuery {
  /** 已归一的筛选；编码口径由 match-query 统一维护。 */
  filters?: ScheduleFilters;
  page?: number;
  pageSize?: number;
}

/** MAT-02 C 端列表：固定 scope=all + 开赛倒序，筛选串追加在分页之后。 */
export function listMatches(query: MatchListQuery = {}): Promise<Page<MatchSummary>> {
  const page = Math.max(1, Math.floor(query.page ?? 1) || 1);
  const pageSize = query.pageSize ?? DEFAULT_PAGE_SIZE;
  const filters = query.filters ? filterQueryString(query.filters) : '';
  const suffix = filters ? `&${filters}` : '';
  return request<Page<MatchSummary>>(
    'GET',
    `/matches?scope=all&sort=startAt:desc&page=${page}&pageSize=${pageSize}${suffix}`,
  );
}

/** MAT-02 首页取数：不限状态按开赛时间倒序取前 4 场，Hero 为第 1 条。 */
export function listRecentMatches(limit = 4): Promise<Page<MatchSummary>> {
  return request<Page<MatchSummary>>('GET', `/matches?sort=startAt:desc&pageSize=${limit}`);
}

/** MAT-14 C 端赛季选项。 */
export async function listMatchSeasons(): Promise<string[]> {
  const { items } = await request<{ items: string[] }>('GET', '/matches/seasons');
  return items || [];
}

export function fetchMatchDetail(id: number): Promise<MatchDetail> {
  return request<MatchDetail>('GET', `/matches/${id}`);
}

// ---- 报名与考勤 ----

export interface MyRegistrationQuery {
  scope: 'active' | 'history';
  page: number;
  pageSize: number;
}

/** REG-03 我的报名（active/history）。 */
export function listMyRegistrations(query: MyRegistrationQuery): Promise<Page<MyRegistration>> {
  const { scope, page, pageSize } = query;
  return request<Page<MyRegistration>>(
    'GET',
    `/me/registrations${queryString({ scope, page, pageSize })}`,
  );
}

export function fetchMyMatchRegistration(id: number): Promise<MyMatchRegistration> {
  return request<MyMatchRegistration>('GET', `/matches/${id}/registrations/me`);
}

export interface FullRoster {
  confirmed: RegistrationRow[];
  waitlist: RegistrationRow[];
  attendance: AttendanceRow[];
  attendanceError: string;
}

/** REG-05 status=0/1 + ATT-03 全部页；考勤失败不阻断名单。 */
export async function fetchFullRoster(id: number): Promise<FullRoster> {
  const attendancePromise = fetchAllPages<AttendanceRow>(`/matches/${id}/attendance`, {}).then(
    (items) => ({ items, error: '' }),
    (error: unknown) => ({
      items: [] as AttendanceRow[],
      error: errorMessage(error, '考勤加载失败，名单未显示考勤'),
    }),
  );
  const [confirmed, waitlist, attendance] = await Promise.all([
    fetchAllPages<RegistrationRow>(`/matches/${id}/registrations`, { status: 0 }),
    fetchAllPages<RegistrationRow>(`/matches/${id}/registrations`, { status: 1 }),
    attendancePromise,
  ]);
  return {
    confirmed,
    waitlist,
    attendance: attendance.items,
    attendanceError: attendance.error,
  };
}

export async function signupMatch(id: number): Promise<Registration> {
  const result = await highRiskWrite<{ registration: Registration }>(
    'POST',
    `/matches/${id}/registrations`,
  );
  return result.registration;
}

export function cancelMyRegistration(id: number): Promise<CancellationResult> {
  return highRiskWrite<CancellationResult>('DELETE', `/matches/${id}/registrations/me`);
}

export async function checkInMatch(id: number): Promise<Attendance> {
  const result = await highRiskWrite<{ attendance: Attendance }>('POST', `/matches/${id}/check-in`);
  return result.attendance;
}

// ---- 比赛状态与事件 ----

export function changeMatchStatus(id: number, body: MatchStatusUpdate): Promise<void> {
  return highRiskWrite<void>('POST', `/matches/${id}/status`, body);
}

export function fetchEvents(id: number): Promise<EventTimeline> {
  return request<EventTimeline>('GET', `/matches/${id}/events`);
}

export async function createMatchEvent(id: number, payload: EventCreate): Promise<MatchEvent> {
  const result = await highRiskWrite<{ event: MatchEvent }>(
    'POST',
    `/matches/${id}/events`,
    payload,
  );
  return result.event;
}

export function updateMatchEvent(eventId: number, payload: EventUpdate): Promise<void> {
  return highRiskWrite<void>('PATCH', `/events/${eventId}`, payload);
}

// ---- 阵容与分组 ----

export function fetchLineup(id: number, side: number): Promise<LineupView> {
  return request<LineupView>('GET', `/matches/${id}/lineups/${side}`);
}

export function fetchGrouping(id: number): Promise<GroupingView> {
  return request<GroupingView>('GET', `/matches/${id}/grouping`);
}

// ---- 通知 ----

export interface NotificationQuery {
  page: number;
  pageSize: number;
  unread?: boolean;
}

/** NTF-01 通知分页；unread=true 仅未读。 */
export function listNotifications(query: NotificationQuery): Promise<Page<Notification>> {
  return request<Page<Notification>>(
    'GET',
    `/notifications${queryString({
      page: query.page,
      pageSize: query.pageSize,
      unread: flag(query.unread),
    })}`,
  );
}

/** NTF-02 未读角标。 */
export async function fetchUnreadNotificationCount(): Promise<number> {
  const { count } = await request<NotificationUnreadCount>('GET', '/notifications/unread-count');
  return count;
}

export function markNotificationRead(id: number): Promise<NotificationReadResult> {
  return plainWrite<NotificationReadResult>('POST', `/notifications/${id}/read`);
}

export function markAllNotificationsRead(): Promise<NotificationReadAllResult> {
  return plainWrite<NotificationReadAllResult>('POST', '/notifications/read-all');
}

// ---- 我的资料 ----

export function saveMyPlayer(payload: PlayerProfileUpdate): Promise<PlayerView> {
  return plainWrite<PlayerView>('PATCH', '/me/player', payload);
}

/** FIL-01：上传只返回 objectKey/signedUrl，绑定仍走 saveMyPlayer。 */
export async function uploadAvatar(filePath: string): Promise<AvatarUploadView> {
  await ensureCsrf();
  return uploadFile<AvatarUploadView>('/files/avatar', filePath);
}

// ---- 球队 ----

export interface TeamPlayersQuery {
  all?: boolean;
  season?: string;
}

/** PLR-01 位置分组预览；all=true 返回各组全部。 */
export async function listTeamPlayers(query: TeamPlayersQuery = {}): Promise<PlayerPublic[]> {
  const { items } = await request<{ items: PlayerPublic[] }>(
    'GET',
    `/team/players${queryString({ all: flag(query.all), season: query.season })}`,
  );
  return items || [];
}

/** STA-01 赛季统计；缺省当前年份。 */
export function fetchTeamStats(season?: string): Promise<TeamStats> {
  return request<TeamStats>('GET', `/team/stats${queryString({ season })}`);
}

export interface LeaderboardsQuery {
  season?: string;
  scope?: LeaderboardScope;
}

/** STA-03 排行榜；生涯累计忽略并省略 season。 */
export function fetchLeaderboards(query: LeaderboardsQuery = {}): Promise<Leaderboard> {
  const season = query.scope === 'all' ? undefined : query.season;
  return request<Leaderboard>(
    'GET',
    `/team/leaderboards${queryString({ season, scope: query.scope })}`,
  );
}

// ---- 队内活动 ----

/** 最近活动（所有报名中活动）/历史/全部；缺省由服务端按 all 处理。 */
export type ActivityScope = 'latest' | 'history' | 'all';

export interface ActivityListQuery {
  type?: ActivityType;
  status?: ActivityStatus;
  scope?: ActivityScope;
  page?: number;
  pageSize?: number;
}

/** ACT-01 C 端活动列表；登录时含 mySignup。「最近活动」为所有报名中活动，正常分页。 */
export function listActivities(query: ActivityListQuery = {}): Promise<Page<ActivitySummary>> {
  const filters = queryString({
    type: query.type,
    status: query.status,
    scope: query.scope,
  });
  const page = Math.max(1, Math.floor(query.page ?? 1) || 1);
  const pageSize = query.pageSize ?? DEFAULT_PAGE_SIZE;
  return request<Page<ActivitySummary>>(
    'GET',
    `/activities${filters}${filters ? '&' : '?'}page=${page}&pageSize=${pageSize}`,
  );
}

/** ACT-02 活动详情；不内联报名名单，报名队列另调 ACT-05。 */
export function fetchActivity(id: number): Promise<ActivityDetail> {
  return request<ActivityDetail>('GET', `/activities/${id}`);
}

export interface ActivitySignupPayload {
  withFamily: boolean;
  familyNote?: string;
}

/** ACT-03 报名；满员与状态冲突由服务端返回 40918/40917。 */
export async function signupActivity(
  id: number,
  payload: ActivitySignupPayload,
): Promise<ActivitySignup> {
  const result = await highRiskWrite<{ signup: ActivitySignup }>(
    'POST',
    `/activities/${id}/signups`,
    payload,
  );
  return result.signup;
}

/** ACT-04 取消本人报名；重复取消返回 cancelled=false。 */
export function cancelActivitySignup(id: number): Promise<{ cancelled: boolean }> {
  return highRiskWrite<{ cancelled: boolean }>('DELETE', `/activities/${id}/signups/me`);
}

export interface ActivityStatusUpdate {
  status: ActivityStatus;
  version: number;
  reason?: string;
}

/** ACT-13 C 端详情流转状态；仅 activity:manage:any 可用。 */
export function changeActivityStatus(id: number, body: ActivityStatusUpdate): Promise<void> {
  return highRiskWrite<void>('POST', `/activities/${id}/status`, body);
}

/** ACT-05 报名队列分页；C12/MP11 详情页登录后按 activity:view:any 展示。 */
export function listActivitySignups(
  id: number,
  query: { page: number; pageSize: number },
): Promise<Page<ActivitySignup>> {
  return request<Page<ActivitySignup>>('GET', `/activities/${id}/signups${queryString(query)}`);
}

// ---- 账本（C 端只读） ----

export interface LedgerFilter {
  type?: LedgerType;
  categoryId?: number;
  from?: string;
  to?: string;
}

export interface LedgerEntryQuery extends LedgerFilter {
  page?: number;
  pageSize?: number;
}

/** LED-01 流水列表；from/to 为 ISO-8601 时间。 */
export function listLedgerEntries(query: LedgerEntryQuery = {}): Promise<Page<LedgerEntry>> {
  const page = Math.max(1, Math.floor(query.page ?? 1) || 1);
  const pageSize = query.pageSize ?? DEFAULT_PAGE_SIZE;
  return request<Page<LedgerEntry>>(
    'GET',
    `/ledger/entries${queryString({
      type: query.type,
      categoryId: query.categoryId,
      from: query.from,
      to: query.to,
      page,
      pageSize,
    })}`,
  );
}

/** LED-02 汇总；余额 = 收入 − 支出。 */
export function fetchLedgerSummary(filter: LedgerFilter = {}): Promise<LedgerSummary> {
  return request<LedgerSummary>(
    'GET',
    `/ledger/summary${queryString({
      type: filter.type,
      categoryId: filter.categoryId,
      from: filter.from,
      to: filter.to,
    })}`,
  );
}

/** LED-08 启用中的分类；direction=null 的通用分类也会返回。 */
export async function listLedgerCategories(): Promise<LedgerCategory[]> {
  const { items } = await request<{ items: LedgerCategory[] }>('GET', '/ledger/categories');
  return items || [];
}
