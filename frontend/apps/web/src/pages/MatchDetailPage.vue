<script setup lang="ts">
import { ERROR_CODES, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import type { QueryParams } from '@yunhefc/api-client';
import {
  ATTENDANCE_VIEW_ANY,
  EVENT_CREATE_OWN,
  EVENT_EDIT_ANY,
  EVENT_EDIT_OWN,
  GROUPING_GENERATE_ANY,
  GROUPING_VIEW_ANY,
  INVITE_CREATE_MATCH,
  INVITE_MANAGE_ANY,
  LINEUP_VIEW_ANY,
  MATCH_EDIT_ANY,
  MATCH_STATUS_ANY,
  REGISTRATION_CREATE_OWN,
  REGISTRATION_REMOVE_ANY,
  hasPermission,
} from '@yunhefc/permissions';
import type {
  Attendance,
  AttendanceRow,
  CancellationResult,
  DerivedScore,
  EventCreate,
  EventHalf,
  EventTeam,
  EventTimeline,
  EventType,
  EventUpdate,
  GroupedPlayer,
  GroupingTeam,
  GroupingView,
  InviteCreated,
  LineupSide,
  LineupView,
  MatchAdmin,
  MatchDetail,
  MatchEvent,
  MatchStatus,
  MatchStatusUpdate,
  MyMatchRegistration,
  Page,
  Registration,
  RegistrationRow,
} from '@yunhefc/shared';
import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  EVENT_TYPE_TEXT,
  MATCH_STATUS_CANCELLED,
  MATCH_STATUS_POSTPONED,
  REGISTRATION_STATUS,
  REGISTRATION_STATUS_TEXT,
  checkInWindowState,
  eventEditWindowOpen,
  eventEntryOpen,
  eventHalfText,
  eventTypeText,
  formatDateTime,
  groupingTeamName,
  isInternalMatchType,
  isOpponentLineupSide,
  matchStatusText,
  matchTypeText,
  positionText,
} from '@yunhefc/shared';
import { Button, Empty, Loading, Popup, Tag } from 'vant';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import { isPending } from '../features/auth/user-status';
import InviteDialog from '../features/match-detail/InviteDialog.vue';
import { type MatchInvitePayload } from '../features/match-detail/invite';
import { cancelState, promotionText, signupState } from '../features/match-detail/registration';
import RosterList from '../features/match-detail/RosterList.vue';
import {
  buildRoster,
  emptyRoster,
  rosterDiff,
  rosterSnapshot,
  type RosterSections,
  type RosterSnapshot,
} from '../features/match-detail/roster';
import {
  isWechatBrowser,
  shareMode,
  stableMatchUrl,
  WECHAT_SHARE_HINT,
} from '../features/match-detail/share';
import { statusOptions } from '../features/match-detail/status-options';
import StatusChangeDialog, {
  type StatusDialogMode,
  type StatusDialogPayload,
} from '../features/match-detail/StatusChangeDialog.vue';
import { matchTagType, scoreText } from '../features/matches/presentation';
import { toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';
import { hasSessionHint } from '../stores/session-hint';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const matchId = Number(route.params.id);

type TabName = 'overview' | 'events' | 'grouping' | 'lineup';

const activeTab = ref<TabName>('overview');
const match = ref<MatchDetail | null>(null);
const registration = ref<Registration | null>(null);
const attendance = ref<Attendance | null>(null);
const loading = ref(true);
const error = ref('');
const checkInLoading = ref(false);
const actionError = ref('');
const actionMessage = ref('');
const refreshError = ref('');
const now = ref(Date.now());
let requestSeq = 0;
let nowTimer: ReturnType<typeof setInterval> | null = null;

const overviewRoster = ref<RosterSections>(emptyRoster());
const overviewRosterLoading = ref(false);
const overviewRosterError = ref('');
const overviewAttendanceError = ref('');
const rosterDiffText = ref('');

const signingUp = ref(false);
const cancelling = ref(false);

const shareMessage = ref('');
const shareError = ref('');
const sharing = ref(false);

const inviteOpen = ref(false);
const inviteSubmitting = ref(false);
const inviteResult = ref<InviteCreated | null>(null);
const inviteError = ref('');

const statusBusy = ref(false);
const statusActionError = ref('');
const statusDialogOpen = ref(false);
const statusDialogMode = ref<StatusDialogMode>('postpone');
const statusInitialStart = ref('');
const statusInitialEnd = ref('');
const statusSubmitting = ref(false);
const statusError = ref('');

const events = ref<MatchEvent[]>([]);
const eventsLoading = ref(false);
const eventsError = ref('');
const eventDerived = ref<DerivedScore | null>(null);
const roster = ref<RegistrationRow[]>([]);

const eventModalOpen = ref(false);
const editingEvent = ref<MatchEvent | null>(null);
const eventFormError = ref('');
const selectorError = ref('');
const eventSubmitting = ref(false);

const grouping = ref<GroupingView | null>(null);
const groupingLoading = ref(false);
const groupingError = ref('');
const groupingTeams: GroupingTeam[] = ['A', 'B'];

const lineup = ref<LineupView | null>(null);
const lineupSide = ref<LineupSide>(0);
const lineupLoading = ref(false);
const lineupError = ref('');
const form = reactive({
  type: 0 as EventType,
  team: 0 as EventTeam,
  playerId: null as number | null,
  assistPlayerId: null as number | null,
  inPlayerId: null as number | null,
  outPlayerId: null as number | null,
  minute: null as number | null,
  half: 0 as EventHalf,
  penalty: false,
});

const pending = computed(() => isPending(session.user?.status ?? -1));
const isCancelled = computed(() => match.value?.status === MATCH_STATUS_CANCELLED);
const isPostponed = computed(() => match.value?.status === MATCH_STATUS_POSTPONED);
const checkInClosed = computed(() => isCancelled.value || isPostponed.value);
const windowState = computed(() =>
  match.value ? checkInWindowState(match.value.startAt, now.value) : 'after',
);
const canViewFullRoster = computed(
  () =>
    session.isAuthenticated &&
    !pending.value &&
    hasPermission(session.permissions, ATTENDANCE_VIEW_ANY),
);
const canManageRegistrations = computed(() =>
  hasPermission(session.permissions, REGISTRATION_REMOVE_ANY),
);
const canChangeStatus = computed(
  () => session.isAuthenticated && hasPermission(session.permissions, MATCH_STATUS_ANY),
);
const removalAllowed = computed(() => {
  const status = match.value?.status;
  return status === 0 || status === 1 || status === 2 || status === 3 || status === 5;
});
const hasActiveRegistration = computed(
  () =>
    registration.value?.status === REGISTRATION_STATUS.CONFIRMED ||
    registration.value?.status === REGISTRATION_STATUS.WAITLISTED,
);
const canInvite = computed(() => {
  if (!match.value || !session.isAuthenticated || pending.value || checkInClosed.value) {
    return false;
  }
  if (!hasPermission(session.permissions, INVITE_CREATE_MATCH)) {
    return false;
  }
  if (hasPermission(session.permissions, INVITE_MANAGE_ANY)) {
    return true;
  }
  return (
    session.player?.type === 1 &&
    registration.value?.type === 1 &&
    registration.value.status === REGISTRATION_STATUS.CONFIRMED
  );
});
const signup = computed(() => {
  const current = match.value;
  if (!current) {
    return null;
  }
  return signupState({
    authenticated: session.isAuthenticated,
    pending: pending.value,
    playerType: session.player?.type ?? null,
    matchStatus: current.status,
    now: now.value,
    signupDeadline: current.signupDeadline,
    queue: current,
    myRegistration: registration.value,
    canSignup: hasPermission(session.permissions, REGISTRATION_CREATE_OWN),
  });
});
const cancel = computed(() => {
  const current = match.value;
  if (!current) {
    return null;
  }
  return cancelState({
    authenticated: session.isAuthenticated,
    pending: pending.value,
    matchStatus: current.status,
    cancelLock: current.cancelLock,
    myRegistration: registration.value,
    now: now.value,
  });
});
const statusOptionList = computed(() =>
  match.value
    ? statusOptions({
        currentStatus: match.value.status,
        canEditAny: hasPermission(session.permissions, MATCH_EDIT_ANY),
      })
    : [],
);
const myRegistrationStatusText = computed(() => {
  if (!registration.value) {
    return '';
  }
  return isCancelled.value
    ? '已作废'
    : (REGISTRATION_STATUS_TEXT[registration.value.status] ?? registration.value.statusLabel ?? '');
});
const confirmed = computed(() => registration.value?.status === 0);
const canCheckIn = computed(
  () => confirmed.value && !checkInClosed.value && windowState.value === 'open',
);
const checkInHint = computed(() => {
  if (!confirmed.value) {
    return '仅确认报名的球员可以签到';
  }
  if (windowState.value === 'before') {
    return '签到未开始：开赛前 4 小时开放';
  }
  if (windowState.value === 'after') {
    return '签到已结束：比赛当天 23:59 截止';
  }
  return '签到窗口开放中：开赛前 4 小时至比赛当天 23:59';
});
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: toBrowserPath(route.fullPath) },
}));

const isEventAdmin = computed(() => hasPermission(session.permissions, EVENT_EDIT_ANY));
const hasEventCreate = computed(
  () =>
    hasPermission(session.permissions, EVENT_CREATE_OWN) ||
    hasPermission(session.permissions, EVENT_EDIT_ANY),
);
const eventEntryAllowed = computed(
  () =>
    Boolean(match.value) &&
    !checkInClosed.value &&
    eventEntryOpen(match.value?.status) &&
    hasEventCreate.value,
);
const playerWindowOpen = computed(() => {
  const current = match.value;
  return current !== null && eventEditWindowOpen(current.endAt);
});
const canCreateEvent = computed(
  () => eventEntryAllowed.value && (isEventAdmin.value || playerWindowOpen.value),
);
const eventDisabledReason = computed(() => {
  if (!match.value || hasEventCreate.value === false || isEventAdmin.value) {
    return '';
  }
  if (!eventEntryOpen(match.value.status)) {
    return checkInClosed.value
      ? '已取消/已延期的比赛不可录入事件'
      : '仅进行中或已结束的比赛可录入事件';
  }
  if (!playerWindowOpen.value) {
    return '已超过赛后 24 小时可编辑窗口';
  }
  return '';
});
const internalMatch = computed(() => isInternalMatchType(match.value?.type));
const teamLocked = computed(() => !isEventAdmin.value && !internalMatch.value);
const canViewGrouping = computed(
  () =>
    internalMatch.value &&
    session.isAuthenticated &&
    hasPermission(session.permissions, GROUPING_VIEW_ANY),
);
const canManageGrouping = computed(() => hasPermission(session.permissions, GROUPING_GENERATE_ANY));
const hasGrouping = computed(() =>
  Boolean(grouping.value && (grouping.value.teams.A.length || grouping.value.teams.B.length)),
);
const groupingPositionBuckets = [
  { key: 0, label: positionText(0) },
  { key: 1, label: positionText(1) },
  { key: 2, label: positionText(2) },
  { key: 3, label: positionText(3) },
  { key: -1, label: '位置未知' },
];

function groupingPositionInBucket(position: number, bucket: number): boolean {
  return bucket === -1 ? position < 0 || position > 3 : position === bucket;
}

const groupingPositionRows = computed(() =>
  groupingPositionBuckets.map((bucket) => ({
    position: bucket.key,
    label: bucket.label,
    a:
      grouping.value?.teams.A.filter((player) =>
        groupingPositionInBucket(player.position, bucket.key),
      ).length ?? 0,
    b:
      grouping.value?.teams.B.filter((player) =>
        groupingPositionInBucket(player.position, bucket.key),
      ).length ?? 0,
  })),
);
const displayedScore = computed(() => eventDerived.value ?? match.value?.derivedScore ?? null);
const showAdminLink = computed(() => session.isAuthenticated && isEventAdmin.value);
const adminBase =
  import.meta.env.VITE_ADMIN_URL ??
  (import.meta.env.DEV ? 'http://localhost:5274/admin/' : '/admin/');
const adminPrefix = adminBase.endsWith('/') ? adminBase : `${adminBase}/`;
const adminEventsUrl = computed(() => `${adminPrefix}events?match=${matchId}`);
const adminGroupingUrl = computed(() => `${adminPrefix}grouping?match=${matchId}`);
const adminRegistrationsUrl = computed(() => `${adminPrefix}registrations?match=${matchId}`);
const rosterAdminUrl = computed(() =>
  canManageRegistrations.value && removalAllowed.value ? adminRegistrationsUrl.value : null,
);
const goalCount = computed(() => events.value.filter((event) => event.type === 0).length);
const cardCount = computed(
  () => events.value.filter((event) => event.type === 3 || event.type === 4).length,
);
const substitutionCount = computed(() => events.value.filter((event) => event.type === 2).length);

const allowedTypes = computed<EventType[]>(() =>
  isEventAdmin.value ? [0, 1, 2, 3, 4] : [0, 1, 3, 4],
);
const rosterPlayers = computed(() =>
  roster.value
    .map((row) => row.player)
    .filter((player, index, all) => all.findIndex((item) => item.id === player.id) === index),
);
const ownPlayerId = computed(() => session.player?.id ?? null);
const playerOptions = computed(() => {
  if (teamLocked.value) {
    return rosterPlayers.value.filter((player) => player.id === ownPlayerId.value);
  }
  return rosterPlayers.value;
});

function displayedScoreText(): string {
  const source = displayedScore.value ?? match.value;
  return source ? scoreText(source) : 'VS';
}

function teamName(team: EventTeam): string {
  if (!match.value) {
    return team === 0 ? '主队' : '客队';
  }
  return team === 0 ? match.value.homeTeamName : match.value.awayTeamName;
}

function eventTime(event: MatchEvent): string {
  return `${eventHalfText(event.half)} ${event.minute}'`;
}

function eventDetail(event: MatchEvent): string {
  if (event.type === 2) {
    return `${event.inPlayer?.name ?? '—'} 换上 · ${event.outPlayer?.name ?? '—'} 换下`;
  }
  const parts = [event.player?.name ?? '—'];
  if (event.assistPlayer) {
    parts.push(`助攻 ${event.assistPlayer.name}`);
  }
  if (event.penalty) {
    parts.push('点球');
  }
  return parts.join(' · ');
}

function canEditEvent(event: MatchEvent): boolean {
  if (!match.value || checkInClosed.value) {
    return false;
  }
  return (
    hasPermission(session.permissions, EVENT_EDIT_OWN) &&
    event.type !== 2 &&
    event.player?.id === ownPlayerId.value &&
    playerWindowOpen.value
  );
}

async function load(silent = false): Promise<void> {
  const seq = ++requestSeq;
  if (!silent) {
    loading.value = true;
    error.value = '';
    actionError.value = '';
    actionMessage.value = '';
    refreshError.value = '';
    rosterDiffText.value = '';
  }
  now.value = Date.now();
  try {
    if (!Number.isFinite(matchId) || matchId <= 0) {
      await router.replace('/404');
      return;
    }
    if (!session.loaded && hasSessionHint()) {
      await session.ensureLoaded().catch(() => undefined);
    }
    const detail = await api.get<MatchDetail>(`/matches/${matchId}`);
    if (seq !== requestSeq) {
      return;
    }
    match.value = detail;
    if (session.isAuthenticated && !pending.value) {
      const mine = await api.get<MyMatchRegistration>(`/matches/${matchId}/registrations/me`);
      if (seq !== requestSeq) {
        return;
      }
      registration.value = mine.registration ?? null;
      attendance.value = mine.attendance ?? null;
    } else {
      registration.value = detail.myRegistration ?? null;
      attendance.value = null;
    }
    error.value = '';
    refreshError.value = '';
    loading.value = false;
    void loadOverviewRoster(seq);
    if (!silent) {
      void loadEvents();
    }
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      await router.replace('/404');
      return;
    }
    const message = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    if (silent && match.value) {
      refreshError.value = message;
    } else {
      error.value = message;
      match.value = null;
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function fetchAllPages<T>(path: string, query: QueryParams): Promise<T[]> {
  const first = await api.get<Page<T>>(path, {
    query: { ...query, page: 1, pageSize: 100 },
  });
  const items = [...first.items];
  for (let page = 2; page <= first.totalPages; page += 1) {
    const next = await api.get<Page<T>>(path, {
      query: { ...query, page, pageSize: 100 },
    });
    items.push(...next.items);
  }
  return items;
}

function ownRosterPlayer() {
  const player = session.player;
  if (!player) {
    return null;
  }
  return {
    id: player.id,
    name: player.name,
    jersey: player.jersey,
    position: player.position,
    positionLabel: player.positionLabel,
  };
}

/** 外挂无 attendance:view:any：只用 REG-04 构造本人单行名单。 */
function guestOwnRoster(): RosterSections {
  const mine = registration.value;
  const player = ownRosterPlayer();
  if (!mine || !player) {
    return emptyRoster();
  }
  const row: RegistrationRow = {
    id: mine.id,
    player,
    type: mine.type,
    typeLabel: mine.typeLabel,
    status: mine.status,
    statusLabel: mine.statusLabel,
    registeredAt: mine.registeredAt,
    cancelledAt: mine.cancelledAt,
  };
  const attendanceRows: AttendanceRow[] = attendance.value
    ? [
        {
          id: attendance.value.id,
          player,
          status: attendance.value.status,
          statusLabel: attendance.value.statusLabel,
          source: attendance.value.source,
          sourceLabel: attendance.value.sourceLabel,
          recordedAt: attendance.value.recordedAt,
        },
      ]
    : [];
  return buildRoster(
    mine.status === REGISTRATION_STATUS.CONFIRMED ? [row] : [],
    mine.status === REGISTRATION_STATUS.WAITLISTED ? [row] : [],
    attendanceRows,
  );
}

async function loadOverviewRoster(seq: number): Promise<void> {
  overviewRosterLoading.value = true;
  overviewRosterError.value = '';
  overviewAttendanceError.value = '';
  try {
    if (!session.isAuthenticated || pending.value || isCancelled.value) {
      overviewRoster.value = emptyRoster();
      return;
    }
    if (!canViewFullRoster.value) {
      overviewRoster.value = guestOwnRoster();
      return;
    }
    const attendancePromise = fetchAllPages<AttendanceRow>(
      `/matches/${matchId}/attendance`,
      {},
    ).catch((caught: unknown) => {
      overviewAttendanceError.value = isApiError(caught)
        ? caught.message
        : '考勤加载失败，名单未显示考勤';
      return [] as AttendanceRow[];
    });
    const [confirmed, waitlist, attendanceRows] = await Promise.all([
      fetchAllPages<RegistrationRow>(`/matches/${matchId}/registrations`, { status: 0 }),
      fetchAllPages<RegistrationRow>(`/matches/${matchId}/registrations`, { status: 1 }),
      attendancePromise,
    ]);
    if (seq !== requestSeq) {
      return;
    }
    overviewRoster.value = buildRoster(confirmed, waitlist, attendanceRows);
    compareRosterReturnNotice();
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    overviewRoster.value = emptyRoster();
    overviewRosterError.value = isApiError(caught) ? caught.message : '名单加载失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      overviewRosterLoading.value = false;
    }
  }
}

async function loadEvents(): Promise<void> {
  eventsLoading.value = true;
  eventsError.value = '';
  try {
    const timeline = await api.get<EventTimeline>(`/matches/${matchId}/events`);
    events.value = timeline.items;
    eventDerived.value = timeline.derivedScore ?? null;
  } catch (caught) {
    eventsError.value = isApiError(caught) ? caught.message : '事件加载失败，请稍后重试';
  } finally {
    eventsLoading.value = false;
  }
}

async function loadRoster(): Promise<void> {
  if (!session.isAuthenticated) {
    roster.value = [];
    return;
  }
  try {
    const page = await api.get<Page<RegistrationRow>>(
      `/matches/${matchId}/registrations?status=0&pageSize=100`,
    );
    roster.value = page.items;
  } catch {
    // REG-05 需要 attendance:view:any；权限异常时至少保留本人，避免弹窗无球员可选。
    const player = session.player;
    roster.value =
      registration.value?.status === 0 && player
        ? [
            {
              id: registration.value.id,
              player: {
                id: player.id,
                name: player.name,
                jersey: player.jersey,
                position: player.position,
                positionLabel: player.positionLabel,
              },
              type: registration.value.type,
              status: registration.value.status,
              registeredAt: registration.value.registeredAt,
            },
          ]
        : [];
  }
}

async function checkIn(): Promise<void> {
  if (!canCheckIn.value || checkInLoading.value) {
    return;
  }
  checkInLoading.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await api.post<{ attendance: Attendance }>(
      `/matches/${matchId}/check-in`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    attendance.value = result.attendance;
    actionMessage.value = '签到成功';
    await load(true);
  } catch (caught) {
    actionError.value = isApiError(caught) ? caught.message : '签到失败，请稍后重试';
  } finally {
    checkInLoading.value = false;
  }
}

/** 05 §2.3 的报名域 40902/40903；api-client 的 ERROR_CODES 暂未收录。 */
const REGISTRATION_DUPLICATE = 40902;
const REGISTRATION_FULL = 40903;

function shouldRefreshAfterConflict(caught: unknown): boolean {
  return (
    isApiError(caught) &&
    [
      ERROR_CODES.VERSION_CONFLICT,
      REGISTRATION_DUPLICATE,
      REGISTRATION_FULL,
      ERROR_CODES.MATCH_STATE_CONFLICT,
    ].includes(caught.code)
  );
}

async function signupNow(): Promise<void> {
  const state = signup.value;
  if (!match.value || signingUp.value || !state || state.action !== 'signup' || state.disabled) {
    return;
  }
  signingUp.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await api.post<{ registration: Registration }>(
      `/matches/${matchId}/registrations`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    actionMessage.value =
      result.registration.status === REGISTRATION_STATUS.WAITLISTED ? '已加入候补' : '报名成功';
    await load(true);
  } catch (caught) {
    actionError.value = isApiError(caught) ? caught.message : '报名失败，请稍后重试';
    if (shouldRefreshAfterConflict(caught)) {
      await load(true);
    }
  } finally {
    signingUp.value = false;
  }
}

async function cancelNow(): Promise<void> {
  const state = cancel.value;
  if (!match.value || cancelling.value || !state || !state.visible || state.disabled) {
    return;
  }
  const cancelledStatus = registration.value?.status ?? null;
  const fallbackName = overviewRoster.value.formalWaitlist[0]?.row.player.name ?? null;
  cancelling.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await api.delete<CancellationResult>(
      `/matches/${matchId}/registrations/me`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    await load(true);
    let promotedName = result.promotedPlayerId ? rosterPlayerName(result.promotedPlayerId) : null;
    if (result.promotedPlayerId && !promotedName) {
      await loadOverviewRoster(requestSeq);
      promotedName = rosterPlayerName(result.promotedPlayerId);
    }
    actionMessage.value = promotionText(result, promotedName ?? fallbackName, cancelledStatus);
  } catch (caught) {
    actionError.value = isApiError(caught) ? caught.message : '取消报名失败，请稍后重试';
    if (shouldRefreshAfterConflict(caught)) {
      await load(true);
    }
  } finally {
    cancelling.value = false;
  }
}

function rosterPlayerName(playerId: number): string | null {
  const entries = [
    ...overviewRoster.value.formalConfirmed,
    ...overviewRoster.value.formalWaitlist,
    ...overviewRoster.value.guestConfirmed,
  ];
  return entries.find((entry) => entry.row.player.id === playerId)?.row.player.name ?? null;
}

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 回退到 execCommand。
  }
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(textarea);
    return copied;
  } catch {
    return false;
  }
}

async function shareMatch(): Promise<void> {
  if (!match.value || sharing.value) {
    return;
  }
  sharing.value = true;
  shareMessage.value = '';
  shareError.value = '';
  const url = stableMatchUrl(matchId, window.location.origin, import.meta.env.BASE_URL);
  try {
    const mode = shareMode({
      isWechat: isWechatBrowser(navigator.userAgent),
      canNativeShare: typeof navigator.share === 'function',
    });
    if (mode === 'wechat') {
      shareMessage.value = WECHAT_SHARE_HINT;
      return;
    }
    if (mode === 'native') {
      try {
        await navigator.share({ title: match.value.title, url });
        shareMessage.value = '已调起系统分享';
        return;
      } catch (caught) {
        if ((caught as { name?: string }).name === 'AbortError') {
          return;
        }
      }
    }
    const copied = await copyText(url);
    shareMessage.value = copied ? '链接已复制' : '复制失败，请手动复制';
    shareError.value = copied ? '' : url;
  } finally {
    sharing.value = false;
  }
}

function openInvite(): void {
  inviteResult.value = null;
  inviteError.value = '';
  inviteOpen.value = true;
}

function closeInvite(value: boolean): void {
  // 创建请求在途时关闭会丢失只返回一次的 inviteUrl。
  if (inviteSubmitting.value) {
    return;
  }
  inviteOpen.value = value;
  if (!value) {
    inviteResult.value = null;
    inviteError.value = '';
  }
}

async function submitInvite(payload: MatchInvitePayload): Promise<void> {
  if (inviteSubmitting.value) {
    return;
  }
  inviteSubmitting.value = true;
  inviteError.value = '';
  try {
    inviteResult.value = await api.post<InviteCreated>(`/matches/${matchId}/invites`, payload, {
      idempotencyKey: newIdempotencyKey(),
    });
  } catch (caught) {
    inviteError.value = isApiError(caught) ? caught.message : '创建邀请失败，请稍后重试';
  } finally {
    inviteSubmitting.value = false;
  }
}

async function copyInvite(url: string): Promise<void> {
  const copied = await copyText(url);
  inviteError.value = copied ? '' : '复制失败，请手动选中链接复制';
}

function openStatusDialog(mode: StatusDialogMode): void {
  statusDialogMode.value = mode;
  statusInitialStart.value = match.value?.startAt ?? '';
  statusInitialEnd.value = match.value?.endAt ?? '';
  statusError.value = '';
  statusDialogOpen.value = true;
}

async function changeStatus(target: number): Promise<void> {
  if (!match.value || statusBusy.value) {
    return;
  }
  statusBusy.value = true;
  statusActionError.value = '';
  try {
    const body: MatchStatusUpdate = {
      status: target as MatchStatus,
      version: match.value.version,
      manualStatus: true,
    };
    await api.post<MatchAdmin>(`/matches/${matchId}/status`, body, {
      idempotencyKey: newIdempotencyKey(),
    });
    await load(true);
  } catch (caught) {
    statusActionError.value = isApiError(caught) ? caught.message : '状态修改失败，请稍后重试';
    if (shouldRefreshAfterConflict(caught)) {
      await load(true);
    }
  } finally {
    statusBusy.value = false;
  }
}

async function onStatusSelect(event: Event): Promise<void> {
  const select = event.target as HTMLSelectElement;
  const target = Number(select.value);
  select.value = '';
  if (!match.value || !Number.isInteger(target)) {
    return;
  }
  if (target === match.value.status) {
    return;
  }
  if (target === MATCH_STATUS_POSTPONED) {
    openStatusDialog('postpone');
    return;
  }
  if (target === MATCH_STATUS_CANCELLED) {
    openStatusDialog('cancel');
    return;
  }
  await changeStatus(target);
}

async function submitStatusDialog(payload: StatusDialogPayload): Promise<void> {
  if (!match.value || statusSubmitting.value) {
    return;
  }
  statusSubmitting.value = true;
  statusError.value = '';
  const body: MatchStatusUpdate =
    'reason' in payload
      ? {
          status: MATCH_STATUS_CANCELLED,
          version: match.value.version,
          manualStatus: true,
          reason: payload.reason,
        }
      : {
          status: MATCH_STATUS_POSTPONED,
          version: match.value.version,
          manualStatus: true,
          startAt: payload.startAt,
          endAt: payload.endAt,
        };
  try {
    await api.post<MatchAdmin>(`/matches/${matchId}/status`, body, {
      idempotencyKey: newIdempotencyKey(),
    });
    statusDialogOpen.value = false;
    await load(true);
  } catch (caught) {
    statusError.value = isApiError(caught) ? caught.message : '状态修改失败，请稍后重试';
    if (shouldRefreshAfterConflict(caught)) {
      await load(true);
    }
  } finally {
    statusSubmitting.value = false;
  }
}

const ROSTER_SNAPSHOT_PREFIX = 'yunhefc.c.rosterSnapshot.';
const ROSTER_SNAPSHOT_TTL_MS = 15 * 60 * 1000;

function rosterSnapshotKey(): string {
  // 快照按用户隔离，避免退出/切换账号后把全队名单泄露给其它身份。
  return `${ROSTER_SNAPSHOT_PREFIX}${matchId}.${session.user?.id ?? 0}`;
}

function saveRosterSnapshot(): void {
  if (!canViewFullRoster.value || !overviewRoster.value.total) {
    return;
  }
  try {
    sessionStorage.setItem(
      rosterSnapshotKey(),
      JSON.stringify({
        savedAt: Date.now(),
        entries: rosterSnapshot(overviewRoster.value),
      }),
    );
  } catch {
    // 存储不可用时仅失去返回提示，不影响名单浏览。
  }
}

function hasRosterSnapshot(): boolean {
  try {
    return sessionStorage.getItem(rosterSnapshotKey()) !== null;
  } catch {
    return false;
  }
}

function compareRosterReturnNotice(): void {
  if (!canViewFullRoster.value) {
    return;
  }
  let raw: string | null;
  try {
    raw = sessionStorage.getItem(rosterSnapshotKey());
  } catch {
    return;
  }
  if (!raw) {
    return;
  }
  try {
    sessionStorage.removeItem(rosterSnapshotKey());
  } catch {
    // 忽略清理失败。
  }
  try {
    const saved = JSON.parse(raw) as { savedAt?: number; entries?: RosterSnapshot };
    if (!saved.entries || !saved.savedAt || Date.now() - saved.savedAt > ROSTER_SNAPSHOT_TTL_MS) {
      return;
    }
    const diff = rosterDiff(saved.entries, overviewRoster.value);
    // 第二次返回但名单无变化时清空上一次的提示。
    rosterDiffText.value = diff.changed ? diff.text : '';
  } catch {
    // 快照损坏时忽略。
  }
}

function onPageShow(event: PageTransitionEvent): void {
  if (event.persisted) {
    void load(true);
  }
}

function onVisibilityChange(): void {
  if (document.visibilityState === 'visible' && hasRosterSnapshot()) {
    void load(true);
  }
}

function resetForm(): void {
  form.type = 0;
  form.team = teamLocked.value ? ((match.value?.ourSide ?? 0) as EventTeam) : 0;
  form.playerId = teamLocked.value ? ownPlayerId.value : null;
  form.assistPlayerId = null;
  form.inPlayerId = null;
  form.outPlayerId = null;
  form.minute = null;
  form.half = 0;
  form.penalty = false;
  eventFormError.value = '';
  selectorError.value = '';
}

async function openCreate(): Promise<void> {
  if (!canCreateEvent.value) {
    return;
  }
  editingEvent.value = null;
  resetForm();
  await loadRoster();
  const onlyPlayer = playerOptions.value.length === 1 ? playerOptions.value[0] : undefined;
  if (!teamLocked.value && form.playerId === null && onlyPlayer) {
    form.playerId = onlyPlayer.id;
  }
  eventModalOpen.value = true;
}

async function openEdit(event: MatchEvent): Promise<void> {
  if (!canEditEvent(event)) {
    return;
  }
  editingEvent.value = event;
  form.type = event.type;
  form.team = event.team;
  form.playerId = event.player?.id ?? null;
  form.assistPlayerId = event.assistPlayer?.id ?? null;
  form.inPlayerId = event.inPlayer?.id ?? null;
  form.outPlayerId = event.outPlayer?.id ?? null;
  form.minute = event.minute;
  form.half = event.half;
  form.penalty = event.penalty;
  eventFormError.value = '';
  selectorError.value = '';
  await loadRoster();
  eventModalOpen.value = true;
}

function eventPayload(): EventCreate {
  const substitution = form.type === 2;
  const goal = form.type === 0;
  return {
    type: form.type,
    team: form.team,
    playerId: substitution ? null : form.playerId,
    assistPlayerId: goal ? form.assistPlayerId : null,
    inPlayerId: substitution ? form.inPlayerId : null,
    outPlayerId: substitution ? form.outPlayerId : null,
    minute: form.minute ?? 0,
    half: form.half,
    penalty: goal ? form.penalty : false,
  };
}

function validateEventForm(): string {
  if (!form.minute || form.minute < 1 || form.minute > 120) {
    return '请填写 1–120 的比赛分钟';
  }
  if (form.type === 2) {
    if (!form.inPlayerId || !form.outPlayerId) {
      return '换人事件必须选择换上和换下球员';
    }
    if (form.inPlayerId === form.outPlayerId) {
      return '换上和换下球员不能相同';
    }
    return '';
  }
  if (!form.playerId) {
    return '请选择球员';
  }
  if (form.type === 0 && form.assistPlayerId === form.playerId) {
    return '助攻球员不能是进球球员本人';
  }
  return '';
}

async function submitEvent(): Promise<void> {
  if (eventSubmitting.value) {
    return;
  }
  const message = validateEventForm();
  if (message) {
    eventFormError.value = message;
    return;
  }
  eventSubmitting.value = true;
  eventFormError.value = '';
  selectorError.value = '';
  try {
    if (editingEvent.value) {
      const update: EventUpdate = {
        ...eventPayload(),
        version: editingEvent.value.version,
      };
      await api.patch(`/events/${editingEvent.value.id}`, update, {
        idempotencyKey: newIdempotencyKey(),
      });
    } else {
      await api.post<{ event: MatchEvent }>(`/matches/${matchId}/events`, eventPayload(), {
        idempotencyKey: newIdempotencyKey(),
      });
    }
    eventModalOpen.value = false;
    activeTab.value = 'events';
    await loadEvents();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.GROUPING_MISMATCH) {
      // 行内提示违规字段与球员，并引导到 B06 重新生成分组。
      selectorError.value = caught.message;
    } else {
      eventFormError.value = isApiError(caught) ? caught.message : '事件保存失败，请稍后重试';
    }
  } finally {
    eventSubmitting.value = false;
  }
}

function openEventsTab(): void {
  activeTab.value = 'events';
  if (!events.value.length && !eventsLoading.value) {
    void loadEvents();
  }
}

function groupingTeamLabel(team: GroupingTeam): string {
  return match.value ? groupingTeamName(match.value, team) : `${team} 队`;
}

function groupingTotal(team: GroupingTeam): string {
  const total = (grouping.value?.teams[team] ?? []).reduce(
    (sum, player) => sum + (player.finalScore ?? 0),
    0,
  );
  return total.toFixed(2);
}

function groupingPlayers(team: GroupingTeam, position: number): GroupedPlayer[] {
  return (grouping.value?.teams[team] ?? []).filter((player) =>
    groupingPositionInBucket(player.position, position),
  );
}

function groupingPlayerLabel(player: GroupedPlayer): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey}`;
}

async function loadGrouping(): Promise<void> {
  if (!canViewGrouping.value) {
    return;
  }
  groupingLoading.value = true;
  groupingError.value = '';
  try {
    grouping.value = await api.get<GroupingView>(`/matches/${matchId}/grouping`);
  } catch (caught) {
    grouping.value = null;
    groupingError.value = isApiError(caught) ? caught.message : '分组加载失败，请稍后重试';
  } finally {
    groupingLoading.value = false;
  }
}

function openGroupingTab(): void {
  activeTab.value = 'grouping';
  if (!grouping.value && !groupingLoading.value) {
    void loadGrouping();
  }
}

const canViewLineup = computed(
  () => session.isAuthenticated && hasPermission(session.permissions, LINEUP_VIEW_ANY),
);
const lineupOpponent = computed(() => isOpponentLineupSide(match.value, lineupSide.value));
const lineupStarterMap = computed(() => {
  const map = new Map<string, NonNullable<LineupView['starters'][number]['player']>>();
  lineup.value?.starters.forEach((starter) => {
    if (starter.player) {
      map.set(starter.slotKey, starter.player);
    }
  });
  return map;
});

function lineupStarter(slotKey: string) {
  return lineupStarterMap.value.get(slotKey) ?? null;
}

function lineupPlayerLabel(player: { name: string; jersey?: number | null }): string {
  return player.jersey === null || player.jersey === undefined
    ? player.name
    : `${player.name} #${player.jersey}`;
}

function defaultLineupSide(): LineupSide {
  return isInternalMatchType(match.value?.type) ? 0 : ((match.value?.ourSide ?? 0) as LineupSide);
}

async function loadLineup(side: LineupSide): Promise<void> {
  lineupLoading.value = true;
  lineupError.value = '';
  try {
    const result = await api.get<LineupView>(`/matches/${matchId}/lineups/${side}`);
    if (lineupSide.value === side) {
      lineup.value = result;
    }
  } catch (caught) {
    if (lineupSide.value === side) {
      lineup.value = null;
      lineupError.value = isApiError(caught) ? caught.message : '阵容加载失败，请稍后重试';
    }
  } finally {
    if (lineupSide.value === side) {
      lineupLoading.value = false;
    }
  }
}

function switchLineupSide(next: LineupSide): void {
  if (next === lineupSide.value) {
    return;
  }
  lineupSide.value = next;
  lineup.value = null;
  void loadLineup(next);
}

function openLineupTab(): void {
  activeTab.value = 'lineup';
  if (!lineup.value && !lineupLoading.value) {
    lineupSide.value = defaultLineupSide();
    void loadLineup(lineupSide.value);
  }
}

onMounted(() => {
  void load();
  window.addEventListener('pageshow', onPageShow);
  document.addEventListener('visibilitychange', onVisibilityChange);
  nowTimer = setInterval(() => {
    now.value = Date.now();
  }, 60_000);
});

onBeforeUnmount(() => {
  window.removeEventListener('pageshow', onPageShow);
  document.removeEventListener('visibilitychange', onVisibilityChange);
  if (nowTimer) {
    clearInterval(nowTimer);
    nowTimer = null;
  }
});
</script>

<template>
  <section class="page">
    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <template v-else-if="match">
      <header class="card match-hero">
        <div class="match-hero__top">
          <span class="small muted">{{ matchTypeText(match.type) }}</span>
          <Tag :type="matchTagType(match.status)" plain>{{ matchStatusText(match.status) }}</Tag>
          <span class="muted small">第 {{ match.seasonMatchNo }} 场</span>
        </div>
        <div class="match-hero__teams">
          <span>{{ match.homeTeamName }}</span>
          <strong>{{ displayedScoreText() }}</strong>
          <span>{{ match.awayTeamName }}</span>
        </div>
        <div class="match-hero__meta muted small">
          <span>{{ formatDateTime(match.startAt) }}</span>
          <span>{{ match.venue }}</span>
        </div>

        <div v-if="isCancelled" class="voided-banner">
          <p class="notice notice--danger" data-testid="cancelled-banner">
            比赛已取消，报名作废，不占名额、不计考勤
          </p>
          <p class="muted small" data-testid="cancelled-history">
            历史报名：正式 {{ match.confirmedCount }} · 外挂 {{ match.guestCount }} · 候补
            {{ match.waitlistCount }}
          </p>
        </div>
        <p v-else-if="isPostponed" class="notice" data-testid="postponed-banner">
          比赛已延期，等待管理员重新排期；报名记录保留。
        </p>

        <div class="match-hero__actions">
          <Button
            size="small"
            type="primary"
            plain
            data-testid="share-button"
            :loading="sharing"
            @click="shareMatch"
          >
            分享比赛
          </Button>
          <Button
            v-if="canInvite"
            size="small"
            type="primary"
            plain
            data-testid="invite-button"
            @click="openInvite"
          >
            邀请队友
          </Button>
          <select
            v-if="canChangeStatus"
            class="status-select"
            data-testid="status-select"
            aria-label="修改比赛状态"
            :disabled="statusBusy"
            @change="onStatusSelect"
          >
            <option value="">修改状态</option>
            <option
              v-for="option in statusOptionList"
              :key="option.status"
              :value="option.status"
              :disabled="option.disabled"
              :title="option.reason"
            >
              {{ option.label }}
            </option>
          </select>
        </div>

        <p v-if="shareMessage" class="hint" data-testid="share-message">{{ shareMessage }}</p>
        <input
          v-if="shareError"
          class="share-manual"
          data-testid="share-manual"
          :value="shareError"
          readonly
          aria-label="分享链接"
        />
        <p v-if="statusActionError" class="action-error" data-testid="status-action-error">
          {{ statusActionError }}
        </p>
      </header>

      <nav class="detail-tabs" aria-label="比赛详情分区">
        <button
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'overview' }"
          @click="activeTab = 'overview'"
        >
          概览
        </button>
        <button
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'events' }"
          @click="openEventsTab"
        >
          事件
        </button>
        <button
          v-if="canViewLineup"
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'lineup' }"
          @click="openLineupTab"
        >
          阵容
        </button>
        <button
          v-if="canViewGrouping"
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'grouping' }"
          @click="openGroupingTab"
        >
          分组
        </button>
      </nav>

      <template v-if="activeTab === 'overview'">
        <section class="card registration-card" data-testid="registration-card">
          <div class="card-head">
            <h2>我的报名</h2>
            <Tag
              v-if="registration || isCancelled"
              size="medium"
              type="primary"
              plain
              data-testid="registration-status"
            >
              {{ myRegistrationStatusText || '已作废' }}
            </Tag>
          </div>

          <template v-if="isCancelled">
            <p class="muted">比赛已取消，报名记录仅保留、已作废，不占名额。</p>
            <div class="hero-queue">
              <div class="hero-queue__item">
                <span>历史报名</span>
                <b>{{ match.confirmedCount + match.guestCount }}</b>
              </div>
              <div class="hero-queue__item">
                <span>历史候补</span>
                <b>{{ match.waitlistCount }}</b>
              </div>
            </div>
          </template>

          <template v-else-if="!session.isAuthenticated">
            <p class="muted">登录后可报名与签到</p>
            <RouterLink :to="loginTarget">
              <Button type="primary" block>去登录</Button>
            </RouterLink>
          </template>

          <template v-else-if="pending">
            <p class="notice" data-testid="pending-banner">
              账号待审核：可以浏览比赛，暂不能报名或签到。
            </p>
            <p v-if="registration" class="muted small">
              当前报名状态：{{ myRegistrationStatusText }}
            </p>
          </template>

          <template v-else>
            <div class="hero-queue queue-progress">
              <div class="hero-queue__item" data-testid="queue-formal">
                <span>正式</span>
                <b>{{ match.confirmedCount }} / {{ match.capacity }}</b>
              </div>
              <div class="hero-queue__item" data-testid="queue-guest">
                <span>外挂</span>
                <b>{{ match.guestCount }} / {{ match.guestCapacity }}</b>
              </div>
              <div class="hero-queue__item" data-testid="queue-waitlist">
                <span>候补</span>
                <b>{{ match.waitlistCount }} / {{ match.waitlistCapacity }}</b>
              </div>
            </div>

            <template v-if="!hasActiveRegistration && signup">
              <template v-if="signup.visible">
                <Button
                  v-if="signup.action === 'login'"
                  type="primary"
                  block
                  @click="router.push(loginTarget)"
                >
                  {{ signup.label }}
                </Button>
                <Button
                  v-else
                  type="primary"
                  block
                  data-testid="signup-button"
                  :disabled="signup.disabled"
                  :loading="signingUp"
                  @click="signupNow"
                >
                  {{ signup.label }}
                </Button>
                <p v-if="signup.reason" class="hint">{{ signup.reason }}</p>
              </template>
              <p v-else-if="signup.reason" class="hint">{{ signup.reason }}</p>
            </template>

            <template v-else-if="hasActiveRegistration && cancel">
              <div v-if="attendance" class="attendance-status">
                <p>
                  考勤状态：<strong>{{ ATTENDANCE_STATUS_TEXT[attendance.status] }}</strong>
                </p>
                <p class="muted small">
                  来源：{{ ATTENDANCE_SOURCE_TEXT[attendance.source] }} ·
                  {{ formatDateTime(attendance.recordedAt) }}
                </p>
              </div>

              <template v-else-if="!checkInClosed">
                <Button
                  v-if="confirmed"
                  type="primary"
                  block
                  data-testid="check-in-button"
                  :disabled="!canCheckIn"
                  :loading="checkInLoading"
                  @click="checkIn"
                >
                  签到
                </Button>
                <p v-if="checkInHint" class="hint">{{ checkInHint }}</p>
              </template>

              <Button
                type="primary"
                plain
                block
                data-testid="cancel-registration"
                :disabled="cancel.disabled"
                :loading="cancelling"
                @click="cancelNow"
              >
                {{ cancel.label }}
              </Button>
              <p v-if="cancel.reason" class="hint">{{ cancel.reason }}</p>
            </template>
          </template>

          <p v-if="actionMessage" class="hint" data-testid="action-message">
            {{ actionMessage }}
          </p>
          <p v-if="actionError" class="action-error" data-testid="action-error">
            {{ actionError }}
          </p>
          <p v-if="refreshError" class="action-error">
            {{ refreshError }}
            <Button
              size="mini"
              type="primary"
              plain
              data-testid="refresh-retry"
              @click="load(true)"
            >
              重试刷新
            </Button>
          </p>
        </section>

        <p v-if="rosterDiffText" class="notice roster-diff" data-testid="roster-diff">
          {{ rosterDiffText }}
        </p>

        <template v-if="!isCancelled">
          <RosterList
            v-if="canViewFullRoster"
            :sections="overviewRoster"
            :loading="overviewRosterLoading"
            :error="overviewRosterError"
            :attendance-error="overviewAttendanceError"
            :admin-url="rosterAdminUrl"
            @retry="loadOverviewRoster(requestSeq)"
            @admin-click="saveRosterSnapshot"
          />
          <section v-else-if="!session.isAuthenticated" class="card roster-hint">
            <p class="muted">登录后查看名单与考勤</p>
          </section>
          <section v-else-if="pending" class="card roster-hint">
            <p class="muted">账号待审核，暂不可查看全队名单。</p>
          </section>
          <template v-else>
            <p class="hint roster-hint">外挂仅显示本人报名。</p>
            <RosterList
              :sections="overviewRoster"
              :loading="overviewRosterLoading"
              :error="overviewRosterError"
              :attendance-error="overviewAttendanceError"
              :admin-url="null"
              @retry="loadOverviewRoster(requestSeq)"
            />
          </template>
        </template>
      </template>

      <template v-else-if="activeTab === 'events'">
        <section class="card events-summary">
          <div class="card-head">
            <h2>比赛事件</h2>
            <span class="muted small">{{ events.length }} 条</span>
          </div>
          <div class="stat-grid">
            <div>
              <b>{{ goalCount }}</b
              ><span>进球</span>
            </div>
            <div>
              <b>{{ cardCount }}</b
              ><span>牌</span>
            </div>
            <div>
              <b>{{ substitutionCount }}</b
              ><span>换人</span>
            </div>
          </div>
          <p v-if="match.status === 4" class="muted small">
            存储比分 {{ match.scoreHome ?? 0 }} : {{ match.scoreAway ?? 0 }}
          </p>
        </section>

        <section class="card events-panel">
          <div v-if="!checkInClosed && hasEventCreate" class="event-actions">
            <Button
              size="small"
              type="primary"
              :disabled="!canCreateEvent"
              :title="eventDisabledReason"
              @click="openCreate"
            >
              录入事件
            </Button>
            <a v-if="showAdminLink" class="ghost-link" :href="adminEventsUrl">去管理后台</a>
          </div>

          <div v-if="eventsLoading" class="state state--inline">
            <Loading size="20">事件加载中…</Loading>
          </div>

          <div v-else-if="eventsError" class="state state--inline">
            <p class="muted">{{ eventsError }}</p>
            <Button size="small" @click="loadEvents">重试</Button>
          </div>

          <Empty v-else-if="!events.length" description="还没有事件记录" />

          <div v-else class="team-timeline">
            <div class="timeline-head">
              <span>{{ match.homeTeamName }}</span>
              <span class="muted small">时间</span>
              <span>{{ match.awayTeamName }}</span>
            </div>
            <div
              v-for="event in events"
              :key="event.id"
              class="timeline-row"
              :class="`timeline-row--${event.team === 0 ? 'home' : 'away'}`"
            >
              <div class="timeline-side timeline-side--home">
                <div v-if="event.team === 0" class="event-card">
                  <div class="event-card__head">
                    <strong>{{ eventTypeText(event.type) }}</strong>
                    <button
                      v-if="canEditEvent(event)"
                      type="button"
                      class="event-edit"
                      @click="openEdit(event)"
                    >
                      编辑
                    </button>
                  </div>
                  <p>{{ eventDetail(event) }}</p>
                  <small class="muted">{{ teamName(event.team) }}</small>
                </div>
              </div>
              <div class="timeline-axis">
                <span class="timeline-time">{{ eventTime(event) }}</span>
                <span class="timeline-dot" />
              </div>
              <div class="timeline-side timeline-side--away">
                <div v-if="event.team === 1" class="event-card">
                  <div class="event-card__head">
                    <strong>{{ eventTypeText(event.type) }}</strong>
                    <button
                      v-if="canEditEvent(event)"
                      type="button"
                      class="event-edit"
                      @click="openEdit(event)"
                    >
                      编辑
                    </button>
                  </div>
                  <p>{{ eventDetail(event) }}</p>
                  <small class="muted">{{ teamName(event.team) }}</small>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <template v-else-if="activeTab === 'lineup'">
        <section class="card lineup-summary">
          <div class="card-head">
            <h2>阵容</h2>
            <span class="muted small">{{ lineup?.formation.name ?? '尚未安排' }}</span>
          </div>
          <div class="lineup-side-switch">
            <button
              type="button"
              class="lineup-side"
              :class="{ 'lineup-side--active': lineupSide === 0 }"
              @click="switchLineupSide(0)"
            >
              {{ match.homeTeamName }}
            </button>
            <button
              type="button"
              class="lineup-side"
              :class="{ 'lineup-side--active': lineupSide === 1 }"
              @click="switchLineupSide(1)"
            >
              {{ match.awayTeamName }}
            </button>
          </div>
          <p v-if="lineupOpponent" class="muted small">对手方仅展示阵型与占位，不展示球员。</p>
        </section>

        <section class="card lineup-panel">
          <div v-if="lineupLoading" class="state state--inline">
            <Loading size="20">阵容加载中…</Loading>
          </div>

          <div v-else-if="lineupError" class="state state--inline">
            <p class="muted">{{ lineupError }}</p>
            <Button size="small" @click="loadLineup(lineupSide)">重试</Button>
          </div>

          <template v-else-if="lineup">
            <div class="lineup-pitch">
              <span class="lineup-pitch-line" />
              <span class="lineup-pitch-circle" />
              <span
                v-for="slot in lineup.formation.slots"
                :key="slot.slotKey"
                class="lineup-slot"
                :class="{ 'lineup-slot--filled': lineupStarter(slot.slotKey) }"
                :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
              >
                <template v-if="lineupStarter(slot.slotKey)">
                  <b>{{ lineupStarter(slot.slotKey)?.jersey ?? '—' }}</b>
                  <small>{{ lineupStarter(slot.slotKey)?.name }}</small>
                </template>
                <span v-else class="lineup-slot__empty">{{ positionText(slot.role) }}</span>
              </span>
            </div>

            <div class="lineup-bench">
              <div class="card-head">
                <h3>替补</h3>
                <span class="muted small">{{ lineup.bench.length }} 人</span>
              </div>
              <p v-if="lineupOpponent" class="muted small">对手方不录入球员。</p>
              <div v-else-if="lineup.bench.length" class="lineup-bench-list">
                <span v-for="player in lineup.bench" :key="player.id" class="lineup-bench-item">
                  {{ lineupPlayerLabel(player) }}
                </span>
              </div>
              <p v-else class="muted small">暂无替补</p>
            </div>
          </template>
        </section>
      </template>

      <template v-else-if="activeTab === 'grouping'">
        <section class="card grouping-summary">
          <div class="card-head">
            <h2>队内分组</h2>
            <span class="muted small">
              {{ grouping?.savedAt ? '已保存' : hasGrouping ? '草稿' : '尚未生成' }}
            </span>
          </div>
          <div class="stat-grid">
            <div>
              <b>{{ groupingTotal('A') }}</b
              ><span>A 队总分</span>
            </div>
            <div>
              <b>{{ groupingTotal('B') }}</b
              ><span>B 队总分</span>
            </div>
            <div>
              <b>{{ (grouping?.diff ?? 0).toFixed(2) }}</b
              ><span>总分差</span>
            </div>
          </div>
          <a v-if="canManageGrouping" class="ghost-link" :href="adminGroupingUrl">去管理后台</a>
        </section>

        <section class="card grouping-panel">
          <div v-if="groupingLoading" class="state state--inline">
            <Loading size="20">分组加载中…</Loading>
          </div>

          <div v-else-if="groupingError" class="state state--inline">
            <p class="muted">{{ groupingError }}</p>
            <Button size="small" @click="loadGrouping">重试</Button>
          </div>

          <Empty v-else-if="!hasGrouping" description="尚未生成分组" />

          <template v-else>
            <div class="grouping-warnings">
              <p
                v-for="warning in grouping?.warnings ?? []"
                :key="warning"
                class="grouping-warning"
              >
                {{ warning }}
              </p>
            </div>

            <div class="grouping-board">
              <section v-for="team in groupingTeams" :key="team" class="grouping-column">
                <header class="grouping-head">
                  <h3>{{ team }} 队 · {{ groupingTeamLabel(team) }}</h3>
                  <span class="muted small">{{ groupingTotal(team) }} 分</span>
                </header>
                <div
                  v-for="bucket in groupingPositionBuckets"
                  :key="bucket.key"
                  class="grouping-position"
                >
                  <p class="grouping-position__title">{{ bucket.label }}</p>
                  <div
                    v-for="player in groupingPlayers(team, bucket.key)"
                    :key="player.playerId"
                    class="grouping-row"
                  >
                    <span>{{ groupingPlayerLabel(player) }}</span>
                    <small>{{ (player.finalScore ?? 0).toFixed(2) }}</small>
                  </div>
                  <p v-if="!groupingPlayers(team, bucket.key).length" class="muted small">—</p>
                </div>
              </section>
            </div>

            <div class="grouping-distribution">
              <div v-for="row in groupingPositionRows" :key="row.position">
                <span>{{ row.label }}</span>
                <b>{{ row.a }} : {{ row.b }}</b>
              </div>
            </div>
          </template>
        </section>
      </template>
    </template>

    <Popup v-model:show="eventModalOpen" position="bottom" round :style="{ maxHeight: '90%' }">
      <div class="event-modal">
        <div class="card-head">
          <h2>{{ editingEvent ? '编辑事件' : '录入事件' }}</h2>
        </div>

        <label class="event-field">
          <span>类型</span>
          <select v-model.number="form.type">
            <option v-for="type in allowedTypes" :key="type" :value="type">
              {{ EVENT_TYPE_TEXT[type] }}
            </option>
          </select>
        </label>

        <label class="event-field">
          <span>球队</span>
          <select v-model.number="form.team" :disabled="teamLocked">
            <option :value="0">{{ teamName(0) }}</option>
            <option :value="1">{{ teamName(1) }}</option>
          </select>
        </label>

        <template v-if="form.type === 2">
          <label class="event-field">
            <span>换上</span>
            <select v-model.number="form.inPlayerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label class="event-field">
            <span>换下</span>
            <select v-model.number="form.outPlayerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
        </template>

        <template v-else>
          <label class="event-field">
            <span>球员</span>
            <select v-model.number="form.playerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label v-if="form.type === 0" class="event-field">
            <span>助攻（可选）</span>
            <select v-model.number="form.assistPlayerId">
              <option :value="null">无</option>
              <option v-for="player in rosterPlayers" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label v-if="form.type === 0" class="event-field event-field--inline">
            <span>点球</span>
            <input v-model="form.penalty" type="checkbox" />
          </label>
        </template>

        <label class="event-field">
          <span>半场</span>
          <select v-model.number="form.half">
            <option :value="0">上半场</option>
            <option :value="1">下半场</option>
          </select>
        </label>

        <label class="event-field">
          <span>时间（分钟）</span>
          <input v-model.number="form.minute" type="number" min="1" max="120" placeholder="1–120" />
        </label>

        <p v-if="selectorError" class="action-error">
          {{ selectorError }}
          <a v-if="showAdminLink" :href="adminGroupingUrl">去 B06 重新生成分组</a>
          <span v-else>请联系管理员到 B06 重新生成分组</span>
        </p>
        <p v-if="eventFormError" class="action-error">{{ eventFormError }}</p>

        <Button
          type="primary"
          block
          :loading="eventSubmitting"
          :disabled="eventSubmitting"
          @click="submitEvent"
        >
          {{ editingEvent ? '保存修改' : '保存事件' }}
        </Button>
      </div>
    </Popup>

    <InviteDialog
      :show="inviteOpen"
      :submitting="inviteSubmitting"
      :result="inviteResult"
      :error="inviteError"
      @update:show="closeInvite"
      @submit="submitInvite"
      @copy="copyInvite"
    />

    <StatusChangeDialog
      :show="statusDialogOpen"
      :mode="statusDialogMode"
      :initial-start-at="statusInitialStart"
      :initial-end-at="statusInitialEnd"
      :submitting="statusSubmitting"
      :error="statusError"
      @update:show="statusDialogOpen = $event"
      @submit="submitStatusDialog"
    />
  </section>
</template>

<style scoped>
.match-hero__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.status-select {
  flex: 1;
  min-width: 130px;
  padding: 7px 10px;
  color: var(--yh-ink);
  font: inherit;
  font-size: 13px;
  background: #ffffff;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.share-manual {
  width: 100%;
  padding: 8px 10px;
  color: var(--yh-ink);
  font: inherit;
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
  word-break: break-all;
}

.voided-banner {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.roster-diff {
  margin: 12px 0 0;
}

.roster-hint {
  margin-top: 12px;
}
</style>
