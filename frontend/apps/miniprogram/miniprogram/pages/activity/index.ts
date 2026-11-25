import { listActivities } from '../../utils/api-client';
import {
  ACTIVITY_SCOPES,
  ACTIVITY_STATUS_OPTIONS,
  ACTIVITY_TYPE_OPTIONS,
  activityFeedKey,
  activityFiltersFromState,
  activityListQuery,
  DEFAULT_ACTIVITY_FILTERS,
  DEFAULT_ACTIVITY_KEY,
  isActivityScope,
  parseActivityFeedKey,
  type ActivityFilters,
} from '../../utils/activity-query';
import { buildActivityCard, type ActivityCardView } from '../../utils/activity-view';
import { currentShanghaiMonth } from '../../utils/datetime';
import { subscribe } from '../../utils/invalidation';
import {
  TIME_FILTER_MAX,
  TIME_FILTER_MIN,
  patchMonth,
  patchRange,
  rangeError,
} from '../../utils/time-filter';
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
  if (scope === 'latest') {
    return '暂无最近活动';
  }
  if (scope === 'finished') {
    return '暂无已结束活动';
  }
  return '暂无活动';
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
    venueVisible: false,
    month: '',
    startDate: '',
    endDate: '',
    timeFilterMin: TIME_FILTER_MIN,
    timeFilterMax: TIME_FILTER_MAX,
    monthPickerDraft: `${currentShanghaiMonth()}-01`,
    rangeDraftStart: '',
    rangeDraftEnd: '',
    rangeOpen: false,
    filtersOpen: false,
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

  openFilters() {
    this.setData({ filtersOpen: true });
  },

  closeFilters() {
    this.setData({ filtersOpen: false });
  },

  selectScope(event: WechatMiniprogram.BaseEvent) {
    const scope = event.currentTarget.dataset.scope;
    if (!isActivityScope(scope)) {
      return;
    }
    if (scope === this.data.scope) {
      // 当前范围加载失败时，重复点击分段即重试。
      if (feed?.state.error) {
        void feed.load();
      }
      return;
    }
    this.setData({ scope, emptyText: emptyTextOf(scope) });
    this.applyFilters();
  },

  /** 只清空抽屉内的条件，保留页面顶部分段已选的范围。 */
  resetFilters() {
    const filters: ActivityFilters = {
      ...DEFAULT_ACTIVITY_FILTERS,
      scope: this.data.scope as ActivityFilters['scope'],
    };
    const key = activityFeedKey(filters);
    this.setData({
      typeIndex: 0,
      statusIndex: 0,
      ...patchMonth(''),
      monthPickerDraft: `${currentShanghaiMonth()}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    void wx.pageScrollTo({ scrollTop: 0 });
    if (!feed) {
      return;
    }
    if (key === feed.state.key) {
      if (!feed.state.loaded || feed.state.error || feed.state.items.length === 0) {
        void feed.load();
      }
      return;
    }
    feed.setKey(key);
  },

  onTypeChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ typeIndex: Number(event.detail.value) });
    this.applyFilters();
  },

  onStatusChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ statusIndex: Number(event.detail.value) });
    this.applyFilters();
  },

  onMonthChange(event: WechatMiniprogram.PickerChange) {
    const month = String(event.detail.value).slice(0, 7);
    this.setData({
      ...patchMonth(month),
      monthPickerDraft: `${month}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    this.applyFilters();
  },

  clearMonth() {
    this.setData({ month: '', monthPickerDraft: `${currentShanghaiMonth()}-01` });
    this.applyFilters();
  },

  /** 清空后原生 picker 默认停在当月，重复确认不会触发 change；提供显式恢复入口。 */
  selectCurrentMonth() {
    const month = currentShanghaiMonth();
    this.setData({
      ...patchMonth(month),
      monthPickerDraft: `${month}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    this.applyFilters();
  },

  openRange() {
    this.setData({
      rangeOpen: true,
      rangeDraftStart: this.data.startDate,
      rangeDraftEnd: this.data.endDate,
    });
  },

  closeRange() {
    this.setData({ rangeOpen: false });
  },

  onRangeStartChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ rangeDraftStart: String(event.detail.value) });
  },

  onRangeEndChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ rangeDraftEnd: String(event.detail.value) });
  },

  confirmRange() {
    const { rangeDraftStart: startDate, rangeDraftEnd: endDate } = this.data;
    const error = rangeError(startDate, endDate);
    if (error) {
      wx.showToast({ title: error, icon: 'none' });
      return;
    }
    this.setData({ ...patchRange(startDate, endDate), rangeOpen: false });
    this.applyFilters();
  },

  clearRange() {
    this.setData({
      startDate: '',
      endDate: '',
      rangeDraftStart: '',
      rangeDraftEnd: '',
      rangeOpen: false,
    });
    this.applyFilters();
  },

  applyFilters() {
    const filters: ActivityFilters = activityFiltersFromState({
      scope: this.data.scope as ActivityFilters['scope'],
      typeIndex: this.data.typeIndex,
      statusIndex: this.data.statusIndex,
      month: this.data.month,
      startDate: this.data.startDate,
      endDate: this.data.endDate,
    });
    const key = activityFeedKey(filters);
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
