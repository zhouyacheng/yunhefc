<script setup lang="ts">
import { describeError } from '@yunhefc/api-client';
import { LEDGER_VIEW_ANY, hasPermission } from '@yunhefc/permissions';
import type { LedgerCategory, LedgerEntry, LedgerSummary, Page } from '@yunhefc/shared';
import { formatDateTime } from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Tag } from 'vant';
import { computed, onMounted, ref } from 'vue';

import { api } from '../api/client';
import {
  LEDGER_TYPE_OPTIONS,
  categoryLabelMap,
  categoryOptions,
  entryCategoryLabel,
  formatMoney,
  formatSignedAmount,
  ledgerTypeText,
} from '../features/ledger/presentation';
import {
  DEFAULT_LEDGER_PAGE_SIZE,
  LEDGER_PAGE_SIZE_OPTIONS,
  ledgerRangeError,
  parseLedgerType,
  toLedgerFilter,
  toLedgerRequest,
  type LedgerFilters,
} from '../features/ledger/query';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();

const typeFilter = ref('');
const categoryFilter = ref('');
const fromDate = ref('');
const toDate = ref('');
const page = ref(1);
const pageSize = ref(DEFAULT_LEDGER_PAGE_SIZE);
const categories = ref<LedgerCategory[]>([]);
const categoriesError = ref('');
const entries = ref<LedgerEntry[]>([]);
const summary = ref<LedgerSummary | null>(null);
const total = ref(0);
const loading = ref(true);
const error = ref('');
let requestSeq = 0;

const allowed = computed(
  () => session.isAuthenticated && hasPermission(session.permissions, LEDGER_VIEW_ANY),
);
const direction = computed(() => parseLedgerType(typeFilter.value));
const options = computed(() => categoryOptions(categories.value, direction.value));
const labels = computed(() => categoryLabelMap(categories.value));
const rangeError = computed(() => ledgerRangeError({ from: fromDate.value, to: toDate.value }));
const hasEntries = computed(() => entries.value.length > 0);

function filters(): LedgerFilters {
  return {
    type: direction.value,
    categoryId: categoryFilter.value === '' ? null : Number(categoryFilter.value),
    from: fromDate.value,
    to: toDate.value,
  };
}

async function load(): Promise<void> {
  if (!allowed.value) {
    loading.value = false;
    return;
  }
  if (rangeError.value) {
    return;
  }
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  const filter = filters();
  try {
    const [entryPage, summaryView] = await Promise.all([
      api.get<Page<LedgerEntry>>('/ledger/entries', {
        query: toLedgerRequest({ ...filter, page: page.value, pageSize: pageSize.value }),
      }),
      api.get<LedgerSummary>('/ledger/summary', { query: toLedgerFilter(filter) }),
    ]);
    if (seq !== requestSeq) {
      return;
    }
    entries.value = entryPage.items;
    total.value = entryPage.total;
    page.value = entryPage.page;
    summary.value = summaryView;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    entries.value = [];
    total.value = 0;
    summary.value = null;
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
  fromDate.value = '';
  toDate.value = '';
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

function amountTone(entry: LedgerEntry): string {
  return entry.type === 0 ? 'income' : 'expense';
}

onMounted(() => {
  if (allowed.value) {
    void loadCategories();
    void load();
  } else {
    loading.value = false;
  }
});
</script>

<template>
  <section class="page">
    <header class="page-head">
      <p class="eyebrow">LEDGER</p>
      <h1>账本</h1>
      <p class="muted">球队收支流水；正式球员只读，管理员在后台维护</p>
    </header>

    <section v-if="!allowed" class="state">
      <Empty description="无权限查看账本" />
      <p class="muted small">账本仅对正式球员与管理员开放。</p>
      <RouterLink to="/profile">
        <Button size="small" plain type="primary">返回个人中心</Button>
      </RouterLink>
    </section>

    <template v-else>
      <div class="ledger-summary">
        <div class="card ledger-summary__item">
          <span class="muted small">收入</span>
          <b class="ledger-summary__value ledger-summary__value--income">
            {{ formatMoney(summary?.income ?? 0) }}
          </b>
        </div>
        <div class="card ledger-summary__item">
          <span class="muted small">支出</span>
          <b class="ledger-summary__value ledger-summary__value--expense">
            {{ formatMoney(summary?.expense ?? 0) }}
          </b>
        </div>
        <div class="card ledger-summary__item">
          <span class="muted small">余额</span>
          <b
            class="ledger-summary__value"
            :class="{ 'ledger-summary__value--negative': (summary?.balance ?? 0) < 0 }"
          >
            {{ formatMoney(summary?.balance ?? 0) }}
          </b>
        </div>
      </div>

      <section class="card ledger-filters">
        <label class="ledger-filter">
          <span>方向</span>
          <select v-model="typeFilter" aria-label="收支方向" @change="onTypeChange">
            <option value="">全部</option>
            <option
              v-for="item in LEDGER_TYPE_OPTIONS"
              :key="item.value"
              :value="String(item.value)"
            >
              {{ item.label }}
            </option>
          </select>
        </label>
        <label class="ledger-filter">
          <span>分类</span>
          <select v-model="categoryFilter" aria-label="账本分类" @change="reload">
            <option value="">全部</option>
            <option v-for="item in options" :key="item.id" :value="String(item.id)">
              {{ item.name }}
            </option>
          </select>
        </label>
        <label class="ledger-filter">
          <span>开始日期</span>
          <input v-model="fromDate" type="date" aria-label="开始日期" @change="reload" />
        </label>
        <label class="ledger-filter">
          <span>结束日期</span>
          <input v-model="toDate" type="date" aria-label="结束日期" @change="reload" />
        </label>
        <Button size="small" plain @click="resetFilters">重置筛选</Button>
      </section>

      <p v-if="categoriesError" class="hint">{{ categoriesError }}</p>
      <p v-if="rangeError" class="action-error">{{ rangeError }}</p>

      <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

      <div v-else-if="error" class="state">
        <p class="muted">{{ error }}</p>
        <Button size="small" type="primary" @click="load()">重试</Button>
      </div>

      <div v-else-if="!hasEntries" class="state">
        <Empty description="该筛选下暂无流水" />
      </div>

      <template v-else>
        <div class="ledger-list card">
          <article
            v-for="entry in entries"
            :key="entry.id"
            class="ledger-row"
            data-testid="ledger-row"
          >
            <div class="ledger-row__head">
              <Tag class="status-tag" :class="`status-tag--${amountTone(entry)}`">
                {{ ledgerTypeText(entry.type) }}
              </Tag>
              <b class="ledger-row__category">{{ entryCategoryLabel(entry, labels) }}</b>
              <b class="ledger-row__amount" :class="`ledger-row__amount--${amountTone(entry)}`">
                {{ formatSignedAmount(entry) }}
              </b>
            </div>
            <p class="ledger-row__description">{{ entry.description }}</p>
            <p class="muted small">
              {{ formatDateTime(entry.occurredAt) }}
              <template v-if="entry.playerName"> · 归属 {{ entry.playerName }}</template>
              <template v-if="entry.activityTitle"> · {{ entry.activityTitle }}</template>
            </p>
          </article>
        </div>

        <footer class="page-footer">
          <label class="page-size">
            每页
            <select :value="pageSize" aria-label="每页条数" @change="onPageSizeChange">
              <option v-for="size in LEDGER_PAGE_SIZE_OPTIONS" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
            条
          </label>
          <Pagination
            :model-value="page"
            mode="simple"
            :total-items="total"
            :items-per-page="pageSize"
            @change="onPageChange"
          />
        </footer>
      </template>
    </template>
  </section>
</template>

<style scoped>
.ledger-summary {
  display: grid;
  gap: 12px;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  margin-bottom: 16px;
}

.ledger-summary__item {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
}

.ledger-summary__value {
  font-size: 24px;
  font-variant-numeric: tabular-nums;
}

.ledger-summary__value--income {
  color: var(--yh-green-ink);
}

.ledger-summary__value--expense {
  color: var(--yh-red);
}

.ledger-summary__value--negative {
  color: var(--yh-red);
}

.ledger-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  padding: 14px 16px;
  margin-bottom: 16px;
}

.ledger-filter {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--yh-muted);
  font-size: 12px;
  font-weight: 600;
}

.ledger-filter select,
.ledger-filter input {
  min-width: 130px;
  min-height: 36px;
  padding: 6px 9px;
  color: var(--yh-ink);
  font-size: 13px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.ledger-list {
  padding: 4px 16px;
}

.ledger-row {
  padding: 14px 0;
}

.ledger-row + .ledger-row {
  border-top: 1px solid var(--yh-line);
}

.ledger-row__head {
  display: flex;
  gap: 10px;
  align-items: center;
}

.ledger-row__category {
  font-size: 15px;
}

.ledger-row__amount {
  margin-left: auto;
  font-size: 17px;
  font-variant-numeric: tabular-nums;
}

.ledger-row__amount--income {
  color: var(--yh-green-ink);
}

.ledger-row__amount--expense {
  color: var(--yh-red);
}

.ledger-row p {
  margin: 4px 0 0;
}

.ledger-row__description {
  font-size: 14px;
}

.status-tag--income.van-tag {
  color: var(--yh-green-ink);
  background: var(--yh-green-soft);
}

.status-tag--expense.van-tag {
  color: var(--yh-red);
  background: var(--yh-red-soft, #fee2e2);
}
</style>
