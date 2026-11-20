import { WEB_URL } from '../../utils/config';
import type { AuthUser, PlayerView } from '../../types/api';
import {
  cancelMyRegistration,
  fetchMatchDetail,
  listMyRegistrations,
  listNotifications,
  markAllNotificationsRead,
  markNotificationRead,
} from '../../utils/api-client';
import { errorMessage, shouldRefreshAfterConflict } from '../../utils/api-errors';
import { promotionText, registrationDecision } from '../../utils/registration';
import { clearUnreadBadge, refreshUnreadBadge } from '../../utils/notifications';
import { createPagedFeed, emptyPagedFeedState, type PagedFeed } from '../../utils/paged-feed';
import { invalidate, subscribe } from '../../utils/invalidation';
import {
  decorateNotification,
  decorateRegistration,
  identityOf,
  type IdentityView,
  type NotificationRow,
  type RegistrationRowView,
} from '../../utils/profile-view';
import {
  getPermissions,
  getPlayer,
  getUser,
  logout as logoutSession,
  restore,
} from '../../utils/session';

const PAGE_SIZE = 10;

/** 首页/详情改动报名后失效此资源，本页在 onShow 重载报名列表。 */
const myRegistrations = subscribe('my-registrations');

let notificationFeed: PagedFeed<NotificationRow> | null = null;
let registrationFeed: PagedFeed<RegistrationRowView> | null = null;

function loggedOutState() {
  return {
    user: null,
    player: null,
    identity: null,
    pending: false,
    unreadCount: null,
    actionError: '',
    actionMessage: '',
  };
}

Page({
  data: {
    loading: true,
    user: null as AuthUser | null,
    player: null as PlayerView | null,
    identity: null as IdentityView | null,
    pending: false,
    notifications: emptyPagedFeedState<NotificationRow>('all'),
    registrations: emptyPagedFeedState<RegistrationRowView>('active'),
    markingAll: false,
    unreadCount: null as number | null,
    busyId: 0,
    cancelBusyId: 0,
    actionError: '',
    actionMessage: '',
  },

  onLoad() {
    let notificationError = '';
    notificationFeed = createPagedFeed<NotificationRow>({
      pageSize: PAGE_SIZE,
      initialKey: 'all',
      fetchPage: async ({ key, page, pageSize }) => {
        const result = await listNotifications({ page, pageSize, unread: key === 'unread' });
        return { ...result, items: result.items.map(decorateNotification) };
      },
      onChange: (state) => {
        this.setData({ notifications: state });
        if (state.error && state.error !== notificationError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        notificationError = state.error;
      },
    });

    let registrationError = '';
    registrationFeed = createPagedFeed<RegistrationRowView>({
      pageSize: PAGE_SIZE,
      initialKey: 'active',
      fetchPage: async ({ key, page, pageSize }) => {
        const scope = key === 'history' ? 'history' : 'active';
        const result = await listMyRegistrations({ scope, page, pageSize });
        return { ...result, items: result.items.map(decorateRegistration) };
      },
      onChange: (state) => {
        this.setData({ registrations: state });
        if (state.error && state.error !== registrationError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        registrationError = state.error;
      },
    });
  },

  onShow() {
    void this.bootstrap();
  },

  onPullDownRefresh() {
    void this.bootstrap(true).then(() => wx.stopPullDownRefresh());
  },

  onUnload() {
    notificationFeed = null;
    registrationFeed = null;
  },

  async bootstrap(force = false) {
    if (!this.data.identity) {
      this.setData({ loading: true });
    }
    const previousUserId = this.data.user?.id ?? null;
    const user = await restore();
    if (!user) {
      notificationFeed?.reset();
      registrationFeed?.reset();
      clearUnreadBadge();
      this.setData({ loading: false, ...loggedOutState() });
      return;
    }

    const player = getPlayer();
    const pending = user.status === 0;
    const switchedUser = previousUserId !== user.id;
    if (switchedUser) {
      notificationFeed?.reset();
      registrationFeed?.reset();
    }
    if (pending) {
      registrationFeed?.reset();
    }
    this.setData({
      loading: false,
      user,
      player,
      identity: identityOf(user, player),
      pending,
      actionError: '',
    });
    void refreshUnreadBadge().then((unreadCount) => {
      if (unreadCount !== null) {
        this.setData({ unreadCount });
      }
    });

    // 已加载过的列表保留分页位置；下拉刷新、切换账号、其他页面改动报名或首次进入时才重载。
    const forceRegistrations = myRegistrations.consume();
    const shouldLoadNotifications = force || switchedUser || !this.data.notifications.loaded;
    const shouldLoadRegistrations =
      !pending && (force || switchedUser || forceRegistrations || !this.data.registrations.loaded);
    await Promise.all([
      shouldLoadNotifications ? (notificationFeed?.load() ?? Promise.resolve()) : Promise.resolve(),
      shouldLoadRegistrations ? (registrationFeed?.load() ?? Promise.resolve()) : Promise.resolve(),
    ]);
  },

  switchNotificationFilter(event: WechatMiniprogram.BaseEvent) {
    notificationFeed?.setKey(String(event.currentTarget.dataset.filter ?? ''));
  },

  loadMoreNotifications() {
    void notificationFeed?.loadMore();
  },

  retryNotifications() {
    void notificationFeed?.load();
  },

  async openNotification(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const item = this.data.notifications.items.find((row) => row.id === id);
    if (!item || this.data.busyId) {
      return;
    }
    this.setData({ busyId: id, actionError: '' });
    try {
      if (item.unread) {
        try {
          await markNotificationRead(id);
          const unreadCount = await refreshUnreadBadge();
          if (unreadCount !== null) {
            this.setData({ unreadCount });
          }
        } catch (error) {
          // 已读失败不阻断 MATCH 跳转。
          this.setData({ actionError: errorMessage(error, '标记已读失败，请稍后重试') });
        }
        await notificationFeed?.load();
      }
      if (item.refType === 'MATCH' && typeof item.refId === 'number' && item.refId > 0) {
        invalidate('my-registrations');
        await wx.navigateTo({ url: `/pages/match-detail/index?id=${item.refId}` });
      }
    } finally {
      this.setData({ busyId: 0 });
    }
  },

  async markAllRead() {
    if (this.data.markingAll || this.data.unreadCount === 0) {
      return;
    }
    this.setData({ markingAll: true, actionError: '', actionMessage: '' });
    try {
      await markAllNotificationsRead();
      await notificationFeed?.load();
      this.setData({ actionMessage: '已全部标记为已读', unreadCount: 0 });
      void refreshUnreadBadge();
    } catch (error) {
      this.setData({ actionError: errorMessage(error, '全部已读失败，请稍后重试') });
    } finally {
      this.setData({ markingAll: false });
    }
  },

  switchRegistrationScope(event: WechatMiniprogram.BaseEvent) {
    registrationFeed?.setKey(String(event.currentTarget.dataset.scope ?? ''));
  },

  loadMoreRegistrations() {
    void registrationFeed?.loadMore();
  },

  retryRegistrations() {
    void registrationFeed?.load();
  },

  goMatch(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    // 详情页可能取消报名；返回时强制重载进行中/历史列表。
    invalidate('my-registrations');
    void wx.navigateTo({ url: `/pages/match-detail/index?id=${id}` });
  },

  async cancelRegistration(event: WechatMiniprogram.BaseEvent) {
    const matchId = Number(event.currentTarget.dataset.id);
    if (!matchId || this.data.cancelBusyId) {
      return;
    }
    const row = (this.data.registrations.items as RegistrationRowView[]).find(
      (item) => item.matchId === matchId,
    );
    if (!row) {
      return;
    }
    this.setData({ cancelBusyId: matchId });
    try {
      const detail = await fetchMatchDetail(matchId);
      const currentStatus = detail.myRegistration?.status ?? row.registrationStatus;
      const { cancel } = registrationDecision({
        authenticated: true,
        pending: false,
        playerType: getPlayer()?.type ?? null,
        permissions: getPermissions(),
        matchStatus: detail.status,
        now: Date.now(),
        signupDeadline: detail.signupDeadline,
        cancelLock: detail.cancelLock,
        queue: detail,
        myRegistration: { status: currentStatus },
      });
      if (!cancel.visible || cancel.disabled) {
        wx.showToast({ title: cancel.reason || '当前不可取消', icon: 'none' });
        return;
      }
      const confirmed = await new Promise<boolean>((resolve) => {
        wx.showModal({
          title: cancel.label,
          content: '确认取消本次报名？',
          success: (result) => resolve(result.confirm),
          fail: () => resolve(false),
        });
      });
      if (!confirmed) {
        return;
      }
      const result = await cancelMyRegistration(matchId);
      wx.showModal({
        title: '已取消',
        content: promotionText(result, null, currentStatus),
        showCancel: false,
      });
      await registrationFeed?.load();
    } catch (error) {
      wx.showToast({
        title: errorMessage(error, '取消报名失败，请稍后重试'),
        icon: 'none',
      });
      if (shouldRefreshAfterConflict(error)) {
        await registrationFeed?.load();
      }
    } finally {
      this.setData({ cancelBusyId: 0 });
    }
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },

  copyPasswordReset() {
    wx.setClipboardData({
      data: `${WEB_URL}/login?mode=reset`,
      success: () => wx.showToast({ title: '已复制网页地址', icon: 'none' }),
    });
  },

  onAvatarError() {
    this.setData({ 'identity.avatarUrl': '' });
  },

  goEdit() {
    if (this.data.pending) {
      wx.showToast({ title: '账号待审核，暂不能编辑资料', icon: 'none' });
      return;
    }
    void wx.navigateTo({ url: '/pages/profile-edit/index' });
  },

  confirmLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定退出当前账号吗？',
      confirmText: '退出',
      confirmColor: '#dc2626',
      success: (res) => {
        if (res.confirm) {
          void this.performLogout();
        }
      },
    });
  },

  async performLogout() {
    try {
      await logoutSession();
      notificationFeed?.reset();
      registrationFeed?.reset();
      clearUnreadBadge();
      this.setData(loggedOutState());
      wx.showToast({ title: '已退出登录', icon: 'none' });
    } catch (error) {
      if (!getUser()) {
        notificationFeed?.reset();
        registrationFeed?.reset();
        clearUnreadBadge();
        this.setData(loggedOutState());
        return;
      }
      wx.showToast({
        title: errorMessage(error, '退出失败，请稍后重试'),
        icon: 'none',
      });
    }
  },
});
