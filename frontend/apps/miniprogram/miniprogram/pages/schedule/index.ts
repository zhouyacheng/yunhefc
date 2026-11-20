import { currentShanghaiMonth } from '../../utils/datetime';
import { listMatches } from '../../utils/api-client';
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
  toScheduleForm,
  type ScheduleForm,
} from '../../utils/schedule-query';

const AUTO_APPLY_DELAY = 300;

type ScheduleFormExtras = Partial<{
  typeIndex: number;
  statusIndex: number;
  monthPickerValue: string;
  rangeStart: string;
  rangeEnd: string;
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
  data: {
    feed: emptyPagedFeedState<MatchCardView>('') as PagedFeedState<MatchCardView>,
    form: toScheduleForm(emptyScheduleFilters()) as ScheduleForm,
    appliedForm: toScheduleForm(emptyScheduleFilters()) as ScheduleForm,
    typeOptions: TYPE_OPTIONS,
    statusOptions: STATUS_OPTIONS,
    typeIndex: 0,
    statusIndex: 0,
    monthPickerValue: `${toScheduleForm(emptyScheduleFilters()).month}-01`,
    rangeStart: '',
    rangeEnd: '',
    rangeOpen: false,
    filtersOpen: false,
  },

  onLoad() {
    unloading = false;
    const filters = emptyScheduleFilters();
    const form = toScheduleForm(filters);
    this.setData({
      form,
      appliedForm: { ...form },
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

  openFilters() {
    this.setData({ filtersOpen: true });
  },

  closeFilters() {
    this.setData({ filtersOpen: false });
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
    this.patchForm(
      { month, startDate: '', endDate: '' },
      { monthPickerValue: `${month}-01`, rangeStart: '', rangeEnd: '' },
    );
  },

  clearMonth() {
    this.patchForm({ month: '' }, { monthPickerValue: `${currentShanghaiMonth()}-01` });
  },

  /** 清空后原生 picker 默认停在当月，重复确认不会触发 change；提供显式恢复入口。 */
  selectCurrentMonth() {
    const month = currentShanghaiMonth();
    this.patchForm({ month, startDate: '', endDate: '' }, { monthPickerValue: `${month}-01` });
  },

  openRange() {
    const form = this.data.form as ScheduleForm;
    this.setData({
      rangeOpen: true,
      rangeStart: form.startDate,
      rangeEnd: form.endDate,
    });
  },

  closeRange() {
    this.setData({ rangeOpen: false });
  },

  onRangeStartChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ rangeStart: String(event.detail.value) });
  },

  onRangeEndChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ rangeEnd: String(event.detail.value) });
  },

  confirmRange() {
    const { rangeStart: startDate, rangeEnd: endDate } = this.data;
    if (!startDate || !endDate) {
      wx.showToast({ title: '请选择开始和结束日期', icon: 'none' });
      return;
    }
    if (startDate > endDate) {
      wx.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' });
      return;
    }
    this.patchForm({ month: '', startDate, endDate }, { rangeOpen: false });
  },

  clearRange() {
    this.patchForm(
      { startDate: '', endDate: '' },
      { rangeOpen: false, rangeStart: '', rangeEnd: '' },
    );
  },

  resetFilters() {
    cancelAutoApply();
    const filters = emptyScheduleFilters();
    const form = toScheduleForm(filters);
    const key = scheduleFeedKey(filters);
    this.setData({
      form,
      appliedForm: { ...form },
      typeIndex: 0,
      statusIndex: 0,
      monthPickerValue: `${form.month}-01`,
      rangeStart: '',
      rangeEnd: '',
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
