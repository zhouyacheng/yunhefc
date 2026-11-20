// MP04 写操作：报名/取消/签到、比赛状态修改与事件录入。
// 与页面读取模型分离，页面只保留 bindtap 包装，数据字段仍由页面 data 提供。
import type {
  Attendance,
  EventFormPlayer,
  GroupingView,
  MatchDetail,
  MatchEvent,
  Registration,
} from '../../types/api';
import { errorMessage, shouldRefreshAfterConflict } from '../../utils/api-errors';
import { eventEditAllowed } from '../../utils/event-form';
import {
  cancelMyRegistration,
  changeMatchStatus,
  checkInMatch,
  createMatchEvent,
  signupMatch,
  updateMatchEvent,
} from '../../utils/api-client';
import { MATCH_STATUS_TEXT, attendanceText } from '../../utils/presentation';
import {
  REGISTRATION_STATUS,
  promotionText,
  type CancelDecision,
  type SignupDecision,
} from '../../utils/registration';
import { markMyRegistrationsDirty } from '../../utils/registration-signal';

export interface MatchActionPage {
  data: {
    id: number;
    detail: MatchDetail | null;
    registration: Registration | null;
    attendance: Attendance | null;
    attendanceText: string;
    signup: SignupDecision | null;
    signupBusy: boolean;
    cancel: CancelDecision | null;
    cancelBusy: boolean;
    checkInBusy: boolean;
    checkInOpen: boolean;
    actionError: string;
    actionMessage: string;
    canChangeStatus: boolean;
    statusOpen: boolean;
    statusBusy: boolean;
    statusError: string;
    canCreateEvent: boolean;
    eventPlayers: EventFormPlayer[];
    eventFormOpen: boolean;
    editingEvent: MatchEvent | null;
    editingVersion: number;
    eventBusy: boolean;
    eventError: string;
    canViewGrouping: boolean;
    grouping: GroupingView | null;
    eventGrouping: GroupingView | null;
    rawEvents: MatchEvent[];
    ownPlayerId: number;
    canEditOwnPerm: boolean;
  };
  setData(patch: Record<string, unknown>): void;
  load(silent: boolean): Promise<void>;
  loadRoster(): Promise<void>;
  loadGrouping(): Promise<void>;
  loadEvents(): Promise<void>;
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

export async function signupNow(page: MatchActionPage): Promise<void> {
  const state = page.data.signup;
  if (
    page.data.signupBusy ||
    !state ||
    !state.visible ||
    state.action !== 'signup' ||
    state.disabled
  ) {
    return;
  }
  const detail = page.data.detail;
  if (!detail) {
    return;
  }
  page.setData({ signupBusy: true, actionError: '', actionMessage: '' });
  try {
    const registration = await signupMatch(detail.id);
    markMyRegistrationsDirty();
    toast(registration.status === REGISTRATION_STATUS.WAITLISTED ? '已加入候补' : '报名成功');
    await page.load(true);
  } catch (error) {
    page.setData({ actionError: errorMessage(error, '报名失败，请稍后重试') });
    if (shouldRefreshAfterConflict(error)) {
      await page.load(true);
    }
  } finally {
    page.setData({ signupBusy: false });
  }
}

export async function cancelNow(page: MatchActionPage): Promise<void> {
  if (page.data.cancelBusy) {
    return;
  }
  const detail = page.data.detail;
  const cancel = page.data.cancel;
  if (!detail || !cancel?.visible || cancel.disabled) {
    return;
  }
  if (!(await confirm(cancel.label, '确认取消本次报名？'))) {
    return;
  }
  const cancelledStatus = page.data.registration?.status ?? null;
  page.setData({ cancelBusy: true, actionError: '', actionMessage: '' });
  try {
    const result = await cancelMyRegistration(detail.id);
    markMyRegistrationsDirty();
    await page.load(true);
    page.setData({ actionMessage: promotionText(result, null, cancelledStatus) });
  } catch (error) {
    page.setData({ actionError: errorMessage(error, '取消报名失败，请稍后重试') });
    if (shouldRefreshAfterConflict(error)) {
      await page.load(true);
    }
  } finally {
    page.setData({ cancelBusy: false });
  }
}

export async function checkInNow(page: MatchActionPage): Promise<void> {
  if (page.data.checkInBusy || !page.data.checkInOpen) {
    return;
  }
  page.setData({ checkInBusy: true, actionError: '', actionMessage: '' });
  try {
    const attendance = await checkInMatch(page.data.id);
    page.setData({ attendance, attendanceText: attendanceText(attendance) });
    toast('签到成功');
    await page.load(true);
  } catch (error) {
    page.setData({ actionError: errorMessage(error, '签到失败，请稍后重试') });
    if (shouldRefreshAfterConflict(error)) {
      await page.load(true);
    }
  } finally {
    page.setData({ checkInBusy: false });
  }
}

export function openStatus(page: MatchActionPage): void {
  if (!page.data.canChangeStatus) {
    return;
  }
  page.setData({ statusOpen: true, statusError: '' });
}

export function closeStatus(page: MatchActionPage): void {
  page.setData({ statusOpen: false });
}

export async function submitStatus(
  page: MatchActionPage,
  body: { status: number; reason?: string; startAt?: string; endAt?: string },
): Promise<void> {
  const detail = page.data.detail;
  if (!detail || page.data.statusBusy) {
    return;
  }
  page.setData({ statusBusy: true, statusError: '' });
  try {
    await changeMatchStatus(detail.id, {
      status: body.status,
      version: detail.version,
      manualStatus: true,
      reason: body.reason,
      startAt: body.startAt,
      endAt: body.endAt,
    });
    page.setData({ statusOpen: false });
    toast(`状态已改为${MATCH_STATUS_TEXT[body.status] ?? ''}`);
    await page.load(true);
  } catch (error) {
    page.setData({ statusError: errorMessage(error, '状态修改失败，请稍后重试') });
    if (shouldRefreshAfterConflict(error)) {
      await page.load(true);
    }
  } finally {
    page.setData({ statusBusy: false });
  }
}

export function onStatusChange(page: MatchActionPage, event: WechatMiniprogram.CustomEvent): void {
  void submitStatus(page, { status: Number(event.detail.status) });
}

export function onStatusPostpone(
  page: MatchActionPage,
  event: WechatMiniprogram.CustomEvent,
): void {
  void submitStatus(page, {
    status: 5,
    startAt: String(event.detail.startAt),
    endAt: String(event.detail.endAt),
  });
}

export function onStatusCancel(page: MatchActionPage, event: WechatMiniprogram.CustomEvent): void {
  void submitStatus(page, { status: 6, reason: String(event.detail.reason) });
}

export async function openCreateEvent(page: MatchActionPage): Promise<void> {
  if (!page.data.canCreateEvent) {
    return;
  }
  if (page.data.eventPlayers.length === 0) {
    await page.loadRoster();
  }
  await ensureEventGrouping(page);
  page.setData({ editingEvent: null, editingVersion: 0, eventError: '', eventFormOpen: true });
}

export async function openEditEvent(
  page: MatchActionPage,
  event: WechatMiniprogram.BaseEvent,
): Promise<void> {
  const id = Number(event.currentTarget.dataset.id);
  const target = page.data.rawEvents.find((item) => item.id === id);
  if (!target) {
    return;
  }
  const detail = page.data.detail;
  if (
    !detail ||
    !eventEditAllowed(target, {
      ownPlayerId: page.data.ownPlayerId || null,
      hasEditOwn: page.data.canEditOwnPerm,
      matchStatus: detail.status,
      endAt: detail.endAt,
      now: Date.now(),
    })
  ) {
    return;
  }
  await ensureEventGrouping(page);
  page.setData({
    editingEvent: target,
    editingVersion: target.version,
    eventError: '',
    eventFormOpen: true,
  });
}

export function closeEventForm(page: MatchActionPage): void {
  page.setData({ eventFormOpen: false, eventError: '' });
}

async function ensureEventGrouping(page: MatchActionPage): Promise<void> {
  if (!page.data.canViewGrouping) {
    return;
  }
  if (!page.data.grouping) {
    await page.loadGrouping();
  }
  page.setData({ eventGrouping: page.data.grouping });
}

export async function onEventSave(
  page: MatchActionPage,
  event: WechatMiniprogram.CustomEvent,
): Promise<void> {
  const { payload, eventId, version } = event.detail as {
    payload: Parameters<typeof createMatchEvent>[1];
    eventId: number | null;
    version: number | null;
  };
  if (page.data.eventBusy) {
    return;
  }
  page.setData({ eventBusy: true, eventError: '' });
  try {
    if (eventId !== null && version !== null) {
      await updateMatchEvent(eventId, { ...payload, version });
    } else {
      await createMatchEvent(page.data.id, payload);
    }
    page.setData({ eventFormOpen: false });
    toast('事件已保存');
    await page.load(true);
  } catch (error) {
    page.setData({ eventError: errorMessage(error, '事件保存失败，请稍后重试') });
    if (shouldRefreshAfterConflict(error)) {
      await page.load(true);
      if (eventId !== null) {
        const refreshed = page.data.rawEvents.find((item) => item.id === eventId);
        if (refreshed) {
          page.setData({ editingVersion: refreshed.version });
        }
      }
    }
  } finally {
    page.setData({ eventBusy: false });
  }
}

export function retryEvents(page: MatchActionPage): void {
  void page.loadEvents();
}
