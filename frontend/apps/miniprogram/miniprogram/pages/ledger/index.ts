import type { LedgerCategory, LedgerEntry, LedgerSummary, LedgerType } from '../../types/api';
import {
  fetchLedgerSummary,
  listLedgerCategories,
  listLedgerEntries,
} from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import { formatDateTime } from '../../utils/datetime';
import {
  ALL_CATEGORIES_OPTION,
  categoryOptionsFor,
  DEFAULT_LEDGER_KEY,
  ledgerFilterKey,
  ledgerFilterQuery,
  parseLedgerFilterKey,
  type LedgerFilterState,
} from '../../utils/ledger-query';
import { LEDGER_VIEW_ANY, hasPermission } from '../../utils/permissions';
import {
  createPagedFeed,
  emptyPagedFeedState,
  type PagedFeed,
  type PagedFeedState,
} from '../../utils/paged-feed';
import { getPermissions, getUser, sessionReady } from '../../utils/session';

const PAGE_SIZE = 10;

interface FilterOption {
  value: LedgerType | '';
  label: string;
}

interface LedgerEntryView {
  id: number;
  categoryName: string;
  description: string;
  timeText: string;
  amountText: string;
  tone: 'income' | 'expense';
  metaText: string;
}

interface SummaryView {
  incomeText: string;
  expenseText: string;
  balanceText: string;
  tone: 'income' | 'expense';
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

/** 负数按 `−¥123.00` 展示，与 Web 一致。 */
function moneyText(value: number): string {
  return value < 0 ? `−¥${Math.abs(value).toFixed(2)}` : `¥${value.toFixed(2)}`;
}

function decorateEntry(entry: LedgerEntry): LedgerEntryView {
  return {
    id: entry.id,
    categoryName: entry.categoryName,
    description: entry.description,
    timeText: formatDateTime(entry.occurredAt),
    amountText: `${entry.type === 0 ? '+' : '−'}${moneyText(entry.amount)}`,
    tone: entry.type === 0 ? 'income' : 'expense',
    metaText: [entry.playerName, entry.activityTitle].filter(Boolean).join(' · '),
  };
}

function summaryView(summary: LedgerSummary): SummaryView {
  return {
    incomeText: moneyText(summary.income),
    expenseText: moneyText(summary.expense),
    balanceText: moneyText(summary.balance),
    tone: summary.balance >= 0 ? 'income' : 'expense',
  };
}

function filterStateOf(data: {
  typeIndex: number;
  categoryOptions: { value: number }[];
  categoryIndex: number;
  fromDate: string;
  toDate: string;
}): LedgerFilterState {
  return {
    type: typeValue(data.typeIndex),
    categoryId: data.categoryOptions[data.categoryIndex]?.value ?? 0,
    fromDate: data.fromDate,
    toDate: data.toDate,
  };
}

Page({
  data: {
    ready: false,
    authenticated: false,
    authorized: false,
    categoriesLoaded: false,
    feed: emptyPagedFeedState<LedgerEntryView>(
      DEFAULT_LEDGER_KEY,
    ) as PagedFeedState<LedgerEntryView>,
    summary: null as SummaryView | null,
    summaryLoading: false,
    summaryError: '',
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    categories: [] as LedgerCategory[],
    categoryOptions: [ALL_CATEGORIES_OPTION],
    categoryIndex: 0,
    fromDate: '',
    toDate: '',
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<LedgerEntryView>({
      pageSize: PAGE_SIZE,
      initialKey: DEFAULT_LEDGER_KEY,
      fetchPage: async ({ key, page, pageSize }) => {
        const filter = ledgerFilterQuery(parseLedgerFilterKey(key));
        const result = await listLedgerEntries({ ...filter, page, pageSize });
        return { ...result, items: result.items.map(decorateEntry) };
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

  onPullDownRefresh() {
    if (!this.data.authorized) {
      wx.stopPullDownRefresh();
      return;
    }
    const loading = feed?.load() ?? Promise.resolve();
    this.loadSummary(feed?.state.key ?? DEFAULT_LEDGER_KEY);
    void loading.then(() => wx.stopPullDownRefresh());
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
    if (!feed.state.loaded && !feed.state.loading) {
      void feed.load();
      this.loadSummary(feed.state.key);
    }
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
      this.setData({ summaryLoading: false, summary: summaryView(summary) });
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

  onFromChange(event: WechatMiniprogram.PickerChange) {
    const fromDate = String(event.detail.value);
    if (this.data.toDate && fromDate > this.data.toDate) {
      wx.showToast({ title: '开始日期不能晚于结束日期', icon: 'none' });
      return;
    }
    this.setData({ fromDate });
    this.applyFilters();
  },

  onToChange(event: WechatMiniprogram.PickerChange) {
    const toDate = String(event.detail.value);
    if (this.data.fromDate && toDate < this.data.fromDate) {
      wx.showToast({ title: '结束日期不能早于开始日期', icon: 'none' });
      return;
    }
    this.setData({ toDate });
    this.applyFilters();
  },

  clearRange() {
    if (!this.data.fromDate && !this.data.toDate) {
      return;
    }
    this.setData({ fromDate: '', toDate: '' });
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
