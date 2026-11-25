import { listActivities } from '../../utils/api-client';
import {
  ACTIVITY_SCOPES,
  ACTIVITY_STATUS_OPTIONS,
  ACTIVITY_TYPE_OPTIONS,
  activityFeedKey,
  activityListQuery,
  DEFAULT_ACTIVITY_FILTERS,
  DEFAULT_ACTIVITY_KEY,
  parseActivityFeedKey,
  type ActivityFilters,
} from '../../utils/activity-query';
import { buildActivityCard, type ActivityCardView } from '../../utils/activity-view';
import { subscribe } from '../../utils/invalidation';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';

const PAGE_SIZE = 10;

let feed: PagedFeed<ActivityCardView> | null = null;
let unloading = false;
let lastError = '';
const activities = subscribe('activities');

function emptyTextOf(scope: ActivityFilters['scope']): string {
  return scope === 'latest' ? '暂无最近活动' : '暂无活动';
}

Page({
  data: {
    feed: emptyPagedFeedState<ActivityCardView>(
      DEFAULT_ACTIVITY_KEY,
    ) as PagedFeedState<ActivityCardView>,
    scopeOptions: ACTIVITY_SCOPES,
    scope: DEFAULT_ACTIVITY_FILTERS.scope,
    typeOptions: ACTIVITY_TYPE_OPTIONS,
    typeIndex: 0,
    statusOptions: ACTIVITY_STATUS_OPTIONS,
    statusIndex: 0,
    emptyText: emptyTextOf(DEFAULT_ACTIVITY_FILTERS.scope),
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<ActivityCardView>({
      pageSize: PAGE_SIZE,
      initialKey: DEFAULT_ACTIVITY_KEY,
      fetchPage: async ({ key, page, pageSize }) => {
        const result = await listActivities({
          ...activityListQuery(parseActivityFeedKey(key)),
          page,
          pageSize,
        });
        return {
          ...result,
          items: result.items.map((item) => buildActivityCard(item, Date.now())),
        };
      },
      onChange: (state) => {
        if (unloading) {
          return;
        }
        this.setData({ feed: state });
        if (state.error && state.error !== lastError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        lastError = state.error;
      },
    });
    void feed.load();
  },

  onShow() {
    // 详情页报名/取消后失效本资源；保留已加载的分页位置，只重载当前筛选。
    if (activities.consume()) {
      void feed?.load();
    }
  },

  onPullDownRefresh() {
    void (feed?.load() ?? Promise.resolve()).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    void feed?.loadMore();
  },

  onUnload() {
    unloading = true;
    feed?.reset();
    feed = null;
  },

  selectScope(event: WechatMiniprogram.BaseEvent) {
    const scope = String(event.currentTarget.dataset.scope ?? 'all') as ActivityFilters['scope'];
    if (scope === this.data.scope) {
      return;
    }
    this.setData({ scope, emptyText: emptyTextOf(scope) });
    this.applyFilters(scope, this.data.typeIndex, this.data.statusIndex);
  },

  onTypeChange(event: WechatMiniprogram.PickerChange) {
    const typeIndex = Number(event.detail.value);
    this.setData({ typeIndex });
    this.applyFilters(this.data.scope, typeIndex, this.data.statusIndex);
  },

  onStatusChange(event: WechatMiniprogram.PickerChange) {
    const statusIndex = Number(event.detail.value);
    this.setData({ statusIndex });
    this.applyFilters(this.data.scope, this.data.typeIndex, statusIndex);
  },

  applyFilters(scope: ActivityFilters['scope'], typeIndex: number, statusIndex: number) {
    const type = ACTIVITY_TYPE_OPTIONS[typeIndex]?.value ?? '';
    const status = ACTIVITY_STATUS_OPTIONS[statusIndex]?.value ?? '';
    const key = activityFeedKey({ scope, type, status });
    if (key === feed?.state.key) {
      return;
    }
    void wx.pageScrollTo({ scrollTop: 0 });
    feed?.setKey(key);
  },

  retry() {
    void feed?.load();
  },

  goDetail(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (!id) {
      return;
    }
    void wx.navigateTo({ url: `/pages/activity-detail/index?id=${id}` });
  },
});
