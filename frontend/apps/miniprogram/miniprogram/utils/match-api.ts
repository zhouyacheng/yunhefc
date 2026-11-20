// C 端比赛接口编排：路径、分页与写操作协议集中在此，页面只关心结果。
import type {
  Attendance,
  AttendanceRow,
  CancellationResult,
  EventCreate,
  EventTimeline,
  EventUpdate,
  GroupingView,
  LineupView,
  MatchDetail,
  MatchEvent,
  MatchStatusUpdate,
  MyMatchRegistration,
  Page,
  Registration,
  RegistrationRow,
} from '../types/api';
import { errorMessage } from './api-errors';
import { newIdempotencyKey } from './idempotency';
import { ensureCsrf, request } from './request';

const ROSTER_PAGE_SIZE = 100;

type QueryValue = string | number | undefined;

function queryString(query: Record<string, QueryValue>): string {
  const parts = Object.entries(query)
    .filter(([, value]) => value !== undefined && value !== '')
    .map(([key, value]) => `${key}=${encodeURIComponent(String(value))}`);
  return parts.length ? `?${parts.join('&')}` : '';
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

export function fetchMatchDetail(id: number): Promise<MatchDetail> {
  return request<MatchDetail>('GET', `/matches/${id}`);
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
  await ensureCsrf();
  const result = await request<{ registration: Registration }>(
    'POST',
    `/matches/${id}/registrations`,
    undefined,
    { idempotencyKey: newIdempotencyKey() },
  );
  return result.registration;
}

export async function cancelMyRegistration(id: number): Promise<CancellationResult> {
  await ensureCsrf();
  return request<CancellationResult>('DELETE', `/matches/${id}/registrations/me`, undefined, {
    idempotencyKey: newIdempotencyKey(),
  });
}

export async function checkInMatch(id: number): Promise<Attendance> {
  await ensureCsrf();
  const result = await request<{ attendance: Attendance }>(
    'POST',
    `/matches/${id}/check-in`,
    undefined,
    {
      idempotencyKey: newIdempotencyKey(),
    },
  );
  return result.attendance;
}

export async function changeMatchStatus(id: number, body: MatchStatusUpdate): Promise<void> {
  await ensureCsrf();
  await request('POST', `/matches/${id}/status`, body, {
    idempotencyKey: newIdempotencyKey(),
  });
}

export async function createMatchEvent(id: number, payload: EventCreate): Promise<MatchEvent> {
  await ensureCsrf();
  const result = await request<{ event: MatchEvent }>('POST', `/matches/${id}/events`, payload, {
    idempotencyKey: newIdempotencyKey(),
  });
  return result.event;
}

export async function updateMatchEvent(eventId: number, payload: EventUpdate): Promise<void> {
  await ensureCsrf();
  await request('PATCH', `/events/${eventId}`, payload, {
    idempotencyKey: newIdempotencyKey(),
  });
}

export function fetchLineup(id: number, side: number): Promise<LineupView> {
  return request<LineupView>('GET', `/matches/${id}/lineups/${side}`);
}

export function fetchGrouping(id: number): Promise<GroupingView> {
  return request<GroupingView>('GET', `/matches/${id}/grouping`);
}

export function fetchEvents(id: number): Promise<EventTimeline> {
  return request<EventTimeline>('GET', `/matches/${id}/events`);
}
