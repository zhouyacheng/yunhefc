import type { ActivityStatus, ActivityType } from '../../types/api';
import { listActivities, type ActivityScope } from '../../utils/api-client';
import { buildActivityCard, type ActivityCardView } from '../../utils/activity-view';
import { subscribe } from '../../utils/invalidation';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';

const PAGE_SIZE = 10;

interface ScopeOption {
  key: ActivityScope;
  label: string;
}

interface FilterOption<T> {
  value: T | '';
  label: string;
}

const SCOPES: ScopeOption[] = [
  { key: 'latest', label: '最近活动' },
  { key: 'history', label: '历史' },
  { key: 'all', label: '全部' },
];

const TYPE_OPTIONS: FilterOption<ActivityType>[] = [
  { value: '', label: '全部类型' },
  { value: 0, label: '聚餐' },
  { value: 1, label: '年会' },
  { value: 2, label: '其他' },
];

const STATUS_OPTIONS: FilterOption<ActivityStatus>[] = [
  { value: '', label: '全部状态' },
  { value: 0, label: '报名中' },
  { value: 1, label: '报名截止' },
  { value: 2, label: '已结束' },
  { value: 3, label: '已取消' },
];

let feed: PagedFeed<ActivityCardView> | null = null;
let unloading = false;
let lastError = '';
const activities = subscribe('activities');

function feedKey(
  scope: ActivityScope,
  type: FilterOption<ActivityType>['value'],
  status: FilterOption<ActivityStatus>['value'],
): string {
  return `${scope}|${type}|${status}`;
}

function emptyTextOf(scope: ActivityScope): string {
  if (scope === 'latest') {
    return '暂无最近活动';
  }
  return scope === 'history' ? '暂无历史活动' : '暂无活动';
}

Page({
  data: {
    feed: emptyPagedFeedState<ActivityCardView>('all||') as PagedFeedState<ActivityCardView>,
    scopeOptions: SCOPES,
    scope: 'all' as ActivityScope,
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    statusOptions: STATUS_OPTIONS,
    statusIndex: 0,
    emptyText: emptyTextOf('all'),
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<ActivityCardView>({
      pageSize: PAGE_SIZE,
      initialKey: feedKey('all', '', ''),
      fetchPage: async ({ key, page, pageSize }) => {
        const [scope, type, status] = key.split('|');
        const result = await listActivities({
          scope: scope as ActivityScope,
          type: type === '' ? undefined : (Number(type) as ActivityType),
          status: status === '' ? undefined : (Number(status) as ActivityStatus),
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
    const scope = String(event.currentTarget.dataset.scope ?? 'all') as ActivityScope;
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

  applyFilters(scope: ActivityScope, typeIndex: number, statusIndex: number) {
    const type = TYPE_OPTIONS[typeIndex]?.value ?? '';
    const status = STATUS_OPTIONS[statusIndex]?.value ?? '';
    const key = feedKey(scope, type, status);
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
