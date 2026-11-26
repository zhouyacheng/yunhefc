import type { LedgerCategory, LedgerType } from '../../types/api';
import {
  fetchLedgerSummary,
  listLedgerCategories,
  listLedgerEntries,
} from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import { currentShanghaiMonth } from '../../utils/datetime';
import { filterDrawerHandlers } from '../../utils/filter-drawer';
import {
  ALL_CATEGORIES_OPTION,
  categoryOptionsFor,
  DEFAULT_LEDGER_KEY,
  ledgerFilterKey,
  ledgerFilterQuery,
  parseLedgerFilterKey,
  type LedgerFilterState,
} from '../../utils/ledger-query';
import {
  decorateLedgerEntry,
  ledgerSummaryView,
  type LedgerEntryView,
  type LedgerSummaryView,
} from '../../utils/ledger-view';
import { LEDGER_VIEW_ANY, hasPermission } from '../../utils/permissions';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';
import { getPermissions, getUser, sessionReady } from '../../utils/session';
import {
  TIME_FILTER_MAX,
  TIME_FILTER_MIN,
  patchMonth,
  patchRange,
  rangeError,
} from '../../utils/time-filter';

const PAGE_SIZE = 10;

interface FilterOption {
  value: LedgerType | '';
  label: string;
}

const TYPE_OPTIONS: FilterOption[] = [
  { value: '', label: '全部方向' },
  { value: 0, label: '收入' },
  { value: 1, label: '支出' },
];

function typeValue(index: number): LedgerType | '' {
  return TYPE_OPTIONS[index]?.value ?? '';
}

let feed: PagedFeed<LedgerEntryView> | null = null;
let summarySeq = 0;
let unloading = false;
let lastFeedError = '';

function filterStateOf(data: {
  typeIndex: number;
  categoryOptions: { value: number }[];
  categoryIndex: number;
  month: string;
  startDate: string;
  endDate: string;
}): LedgerFilterState {
  return {
    type: typeValue(data.typeIndex),
    categoryId: data.categoryOptions[data.categoryIndex]?.value ?? 0,
    month: data.month,
    startDate: data.startDate,
    endDate: data.endDate,
  };
}

Page({
  ...filterDrawerHandlers,

  data: {
    ready: false,
    authenticated: false,
    authorized: false,
    categoriesLoaded: false,
    feed: emptyPagedFeedState<LedgerEntryView>(
      DEFAULT_LEDGER_KEY,
    ) as PagedFeedState<LedgerEntryView>,
    summary: null as LedgerSummaryView | null,
    summaryLoading: false,
    summaryError: '',
    summaryFiltered: false,
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    categories: [] as LedgerCategory[],
    categoryOptions: [ALL_CATEGORIES_OPTION],
    categoryIndex: 0,
    month: '',
    startDate: '',
    endDate: '',
    timeFilterMin: TIME_FILTER_MIN,
    timeFilterMax: TIME_FILTER_MAX,
    monthDraft: `${currentShanghaiMonth()}-01`,
    rangeDraftStart: '',
    rangeDraftEnd: '',
    rangeOpen: false,
    filtersOpen: false,
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<LedgerEntryView>({
      pageSize: PAGE_SIZE,
      initialKey: DEFAULT_LEDGER_KEY,
      fetchPage: async ({ key, page, pageSize }) => {
        const filter = ledgerFilterQuery(parseLedgerFilterKey(key));
        const result = await listLedgerEntries({ ...filter, page, pageSize });
        return { ...result, items: result.items.map(decorateLedgerEntry) };
      },
      onChange: (state) => {
        if (unloading) {
          return;
        }
        this.setData({ feed: state });
        if (state.error && state.error !== lastFeedError && state.items.length > 0) {
          wx.showToast({ title: state.error, icon: 'none' });
        }
        lastFeedError = state.error;
      },
    });
  },

  onShow() {
    void this.bootstrap();
  },

  onReachBottom() {
    if (this.data.authorized) {
      void feed?.loadMore();
    }
  },

  onUnload() {
    unloading = true;
    summarySeq += 1;
    feed?.reset();
    feed = null;
    lastFeedError = '';
  },

  async bootstrap() {
    await sessionReady();
    const authenticated = !!getUser();
    const authorized = authenticated && hasPermission(getPermissions(), LEDGER_VIEW_ANY);
    this.setData({ ready: true, authenticated, authorized });
    if (!authorized || !feed) {
      return;
    }
    if (!this.data.categoriesLoaded) {
      void this.loadCategories();
    }
    // 只读页没有编辑入口：每次进入刷新第一页与汇总，避免长时间停留后看到旧数据。
    void feed.load();
    this.loadSummary(feed.state.key);
  },

  async loadCategories() {
    try {
      const categories = await listLedgerCategories();
      const selected = this.data.categoryOptions[this.data.categoryIndex]?.value ?? 0;
      const options = categoryOptionsFor(categories, typeValue(this.data.typeIndex));
      const index = options.findIndex((option) => option.value === selected);
      this.setData({
        categoriesLoaded: true,
        categories,
        categoryOptions: options,
        categoryIndex: index >= 0 ? index : 0,
      });
    } catch (error) {
      // 分类失败不阻断流水；下次进入页面重试。
      wx.showToast({ title: errorMessage(error, '分类加载失败'), icon: 'none' });
    }
  },

  async loadSummary(key: string) {
    const seq = ++summarySeq;
    this.setData({ summaryLoading: true, summaryError: '' });
    try {
      const summary = await fetchLedgerSummary(ledgerFilterQuery(parseLedgerFilterKey(key)));
      if (seq !== summarySeq) {
        return;
      }
      this.setData({
        summaryLoading: false,
        summary: ledgerSummaryView(summary),
        summaryFiltered: key !== DEFAULT_LEDGER_KEY,
      });
    } catch (error) {
      if (seq !== summarySeq) {
        return;
      }
      this.setData({
        summaryLoading: false,
        summaryError: errorMessage(error, '汇总加载失败，请稍后重试'),
      });
    }
  },

  /** 清空全部条件；抽屉保持打开，与活动列表一致。 */
  resetFilters() {
    this.setData({
      typeIndex: 0,
      categoryOptions: categoryOptionsFor(this.data.categories, ''),
      categoryIndex: 0,
      ...patchMonth(''),
      monthDraft: `${currentShanghaiMonth()}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    if (!feed) {
      return;
    }
    const key = ledgerFilterKey(filterStateOf(this.data));
    void wx.pageScrollTo({ scrollTop: 0 });
    if (key !== feed.state.key) {
      feed.setKey(key);
      this.loadSummary(key);
      return;
    }
    // 条件已是默认值：仅在列表为空或失败时重试，避免重置后停在空态。
    if (!feed.state.loaded || feed.state.error || feed.state.items.length === 0) {
      void feed.load();
    }
  },

  applyFilters() {
    if (!feed) {
      return;
    }
    const key = ledgerFilterKey(filterStateOf(this.data));
    if (key === feed.state.key) {
      return;
    }
    void wx.pageScrollTo({ scrollTop: 0 });
    feed.setKey(key);
    this.loadSummary(key);
  },

  onTypeChange(event: WechatMiniprogram.PickerChange) {
    const typeIndex = Number(event.detail.value);
    const selected = this.data.categoryOptions[this.data.categoryIndex]?.value ?? 0;
    const options = categoryOptionsFor(this.data.categories, typeValue(typeIndex));
    const index = options.findIndex((option) => option.value === selected);
    // 方向变化后保留仍兼容的分类，否则回到「全部分类」，避免空列表。
    this.setData({
      typeIndex,
      categoryOptions: options,
      categoryIndex: index >= 0 ? index : 0,
    });
    this.applyFilters();
  },

  onCategoryChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ categoryIndex: Number(event.detail.value) });
    this.applyFilters();
  },

  /** 月份与日期范围互斥：应用月份时清空范围及其草稿。 */
  applyMonth(month: string) {
    this.setData({
      ...patchMonth(month),
      monthDraft: `${month || currentShanghaiMonth()}-01`,
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    this.applyFilters();
  },

  onMonthChange(event: WechatMiniprogram.PickerChange) {
    this.applyMonth(String(event.detail.value).slice(0, 7));
  },

  clearMonth() {
    this.applyMonth('');
  },

  /** 清空后原生 picker 默认停在当月，重复确认不会触发 change；提供显式恢复入口。 */
  selectCurrentMonth() {
    this.applyMonth(currentShanghaiMonth());
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

  retrySummary() {
    this.loadSummary(feed?.state.key ?? DEFAULT_LEDGER_KEY);
  },

  retry() {
    void feed?.load();
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },
});
