import type { MatchSummary, Page } from '../../types/api';
import { currentShanghaiMonth } from '../../utils/datetime';
import { buildMatchCard, type MatchCardView } from '../../utils/home-view';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';
import { request } from '../../utils/request';
import {
  DEFAULT_PAGE_SIZE,
  STATUS_OPTIONS,
  TYPE_OPTIONS,
  emptyScheduleFilters,
  fromScheduleForm,
  sameForm,
  scheduleFeedKey,
  scheduleRequestPath,
  toScheduleForm,
  type ScheduleForm,
} from '../../utils/schedule-query';

const AUTO_APPLY_DELAY = 300;

let feed: PagedFeed<MatchCardView> | null = null;
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
      fetchPage: async ({ key, page, pageSize }) => {
        const result = await request<Page<MatchSummary>>(
          'GET',
          scheduleRequestPath(key, page, pageSize),
        );
        return { ...result, items: result.items.map(buildMatchCard) };
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
    feed?.reset();
    feed = null;
  },

  openFilters() {
    this.setData({ filtersOpen: true });
  },

  closeFilters() {
    this.setData({ filtersOpen: false });
  },

  patchForm(patch: Partial<ScheduleForm>, extra: Record<string, unknown> = {}) {
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
    const key = scheduleFeedKey(fromScheduleForm(form));
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
