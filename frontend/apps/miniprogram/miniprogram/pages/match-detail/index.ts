import type {
  Attendance,
  AttendanceRow,
  EventCreate,
  EventFormPlayer,
  GroupingView,
  MatchDetail,
  MatchEvent,
  Registration,
  RegistrationRow,
} from '../../types/api';
import { errorMessage } from '../../utils/api-errors';
import { eventCreateAllowed, eventEditAllowed } from '../../utils/event-form';
import { buildGroupingBoard, type GroupingBoardView } from '../../utils/grouping-view';
import { createGuardedLoads, type GuardedLoads } from '../../utils/guarded-load';
import { buildLineupBoard, type LineupBoardView } from '../../utils/lineup-view';
import {
  buildEventRows,
  buildMatchDetailHeader,
  buildRosterView,
  type EventRowView,
  type MatchDetailHeaderView,
  type RosterView,
} from '../../utils/match-detail-view';
import {
  fetchEvents,
  fetchFullRoster,
  fetchGrouping,
  fetchLineup,
  fetchMatchDetail,
  fetchMyMatchRegistration,
} from '../../utils/api-client';
import { isInternalMatchType } from '../../utils/match-type';
import { checkInWindowState, eventEditWindowOpen, eventEntryOpen } from '../../utils/match-window';
import { statusOptions, type StatusOption } from '../../utils/match-status';
import { attendanceText, registrationStatusText } from '../../utils/presentation';
import {
  ATTENDANCE_CHECKIN_OWN,
  ATTENDANCE_VIEW_ANY,
  EVENT_CREATE_OWN,
  EVENT_EDIT_ANY,
  EVENT_EDIT_OWN,
  GROUPING_VIEW_ANY,
  LINEUP_VIEW_ANY,
  MATCH_EDIT_ANY,
  MATCH_STATUS_ANY,
  hasPermission,
  isPending,
} from '../../utils/permissions';
import {
  REGISTRATION_STATUS,
  registrationDecision,
  type CancelDecision,
  type SignupDecision,
} from '../../utils/registration';
import { buildRoster, type RosterSections } from '../../utils/roster';
import { getUser, getPlayer, getPermissions, sessionReady } from '../../utils/session';
import * as actions from './actions';
import type { QueueView } from '../../utils/match-view';
import { queuesOf } from '../../utils/match-view';

type TabKey = 'overview' | 'events' | 'lineup' | 'grouping';

/** 名单 loader 的返回值：完整名单或本人视角，都是空态之外的成功结果。 */
interface RosterLoad {
  sections: RosterSections;
  confirmed: RegistrationRow[];
  attendanceError?: string;
}

const EMPTY_ROSTER_VIEW = buildRosterView(buildRoster([], [], []));

let nowTimer: ReturnType<typeof setInterval> | null = null;

function stopTimer(): void {
  if (nowTimer !== null) {
    clearInterval(nowTimer);
    nowTimer = null;
  }
}

function toast(title: string): void {
  wx.showToast({ title, icon: 'none' });
}

function confirm(title: string, content: string): Promise<boolean> {
  return new Promise((resolve) => {
    wx.showModal({
      title,
      content,
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    });
  });
}

function checkInHintOf(
  registration: Registration | null,
  windowState: 'open' | 'before' | 'after',
  closed: boolean,
): string {
  if (closed) {
    return '比赛已取消或延期，无法签到';
  }
  if (registration?.status !== REGISTRATION_STATUS.CONFIRMED) {
    return '仅确认报名的球员可以签到';
  }
  if (windowState === 'before') {
    return '签到未开始：开赛前 4 小时开放';
  }
  if (windowState === 'after') {
    return '签到已结束：比赛当天 23:59 截止';
  }
  return '签到窗口开放中：开赛前 4 小时至比赛当天 23:59';
}

function ownRosterRows(
  registration: Registration | null,
  attendance: Attendance | null,
): { confirmed: RegistrationRow[]; waitlist: RegistrationRow[]; attendance: AttendanceRow[] } {
  const player = getPlayer();
  if (!registration || !player) {
    return { confirmed: [], waitlist: [], attendance: [] };
  }
  const row: RegistrationRow = {
    id: registration.id,
    player: {
      id: player.id,
      name: player.name,
      jersey: player.jersey,
      position: player.position,
      positionLabel: player.positionLabel,
      skill: player.skill,
    },
    type: registration.type,
    typeLabel: registration.typeLabel,
    status: registration.status,
    statusLabel: registration.statusLabel,
    registeredAt: registration.registeredAt,
    cancelledAt: registration.cancelledAt,
  };
  const attendanceRows: AttendanceRow[] = attendance
    ? [
        {
          id: attendance.id,
          player: {
            id: player.id,
            name: player.name,
            jersey: player.jersey,
            position: player.position,
            positionLabel: player.positionLabel,
          },
          status: attendance.status,
          statusLabel: attendance.statusLabel,
          source: attendance.source,
          sourceLabel: attendance.sourceLabel,
          recordedAt: attendance.recordedAt,
        },
      ]
    : [];
  return {
    confirmed: registration.status === REGISTRATION_STATUS.CONFIRMED ? [row] : [],
    waitlist: registration.status === REGISTRATION_STATUS.WAITLISTED ? [row] : [],
    attendance: attendanceRows,
  };
}

/** 事件表单球员候选：确认名单去重后转成精简球员。 */
function eventPlayersOf(confirmed: RegistrationRow[]): EventFormPlayer[] {
  const seen = new Set<number>();
  const players: EventFormPlayer[] = [];
  for (const row of confirmed) {
    if (seen.has(row.player.id)) {
      continue;
    }
    seen.add(row.player.id);
    players.push({
      id: row.player.id,
      name: row.player.name,
      jersey: row.player.jersey,
      position: row.player.position,
      positionLabel: row.player.positionLabel,
    });
  }
  return players;
}

Page({
  data: {
    id: 0,
    seq: 0,
    loading: true,
    error: '',
    notFound: false,
    detail: null as MatchDetail | null,
    header: null as MatchDetailHeaderView | null,
    activeTab: 'overview' as TabKey,
    tabs: [] as { key: TabKey; label: string }[],
    derivedScore: null as { scoreHome: number | null; scoreAway: number | null } | null,
    authenticated: false,
    pending: false,
    canCheckInPerm: false,
    canViewRoster: false,
    canCreatePerm: false,
    canEditOwnPerm: false,
    isEventAdmin: false,
    canCreateEvent: false,
    showEventCreate: false,
    eventDisabledReason: '',
    canChangeStatus: false,
    canEditAny: false,
    canViewLineup: false,
    canViewGrouping: false,
    ownPlayerId: 0,
    registration: null as Registration | null,
    attendance: null as Attendance | null,
    hasActiveRegistration: false,
    signup: null as SignupDecision | null,
    cancel: null as CancelDecision | null,
    queues: [] as QueueView[],
    checkInOpen: false,
    checkInClosed: false,
    checkInHint: '',
    attendanceText: '',
    registrationStatusText: '',
    registrationTone: 'muted',
    actionMessage: '',
    actionError: '',
    signupBusy: false,
    cancelBusy: false,
    checkInBusy: false,
    roster: EMPTY_ROSTER_VIEW as RosterView,
    rosterLoading: false,
    rosterError: '',
    attendanceError: '',
    rawEvents: [] as MatchEvent[],
    events: [] as EventRowView[],
    eventsLoading: false,
    eventsError: '',
    eventStats: { goals: 0, cards: 0, subs: 0 },
    eventFormOpen: false,
    editingEvent: null as MatchEvent | null,
    editingVersion: 0,
    eventPlayers: [] as EventFormPlayer[],
    eventGrouping: null as GroupingView | null,
    eventBusy: false,
    eventError: '',
    lineupSide: 0,
    lineup: null as LineupBoardView | null,
    lineupLoading: false,
    lineupError: '',
    grouping: null as GroupingView | null,
    groupingBoard: null as GroupingBoardView | null,
    groupingLoading: false,
    groupingError: '',
    statusOpen: false,
    statusOptions: [] as StatusOption[],
    statusBusy: false,
    statusError: '',
  },

  /** 四个 loader 共用的代次登记表；懒创建，卸载后连同页面实例一起回收。 */
  loadRegistry: null as GuardedLoads | null,

  guardedLoads(): GuardedLoads {
    if (!this.loadRegistry) {
      this.loadRegistry = createGuardedLoads((patch) => this.setData(patch));
    }
    return this.loadRegistry;
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0);
    this.setData({ id });
    if (!id) {
      this.setData({ loading: false, notFound: true });
      return;
    }
    void this.load(false);
  },

  onShow() {
    stopTimer();
    nowTimer = setInterval(() => this.syncWindowState(), 30_000);
    if (this.data.detail) {
      this.syncWindowState();
    }
  },

  onHide() {
    stopTimer();
  },

  onUnload() {
    stopTimer();
    // 递增后迟到的详情响应会被丢弃；loader 的在途请求同时过期，页面已卸载，无需 setData。
    this.data.seq += 1;
    this.guardedLoads().invalidateAll();
  },

  onPullDownRefresh() {
    void this.load(true).then(() => wx.stopPullDownRefresh());
  },

  async load(silent: boolean): Promise<void> {
    const seq = this.data.seq + 1;
    this.guardedLoads().invalidateAll();
    this.setData({
      seq,
      rosterLoading: false,
      eventsLoading: false,
      lineupLoading: false,
      groupingLoading: false,
      ...(silent && this.data.detail
        ? {}
        : {
            loading: true,
            error: '',
            notFound: false,
            detail: null,
            header: null,
            roster: EMPTY_ROSTER_VIEW,
            events: [],
            rawEvents: [],
          }),
    });
    try {
      await sessionReady();
      const detail = await fetchMatchDetail(this.data.id);
      if (seq !== this.data.seq) {
        return;
      }
      const user = getUser();
      const authenticated = user !== null;
      const pending = isPending(user?.status);
      const permissions = getPermissions();
      const canViewRoster =
        authenticated && !pending && hasPermission(permissions, ATTENDANCE_VIEW_ANY);
      const canViewGrouping =
        authenticated &&
        hasPermission(permissions, GROUPING_VIEW_ANY) &&
        isInternalMatchType(detail.type);

      let registration = detail.myRegistration ?? null;
      let attendance: Attendance | null = null;
      if (authenticated && !pending) {
        const mine = await fetchMyMatchRegistration(this.data.id);
        if (seq !== this.data.seq) {
          return;
        }
        registration = mine.registration ?? null;
        attendance = mine.attendance ?? null;
      }

      const tabs: { key: TabKey; label: string }[] = [
        { key: 'overview', label: '概览' },
        { key: 'events', label: '事件' },
      ];
      if (authenticated && hasPermission(permissions, LINEUP_VIEW_ANY)) {
        tabs.push({ key: 'lineup', label: '阵容' });
      }
      if (canViewGrouping) {
        tabs.push({ key: 'grouping', label: '分组' });
      }
      const activeTab = tabs.some((tab) => tab.key === this.data.activeTab)
        ? this.data.activeTab
        : 'overview';

      this.setData({
        loading: false,
        notFound: false,
        detail,
        header: buildMatchDetailHeader(detail, detail.derivedScore ?? null),
        queues: queuesOf(detail),
        authenticated,
        pending,
        registration,
        attendance,
        attendanceText: attendanceText(attendance),
        canCheckInPerm: hasPermission(permissions, ATTENDANCE_CHECKIN_OWN),
        canViewRoster,
        canCreatePerm:
          hasPermission(permissions, EVENT_CREATE_OWN) ||
          hasPermission(permissions, EVENT_EDIT_ANY),
        canEditOwnPerm: hasPermission(permissions, EVENT_EDIT_OWN),
        isEventAdmin: hasPermission(permissions, EVENT_EDIT_ANY),
        canCreateEvent: eventCreateAllowed({
          matchStatus: detail.status,
          hasCreate:
            !pending &&
            (hasPermission(permissions, EVENT_CREATE_OWN) ||
              hasPermission(permissions, EVENT_EDIT_ANY)),
          isAdmin: !pending && hasPermission(permissions, EVENT_EDIT_ANY),
          endAt: detail.endAt,
          now: Date.now(),
        }),
        canChangeStatus: authenticated && hasPermission(permissions, MATCH_STATUS_ANY),
        canEditAny: hasPermission(permissions, MATCH_EDIT_ANY),
        canViewLineup: authenticated && hasPermission(permissions, LINEUP_VIEW_ANY),
        canViewGrouping,
        ownPlayerId: getPlayer()?.id ?? 0,
        tabs,
        activeTab,
        statusOptions: statusOptions({
          currentStatus: detail.status,
          canEditAny: hasPermission(permissions, MATCH_EDIT_ANY),
        }),
        ...(silent ? {} : { actionError: '', actionMessage: '' }),
      });
      this.syncWindowState();

      void this.loadRoster();
      // 等待事件刷新完成，冲突重试时 rawEvents 已是最新，editingVersion 不会拿到旧值。
      await this.loadEvents();
      if (this.data.lineup) {
        void this.loadLineup(this.data.lineupSide);
      }
      if (this.data.grouping) {
        void this.loadGrouping();
      }
    } catch (error) {
      if (seq !== this.data.seq) {
        return;
      }
      if (
        error instanceof Error &&
        'statusCode' in error &&
        (error as { statusCode: number }).statusCode === 404
      ) {
        this.setData({
          loading: false,
          notFound: true,
          detail: null,
          header: null,
          roster: EMPTY_ROSTER_VIEW,
          events: [],
          rawEvents: [],
          eventPlayers: [],
        });
        return;
      }
      if (silent && this.data.detail) {
        this.setData({ loading: false });
        toast(errorMessage(error, '刷新失败，请稍后重试'));
        return;
      }
      this.setData({
        loading: false,
        error: errorMessage(error, '加载失败，请稍后重试'),
        detail: null,
        header: null,
      });
    }
  },

  async loadRoster(): Promise<void> {
    const detail = this.data.detail;
    if (!detail) {
      return;
    }
    await this.guardedLoads().run<RosterLoad>({
      key: 'roster',
      errorText: '名单加载失败，请稍后重试',
      begin: { attendanceError: '' },
      fallback: { roster: EMPTY_ROSTER_VIEW },
      fetch: async () => {
        if (!this.data.authenticated || this.data.pending || detail.status === 6) {
          return { sections: buildRoster([], [], []), confirmed: [] };
        }
        if (this.data.canViewRoster) {
          const full = await fetchFullRoster(this.data.id);
          return {
            sections: buildRoster(full.confirmed, full.waitlist, full.attendance),
            confirmed: full.confirmed,
            attendanceError: full.attendanceError,
          };
        }
        const own = ownRosterRows(this.data.registration, this.data.attendance);
        return {
          sections: buildRoster(own.confirmed, own.waitlist, own.attendance),
          confirmed: own.confirmed,
        };
      },
      map: (loaded) => ({
        roster: buildRosterView(loaded.sections),
        eventPlayers: eventPlayersOf(loaded.confirmed),
        ...(loaded.attendanceError === undefined
          ? {}
          : { attendanceError: loaded.attendanceError }),
      }),
      after: () => this.syncWindowState(),
    });
  },

  async loadEvents(): Promise<void> {
    await this.guardedLoads().run({
      key: 'events',
      errorText: '事件加载失败，请稍后重试',
      fetch: () => fetchEvents(this.data.id),
      map: (timeline) => {
        const derived = timeline.derivedScore ?? null;
        const detail = this.data.detail;
        return {
          rawEvents: timeline.items,
          derivedScore: derived,
          eventStats: {
            goals: timeline.items.filter((event) => event.type === 0).length,
            cards: timeline.items.filter((event) => event.type === 3 || event.type === 4).length,
            subs: timeline.items.filter((event) => event.type === 2).length,
          },
          ...(detail ? { header: buildMatchDetailHeader(detail, derived) } : {}),
        };
      },
      after: () => this.refreshEventRows(),
    });
  },

  refreshEventRows(): void {
    const detail = this.data.detail;
    if (!detail) {
      return;
    }
    const now = Date.now();
    this.setData({
      events: buildEventRows(this.data.rawEvents, detail, (event) =>
        eventEditAllowed(event, {
          ownPlayerId: this.data.ownPlayerId || null,
          hasEditOwn: this.data.canEditOwnPerm,
          matchStatus: detail.status,
          endAt: detail.endAt,
          now,
        }),
      ),
    });
  },

  syncWindowState(): void {
    const detail = this.data.detail;
    if (!detail) {
      return;
    }
    const now = Date.now();
    const registration = this.data.registration;
    const closed = detail.status === 5 || detail.status === 6;
    const windowState = checkInWindowState(detail.startAt, now);
    const decision = registrationDecision({
      authenticated: this.data.authenticated,
      pending: this.data.pending,
      playerType: getPlayer()?.type ?? null,
      permissions: getPermissions(),
      matchStatus: detail.status,
      now,
      signupDeadline: detail.signupDeadline,
      cancelLock: detail.cancelLock,
      queue: detail,
      myRegistration: registration,
    });
    const signup = decision.signup;
    const cancel = decision.cancel;
    const isAdmin = !this.data.pending && hasPermission(getPermissions(), EVENT_EDIT_ANY);
    const canCreateEvent = eventCreateAllowed({
      matchStatus: detail.status,
      hasCreate: !this.data.pending && this.data.canCreatePerm,
      isAdmin,
      endAt: detail.endAt,
      now,
    });
    const status = registration?.status;
    const entryOpen = eventEntryOpen(detail.status);
    const playerWindowOpen = eventEditWindowOpen(detail.endAt, now);
    const showEventCreate = !this.data.pending && this.data.canCreatePerm && !closed;
    const eventDisabledReason =
      !showEventCreate || isAdmin
        ? ''
        : !entryOpen
          ? closed
            ? '已取消/已延期的比赛不可录入事件'
            : '仅进行中或已结束的比赛可录入事件'
          : !playerWindowOpen
            ? '已超过赛后 24 小时可编辑窗口'
            : '';
    this.setData({
      now,
      signup,
      cancel,
      hasActiveRegistration: signup.kind === 'registered',
      checkInOpen:
        registration?.status === REGISTRATION_STATUS.CONFIRMED &&
        !closed &&
        windowState === 'open' &&
        this.data.canCheckInPerm,
      checkInClosed: closed,
      checkInHint: checkInHintOf(registration, windowState, closed),
      canCreateEvent,
      showEventCreate,
      eventDisabledReason,
      attendanceText: attendanceText(this.data.attendance),
      registrationStatusText:
        registration?.status === undefined
          ? ''
          : detail.status === 6
            ? '已作废'
            : registrationStatusText(status),
      registrationTone:
        detail.status === 6
          ? 'muted'
          : status === REGISTRATION_STATUS.CONFIRMED
            ? 'open'
            : status === REGISTRATION_STATUS.WAITLISTED
              ? 'postponed'
              : 'muted',
    });
    this.refreshEventRows();
  },

  retry() {
    void this.load(false);
  },

  switchTab(event: WechatMiniprogram.BaseEvent) {
    const key = String(event.currentTarget.dataset.key) as TabKey;
    this.setData({ activeTab: key });
    if (key === 'lineup' && !this.data.lineup && !this.data.lineupLoading) {
      const detail = this.data.detail;
      const side = detail && isInternalMatchType(detail.type) ? 0 : (detail?.ourSide ?? 0);
      void this.loadLineup(side);
    }
    if (key === 'grouping' && !this.data.grouping && !this.data.groupingLoading) {
      void this.loadGrouping();
    }
  },

  /** 写操作结果落地：成功写 patch、提示并重载；冲突失败重载后按需重取事件版本。 */
  async applyWriteOutcome(outcome: actions.WriteOutcome): Promise<void> {
    if (outcome.ok) {
      if (Object.keys(outcome.patch).length > 0) {
        this.setData(outcome.patch);
      }
      if (outcome.toast) {
        toast(outcome.toast);
      }
      await this.load(true);
      return;
    }
    if (!outcome.reload) {
      return;
    }
    await this.load(true);
    if (outcome.refreshEventId !== null) {
      const version = actions.refreshedEventVersion(this.data.rawEvents, outcome.refreshEventId);
      if (version !== null) {
        this.setData({ editingVersion: version });
      }
    }
  },

  async signupNow(): Promise<void> {
    const state = this.data.signup;
    if (
      this.data.signupBusy ||
      !state ||
      !state.visible ||
      state.action !== 'signup' ||
      state.disabled
    ) {
      return;
    }
    const detail = this.data.detail;
    if (!detail) {
      return;
    }
    this.setData({ signupBusy: true, actionError: '', actionMessage: '' });
    try {
      const outcome = await actions.signup(detail.id);
      if (!outcome.ok) {
        this.setData({ actionError: outcome.error });
      }
      await this.applyWriteOutcome(outcome);
    } finally {
      this.setData({ signupBusy: false });
    }
  },

  async cancelNow(): Promise<void> {
    if (this.data.cancelBusy) {
      return;
    }
    const detail = this.data.detail;
    const cancel = this.data.cancel;
    if (!detail || !cancel?.visible || cancel.disabled) {
      return;
    }
    if (!(await confirm(cancel.label, '确认取消本次报名？'))) {
      return;
    }
    const cancelledStatus = this.data.registration?.status ?? null;
    this.setData({ cancelBusy: true, actionError: '', actionMessage: '' });
    try {
      const outcome = await actions.cancel({ matchId: detail.id, cancelledStatus });
      if (!outcome.ok) {
        this.setData({ actionError: outcome.error });
      }
      await this.applyWriteOutcome(outcome);
    } finally {
      this.setData({ cancelBusy: false });
    }
  },

  async checkInNow(): Promise<void> {
    if (this.data.checkInBusy || !this.data.checkInOpen) {
      return;
    }
    this.setData({ checkInBusy: true, actionError: '', actionMessage: '' });
    try {
      const outcome = await actions.checkIn(this.data.id);
      if (!outcome.ok) {
        this.setData({ actionError: outcome.error });
      }
      await this.applyWriteOutcome(outcome);
    } finally {
      this.setData({ checkInBusy: false });
    }
  },

  openStatus() {
    if (!this.data.canChangeStatus) {
      return;
    }
    this.setData({ statusOpen: true, statusError: '' });
  },

  closeStatus() {
    this.setData({ statusOpen: false });
  },

  async submitStatus(body: {
    status: number;
    reason?: string;
    startAt?: string;
    endAt?: string;
  }): Promise<void> {
    const detail = this.data.detail;
    if (!detail || this.data.statusBusy) {
      return;
    }
    this.setData({ statusBusy: true, statusError: '' });
    try {
      const outcome = await actions.changeStatus({
        matchId: detail.id,
        version: detail.version,
        ...body,
      });
      if (!outcome.ok) {
        this.setData({ statusError: outcome.error });
      }
      await this.applyWriteOutcome(outcome);
    } finally {
      this.setData({ statusBusy: false });
    }
  },

  onStatusChange(event: WechatMiniprogram.CustomEvent) {
    void this.submitStatus({ status: Number(event.detail.status) });
  },

  onStatusPostpone(event: WechatMiniprogram.CustomEvent) {
    void this.submitStatus({
      status: 5,
      startAt: String(event.detail.startAt),
      endAt: String(event.detail.endAt),
    });
  },

  onStatusCancel(event: WechatMiniprogram.CustomEvent) {
    void this.submitStatus({ status: 6, reason: String(event.detail.reason) });
  },

  async openCreateEvent(): Promise<void> {
    if (!this.data.canCreateEvent) {
      return;
    }
    if (this.data.eventPlayers.length === 0) {
      await this.loadRoster();
    }
    await this.ensureEventGrouping();
    this.setData({ editingEvent: null, editingVersion: 0, eventError: '', eventFormOpen: true });
  },

  async openEditEvent(event: WechatMiniprogram.BaseEvent): Promise<void> {
    const id = Number(event.currentTarget.dataset.id);
    const target = this.data.rawEvents.find((item) => item.id === id);
    if (!target) {
      return;
    }
    const detail = this.data.detail;
    if (
      !detail ||
      !eventEditAllowed(target, {
        ownPlayerId: this.data.ownPlayerId || null,
        hasEditOwn: this.data.canEditOwnPerm,
        matchStatus: detail.status,
        endAt: detail.endAt,
        now: Date.now(),
      })
    ) {
      return;
    }
    await this.ensureEventGrouping();
    this.setData({
      editingEvent: target,
      editingVersion: target.version,
      eventError: '',
      eventFormOpen: true,
    });
  },

  async ensureEventGrouping(): Promise<void> {
    if (!this.data.canViewGrouping) {
      return;
    }
    if (!this.data.grouping) {
      await this.loadGrouping();
    }
    this.setData({ eventGrouping: this.data.grouping });
  },

  closeEventForm() {
    this.setData({ eventFormOpen: false, eventError: '' });
  },

  async onEventSave(event: WechatMiniprogram.CustomEvent): Promise<void> {
    const { payload, eventId, version } = event.detail as {
      payload: EventCreate;
      eventId: number | null;
      version: number | null;
    };
    if (this.data.eventBusy) {
      return;
    }
    this.setData({ eventBusy: true, eventError: '' });
    try {
      const outcome = await actions.saveEvent({
        matchId: this.data.id,
        payload,
        eventId,
        version,
      });
      if (!outcome.ok) {
        this.setData({ eventError: outcome.error });
      }
      await this.applyWriteOutcome(outcome);
    } finally {
      this.setData({ eventBusy: false });
    }
  },

  retryEvents() {
    void this.loadEvents();
  },

  async loadLineup(side: number): Promise<void> {
    const detail = this.data.detail;
    if (!detail || !this.data.canViewLineup) {
      return;
    }
    await this.guardedLoads().run({
      key: 'lineup',
      errorText: '阵容加载失败，请稍后重试',
      begin: { lineupSide: side },
      fallback: { lineup: null },
      fetch: () => fetchLineup(this.data.id, side),
      map: (view) => ({ lineup: buildLineupBoard(view, detail) }),
    });
  },

  switchLineupSide(event: WechatMiniprogram.BaseEvent) {
    const side = Number(event.currentTarget.dataset.side);
    void this.loadLineup(side);
  },

  async loadGrouping(): Promise<void> {
    const detail = this.data.detail;
    if (!detail || !this.data.canViewGrouping) {
      return;
    }
    await this.guardedLoads().run({
      key: 'grouping',
      errorText: '分组加载失败，请稍后重试',
      fallback: { grouping: null, groupingBoard: null },
      fetch: () => fetchGrouping(this.data.id),
      map: (view) => ({ grouping: view, groupingBoard: buildGroupingBoard(view, detail) }),
    });
  },

  retryLineup() {
    void this.loadLineup(this.data.lineupSide);
  },

  retryGrouping() {
    void this.loadGrouping();
  },

  retryRoster() {
    void this.loadRoster();
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },

  goBack() {
    const pages = getCurrentPages();
    if (pages.length > 1) {
      void wx.navigateBack();
      return;
    }
    void wx.switchTab({ url: '/pages/schedule/index' });
  },
});
