import type { MatchDetail, MatchSummary, Page } from '../../types/api';
import { WEB_URL } from '../../utils/config';
import {
  buildHero,
  buildRecent,
  HOME_FEATURES,
  type HeroView,
  type RecentView,
} from '../../utils/home-view';
import { request } from '../../utils/request';
import { getPlayer, getUser, sessionReady } from '../../utils/session';

let requestSeq = 0;
let heroDetail: MatchDetail | null = null;
let hasLoaded = false;
let ctaTimer: ReturnType<typeof setInterval> | undefined;

function showWebOnly(): void {
  wx.showModal({
    title: '报名请使用网页版',
    content: `小程序暂不支持报名/候补，请前往 ${WEB_URL} 登录后操作。`,
    confirmText: '复制网址',
    cancelText: '知道了',
    success: (res) => {
      if (res.confirm) {
        wx.setClipboardData({ data: WEB_URL });
      }
    },
  });
}

interface TimerPage {
  syncCta: () => void;
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
    recent: [] as RecentView[],
    features: HOME_FEATURES,
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
    await this.load(true);
  },

  async load(silent: boolean) {
    const seq = ++requestSeq;
    if (!silent) {
      this.setData({ loading: true, error: '' });
    }
    try {
      const page = await request<Page<MatchSummary>>(
        'GET',
        '/matches?sort=startAt:desc&pageSize=4',
      );
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

      const detail = await request<MatchDetail>('GET', `/matches/${first.id}`);
      if (seq !== requestSeq) {
        return;
      }
      heroDetail = detail;
      hasLoaded = true;
      this.setData({
        hero: buildHero(detail, getUser(), getPlayer(), Date.now()),
        recent: recent.map(buildRecent),
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
    this.setData({ hero: buildHero(heroDetail, getUser(), getPlayer(), Date.now()) });
  },

  retry() {
    void this.load(false);
  },

  onCtaTap() {
    const hero = this.data.hero;
    if (!hero || hero.cta.disabled) {
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
    if (hero.cta.action === 'register') {
      showWebOnly();
    }
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
});
