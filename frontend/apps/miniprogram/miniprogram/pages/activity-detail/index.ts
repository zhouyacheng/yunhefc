import type { ActivityDetail, ActivitySignup, ActivityStatus } from '../../types/api';
import {
  cancelActivitySignup,
  changeActivityStatus,
  fetchActivity,
  listActivitySignups,
  signupActivity,
} from '../../utils/api-client';
import { activityStatusOptions, type ActivityStatusOption } from '../../utils/activity-status';
import {
  ACTIVITY_SHARE_COVER_FALLBACK,
  buildActivityShareCoverData,
  renderActivityShareCover,
} from '../../utils/activity-share-cover';
import { errorMessage, shouldRefreshAfterActivityConflict } from '../../utils/api-errors';
import {
  activityAction,
  activityCanCancel,
  activityCapacityPercent,
  activityCostText,
  activityCountText,
  activitySignupCardText,
  activityStatusText,
  activityStatusTone,
  activityTimeText,
  activityTypeText,
  clampGalleryIndex,
  familySummary,
  withoutFailedImage,
  type ActivityActionView,
  type ActivityStatusTone,
} from '../../utils/activity-view';
import { formatDayStartTime } from '../../utils/datetime';
import { invalidate } from '../../utils/invalidation';
import {
  ACTIVITY_MANAGE_ANY,
  ACTIVITY_SIGNUP_OWN,
  ACTIVITY_VIEW_ANY,
  hasPermission,
} from '../../utils/permissions';
import { getPermissions, getPlayer, getUser, sessionReady } from '../../utils/session';
import { SHARE_COVER_HEIGHT, SHARE_COVER_WIDTH, generateShareCover } from '../../utils/share-cover';

/** 报名队列行：在 ACT-05 返回字段上补展示文案。 */
interface ActivitySignupRowView extends ActivitySignup {
  initial: string;
  familyText: string;
  isMine: boolean;
}

let requestSeq = 0;
let hasLoaded = false;

const RESTRICTED_SIGNUP_HINT = '仅正式球员可报名';
const SIGNUP_PAGE_SIZE = 100;

function confirmDialog(title: string, content: string): Promise<boolean> {
  return new Promise<boolean>((resolve) => {
    wx.showModal({
      title,
      content,
      success: (result) => resolve(result.confirm),
      fail: () => resolve(false),
    });
  });
}

Page({
  data: {
    id: 0,
    loading: true,
    error: '',
    activity: null as ActivityDetail | null,
    typeText: '',
    statusText: '',
    statusTone: 'closed' as ActivityStatusTone,
    action: null as ActivityActionView | null,
    timeText: '',
    deadlineText: '',
    costText: '',
    countText: '',
    signupCardText: '',
    capacityPercent: 0,
    galleryIndex: 0,
    mySignup: null as ActivitySignup | null,
    registered: false,
    canCancel: false,
    authenticated: false,
    pending: false,
    canSignupPerm: false,
    hint: '',
    signupBusy: false,
    cancelBusy: false,
    actionError: '',
    sheetOpen: false,
    withFamily: false,
    familyNote: '',
    canChangeStatus: false,
    statusOpen: false,
    statusOptions: [] as ActivityStatusOption[],
    statusBusy: false,
    statusError: '',
    canViewSignups: false,
    signups: [] as ActivitySignupRowView[],
    signupTotal: null as number | null,
    signupsLoading: false,
    signupsError: '',
    shareCoverPath: ACTIVITY_SHARE_COVER_FALLBACK,
    shareCoverReady: false,
    shareCoverStyle: `width:${SHARE_COVER_WIDTH}px;height:${SHARE_COVER_HEIGHT}px;`,
  },

  /** 分享封面生成代次；连续生成时丢弃旧图。 */
  shareCoverSeq: 0,

  onLoad(options: Record<string, string | undefined>) {
    const id = Number(options.id);
    if (!id || id < 1) {
      this.setData({ loading: false, error: '活动不存在' });
      return;
    }
    this.setData({ id });
    void this.load(false);
  },

  onShow() {
    // 从登录页返回后刷新登录态与 mySignup；首次 onShow 早于首次请求完成，不会重复加载。
    if (hasLoaded) {
      void this.load(true);
    }
  },

  onUnload() {
    requestSeq += 1;
    this.shareCoverSeq += 1;
    hasLoaded = false;
  },

  onShareAppMessage() {
    const activity = this.data.activity;
    return {
      title: activity ? `${activity.title} · 活动邀请` : '云河FC 活动',
      path: `/pages/activity-detail/index?id=${this.data.id}`,
      imageUrl: this.data.shareCoverReady
        ? this.data.shareCoverPath
        : ACTIVITY_SHARE_COVER_FALLBACK,
    };
  },

  async load(silent: boolean) {
    const id = this.data.id;
    if (!id) {
      return;
    }
    const seq = ++requestSeq;
    if (!silent) {
      this.setData({ loading: true, error: '' });
    }
    try {
      await sessionReady();
      if (seq !== requestSeq) {
        return;
      }
      const activity = await fetchActivity(id);
      if (seq !== requestSeq) {
        return;
      }
      hasLoaded = true;
      this.applyActivity(activity);
      if (this.data.canViewSignups) {
        void this.loadSignups(seq);
      }
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      const message = errorMessage(error, '加载失败，请稍后重试');
      if (silent && this.data.activity) {
        wx.showToast({ title: message, icon: 'none' });
        this.setData({ loading: false });
        return;
      }
      this.setData({ loading: false, error: message });
    }
  },

  applyActivity(activity: ActivityDetail) {
    const now = Date.now();
    const user = getUser();
    const authenticated = !!user;
    const pending = user?.status === 0;
    const mySignup = activity.mySignup && activity.mySignup.status === 0 ? activity.mySignup : null;
    const action = activityAction(activity, now);
    const canSignupPerm = authenticated && hasPermission(getPermissions(), ACTIVITY_SIGNUP_OWN);
    const canChangeStatus =
      authenticated && hasPermission(getPermissions(), ACTIVITY_MANAGE_ANY);
    const canViewSignups =
      authenticated && hasPermission(getPermissions(), ACTIVITY_VIEW_ANY);
    const hint =
      action.reason || (authenticated && !pending && !canSignupPerm ? RESTRICTED_SIGNUP_HINT : '');
    this.setData({
      activity,
      typeText: activityTypeText(activity.type),
      statusText: activityStatusText(activity.status),
      statusTone: activityStatusTone(activity.status),
      action,
      timeText: activityTimeText(activity),
      deadlineText: formatDayStartTime(activity.signupDeadline),
      costText: activityCostText(activity),
      countText: activityCountText(activity),
      signupCardText: activitySignupCardText(activity),
      capacityPercent: activityCapacityPercent(activity),
      galleryIndex: clampGalleryIndex(this.data.galleryIndex, activity.locationImageUrls.length),
      mySignup,
      registered: !!mySignup,
      canCancel: activityCanCancel(activity, now),
      authenticated,
      pending,
      canSignupPerm,
      canChangeStatus,
      canViewSignups,
      signups: canViewSignups ? this.data.signups : [],
      signupTotal: canViewSignups ? this.data.signupTotal : null,
      signupsLoading: canViewSignups ? this.data.signupsLoading : false,
      signupsError: canViewSignups ? this.data.signupsError : '',
      statusOptions: activityStatusOptions(activity.status),
      hint,
      loading: false,
      error: '',
      actionError: '',
    });
    void this.prepareShareCover();
  },

  /** 按当前活动生成 5:4 分享封面；失败回退包内静态品牌图，分享按钮随后恢复可用。 */
  async prepareShareCover(): Promise<void> {
    const activity = this.data.activity;
    if (!activity) {
      return;
    }
    const seq = this.shareCoverSeq + 1;
    this.shareCoverSeq = seq;
    const path = await generateShareCover(
      '#activity-share-cover',
      (canvas) => renderActivityShareCover(canvas, buildActivityShareCoverData(activity)),
      ACTIVITY_SHARE_COVER_FALLBACK,
    );
    if (seq !== this.shareCoverSeq) {
      return;
    }
    this.setData({ shareCoverPath: path, shareCoverReady: true });
  },

  /** ACT-05 报名队列；详情接口不内联名单，登录且持 activity:view:any 时单独加载。 */
  async loadSignups(seq: number) {
    if (!this.data.canViewSignups) {
      return;
    }
    this.setData({
      signupsLoading: this.data.signups.length === 0,
      signupsError: '',
    });
    try {
      const page = await listActivitySignups(this.data.id, {
        page: 1,
        pageSize: SIGNUP_PAGE_SIZE,
      });
      if (seq !== requestSeq) {
        return;
      }
      const myPlayerId = getPlayer()?.id ?? this.data.mySignup?.playerId ?? null;
      this.setData({
        signups: page.items.map((item) => ({
          ...item,
          initial: item.playerName.slice(0, 1),
          familyText: item.withFamily ? familySummary(item) : '',
          isMine: myPlayerId !== null && item.playerId === myPlayerId,
        })),
        signupTotal: page.total,
        signupsLoading: false,
      });
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      this.setData({
        signupsLoading: false,
        signupsError: errorMessage(error, '报名队列加载失败，请稍后重试'),
      });
    }
  },

  retrySignups() {
    void this.loadSignups(requestSeq);
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },

  openSignup() {
    const action = this.data.action;
    if (!action || action.disabled || this.data.signupBusy) {
      if (action?.reason) {
        wx.showToast({ title: action.reason, icon: 'none' });
      }
      return;
    }
    if (!this.data.canSignupPerm) {
      wx.showToast({ title: RESTRICTED_SIGNUP_HINT, icon: 'none' });
      return;
    }
    this.setData({
      sheetOpen: true,
      withFamily: false,
      familyNote: '',
      actionError: '',
    });
  },

  closeSheet() {
    if (this.data.signupBusy) {
      return;
    }
    this.setData({ sheetOpen: false });
  },

  onFamilyToggle(event: WechatMiniprogram.CustomEvent) {
    this.setData({ withFamily: Boolean(event.detail) });
  },

  onFamilyNoteChange(event: WechatMiniprogram.CustomEvent) {
    this.setData({ familyNote: String(event.detail || '') });
  },

  async submitSignup() {
    if (this.data.signupBusy) {
      return;
    }
    this.setData({ signupBusy: true, actionError: '' });
    try {
      const familyAllowed = this.data.activity?.familyAllowed === true;
      const withFamily = familyAllowed && this.data.withFamily;
      const familyNote = withFamily ? this.data.familyNote.trim() : '';
      await signupActivity(this.data.id, {
        withFamily,
        familyNote: familyNote || undefined,
      });
      invalidate('activities');
      this.setData({ sheetOpen: false });
      wx.showToast({ title: '报名成功', icon: 'none' });
      await this.load(true);
    } catch (error) {
      const message = errorMessage(error, '报名失败，请稍后重试');
      this.setData({ actionError: message });
      wx.showToast({ title: message, icon: 'none' });
      if (shouldRefreshAfterActivityConflict(error)) {
        this.setData({ sheetOpen: false });
        await this.load(true);
      }
    } finally {
      this.setData({ signupBusy: false });
    }
  },

  async cancelSignup() {
    if (this.data.cancelBusy || !this.data.mySignup) {
      return;
    }
    if (!this.data.canCancel) {
      wx.showToast({ title: '报名已截止，无法取消', icon: 'none' });
      return;
    }
    if (!(await confirmDialog('取消报名', '确认取消本次活动报名？'))) {
      return;
    }
    this.setData({ cancelBusy: true, actionError: '' });
    try {
      const result = await cancelActivitySignup(this.data.id);
      invalidate('activities');
      wx.showToast({ title: result.cancelled ? '已取消报名' : '报名已取消', icon: 'none' });
      await this.load(true);
    } catch (error) {
      const message = errorMessage(error, '取消报名失败，请稍后重试');
      this.setData({ actionError: message });
      wx.showToast({ title: message, icon: 'none' });
      if (shouldRefreshAfterActivityConflict(error)) {
        await this.load(true);
      }
    } finally {
      this.setData({ cancelBusy: false });
    }
  },

  openStatus() {
    if (!this.data.canChangeStatus) {
      return;
    }
    this.setData({
      statusOpen: true,
      statusError: '',
    });
  },

  closeStatus() {
    if (this.data.statusBusy) {
      return;
    }
    this.setData({ statusOpen: false });
  },

  onStatusChange(event: WechatMiniprogram.CustomEvent) {
    void this.submitStatus(Number(event.detail.status) as ActivityStatus);
  },

  onStatusCancel(event: WechatMiniprogram.CustomEvent) {
    void this.submitStatus(3, String(event.detail.reason));
  },

  async submitStatus(status: ActivityStatus, reason?: string) {
    const activity = this.data.activity;
    if (!activity || this.data.statusBusy) {
      return;
    }
    this.setData({ statusBusy: true, statusError: '' });
    try {
      await changeActivityStatus(activity.id, {
        status,
        version: activity.version,
        ...(reason ? { reason } : {}),
      });
      invalidate('activities');
      this.setData({ statusOpen: false });
      wx.showToast({
        title: `状态已改为${activityStatusText(status)}`,
        icon: 'none',
      });
      await this.load(true);
    } catch (error) {
      const message = errorMessage(error, '状态修改失败，请稍后重试');
      this.setData({ statusError: message });
      if (shouldRefreshAfterActivityConflict(error)) {
        await this.load(true);
      }
    } finally {
      this.setData({ statusBusy: false });
    }
  },

  retry() {
    hasLoaded = false;
    void this.load(false);
  },

  onImageError(event: WechatMiniprogram.CustomEvent) {
    const index = Number(event.currentTarget.dataset.index);
    const urls = this.data.activity?.locationImageUrls ?? [];
    const next = withoutFailedImage(urls, index);
    this.setData({
      'activity.locationImageUrls': next,
      galleryIndex: clampGalleryIndex(this.data.galleryIndex, next.length),
    });
  },

  onGalleryChange(event: WechatMiniprogram.CustomEvent) {
    this.setData({ galleryIndex: Number(event.detail.current) || 0 });
  },

  prevImage() {
    this.shiftGallery(-1);
  },

  nextImage() {
    this.shiftGallery(1);
  },

  shiftGallery(step: number) {
    const count = this.data.activity?.locationImageUrls.length ?? 0;
    if (count < 2) {
      return;
    }
    this.setData({ galleryIndex: (this.data.galleryIndex + step + count) % count });
  },
});
