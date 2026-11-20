import type { AuthUser, MatchSummary, Page } from '../../types/api';
import { formatDateYmd, formatHourMinute } from '../../utils/datetime';
import { request } from '../../utils/request';
import { getUser } from '../../utils/session';

interface Row extends MatchSummary {
  dateText: string;
  timeText: string;
  scoreText: string;
}

const PAGE_SIZE = 10;

function decorate(match: MatchSummary): Row {
  return {
    ...match,
    dateText: formatDateYmd(match.startAt) || match.startAt,
    timeText: formatHourMinute(match.startAt),
    scoreText:
      match.scoreHome === null || match.scoreAway === null
        ? ''
        : `${match.scoreHome} : ${match.scoreAway}`,
  };
}

Page({
  data: {
    scope: 'upcoming' as 'upcoming' | 'history',
    rows: [] as Row[],
    loading: false,
    error: '',
    page: 1,
    totalPages: 1,
    user: null as AuthUser | null,
  },

  onShow() {
    this.setData({ user: getUser() });
    void this.load(true);
  },

  onPullDownRefresh() {
    void this.load(true).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (!this.data.loading && this.data.page < this.data.totalPages) {
      void this.load(false);
    }
  },

  switchScope(event: WechatMiniprogram.BaseEvent) {
    const scope = event.currentTarget.dataset.scope as 'upcoming' | 'history';
    if (scope === this.data.scope) {
      return;
    }
    this.setData({ scope, rows: [], page: 1, totalPages: 1 });
    void this.load(true);
  },

  async load(reset: boolean) {
    if (this.data.loading) {
      return;
    }
    const page = reset ? 1 : this.data.page + 1;
    this.setData({ loading: true, error: '' });
    try {
      const result = await request<Page<MatchSummary>>(
        'GET',
        `/matches?scope=${this.data.scope}&page=${page}&pageSize=${PAGE_SIZE}`,
      );
      const rows = result.items.map(decorate);
      this.setData({
        rows: reset ? rows : this.data.rows.concat(rows),
        page: result.page,
        totalPages: result.totalPages,
      });
    } catch (error) {
      this.setData({ error: (error as Error).message || '加载失败，请稍后重试' });
    } finally {
      this.setData({ loading: false });
    }
  },

  goDetail(event: WechatMiniprogram.BaseEvent) {
    const id = event.currentTarget.dataset.id as number;
    void wx.navigateTo({ url: `/pages/match-detail/index?id=${id}` });
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },
});
