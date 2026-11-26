import { describeError } from '@yunhefc/api-client';
import type {
  LedgerCategory,
  LedgerDailyTotal,
  LedgerEntry,
  LedgerSummary,
  Page,
} from '@yunhefc/shared';
import { computed, ref, type Ref } from 'vue';

import { api } from '../../api/client';
import { currentShanghaiMonth } from '../common/time-filter';
import type { TimeForm } from '../common/time-filter';
import {
  categoryLabelMap,
  categoryOptions,
  groupEntriesByDay,
  ledgerMonthLabel,
} from './presentation';
import {
  DEFAULT_LEDGER_PAGE_SIZE,
  parseLedgerType,
  shiftMonth as nextMonth,
  toDailyTotalRequest,
  toLedgerFilter,
  toLedgerRequest,
  type LedgerFilters,
} from './query';

/**
 * C13 明细视图状态：月份、筛选、关键字、分页，以及流水/汇总/日小计三路读取。
 * 汇总卡与明细共用同一份月份与筛选，统计视图由 useLedgerStats 独立维护。
 */
export function useLedgerDetail(allowed: Ref<boolean>) {
  const month = ref(currentShanghaiMonth());
  const custom = ref<TimeForm>({ month: '', startDate: '', endDate: '' });
  const typeFilter = ref('');
  const categoryFilter = ref('');
  const keyword = ref('');
  const page = ref(1);
  const pageSize = ref(DEFAULT_LEDGER_PAGE_SIZE);
  const categories = ref<LedgerCategory[]>([]);
  const categoriesError = ref('');
  const entries = ref<LedgerEntry[]>([]);
  const dailyTotals = ref<LedgerDailyTotal[]>([]);
  const summary = ref<LedgerSummary | null>(null);
  const total = ref(0);
  const loading = ref(true);
  const error = ref('');
  /** 与汇总结果同步更新，避免重载期间把旧数字标成「筛选合计」。 */
  const summaryFiltered = ref(false);
  let requestSeq = 0;
  let searchTimer: ReturnType<typeof setTimeout> | undefined;

  const direction = computed(() => parseLedgerType(typeFilter.value));
  const options = computed(() => categoryOptions(categories.value, direction.value));
  const labels = computed(() => categoryLabelMap(categories.value));
  const customActive = computed(() => !!custom.value.startDate && !!custom.value.endDate);
  const hasEntries = computed(() => entries.value.length > 0);
  const groups = computed(() => groupEntriesByDay(entries.value, dailyTotals.value));

  const monthLabel = computed(() => {
    if (!customActive.value) {
      return ledgerMonthLabel(month.value);
    }
    const start = custom.value.startDate.split('-');
    const end = custom.value.endDate.split('-');
    return `${Number(start[1])}/${Number(start[2])}–${Number(end[1])}/${Number(end[2])}`;
  });

  function isFiltered(filter: LedgerFilters): boolean {
    return (
      filter.type !== null ||
      filter.categoryId !== null ||
      (filter.startDate !== '' && filter.endDate !== '')
    );
  }

  function filters(): LedgerFilters {
    return {
      type: direction.value,
      categoryId: categoryFilter.value === '' ? null : Number(categoryFilter.value),
      // 自定义时间优先；否则用页面标题的月份。
      month: customActive.value ? '' : month.value,
      startDate: custom.value.startDate,
      endDate: custom.value.endDate,
    };
  }

  async function load(): Promise<void> {
    if (!allowed.value) {
      loading.value = false;
      return;
    }
    const seq = ++requestSeq;
    loading.value = true;
    error.value = '';
    const filter = filters();
    try {
      const [entryPage, summaryView, daily] = await Promise.all([
        api.get<Page<LedgerEntry>>('/ledger/entries', {
          query: toLedgerRequest({
            ...filter,
            page: page.value,
            pageSize: pageSize.value,
            keyword: keyword.value,
          }),
        }),
        api.get<LedgerSummary>('/ledger/summary', { query: toLedgerFilter(filter) }),
        api.get<{ items: LedgerDailyTotal[] }>('/ledger/daily-totals', {
          query: toDailyTotalRequest(filter, keyword.value),
        }),
      ]);
      if (seq !== requestSeq) {
        return;
      }
      entries.value = entryPage.items;
      total.value = entryPage.total;
      page.value = entryPage.page;
      summary.value = summaryView;
      dailyTotals.value = daily.items ?? [];
      summaryFiltered.value = isFiltered(filter);
    } catch (caught) {
      if (seq !== requestSeq) {
        return;
      }
      entries.value = [];
      dailyTotals.value = [];
      total.value = 0;
      summary.value = null;
      summaryFiltered.value = false;
      error.value = describeError(caught, '账本加载失败，请稍后重试');
    } finally {
      if (seq === requestSeq) {
        loading.value = false;
      }
    }
  }

  async function loadCategories(): Promise<void> {
    try {
      const result = await api.get<{ items: LedgerCategory[] }>('/ledger/categories');
      categories.value = result.items;
      categoriesError.value = '';
    } catch (caught) {
      categories.value = [];
      categoriesError.value = describeError(caught, '分类加载失败');
    }
  }

  function reload(): void {
    page.value = 1;
    void load();
  }

  /** 选月与自定义时间互斥：选月即清空自定义区间。 */
  function selectMonth(next: string): void {
    const wasCustom = customActive.value;
    if (wasCustom) {
      custom.value = { month: '', startDate: '', endDate: '' };
    }
    if (wasCustom || next !== month.value) {
      month.value = next;
      reload();
    }
  }

  function shiftMonthBy(delta: number): void {
    if (customActive.value) {
      return;
    }
    const next = nextMonth(month.value, delta);
    if (next !== month.value) {
      month.value = next;
      reload();
    }
  }

  function onCustomChange(): void {
    reload();
  }

  function onSearchInput(): void {
    clearTimeout(searchTimer);
    searchTimer = setTimeout(() => {
      reload();
    }, 300);
  }

  function onTypeChange(): void {
    if (
      categoryFilter.value !== '' &&
      !options.value.some((item) => String(item.id) === categoryFilter.value)
    ) {
      categoryFilter.value = '';
    }
    reload();
  }

  function resetFilters(): void {
    typeFilter.value = '';
    categoryFilter.value = '';
    keyword.value = '';
    custom.value = { month: '', startDate: '', endDate: '' };
    reload();
  }

  function onPageChange(next: number): void {
    page.value = next;
    void load();
  }

  function onPageSizeChange(event: Event): void {
    pageSize.value = Number((event.target as HTMLSelectElement).value);
    page.value = 1;
    void load();
  }

  return {
    month,
    monthLabel,
    custom,
    customActive,
    typeFilter,
    categoryFilter,
    keyword,
    direction,
    options,
    labels,
    categoriesError,
    entries,
    groups,
    summary,
    total,
    page,
    pageSize,
    loading,
    error,
    hasEntries,
    summaryFiltered,
    load,
    loadCategories,
    reload,
    selectMonth,
    shiftMonthBy,
    onCustomChange,
    onSearchInput,
    onTypeChange,
    resetFilters,
    onPageChange,
    onPageSizeChange,
  };
}
