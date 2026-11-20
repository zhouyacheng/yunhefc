import type { EventTimeline, MatchDetail } from '../../types/api';
import {
  buildEventRows,
  buildMatchDetailHeader,
  type EventRowView,
  type MatchDetailHeaderView,
} from '../../utils/match-detail-view';
import { ApiError, request } from '../../utils/request';

let requestSeq = 0;
let currentDetail: MatchDetail | null = null;

Page({
  data: {
    id: 0,
    loading: true,
    error: '',
    notFound: false,
    header: null as MatchDetailHeaderView | null,
    events: [] as EventRowView[],
    eventsLoading: false,
    eventsError: '',
  },

  onLoad(query: Record<string, string | undefined>) {
    const id = Number(query.id || 0);
    this.setData({ id });
    if (!id) {
      this.setData({ loading: false, notFound: true });
      return;
    }
    void this.load();
  },

  onPullDownRefresh() {
    const loading = this.load();
    void loading.then(() => wx.stopPullDownRefresh());
  },

  onUnload() {
    requestSeq += 1;
    currentDetail = null;
  },

  async load(): Promise<void> {
    const seq = ++requestSeq;
    this.setData({
      loading: true,
      error: '',
      notFound: false,
      header: null,
      events: [],
      eventsLoading: false,
      eventsError: '',
    });
    try {
      const detail = await request<MatchDetail>('GET', `/matches/${this.data.id}`);
      if (seq !== requestSeq) {
        return;
      }
      currentDetail = detail;
      this.setData({
        loading: false,
        header: buildMatchDetailHeader(detail, detail.derivedScore ?? null),
      });
      void this.loadEvents(detail, seq);
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      const apiError = error instanceof ApiError ? error : null;
      if (apiError?.statusCode === 404) {
        this.setData({ loading: false, notFound: true });
        return;
      }
      this.setData({
        loading: false,
        error: apiError?.message || '加载失败，请稍后重试',
      });
    }
  },

  async loadEvents(detail: MatchDetail, seq: number): Promise<void> {
    this.setData({ eventsLoading: true, eventsError: '' });
    try {
      const timeline = await request<EventTimeline>('GET', `/matches/${this.data.id}/events`);
      if (seq !== requestSeq) {
        return;
      }
      this.setData({
        events: buildEventRows(timeline.items, detail),
        header: buildMatchDetailHeader(
          detail,
          timeline.derivedScore ?? detail.derivedScore ?? null,
        ),
      });
    } catch (error) {
      if (seq !== requestSeq) {
        return;
      }
      this.setData({ eventsError: (error as Error).message || '事件加载失败，请稍后重试' });
    } finally {
      if (seq === requestSeq) {
        this.setData({ eventsLoading: false });
      }
    }
  },

  retry() {
    void this.load();
  },

  retryEvents() {
    if (!currentDetail) {
      return;
    }
    void this.loadEvents(currentDetail, ++requestSeq);
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
