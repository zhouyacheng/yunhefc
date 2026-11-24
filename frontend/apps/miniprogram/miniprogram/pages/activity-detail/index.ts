import type { ActivityDetail, ActivitySignup } from '../../types/api';
import { cancelActivitySignup, fetchActivity, signupActivity } from '../../utils/api-client';
import { errorMessage, shouldRefreshAfterActivityConflict } from '../../utils/api-errors';
import {
  activityAction,
  activityCanCancel,
  activityCapacityPercent,
  activityCostText,
  activityCountText,
  activityStatusText,
  activityStatusTone,
  activityTimeText,
  activityTypeText,
  familySummary,
  type ActivityActionView,
  type ActivityStatusTone,
} from '../../utils/activity-view';
import { formatDateTime, formatDayStartTime } from '../../utils/datetime';
import { invalidate } from '../../utils/invalidation';
import { ACTIVITY_SIGNUP_OWN, hasPermission } from '../../utils/permissions';
import { getPermissions, getUser, sessionReady } from '../../utils/session';

let requestSeq = 0;
let hasLoaded = false;

const RESTRICTED_SIGNUP_HINT = '仅正式球员可报名';

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
    capacityPercent: 0,
    mySignup: null as ActivitySignup | null,
    familyText: '',
    signedUpText: '',
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
  },

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
    hasLoaded = false;
  },

  onShareAppMessage() {
    const activity = this.data.activity;
    return {
      title: activity ? `${activity.title} · 云河FC` : '云河FC 活动',
      path: `/pages/activity-detail/index?id=${this.data.id}`,
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
      capacityPercent: activityCapacityPercent(activity),
      mySignup,
      familyText: familySummary(activity.mySignup),
      signedUpText: mySignup ? formatDateTime(mySignup.signedUpAt) : '',
      registered: !!mySignup,
      canCancel: activityCanCancel(activity, now),
      authenticated,
      pending,
      canSignupPerm,
      hint,
      loading: false,
      error: '',
      actionError: '',
    });
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

  retry() {
    hasLoaded = false;
    void this.load(false);
  },

  onImageError() {
    this.setData({ 'activity.locationImageUrls': [] });
  },
});
