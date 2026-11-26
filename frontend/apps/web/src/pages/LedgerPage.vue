<script setup lang="ts">
import { LEDGER_VIEW_ANY, hasPermission } from '@yunhefc/permissions';
import type { LedgerEntry } from '@yunhefc/shared';
import { formatAmount } from '@yunhefc/shared';
import { Button, Empty, Icon, Loading, Pagination } from 'vant';
import { computed, onMounted, ref } from 'vue';

import LedgerStatsPanel from '../components/LedgerStatsPanel.vue';
import LedgerSummaryCard from '../components/LedgerSummaryCard.vue';
import MonthRangeFilter from '../components/MonthRangeFilter.vue';
import {
  LEDGER_TYPE_OPTIONS,
  amountSign,
  categoryIcon,
  entryCategoryLabel,
  entryMetaText,
  entryTitle,
  formatEntryAmount,
} from '../features/ledger/presentation';
import { LEDGER_PAGE_SIZE_OPTIONS } from '../features/ledger/query';
import { useLedgerDetail } from '../features/ledger/useLedgerDetail';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();

type TabKey = 'detail' | 'stats';

const allowed = computed(
  () => session.isAuthenticated && hasPermission(session.permissions, LEDGER_VIEW_ANY),
);
const detail = useLedgerDetail(allowed);
const {
  month,
  monthLabel,
  custom,
  customActive,
  typeFilter,
  categoryFilter,
  keyword,
  options,
  labels,
  categoriesError,
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
} = detail;

const activeTab = ref<TabKey>('detail');

function amountTone(entry: LedgerEntry): string {
  return entry.type === 0 ? 'income' : 'expense';
}

function iconOf(entry: LedgerEntry): string {
  return categoryIcon(entryCategoryLabel(entry, labels.value));
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
    </header>

    <section v-if="!allowed" class="state">
      <Empty description="无权限查看账本" />
      <p class="muted small">账本仅对正式球员与管理员开放。</p>
      <RouterLink to="/profile">
        <Button size="small" plain type="primary">返回个人中心</Button>
      </RouterLink>
    </section>

    <template v-else>
      <LedgerSummaryCard
        :month="month"
        :month-label="monthLabel"
        :custom-active="customActive"
        :expense="summary?.expense ?? 0"
        :income="summary?.income ?? 0"
        :balance="summary?.balance ?? 0"
        :filtered="summaryFiltered"
        @prev="shiftMonthBy(-1)"
        @next="shiftMonthBy(1)"
        @pick="selectMonth"
      />

      <nav class="ledger-tabs" role="tablist" aria-label="账本视图">
        <button
          type="button"
          role="tab"
          class="ledger-tabs__tab"
          :class="{ 'is-active': activeTab === 'detail' }"
          :aria-selected="activeTab === 'detail'"
          @click="activeTab = 'detail'"
        >
          明细
        </button>
        <button
          type="button"
          role="tab"
          class="ledger-tabs__tab"
          :class="{ 'is-active': activeTab === 'stats' }"
          :aria-selected="activeTab === 'stats'"
          @click="activeTab = 'stats'"
        >
          统计
        </button>
        <div v-if="activeTab === 'detail'" class="ledger-search">
          <Icon name="search" class="ledger-search__icon" aria-hidden="true" />
          <input
            v-model="keyword"
            type="search"
            class="ledger-search__input"
            placeholder="搜索摘要"
            aria-label="搜索流水"
            maxlength="64"
            @input="onSearchInput"
          />
        </div>
      </nav>

      <div v-if="activeTab === 'detail'">
        <section class="schedule-filters">
          <label class="schedule-filter">
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
          <label class="schedule-filter">
            <span>分类</span>
            <select v-model="categoryFilter" aria-label="账本分类" @change="reload">
              <option value="">全部</option>
              <option v-for="item in options" :key="item.id" :value="String(item.id)">
                {{ item.name }}
              </option>
            </select>
          </label>
          <MonthRangeFilter v-model="custom" :show-month="false" @change="onCustomChange" />
          <div class="schedule-filter-actions">
            <Button size="small" native-type="button" plain @click="resetFilters">重置</Button>
          </div>
        </section>

        <p v-if="categoriesError" class="hint">{{ categoriesError }}</p>

        <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

        <div v-else-if="error" class="state">
          <p class="muted">{{ error }}</p>
          <Button size="small" type="primary" @click="load()">重试</Button>
        </div>

        <div v-else-if="!hasEntries" class="state">
          <Empty description="该筛选下暂无流水" />
        </div>

        <template v-else>
          <section
            v-for="group in groups"
            :key="group.key"
            class="ledger-day"
            data-testid="ledger-day"
          >
            <header class="ledger-day__head">
              <span class="ledger-day__date">{{ group.label }}</span>
              <span class="ledger-day__totals">
                支 {{ formatAmount(group.expense) }}
                <span>收 {{ formatAmount(group.income) }}</span>
              </span>
            </header>
            <div class="ledger-list card">
              <article
                v-for="entry in group.entries"
                :key="entry.id"
                class="ledger-row"
                data-testid="ledger-row"
              >
                <span class="ledger-row__icon" :class="`ledger-row__icon--${amountTone(entry)}`">
                  <Icon :name="iconOf(entry)" />
                </span>
                <div class="ledger-row__main">
                  <p class="ledger-row__title">{{ entryTitle(entry, labels) }}</p>
                  <p class="ledger-row__meta">{{ entryMetaText(entry, labels) }}</p>
                </div>
                <b class="ledger-row__amount" :class="`ledger-row__amount--${amountTone(entry)}`">
                  <span class="ledger-row__sign">{{ amountSign(entry) }}</span
                  >{{ formatEntryAmount(entry) }}
                </b>
              </article>
            </div>
          </section>

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
      </div>

      <LedgerStatsPanel
        v-show="activeTab === 'stats'"
        :active="activeTab === 'stats'"
        :page-month="month"
      />
    </template>
  </section>
</template>

<style scoped>
.ledger-tabs {
  display: flex;
  gap: 22px;
  align-items: center;
  margin-bottom: 14px;
  border-bottom: 1px solid var(--yh-line);
}

.ledger-tabs__tab {
  position: relative;
  padding: 10px 2px 12px;
  color: var(--yh-muted);
  font-size: 16px;
  font-weight: 600;
  background: transparent;
  border: 0;
  cursor: pointer;
}

.ledger-tabs__tab.is-active {
  color: var(--yh-ink);
}

.ledger-tabs__tab.is-active::after {
  position: absolute;
  right: 0;
  bottom: -1px;
  left: 0;
  height: 3px;
  background: var(--yh-blue);
  border-radius: 3px 3px 0 0;
  content: '';
}

.ledger-search {
  display: flex;
  flex: 1;
  gap: 6px;
  align-items: center;
  max-width: 280px;
  margin-left: auto;
  padding: 6px 12px;
  background: var(--yh-slate-soft);
  border-radius: 999px;
}

.ledger-search__icon {
  color: var(--yh-muted);
  font-size: 15px;
}

.ledger-search__input {
  width: 100%;
  color: var(--yh-ink);
  font-size: 13px;
  background: transparent;
  border: 0;
  outline: none;
}

.ledger-day + .ledger-day {
  margin-top: 18px;
}

.ledger-day__head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 4px 8px;
}

.ledger-day__date {
  color: var(--yh-slate-ink);
  font-size: 13px;
  font-weight: 600;
}

.ledger-day__totals {
  display: flex;
  gap: 12px;
  color: var(--yh-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
}

.ledger-list {
  padding: 0 16px;
}

.ledger-row {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 14px 0;
}

.ledger-row + .ledger-row {
  border-top: 1px solid var(--yh-line);
}

.ledger-row__icon {
  display: grid;
  flex: none;
  place-items: center;
  width: 38px;
  height: 38px;
  color: var(--yh-blue-ink);
  font-size: 19px;
  background: var(--yh-blue-soft);
  border-radius: 12px;
}

.ledger-row__icon--income {
  color: var(--yh-green-ink);
  background: var(--yh-green-soft);
}

.ledger-row__main {
  flex: 1;
  min-width: 0;
}

.ledger-row__title {
  overflow: hidden;
  margin: 0;
  color: var(--yh-ink);
  font-size: 15px;
  font-weight: 600;
  line-height: 1.45;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ledger-row__meta {
  overflow: hidden;
  margin: 2px 0 0;
  color: var(--yh-muted);
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  line-height: 1.5;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ledger-row__amount {
  flex: none;
  width: 128px;
  font-size: 17px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.ledger-row__sign {
  font-size: 0.7em;
}

.ledger-row__amount--income {
  color: var(--yh-green-ink);
}

.ledger-row__amount--expense {
  color: var(--yh-red);
}

@media (max-width: 760px) {
  .ledger-row {
    gap: 10px;
  }

  .ledger-row__amount {
    width: 104px;
  }

  .ledger-search {
    max-width: 150px;
  }
}
</style>
