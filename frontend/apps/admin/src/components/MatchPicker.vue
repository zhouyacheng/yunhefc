<script setup lang="ts">
import type { MatchSummary } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT } from '@yunhefc/shared';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api, publicApi } from '../api/client';
import {
  currentShanghaiSeason,
  groupMatches,
  mergeSeasons,
  optionLabel,
} from '../features/match-catalog/selector';
import {
  createAdminMatchSource,
  createPublicMatchSource,
  INTERNAL_FETCH_SIZE,
} from '../features/match-catalog/sources';
import type { MatchCatalogPage } from '../features/match-catalog/types';
import { useMatchCatalog } from '../features/match-catalog/useMatchCatalog';

const props = withDefaults(
  defineProps<{
    modelValue: number | null;
    /** public 走 MAT-02/14（只读用户可能没有 match:view:any）；admin 走 MAT-09/13。 */
    source?: 'public' | 'admin';
    /** B06 分组页：只保留队内赛(1)/训练赛(2)。 */
    internalOnly?: boolean;
  }>(),
  { source: 'public', internalOnly: false },
);

const emit = defineEmits<{
  'update:modelValue': [id: number | null];
  change: [match: MatchSummary | null];
  'update:loading': [loading: boolean];
  'load-error': [message: string];
}>();

const route = useRoute();
const router = useRouter();

const currentSeason = currentShanghaiSeason();
const selectedMatch = ref<MatchSummary | null>(null);
let selectionSeq = 0;
/** 自己写 URL 时记录的 id，避免 route watch 回环。 */
const selfWrittenIds = new Set<number>();

const source =
  props.source === 'admin'
    ? createAdminMatchSource(api)
    : createPublicMatchSource(publicApi, { internalOnly: props.internalOnly });
const catalog = useMatchCatalog(source, {
  onLoaded: handleLoaded,
  onError: (message) => emit('load-error', message),
});
const { season, dateRange, status, page, items, total, totalPages, truncated, loading, seasons } =
  catalog;

const seasonOptions = computed(() =>
  mergeSeasons([currentSeason, season.value, selectedMatch.value?.season, ...seasons.value]),
);
const optionGroups = computed(() => groupMatches(items.value, selectedMatch.value));

watch(loading, (value) => emit('update:loading', value), { immediate: true });

function parseMatchId(value: unknown): number | null {
  const raw = Array.isArray(value) ? value[0] : value;
  if (typeof raw === 'number') {
    return Number.isInteger(raw) && raw > 0 ? raw : null;
  }
  if (typeof raw !== 'string' || !/^\d+$/.test(raw)) {
    return null;
  }
  const id = Number(raw);
  return Number.isSafeInteger(id) && id > 0 ? id : null;
}

function handleLoaded(result: MatchCatalogPage): void {
  const selected = selectedMatch.value;
  if (selected) {
    const refreshed = result.items.find((match) => match.id === selected.id);
    if (refreshed) {
      selectedMatch.value = refreshed;
    }
    return;
  }
  const first = result.items[0] ?? null;
  if (first) {
    commitSelection(first, false);
  } else {
    emit('change', null);
  }
}

function commitSelection(match: MatchSummary | null, writeUrl: boolean): void {
  const changed = (match?.id ?? null) !== (selectedMatch.value?.id ?? null);
  selectedMatch.value = match;
  if (changed) {
    emit('update:modelValue', match?.id ?? null);
    emit('change', match);
  }
  if (writeUrl && match) {
    writeMatchQuery(match.id);
  }
}

function writeMatchQuery(id: number): void {
  if (parseMatchId(route.query.match) === id) {
    return;
  }
  selfWrittenIds.add(id);
  void router.replace({ query: { ...route.query, match: String(id) } }).catch(() => {
    selfWrittenIds.delete(id);
  });
}

function onSelect(id: number | null): void {
  if (id === null) {
    return;
  }
  const match =
    items.value.find((item) => item.id === id) ??
    (selectedMatch.value?.id === id ? selectedMatch.value : null);
  if (match) {
    commitSelection(match, true);
  }
}

function onFilterChange(): void {
  void catalog.setFilters();
}

function changePage(delta: number): void {
  void catalog.setPage(page.value + delta);
}

function resetFilters(): void {
  void catalog.setFilters({ season: currentSeason, dateRange: null, status: '' });
}

async function resolveRequested(id: number): Promise<void> {
  const seq = ++selectionSeq;
  const fromPage = items.value.find((match) => match.id === id);
  const match = fromPage ?? (await catalog.byId(id));
  if (seq !== selectionSeq) {
    return;
  }
  if (!match) {
    return;
  }
  if (match.season && match.season !== season.value) {
    commitSelection(match, false);
    await catalog.setFilters({ season: match.season });
    return;
  }
  commitSelection(match, false);
  await catalog.reload();
}

async function reload(): Promise<void> {
  const before = selectedMatch.value;
  await catalog.reload();
  if (before && selectedMatch.value?.id === before.id) {
    emit('change', selectedMatch.value);
  }
}

function updateSummary(match: MatchSummary): void {
  if (selectedMatch.value?.id === match.id) {
    selectedMatch.value = match;
  }
  catalog.updateItem(match);
}

watch(
  () => route.query.match,
  (raw) => {
    const id = parseMatchId(raw);
    if (id === null) {
      // 侧栏同一路由会清空 query；保留当前选择，不清空页面内容。
      return;
    }
    if (selfWrittenIds.delete(id)) {
      return;
    }
    if (id !== selectedMatch.value?.id) {
      void resolveRequested(id);
    }
  },
);

watch(
  () => props.modelValue,
  (id) => {
    if (id !== null && id !== selectedMatch.value?.id) {
      void resolveRequested(id);
    }
  },
);

onMounted(async () => {
  const requested = parseMatchId(route.query.match) ?? props.modelValue;
  if (requested !== null) {
    const match = await catalog.byId(requested);
    if (match) {
      selectedMatch.value = match;
      emit('update:modelValue', match.id);
      emit('change', match);
      if (match.season) {
        await catalog.setFilters({ season: match.season });
      } else {
        await catalog.reload();
      }
      return;
    }
  }
  await catalog.reload();
});

onBeforeUnmount(() => {
  selectionSeq += 1;
});

defineExpose({ reload, updateSummary });
</script>

<template>
  <div class="match-picker-bar">
    <el-select
      :model-value="selectedMatch?.id ?? null"
      class="match-picker"
      filterable
      :loading="loading"
      placeholder="选择比赛"
      @change="onSelect"
    >
      <el-option-group v-for="group in optionGroups" :key="group.key" :label="group.label">
        <el-option
          v-for="match in group.items"
          :key="match.id"
          :value="match.id"
          :label="optionLabel(match)"
        />
      </el-option-group>
      <template #empty>
        <p class="picker-empty">
          {{ internalOnly ? '当前筛选下暂无队内赛/训练赛' : '当前筛选下暂无比赛' }}
        </p>
      </template>
      <template #footer>
        <div class="picker-footer" @mousedown.prevent @click.stop>
          <span class="muted small">共 {{ total }} 场</span>
          <div class="picker-pager">
            <el-button text size="small" :disabled="page <= 1" @click="changePage(-1)">
              上一页
            </el-button>
            <span class="muted small">{{ page }} / {{ totalPages }}</span>
            <el-button text size="small" :disabled="page >= totalPages" @click="changePage(1)">
              下一页
            </el-button>
          </div>
        </div>
        <p v-if="truncated" class="picker-hint muted small">
          仅加载最近 {{ INTERNAL_FETCH_SIZE }} 场，请用赛季/日期缩小范围。
        </p>
      </template>
    </el-select>

    <el-select v-model="season" class="season-filter" @change="onFilterChange">
      <el-option label="全部赛季" value="" />
      <el-option
        v-for="value in seasonOptions"
        :key="value"
        :label="`${value} 赛季`"
        :value="value"
      />
    </el-select>

    <el-date-picker
      v-model="dateRange"
      class="date-filter"
      type="daterange"
      value-format="YYYY-MM-DD"
      range-separator="至"
      start-placeholder="开始日期"
      end-placeholder="结束日期"
      clearable
      @change="onFilterChange"
    />

    <el-select
      v-model="status"
      class="status-filter"
      placeholder="全部状态"
      @change="onFilterChange"
    >
      <el-option label="全部状态" value="" />
      <el-option
        v-for="(label, code) in MATCH_STATUS_TEXT"
        :key="code"
        :label="label"
        :value="Number(code)"
      />
    </el-select>

    <el-button @click="resetFilters">重置</el-button>
    <slot name="actions" />
  </div>
</template>

<style scoped>
.match-picker-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.season-filter,
.status-filter {
  width: 150px;
}

.date-filter {
  width: 280px;
}

.picker-empty {
  margin: 0;
  padding: 8px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}

.picker-footer {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 12px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.picker-pager {
  display: flex;
  align-items: center;
  gap: 4px;
}

.picker-pager :deep(.el-button) {
  margin-left: 0;
}

.picker-hint {
  margin: 0;
  padding: 0 12px 6px;
}

@media (max-width: 1000px) {
  .match-picker,
  .date-filter {
    width: 100%;
  }
}
</style>
