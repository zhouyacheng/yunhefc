<script setup lang="ts">
import type { MatchSummary } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT } from '@yunhefc/shared';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api, publicApi } from '../api/client';
import {
  buildMonthRange,
  formatMonthRange,
  monthText,
  parseMonthRange,
  remapMonthRange,
} from '../features/match-catalog/month-range';
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
import type { MatchCatalogPage, MatchFilters } from '../features/match-catalog/types';
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
const { season, monthRange, status, page, items, total, totalPages, truncated, loading, seasons } =
  catalog;

const seasonOptions = computed(() =>
  mergeSeasons([currentSeason, season.value, selectedMatch.value?.season, ...seasons.value]),
);
const optionGroups = computed(() => groupMatches(items.value, selectedMatch.value));

const monthOpen = ref(false);
const pendingStart = ref<number | null>(null);
const hoverMonth = ref<number | null>(null);
const months = Array.from({ length: 12 }, (_, index) => index + 1);

const monthDisabled = computed(() => season.value === '');
const rangeParts = computed(() => parseMonthRange(monthRange.value));
const previewRange = computed(() => {
  if (pendingStart.value !== null) {
    const hover = hoverMonth.value ?? pendingStart.value;
    return {
      start: Math.min(pendingStart.value, hover),
      end: Math.max(pendingStart.value, hover),
    };
  }
  return rangeParts.value;
});
const monthLabel = computed(() =>
  monthDisabled.value
    ? '全部赛季不筛选月份'
    : formatMonthRange(monthRange.value) || '开始月份 至 结束月份',
);

function isMonthInRange(month: number): boolean {
  const range = previewRange.value;
  return range ? month >= range.start && month <= range.end : false;
}

function isMonthEdge(month: number): boolean {
  if (pendingStart.value !== null) {
    return month === pendingStart.value;
  }
  const parts = rangeParts.value;
  return parts ? month === parts.start || month === parts.end : false;
}

watch(loading, (value) => emit('update:loading', value), { immediate: true });

watch(monthOpen, () => {
  pendingStart.value = null;
  hoverMonth.value = null;
});

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

function onFilterChange(next: Partial<MatchFilters> = {}): void {
  if ('season' in next) {
    monthOpen.value = false;
    pendingStart.value = null;
    hoverMonth.value = null;
    next = {
      ...next,
      monthRange:
        next.season === '' || next.season === undefined
          ? null
          : remapMonthRange(monthRange.value, next.season),
    };
  }
  void catalog.setFilters(next);
}

function toggleMonthPanel(): void {
  if (monthDisabled.value) {
    return;
  }
  monthOpen.value = !monthOpen.value;
}

function pickMonth(month: number): void {
  if (monthDisabled.value) {
    return;
  }
  if (pendingStart.value === null) {
    pendingStart.value = month;
    return;
  }
  const range = buildMonthRange(season.value, pendingStart.value, month);
  pendingStart.value = null;
  hoverMonth.value = null;
  monthOpen.value = false;
  if (range) {
    onFilterChange({ monthRange: range });
  }
}

function clearMonthRange(): void {
  if (monthDisabled.value) {
    return;
  }
  monthOpen.value = false;
  pendingStart.value = null;
  hoverMonth.value = null;
  onFilterChange({ monthRange: null });
}

function changePage(delta: number): void {
  void catalog.setPage(page.value + delta);
}

function resetFilters(): void {
  onFilterChange({ season: currentSeason, monthRange: null, status: '' });
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
      <template #label="{ label }">
        <span class="match-picker-label">
          {{ selectedMatch ? optionLabel(selectedMatch) : label }}
        </span>
      </template>
      <template #empty>
        <p class="picker-empty">
          {{
            items.length > 0
              ? '没有其他比赛'
              : internalOnly
                ? '当前筛选下暂无队内赛/训练赛'
                : '当前筛选下暂无比赛'
          }}
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
          仅加载最近 {{ INTERNAL_FETCH_SIZE }} 场，请用赛季/月份缩小范围。
        </p>
      </template>
    </el-select>

    <el-select
      :model-value="season"
      class="season-filter"
      @change="onFilterChange({ season: $event })"
    >
      <el-option label="全部赛季" value="" />
      <el-option
        v-for="value in seasonOptions"
        :key="value"
        :label="`${value} 赛季`"
        :value="value"
      />
    </el-select>

    <el-popover
      v-model:visible="monthOpen"
      trigger="click"
      placement="bottom-start"
      :width="252"
      :disabled="monthDisabled"
      popper-class="month-range-popper"
    >
      <template #reference>
        <div
          class="month-filter"
          :class="{ 'is-disabled': monthDisabled }"
          role="button"
          :tabindex="monthDisabled ? -1 : 0"
          :aria-disabled="monthDisabled"
          aria-haspopup="dialog"
          :aria-expanded="monthOpen"
          :title="monthDisabled ? '全部赛季不筛选月份' : undefined"
          @keydown.enter.prevent="toggleMonthPanel"
          @keydown.space.prevent="toggleMonthPanel"
        >
          <svg
            class="month-filter__icon"
            viewBox="0 0 1024 1024"
            width="14"
            height="14"
            aria-hidden="true"
          >
            <path
              fill="currentColor"
              d="M128 384v512h768V192H768v32a32 32 0 1 1-64 0v-32H320v32a32 32 0 0 1-64 0v-32H128v128h768v64zm192-256h384V96a32 32 0 1 1 64 0v32h160a32 32 0 0 1 32 32v768a32 32 0 0 1-32 32H96a32 32 0 0 1-32-32V160a32 32 0 0 1 32-32h160V96a32 32 0 0 1 64 0zm-32 384h64a32 32 0 0 1 0 64h-64a32 32 0 0 1 0-64m0 192h64a32 32 0 1 1 0 64h-64a32 32 0 1 1 0-64m192-192h64a32 32 0 0 1 0 64h-64a32 32 0 0 1 0-64m0 192h64a32 32 0 1 1 0 64h-64a32 32 0 1 1 0-64m192-192h64a32 32 0 1 1 0 64h-64a32 32 0 1 1 0-64m0 192h64a32 32 0 1 1 0 64h-64a32 32 0 1 1 0-64"
            />
          </svg>
          <span
            class="month-filter__value"
            :class="{ 'is-placeholder': !monthRange && !monthDisabled }"
          >
            {{ monthLabel }}
          </span>
          <span
            v-if="monthRange && !monthDisabled"
            class="month-filter__clear"
            role="button"
            tabindex="0"
            aria-label="清除月份"
            @click.stop="clearMonthRange"
            @keydown.enter.stop.prevent="clearMonthRange"
          >
            ×
          </span>
        </div>
      </template>
      <div class="month-panel" role="dialog" aria-label="选择月份范围">
        <button
          v-for="month in months"
          :key="month"
          type="button"
          class="month-panel__cell"
          :class="{ 'is-in-range': isMonthInRange(month), 'is-edge': isMonthEdge(month) }"
          :aria-pressed="isMonthInRange(month)"
          @click="pickMonth(month)"
          @mouseenter="hoverMonth = month"
          @mouseleave="hoverMonth = null"
        >
          {{ monthText(month) }}
        </button>
      </div>
    </el-popover>

    <el-select
      :model-value="status"
      class="status-filter"
      placeholder="全部状态"
      @change="onFilterChange({ status: $event })"
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

.month-filter {
  width: 280px;
  max-width: 100%;
  display: flex;
  align-items: center;
  gap: 8px;
  height: 42px;
  padding: 0 12px;
  border: 1px solid var(--el-border-color);
  border-radius: 9px;
  background: var(--el-fill-color-blank);
  color: var(--el-text-color-regular);
  font-size: 14px;
  cursor: pointer;
  box-sizing: border-box;
}

.month-filter:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 1px;
}

.month-filter.is-disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.month-filter__icon {
  flex: none;
  color: var(--el-text-color-secondary);
}

.month-filter__value {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.month-filter__value.is-placeholder {
  color: var(--el-text-color-placeholder);
}

.month-filter__clear {
  flex: none;
  color: var(--el-text-color-secondary);
  font-size: 16px;
  line-height: 1;
}

.month-filter__clear:hover {
  color: var(--el-text-color-primary);
}

.match-picker-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  .month-filter {
    width: 100%;
  }
}
</style>

<style>
/* el-popover 内容 teleport 到 body，scoped 样式匹配不到。 */
.month-range-popper.el-popper {
  padding: 0;
}

.month-range-popper .month-panel {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  width: 252px;
  padding: 12px;
  box-sizing: border-box;
}

.month-range-popper .month-panel__cell {
  height: 36px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 13px;
  cursor: pointer;
}

.month-range-popper .month-panel__cell:hover {
  background: var(--el-color-primary-light-9);
}

.month-range-popper .month-panel__cell.is-in-range {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
}

.month-range-popper .month-panel__cell.is-edge {
  background: var(--el-color-primary);
  color: #fff;
}
</style>
