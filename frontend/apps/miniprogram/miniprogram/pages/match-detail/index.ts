import type {
  Attendance,
  AttendanceRow,
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
    roster: buildRosterView(buildRoster([], [], [])) as RosterView,
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
    // 递增后迟到的响应会被丢弃；页面已卸载，无需 setData。
    this.data.seq += 1;
  },

  onPullDownRefresh() {
    void this.load(true).then(() => wx.stopPullDownRefresh());
  },

  async load(silent: boolean): Promise<void> {
    const seq = this.data.seq + 1;
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
            roster: buildRosterView(buildRoster([], [], [])),
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

      void this.loadRoster(seq);
      // 等待事件刷新完成，冲突重试时 rawEvents 已是最新，editingVersion 不会拿到旧值。
      await this.loadEvents(seq);
      if (this.data.lineup) {
        void this.loadLineup(this.data.lineupSide, seq);
      }
      if (this.data.grouping) {
        void this.loadGrouping(seq);
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
          roster: buildRosterView(buildRoster([], [], [])),
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

  async loadRoster(seq: number): Promise<void> {
    const detail = this.data.detail;
    if (!detail) {
      return;
    }
    this.setData({ rosterLoading: true, rosterError: '', attendanceError: '' });
    try {
      let sections: RosterSections;
      let confirmed: RegistrationRow[] = [];
      if (!this.data.authenticated || this.data.pending || detail.status === 6) {
        sections = buildRoster([], [], []);
      } else if (this.data.canViewRoster) {
        const full = await fetchFullRoster(this.data.id);
        if (seq !== this.data.seq) {
          return;
        }
        sections = buildRoster(full.confirmed, full.waitlist, full.attendance);
        confirmed = full.confirmed;
        this.setData({ attendanceError: full.attendanceError });
      } else {
        const own = ownRosterRows(this.data.registration, this.data.attendance);
        sections = buildRoster(own.confirmed, own.waitlist, own.attendance);
        confirmed = own.confirmed;
      }
      if (seq !== this.data.seq) {
        return;
      }
      const seen = new Set<number>();
      const eventPlayers: EventFormPlayer[] = [];
      for (const row of confirmed) {
        if (seen.has(row.player.id)) {
          continue;
        }
        seen.add(row.player.id);
        eventPlayers.push({
          id: row.player.id,
          name: row.player.name,
          jersey: row.player.jersey,
          position: row.player.position,
          positionLabel: row.player.positionLabel,
        });
      }
      this.setData({ roster: buildRosterView(sections), eventPlayers });
      this.syncWindowState();
    } catch (error) {
      if (seq !== this.data.seq) {
        return;
      }
      this.setData({
        roster: buildRosterView(buildRoster([], [], [])),
        rosterError: errorMessage(error, '名单加载失败，请稍后重试'),
      });
    } finally {
      if (seq === this.data.seq) {
        this.setData({ rosterLoading: false });
      }
    }
  },

  async loadEvents(seq: number): Promise<void> {
    this.setData({ eventsLoading: true, eventsError: '' });
    try {
      const timeline = await fetchEvents(this.data.id);
      if (seq !== this.data.seq) {
        return;
      }
      const derived = timeline.derivedScore ?? null;
      this.setData({
        rawEvents: timeline.items,
        derivedScore: derived,
        eventStats: {
          goals: timeline.items.filter((event) => event.type === 0).length,
          cards: timeline.items.filter((event) => event.type === 3 || event.type === 4).length,
          subs: timeline.items.filter((event) => event.type === 2).length,
        },
      });
      const detail = this.data.detail;
      if (detail) {
        this.setData({ header: buildMatchDetailHeader(detail, derived) });
      }
      this.refreshEventRows();
    } catch (error) {
      if (seq !== this.data.seq) {
        return;
      }
      this.setData({ eventsError: errorMessage(error, '事件加载失败，请稍后重试') });
    } finally {
      if (seq === this.data.seq) {
        this.setData({ eventsLoading: false });
      }
    }
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
      void this.loadLineup(side, this.data.seq);
    }
    if (key === 'grouping' && !this.data.grouping && !this.data.groupingLoading) {
      void this.loadGrouping(this.data.seq);
    }
  },

  signupNow() {
    return actions.signupNow(this);
  },

  cancelNow() {
    return actions.cancelNow(this);
  },

  checkInNow() {
    return actions.checkInNow(this);
  },

  openStatus() {
    actions.openStatus(this);
  },

  closeStatus() {
    actions.closeStatus(this);
  },

  submitStatus(body: { status: number; reason?: string; startAt?: string; endAt?: string }) {
    return actions.submitStatus(this, body);
  },

  onStatusChange(event: WechatMiniprogram.CustomEvent) {
    actions.onStatusChange(this, event);
  },

  onStatusPostpone(event: WechatMiniprogram.CustomEvent) {
    actions.onStatusPostpone(this, event);
  },

  onStatusCancel(event: WechatMiniprogram.CustomEvent) {
    actions.onStatusCancel(this, event);
  },

  openCreateEvent() {
    return actions.openCreateEvent(this);
  },

  openEditEvent(event: WechatMiniprogram.BaseEvent) {
    return actions.openEditEvent(this, event);
  },

  closeEventForm() {
    actions.closeEventForm(this);
  },

  onEventSave(event: WechatMiniprogram.CustomEvent) {
    return actions.onEventSave(this, event);
  },

  retryEvents() {
    actions.retryEvents(this);
  },

  async loadLineup(side: number, seq: number): Promise<void> {
    const detail = this.data.detail;
    if (!detail || !this.data.canViewLineup) {
      return;
    }
    this.setData({ lineupLoading: true, lineupError: '', lineupSide: side });
    try {
      const view = await fetchLineup(this.data.id, side);
      if (seq !== this.data.seq || this.data.lineupSide !== side) {
        return;
      }
      this.setData({ lineup: buildLineupBoard(view, detail) });
    } catch (error) {
      if (seq !== this.data.seq || this.data.lineupSide !== side) {
        return;
      }
      this.setData({ lineup: null, lineupError: errorMessage(error, '阵容加载失败，请稍后重试') });
    } finally {
      if (seq === this.data.seq && this.data.lineupSide === side) {
        this.setData({ lineupLoading: false });
      }
    }
  },

  switchLineupSide(event: WechatMiniprogram.BaseEvent) {
    const side = Number(event.currentTarget.dataset.side);
    void this.loadLineup(side, this.data.seq);
  },

  async loadGrouping(seq: number): Promise<void> {
    const detail = this.data.detail;
    if (!detail || !this.data.canViewGrouping) {
      return;
    }
    this.setData({ groupingLoading: true, groupingError: '' });
    try {
      const view = await fetchGrouping(this.data.id);
      if (seq !== this.data.seq) {
        return;
      }
      this.setData({ grouping: view, groupingBoard: buildGroupingBoard(view, detail) });
    } catch (error) {
      if (seq !== this.data.seq) {
        return;
      }
      this.setData({
        grouping: null,
        groupingBoard: null,
        groupingError: errorMessage(error, '分组加载失败，请稍后重试'),
      });
    } finally {
      if (seq === this.data.seq) {
        this.setData({ groupingLoading: false });
      }
    }
  },

  retryLineup() {
    void this.loadLineup(this.data.lineupSide, this.data.seq);
  },

  retryGrouping() {
    void this.loadGrouping(this.data.seq);
  },

  retryRoster() {
    void this.loadRoster(this.data.seq);
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
