import { currentShanghaiMonth } from '../../utils/datetime';
import { filterDrawerHandlers } from '../../utils/filter-drawer';
import { listMatches } from '../../utils/api-client';
import {
  TIME_FILTER_MAX,
  TIME_FILTER_MIN,
  patchMonth,
  patchRange,
  rangeError,
} from '../../utils/time-filter';
import { buildMatchCard, type MatchCardView } from '../../utils/match-view';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';
import {
  DEFAULT_PAGE_SIZE,
  STATUS_OPTIONS,
  TYPE_OPTIONS,
  emptyScheduleFilters,
  fromScheduleForm,
  sameForm,
  scheduleFeedKey,
  toAppliedForm,
  toScheduleForm,
  type ScheduleForm,
} from '../../utils/schedule-query';

const AUTO_APPLY_DELAY = 300;

type ScheduleFormExtras = Partial<{
  typeIndex: number;
  statusIndex: number;
  monthPickerValue: string;
  rangeDraftStart: string;
  rangeDraftEnd: string;
  rangeOpen: boolean;
  filtersOpen: boolean;
}>;

let feed: PagedFeed<MatchCardView> | null = null;
let unloading = false;
let autoTimer: ReturnType<typeof setTimeout> | undefined;
let lastError = '';
/** 最近一次失败请求的语义，决定页脚重试是重载第 1 页还是续拉下一页。 */
let lastAction: 'reload' | 'more' = 'reload';

function cancelAutoApply(): void {
  if (autoTimer !== undefined) {
    clearTimeout(autoTimer);
    autoTimer = undefined;
  }
}

Page({
  ...filterDrawerHandlers,

  data: {
    feed: emptyPagedFeedState<MatchCardView>('') as PagedFeedState<MatchCardView>,
    form: toScheduleForm(emptyScheduleFilters()) as ScheduleForm,
    appliedForm: toAppliedForm(emptyScheduleFilters()) as ScheduleForm,
    typeOptions: TYPE_OPTIONS,
    statusOptions: STATUS_OPTIONS,
    typeIndex: 0,
    statusIndex: 0,
    venueVisible: true,
    timeFilterMin: TIME_FILTER_MIN,
    timeFilterMax: TIME_FILTER_MAX,
    monthPickerValue: `${toScheduleForm(emptyScheduleFilters()).month}-01`,
    rangeDraftStart: '',
    rangeDraftEnd: '',
    rangeOpen: false,
    filtersOpen: false,
  },

  onLoad() {
    unloading = false;
    const filters = emptyScheduleFilters();
    const form = toScheduleForm(filters);
    this.setData({
      form,
      appliedForm: toAppliedForm(filters),
      monthPickerValue: `${form.month}-01`,
    });

    feed = createPagedFeed<MatchCardView>({
      pageSize: DEFAULT_PAGE_SIZE,
      initialKey: scheduleFeedKey(filters),
      // appliedForm 是筛选的唯一来源；feed 只在 key 变化时重载，两者同步更新。
      fetchPage: async ({ page, pageSize }) => {
        const result = await listMatches({
          filters: fromScheduleForm(this.data.appliedForm),
          page,
          pageSize,
        });
        return { ...result, items: result.items.map(buildMatchCard) };
      },
      onChange: (state) => {
        if (unloading) {
          return;
        }
        this.setData({ feed: state });
        // 已有列表时刷新失败只 toast；首屏失败由空态展示 error。
        if (state.error && state.error !== lastError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        lastError = state.error;
      },
    });
    void feed.load();
  },

  onPullDownRefresh() {
    lastAction = 'reload';
    const loading = feed?.load();
    void (loading ?? Promise.resolve()).then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    this.loadMore();
  },

  onUnload() {
    cancelAutoApply();
    unloading = true;
    feed?.reset();
    feed = null;
  },

  patchForm(patch: Partial<ScheduleForm>, extra: ScheduleFormExtras = {}) {
    const form = { ...(this.data.form as ScheduleForm), ...patch };
    this.setData({ form, ...extra });
    this.scheduleAutoApply(form);
  },

  scheduleAutoApply(form: ScheduleForm) {
    cancelAutoApply();
    if (sameForm(form, this.data.appliedForm as ScheduleForm)) {
      return;
    }
    autoTimer = setTimeout(() => {
      autoTimer = undefined;
      this.applyFilters(form);
    }, AUTO_APPLY_DELAY);
  },

  applyNow() {
    cancelAutoApply();
    this.applyFilters(this.data.form as ScheduleForm);
  },

  applyFilters(form: ScheduleForm) {
    const filters = fromScheduleForm(form);
    const key = scheduleFeedKey(filters);
    this.setData({ appliedForm: { ...form } });
    if (key === feed?.state.key) {
      return;
    }
    lastAction = 'reload';
    void wx.pageScrollTo({ scrollTop: 0 });
    feed?.setKey(key);
  },

  onTypeChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const option = TYPE_OPTIONS[index];
    this.patchForm({ type: option ? option.value : '' }, { typeIndex: index });
  },

  onStatusChange(event: WechatMiniprogram.PickerChange) {
    const index = Number(event.detail.value);
    const option = STATUS_OPTIONS[index];
    this.patchForm({ status: option ? option.value : '' }, { statusIndex: index });
  },

  onVenueInput(event: WechatMiniprogram.Input) {
    this.patchForm({ venue: event.detail.value });
  },

  onVenueConfirm() {
    this.applyNow();
  },

  onMonthChange(event: WechatMiniprogram.PickerChange) {
    const month = String(event.detail.value).slice(0, 7);
    this.patchForm(patchMonth(month), {
      monthPickerValue: `${month}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
  },

  clearMonth() {
    this.patchForm({ month: '' }, { monthPickerValue: `${currentShanghaiMonth()}-01` });
  },

  /** 清空后原生 picker 默认停在当月，重复确认不会触发 change；提供显式恢复入口。 */
  selectCurrentMonth() {
    const month = currentShanghaiMonth();
    this.patchForm(patchMonth(month), { monthPickerValue: `${month}-01` });
  },

  openRange() {
    const form = this.data.form as ScheduleForm;
    this.setData({
      rangeOpen: true,
      rangeDraftStart: form.startDate,
      rangeDraftEnd: form.endDate,
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
    this.patchForm(patchRange(startDate, endDate), { rangeOpen: false });
  },

  clearRange() {
    this.patchForm(
      { startDate: '', endDate: '' },
      { rangeOpen: false, rangeDraftStart: '', rangeDraftEnd: '' },
    );
  },

  resetFilters() {
    cancelAutoApply();
    const filters = emptyScheduleFilters();
    const form = toScheduleForm(filters);
    const key = scheduleFeedKey(filters);
    this.setData({
      form,
      appliedForm: toAppliedForm(filters),
      typeIndex: 0,
      statusIndex: 0,
      monthPickerValue: `${form.month}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    void wx.pageScrollTo({ scrollTop: 0 });
    if (!feed) {
      return;
    }
    lastAction = 'reload';
    if (key === feed.state.key) {
      if (!feed.state.loaded || feed.state.error || feed.state.items.length === 0) {
        void feed.load();
      }
      return;
    }
    feed.setKey(key);
  },

  retry() {
    lastAction = 'reload';
    void feed?.load();
  },

  loadMore() {
    lastAction = 'more';
    void feed?.loadMore();
  },

  retryFooter() {
    if (lastAction === 'more') {
      this.loadMore();
      return;
    }
    this.retry();
  },

  goDetail(event: WechatMiniprogram.BaseEvent) {
    const id = event.currentTarget.dataset.id as number;
    void wx.navigateTo({ url: `/pages/match-detail/index?id=${id}` });
  },
});
