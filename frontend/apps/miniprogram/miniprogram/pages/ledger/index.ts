import type {
  LedgerCategory,
  LedgerDailyTotal,
  LedgerPeriod,
  LedgerStatistics,
  LedgerType,
} from '../../types/api';
import {
  fetchLedgerStatistics,
  fetchLedgerSummary,
  listLedgerCategories,
  listLedgerDailyTotals,
  listLedgerEntries,
} from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import {
  currentShanghaiDate,
  currentShanghaiMonth,
  formatMonthLabel,
  shiftMonth as shiftMonthValue,
} from '../../utils/datetime';
import { filterDrawerHandlers } from '../../utils/filter-drawer';
import {
  ALL_CATEGORIES_OPTION,
  categoryOptionsFor,
  ledgerFilterKey,
  ledgerFilterQuery,
  ledgerIsFiltered,
  ledgerStatisticsQuery,
  ledgerSummaryQuery,
  parseLedgerFilterKey,
  shiftStatsAnchor,
  type LedgerFilterState,
} from '../../utils/ledger-query';
import {
  decorateLedgerEntry,
  groupLedgerDays,
  ledgerStatisticsView,
  ledgerSummaryView,
  type LedgerDayGroupView,
  type LedgerEntryView,
  type LedgerStatsDirection,
  type LedgerStatisticsView,
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
import { TIME_FILTER_MAX, TIME_FILTER_MIN, patchRange, rangeError } from '../../utils/time-filter';

const PAGE_SIZE = 10;
const INITIAL_MONTH = currentShanghaiMonth();
const INITIAL_KEY = ledgerFilterKey({
  type: '',
  categoryId: 0,
  month: INITIAL_MONTH,
  startDate: '',
  endDate: '',
  keyword: '',
});

interface FilterOption {
  value: LedgerType | '';
  label: string;
}

const TYPE_OPTIONS: FilterOption[] = [
  { value: '', label: '全部方向' },
  { value: 0, label: '收入' },
  { value: 1, label: '支出' },
];

const STATS_PERIODS: { value: LedgerPeriod; label: string }[] = [
  { value: 'week', label: '按周' },
  { value: 'month', label: '按月' },
  { value: 'year', label: '按年' },
  { value: 'custom', label: '自定义时间' },
];

const STATS_DIRECTIONS: { value: LedgerStatsDirection; label: string }[] = [
  { value: 'expense', label: '支出' },
  { value: 'income', label: '收入' },
];

function typeValue(index: number): LedgerType | '' {
  return TYPE_OPTIONS[index]?.value ?? '';
}

function filterStateOf(data: {
  typeIndex: number;
  categoryOptions: { value: number }[];
  categoryIndex: number;
  month: string;
  startDate: string;
  endDate: string;
  keyword: string;
  customActive: boolean;
}): LedgerFilterState {
  return {
    type: typeValue(data.typeIndex),
    categoryId: data.categoryOptions[data.categoryIndex]?.value ?? 0,
    month: data.customActive ? '' : data.month,
    startDate: data.startDate,
    endDate: data.endDate,
    keyword: data.keyword,
  };
}

let feed: PagedFeed<LedgerEntryView> | null = null;
let summarySeq = 0;
let dailySeq = 0;
let statsSeq = 0;
let rawStats: LedgerStatistics | null = null;
let unloading = false;
let lastFeedError = '';
let lastSummaryKey: string | null = null;
let keywordTimer: ReturnType<typeof setTimeout> | undefined;

Page({
  ...filterDrawerHandlers,

  data: {
    ready: false,
    authenticated: false,
    authorized: false,
    categoriesLoaded: false,
    tab: 'detail' as 'detail' | 'stats',
    month: INITIAL_MONTH,
    monthLabel: formatMonthLabel(INITIAL_MONTH),
    customActive: false,
    rangeText: '',
    keyword: '',
    feed: emptyPagedFeedState<LedgerEntryView>(INITIAL_KEY) as PagedFeedState<LedgerEntryView>,
    groups: [] as LedgerDayGroupView[],
    dailyTotals: [] as LedgerDailyTotal[],
    summary: null as LedgerSummaryView | null,
    summaryLoading: false,
    summaryError: '',
    summaryFiltered: false,
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    categories: [] as LedgerCategory[],
    categoryOptions: [ALL_CATEGORIES_OPTION],
    categoryIndex: 0,
    startDate: '',
    endDate: '',
    timeFilterMin: TIME_FILTER_MIN,
    timeFilterMax: TIME_FILTER_MAX,
    rangeDraftStart: '',
    rangeDraftEnd: '',
    rangeOpen: false,
    /** 共用日期范围弹层：明细筛选或统计自定义时间。 */
    rangeMode: 'detail' as 'detail' | 'stats',
    filtersOpen: false,
    hideMonth: true,
    statsPeriods: STATS_PERIODS,
    statsPeriod: 'month' as LedgerPeriod,
    statsAnchor: currentShanghaiDate(),
    statsFrom: '',
    statsTo: '',
    statsDirections: STATS_DIRECTIONS,
    statsDirection: 'expense' as LedgerStatsDirection,
    stats: null as LedgerStatisticsView | null,
    statsLoading: false,
    statsError: '',
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<LedgerEntryView>({
      pageSize: PAGE_SIZE,
      initialKey: INITIAL_KEY,
      fetchPage: async ({ key, page, pageSize }) => {
        const filter = ledgerFilterQuery(parseLedgerFilterKey(key));
        const result = await listLedgerEntries({ ...filter, page, pageSize });
        return { ...result, items: result.items.map(decorateLedgerEntry) };
      },
      onChange: (state) => {
        if (unloading) {
          return;
        }
        this.setData({
          feed: state,
          groups: groupLedgerDays(state.items, this.data.dailyTotals),
        });
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
    if (this.data.authorized && this.data.tab === 'detail') {
      void feed?.loadMore();
    }
  },

  onUnload() {
    unloading = true;
    clearTimeout(keywordTimer);
    summarySeq += 1;
    dailySeq += 1;
    statsSeq += 1;
    feed?.reset();
    feed = null;
    rawStats = null;
    lastFeedError = '';
    lastSummaryKey = null;
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
    lastSummaryKey = null;
    void feed.load();
    this.loadAux(feed.state.key);
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

  /** 汇总与日小计随筛选键变化；关键字只进日小计，不进汇总。 */
  loadAux(key: string) {
    const state = parseLedgerFilterKey(key);
    const dailyRequest = ++dailySeq;
    this.setData({ summaryFiltered: ledgerIsFiltered(state) });

    // 关键字变化不重取汇总：汇总口径不含 keyword。
    const summaryKey = ledgerFilterKey({ ...state, keyword: '' });
    if (summaryKey !== lastSummaryKey) {
      const summaryRequest = ++summarySeq;
      lastSummaryKey = summaryKey;
      this.setData({ summaryLoading: true, summaryError: '' });
      fetchLedgerSummary(ledgerSummaryQuery(state))
        .then((summary) => {
          if (summaryRequest !== summarySeq || unloading) {
            return;
          }
          this.setData({ summaryLoading: false, summary: ledgerSummaryView(summary) });
        })
        .catch((error) => {
          if (summaryRequest !== summarySeq || unloading) {
            return;
          }
          // 失败后允许下次重试。
          lastSummaryKey = null;
          this.setData({
            summaryLoading: false,
            summaryError: errorMessage(error, '汇总加载失败，请稍后重试'),
          });
        });
    }

    listLedgerDailyTotals(ledgerFilterQuery(state))
      .then((totals) => {
        if (dailyRequest !== dailySeq || unloading) {
          return;
        }
        this.setData({
          dailyTotals: totals,
          groups: groupLedgerDays(this.data.feed.items, totals),
        });
      })
      .catch(() => {
        if (dailyRequest !== dailySeq || unloading) {
          return;
        }
        // 日小计失败不阻断流水：回退已加载流水之和。
        this.setData({
          dailyTotals: [],
          groups: groupLedgerDays(this.data.feed.items, []),
        });
      });
  },

  /** 条件变化后同步 feed key 与辅助数据；抽屉不自动关闭。 */
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
    this.loadAux(key);
  },

  /** 清空抽屉内条件；月份由页面标题承载，保持当前月，抽屉保持打开。 */
  resetFilters() {
    this.setData({
      typeIndex: 0,
      categoryOptions: categoryOptionsFor(this.data.categories, ''),
      categoryIndex: 0,
      keyword: '',
      customActive: false,
      rangeText: '',
      startDate: '',
      endDate: '',
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    this.applyFilters();
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

  onKeywordInput(event: WechatMiniprogram.Input) {
    this.setData({ keyword: String(event.detail.value ?? '') });
    clearTimeout(keywordTimer);
    keywordTimer = setTimeout(() => this.applyFilters(), 300);
  },

  onKeywordConfirm() {
    clearTimeout(keywordTimer);
    this.applyFilters();
  },

  /** 月份切换：清空自定义时间，回到整月口径。 */
  onMonthChange(event: WechatMiniprogram.PickerChange) {
    const month = String(event.detail.value).slice(0, 7);
    this.setData({
      month,
      monthLabel: formatMonthLabel(month),
      customActive: false,
      rangeText: '',
      startDate: '',
      endDate: '',
      rangeDraftStart: '',
      rangeDraftEnd: '',
    });
    this.applyFilters();
    this.syncStatsToMonth();
  },

  shiftMonth(event: WechatMiniprogram.TouchEvent) {
    if (this.data.customActive) {
      return;
    }
    const delta = Number(event.currentTarget.dataset.delta ?? 0);
    const next = shiftMonthValue(this.data.month, delta);
    if (next === this.data.month) {
      return;
    }
    this.setData({
      month: next,
      monthLabel: formatMonthLabel(next),
      startDate: '',
      endDate: '',
    });
    this.applyFilters();
    this.syncStatsToMonth();
  },

  /** 明细筛选与统计自定义时间共用同一个日期范围弹层，由 rangeMode 分发。 */
  openRange() {
    this.setData({
      rangeMode: 'detail',
      rangeOpen: true,
      rangeDraftStart: this.data.startDate,
      rangeDraftEnd: this.data.endDate,
    });
  },

  openStatsRange() {
    this.setData({
      rangeMode: 'stats',
      rangeOpen: true,
      rangeDraftStart: this.data.statsFrom,
      rangeDraftEnd: this.data.statsTo,
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
    const { rangeDraftStart: from, rangeDraftEnd: to, rangeMode } = this.data;
    const error = rangeError(from, to);
    if (error) {
      wx.showToast({ title: error, icon: 'none' });
      return;
    }
    if (rangeMode === 'stats') {
      this.setData({ statsFrom: from, statsTo: to, rangeOpen: false });
      void this.loadStats();
      return;
    }
    this.setData({
      ...patchRange(from, to),
      startDate: from,
      endDate: to,
      rangeOpen: false,
      customActive: true,
      rangeText: `${from} 至 ${to}`,
    });
    this.applyFilters();
  },

  clearRange() {
    if (this.data.rangeMode === 'stats') {
      // 保留自定义周期，清空后由页面引导重新选择，避免发出缺少 from/to 的请求。
      this.setData({ statsFrom: '', statsTo: '', rangeOpen: false });
      return;
    }
    this.setData({
      startDate: '',
      endDate: '',
      rangeDraftStart: '',
      rangeDraftEnd: '',
      rangeOpen: false,
      customActive: false,
      rangeText: '',
    });
    this.applyFilters();
  },

  switchTab(event: WechatMiniprogram.TouchEvent) {
    const tab = event.currentTarget.dataset.tab === 'stats' ? 'stats' : 'detail';
    this.setData({ tab });
    if (tab !== 'stats') {
      return;
    }
    if (this.data.statsPeriod === 'custom' && (!this.data.statsFrom || !this.data.statsTo)) {
      return;
    }
    // 每次进入统计都按页面月份重取，避免在明细改月份后看到旧口径。
    if (this.data.statsPeriod !== 'custom') {
      this.setData({ statsAnchor: `${this.data.month}-15` });
    }
    void this.loadStats();
  },

  /** 页面月份变化时，若统计为周/月/年口径则同步到该月。 */
  syncStatsToMonth() {
    if (this.data.tab !== 'stats' || this.data.statsPeriod === 'custom') {
      return;
    }
    this.setData({ statsAnchor: `${this.data.month}-01` });
    void this.loadStats();
  },

  async loadStats() {
    if (this.data.statsPeriod === 'custom' && (!this.data.statsFrom || !this.data.statsTo)) {
      return;
    }
    const seq = ++statsSeq;
    this.setData({ statsLoading: true, statsError: '' });
    try {
      const result = await fetchLedgerStatistics(
        ledgerStatisticsQuery({
          period: this.data.statsPeriod,
          anchor: this.data.statsAnchor,
          from: this.data.statsFrom,
          to: this.data.statsTo,
        }),
      );
      if (seq !== statsSeq || unloading) {
        return;
      }
      rawStats = result;
      this.setData({
        statsLoading: false,
        stats: ledgerStatisticsView(result, this.data.statsDirection),
      });
    } catch (error) {
      if (seq !== statsSeq || unloading) {
        return;
      }
      rawStats = null;
      this.setData({
        statsLoading: false,
        stats: null,
        statsError: errorMessage(error, '统计加载失败，请稍后重试'),
      });
    }
  },

  selectStatsPeriod(event: WechatMiniprogram.TouchEvent) {
    const period = event.currentTarget.dataset.period as LedgerPeriod;
    if (period === this.data.statsPeriod) {
      if (period === 'custom') {
        this.openStatsRange();
      }
      return;
    }
    this.setData({ statsPeriod: period });
    if (period === 'custom') {
      if (this.data.statsFrom && this.data.statsTo) {
        void this.loadStats();
      } else {
        this.openStatsRange();
      }
      return;
    }
    void this.loadStats();
  },

  shiftStats(event: WechatMiniprogram.TouchEvent) {
    if (this.data.statsPeriod === 'custom') {
      return;
    }
    const delta = Number(event.currentTarget.dataset.delta ?? 0);
    this.setData({
      statsAnchor: shiftStatsAnchor(this.data.statsAnchor, this.data.statsPeriod, delta),
    });
    void this.loadStats();
  },

  openStatsPeriod() {
    if (this.data.statsPeriod === 'custom') {
      this.openStatsRange();
    }
  },

  toggleStatsDirection(event: WechatMiniprogram.TouchEvent) {
    const direction = event.currentTarget.dataset.direction === 'income' ? 'income' : 'expense';
    this.setData({
      statsDirection: direction,
      stats: rawStats ? ledgerStatisticsView(rawStats, direction) : null,
    });
  },

  retrySummary() {
    lastSummaryKey = null;
    this.loadAux(feed?.state.key ?? INITIAL_KEY);
  },

  retry() {
    void feed?.load();
  },

  retryStats() {
    void this.loadStats();
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },
});
