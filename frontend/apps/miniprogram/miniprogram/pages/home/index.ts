import type { MatchDetail } from '../../types/api';
import { adminEntryUrl } from '../../utils/admin-entry';
import {
  fetchMatchDetail,
  issueAdminTicket,
  listRecentMatches,
  signupMatch,
} from '../../utils/api-client';
import { errorMessage, shouldRefreshAfterConflict } from '../../utils/api-errors';
import { contentVisible } from '../../utils/content';
import { buildHero, HOME_FEATURES, type HeroView } from '../../utils/home-view';
import { buildMatchCard, type MatchCardView } from '../../utils/match-view';
import { invalidate } from '../../utils/invalidation';
import { LEDGER_VIEW_ANY, hasBackendPermission, hasPermission, isPending } from '../../utils/permissions';
import { REGISTRATION_STATUS } from '../../utils/registration';
import { getPermissions, getPlayer, getUser, sessionReady } from '../../utils/session';

let requestSeq = 0;
let heroDetail: MatchDetail | null = null;
let hasLoaded = false;
let ctaTimer: ReturnType<typeof setInterval> | undefined;

function heroView(detail: MatchDetail, now: number): HeroView {
  return buildHero(detail, getUser(), getPlayer(), getPermissions(), now);
}

interface TimerPage {
  syncCta: () => void;
}

interface QuickEntryView {
  icon: string;
  title: string;
  url: string;
}

function stopTimer(): void {
  if (ctaTimer !== undefined) {
    clearInterval(ctaTimer);
    ctaTimer = undefined;
  }
}

function startTimer(page: TimerPage): void {
  stopTimer();
  ctaTimer = setInterval(() => page.syncCta(), 30_000);
}

Page({
  data: {
    loading: true,
    error: '',
    hero: null as HeroView | null,
    recent: [] as MatchCardView[],
    features: HOME_FEATURES,
    signupBusy: false,
    canEnterAdmin: false,
    quickEntries: [] as QuickEntryView[],
    adminBusy: false,
  },

  onShow() {
    void this.bootstrap();
    startTimer(this);
  },

  onHide() {
    stopTimer();
  },

  onUnload() {
    stopTimer();
    requestSeq += 1;
    heroDetail = null;
    hasLoaded = false;
  },

  onPullDownRefresh() {
    // 静默刷新：失败保留已有内容并 toast，仅首次加载无内容时展示整页错误。
    void this.load(true).then(() => wx.stopPullDownRefresh());
  },

  async bootstrap() {
    // 等启动静默登录结束再加载，保证 myRegistration 与 CTA 使用当前身份。
    await sessionReady();
    const permissions = getPermissions();
    const canViewLedger =
      !isPending(getUser()?.status) && hasPermission(permissions, LEDGER_VIEW_ANY);
    // P-142：动态入口仅对登录的正式球员与管理员显示，与 C01 同口径。
    const canViewContent = contentVisible(getUser(), permissions);
    this.setData({
      canEnterAdmin: hasBackendPermission(permissions),
      quickEntries: [
        ...(canViewContent ? [{ icon: '📷', title: '动态', url: '/pages/posts/index' }] : []),
        { icon: '🎉', title: '活动', url: '/pages/activity/index' },
        ...(canViewLedger ? [{ icon: '💰', title: '账本', url: '/pages/ledger/index' }] : []),
      ],
    });
    await this.load(true);
  },

  async load(silent: boolean) {
    const seq = ++requestSeq;
    if (!silent) {
      this.setData({ loading: true, error: '' });
    }
    try {
      const page = await listRecentMatches();
      if (seq !== requestSeq) {
        return;
      }
      const [first] = page.items;
      const recent = page.items.filter((match) => match.id !== first?.id).slice(0, 3);
      if (!first) {
        heroDetail = null;
        hasLoaded = true;
        this.setData({ hero: null, recent: [], loading: false, error: '' });
        return;
      }

      const detail = await fetchMatchDetail(first.id);
      if (seq !== requestSeq) {
        return;
      }
      heroDetail = detail;
      hasLoaded = true;
      this.setData({
        hero: heroView(detail, Date.now()),
        recent: recent.map(buildMatchCard),
        loading: false,
        error: '',
      });
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      const message = (error as Error).message || '加载失败，请稍后重试';
      if (silent && hasLoaded) {
        wx.showToast({ title: message, icon: 'none' });
        this.setData({ loading: false });
        return;
      }
      heroDetail = null;
      this.setData({ loading: false, error: message, hero: null, recent: [] });
    }
  },

  syncCta() {
    if (!heroDetail) {
      return;
    }
    this.setData({ hero: heroView(heroDetail, Date.now()) });
  },

  retry() {
    void this.load(false);
  },

  async onCtaTap() {
    const hero = this.data.hero;
    if (!hero || hero.cta.disabled || this.data.signupBusy) {
      return;
    }
    if (hero.cta.action === 'login') {
      void wx.navigateTo({ url: '/pages/login/index' });
      return;
    }
    if (hero.cta.action === 'view-detail') {
      void wx.navigateTo({ url: `/pages/match-detail/index?id=${hero.id}` });
      return;
    }
    if (hero.cta.action !== 'register') {
      return;
    }
    this.setData({ signupBusy: true });
    try {
      const registration = await signupMatch(hero.id);
      invalidate('my-registrations');
      wx.showToast({
        title: registration.status === REGISTRATION_STATUS.WAITLISTED ? '已加入候补' : '报名成功',
        icon: 'none',
      });
      await this.load(true);
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '报名失败，请稍后重试'), icon: 'none' });
      if (shouldRefreshAfterConflict(error)) {
        await this.load(true);
      }
    } finally {
      this.setData({ signupBusy: false });
    }
  },

  /** 与 C 端一致：先签一次性票据；个人主体不支持 web-view，复制链接由浏览器打开。 */
  async onAdminTap() {
    if (!this.data.canEnterAdmin || this.data.adminBusy) {
      return;
    }
    this.setData({ adminBusy: true });
    let url = adminEntryUrl(null);
    let hint = '请在浏览器打开，并用手机号 + 密码登录后台。';
    try {
      const { ticket } = await issueAdminTicket();
      url = adminEntryUrl(ticket);
      hint = '链接 60 秒内有效，请在浏览器打开自动登录。';
    } catch {
      // 票据签发失败回退 B 端登录页，由后台自行登录（与 C 端一致）。
    }
    wx.setClipboardData({
      data: url,
      success: () => {
        wx.showModal({
          title: '管理后台',
          content: `链接已复制，${hint}`,
          showCancel: false,
          confirmText: '知道了',
        });
      },
      fail: () => {
        wx.showModal({
          title: '管理后台',
          content: `复制失败，请手动访问：${url}`,
          showCancel: false,
        });
      },
      complete: () => this.setData({ adminBusy: false }),
    });
  },

  goDetail(event: WechatMiniprogram.BaseEvent) {
    const id = event.currentTarget.dataset.id as number;
    void wx.navigateTo({ url: `/pages/match-detail/index?id=${id}` });
  },

  goSchedule() {
    void wx.switchTab({ url: '/pages/schedule/index' });
  },

  goTeam() {
    void wx.switchTab({ url: '/pages/team/index' });
  },

  goQuickEntry(event: WechatMiniprogram.BaseEvent) {
    const url = event.currentTarget.dataset.url as string;
    void wx.navigateTo({ url });
  },
});
