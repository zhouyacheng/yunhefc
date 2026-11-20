import type { AuthUser, MatchSummary, Page } from '../../types/api';
import { formatDateYmd, formatHourMinute } from '../../utils/datetime';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
} from '../../utils/paged-feed';
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

let feed: PagedFeed<Row> | null = null;

Page({
  data: {
    feed: emptyPagedFeedState<Row>('upcoming'),
    user: null as AuthUser | null,
  },

  onLoad() {
    let lastError = '';
    feed = createPagedFeed<Row>({
      pageSize: PAGE_SIZE,
      initialKey: 'upcoming',
      fetchPage: async ({ key, page, pageSize }) => {
        const result = await request<Page<MatchSummary>>(
          'GET',
          `/matches?scope=${key}&page=${page}&pageSize=${pageSize}`,
        );
        return { ...result, items: result.items.map(decorate) };
      },
      onChange: (state) => {
        this.setData({ feed: state });
        // 已有列表时刷新失败只 toast；首屏失败由空态展示 error。
        if (state.error && state.error !== lastError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        lastError = state.error;
      },
    });
  },

  onShow() {
    this.setData({ user: getUser() });
    void feed?.load();
  },

  onUnload() {
    feed = null;
  },

  onPullDownRefresh() {
    const loading = feed?.load();
    void (loading ?? Promise.resolve()).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    void feed?.loadMore();
  },

  switchScope(event: WechatMiniprogram.BaseEvent) {
    const scope = event.currentTarget.dataset.scope as string;
    feed?.setKey(scope);
  },

  goDetail(event: WechatMiniprogram.BaseEvent) {
    const id = event.currentTarget.dataset.id as number;
    void wx.navigateTo({ url: `/pages/match-detail/index?id=${id}` });
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },
});
