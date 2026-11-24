import type { LedgerEntry, LedgerSummary, LedgerType } from '../../types/api';
import {
  fetchLedgerSummary,
  listLedgerCategories,
  listLedgerEntries,
  type LedgerFilter,
} from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import { formatDateTime } from '../../utils/datetime';
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

const ALL_CATEGORIES = { value: 0, label: '全部分类' };

let feed: PagedFeed<LedgerEntryView> | null = null;
let summarySeq = 0;
let unloading = false;
let lastFeedError = '';

function moneyText(value: number): string {
  return `¥${value.toFixed(2)}`;
}

function decorateEntry(entry: LedgerEntry): LedgerEntryView {
  return {
    id: entry.id,
    categoryName: entry.categoryName,
    description: entry.description,
    timeText: formatDateTime(entry.occurredAt),
    amountText: `${entry.type === 0 ? '+' : '-'}${moneyText(entry.amount)}`,
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

function rangeStart(date: string): string {
  return date ? `${date}T00:00:00` : '';
}

function rangeEnd(date: string): string {
  return date ? `${date}T23:59:59` : '';
}

/** key = `type|categoryId|from|to`；空值为不过滤。 */
function parseKey(key: string): LedgerFilter {
  const [type, categoryId, from, to] = key.split('|');
  return {
    type: type === '' ? undefined : (Number(type) as LedgerType),
    categoryId: Number(categoryId) || undefined,
    from: from || undefined,
    to: to || undefined,
  };
}

Page({
  data: {
    ready: false,
    authenticated: false,
    authorized: false,
    categoriesLoaded: false,
    feed: emptyPagedFeedState<LedgerEntryView>('|0||') as PagedFeedState<LedgerEntryView>,
    summary: null as SummaryView | null,
    summaryLoading: false,
    summaryError: '',
    typeOptions: TYPE_OPTIONS,
    typeIndex: 0,
    categoryOptions: [ALL_CATEGORIES],
    categoryIndex: 0,
    fromDate: '',
    toDate: '',
  },

  onLoad() {
    unloading = false;
    feed = createPagedFeed<LedgerEntryView>({
      pageSize: PAGE_SIZE,
      initialKey: '|0||',
      fetchPage: async ({ key, page, pageSize }) => {
        const result = await listLedgerEntries({ ...parseKey(key), page, pageSize });
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
    this.loadSummary(feed?.state.key ?? '|0||');
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
      const items = await listLedgerCategories();
      this.setData({
        categoriesLoaded: true,
        categoryOptions: [
          ALL_CATEGORIES,
          ...items.map((category) => ({ value: category.id, label: category.name })),
        ],
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
      const summary = await fetchLedgerSummary(parseKey(key));
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
    const type = TYPE_OPTIONS[this.data.typeIndex]?.value ?? '';
    const categoryId = this.data.categoryOptions[this.data.categoryIndex]?.value ?? 0;
    const key = `${type}|${categoryId}|${rangeStart(this.data.fromDate)}|${rangeEnd(this.data.toDate)}`;
    if (key === feed.state.key) {
      return;
    }
    void wx.pageScrollTo({ scrollTop: 0 });
    feed.setKey(key);
    this.loadSummary(key);
  },

  onTypeChange(event: WechatMiniprogram.PickerChange) {
    this.setData({ typeIndex: Number(event.detail.value) });
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
    this.loadSummary(feed?.state.key ?? '|0||');
  },

  retry() {
    void feed?.load();
  },

  loadMore() {
    void feed?.loadMore();
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },
});
