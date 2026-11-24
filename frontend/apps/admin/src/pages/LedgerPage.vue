<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { describeError, isVersionConflict, newIdempotencyKey } from '@yunhefc/api-client';
import { LEDGER_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  ActivitySummary,
  LedgerCategory,
  LedgerEntry,
  LedgerSummary,
  LedgerType,
  Page,
  PlayerAdmin,
} from '@yunhefc/shared';
import { formatAmount, formatDateTime } from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api, publicApi } from '../api/client';
import { fromPickerDate, toPickerDate } from '../features/time/picker';
import {
  LEDGER_DIRECTION_OPTIONS,
  LEDGER_TYPE_OPTIONS,
  categoriesForType,
  ledgerDirectionText,
  ledgerTypeText,
  validateLedgerAmount,
} from '../features/ledger/form';
import { useSessionStore } from '../stores/session';

interface ItemsResponse<T> {
  items: T[];
}

interface LedgerFormState {
  type: LedgerType;
  categoryId: number | null;
  amount: string;
  description: string;
  occurredAt: Date | null;
  playerId: number | null;
  activityId: number | null;
}

interface LedgerEntryPayload {
  type: LedgerType;
  categoryId: number;
  amount: number;
  description: string;
  occurredAt: string;
  playerId: number | null;
  activityId: number | null;
  version?: number;
}

interface CategoryFormState {
  name: string;
  direction: '' | LedgerType;
  sortNo: number;
}

interface PlayerOption {
  id: number;
  name: string;
}

interface ActivityOption {
  id: number;
  title: string;
}

const session = useSessionStore();
const canManage = computed(() => hasPermission(session.permissions, LEDGER_MANAGE_ANY));

const rows = ref<LedgerEntry[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const typeFilter = ref('');
const categoryFilter = ref('');
const playerFilter = ref('');
const activityFilter = ref('');
const dateRange = ref<[string, string] | null>(null);
const keyword = ref('');
const loading = ref(false);
const error = ref('');
const summary = ref<LedgerSummary>({ income: 0, expense: 0, balance: 0 });
const summaryLoading = ref(false);
const summaryError = ref('');
let entrySeq = 0;
let summarySeq = 0;

const activeCategories = ref<LedgerCategory[]>([]);
const allCategories = ref<LedgerCategory[]>([]);
const categoryError = ref('');
const playerOptions = ref<PlayerOption[]>([]);
const activityOptions = ref<ActivityOption[]>([]);

const entryDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  submitting: false,
  error: '',
  entry: null as LedgerEntry | null,
});
const entryForm = reactive<LedgerFormState>(defaultEntryForm());

const categoryDrawer = reactive({
  visible: false,
  loading: false,
  error: '',
  busy: false,
});
const categoryDialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  submitting: false,
  error: '',
  category: null as LedgerCategory | null,
});
const categoryForm = reactive<CategoryFormState>({ name: '', direction: '', sortNo: 0 });

const hasActiveFilters = computed(
  () =>
    !isBlank(typeFilter.value) ||
    !isBlank(categoryFilter.value) ||
    !isBlank(playerFilter.value) ||
    !isBlank(activityFilter.value) ||
    dateRange.value !== null ||
    keyword.value.trim() !== '',
);

/** el-select 清空后可能回写 undefined/null，统一视为未选择。 */
function isBlank(value: unknown): boolean {
  return value === '' || value === null || value === undefined;
}

const filterCategoryOptions = computed(() =>
  isBlank(typeFilter.value)
    ? activeCategories.value
    : categoriesForType(activeCategories.value, Number(typeFilter.value) as LedgerType),
);

const formCategoryOptions = computed(() => {
  const options = categoriesForType(activeCategories.value, entryForm.type);
  const current = allCategories.value.find((category) => category.id === entryForm.categoryId);
  if (current && !options.some((category) => category.id === current.id)) {
    return [...options, current];
  }
  return options;
});

const entryDialogTitle = computed(() => (entryDialog.mode === 'create' ? '新增流水' : '编辑流水'));
const categoryDialogTitle = computed(() =>
  categoryDialog.mode === 'create' ? '新增分类' : '编辑分类',
);

function nowPickerDate(): Date {
  return toPickerDate(new Date().toISOString())!;
}

function defaultEntryForm(): LedgerFormState {
  return {
    type: 1,
    categoryId: null,
    amount: '',
    description: '',
    occurredAt: nowPickerDate(),
    playerId: null,
    activityId: null,
  };
}

function buildFilterQuery(): QueryParams {
  const query: QueryParams = {};
  if (!isBlank(typeFilter.value)) {
    query.type = Number(typeFilter.value);
  }
  if (!isBlank(categoryFilter.value)) {
    query.categoryId = Number(categoryFilter.value);
  }
  if (!isBlank(playerFilter.value)) {
    query.playerId = Number(playerFilter.value);
  }
  if (!isBlank(activityFilter.value)) {
    query.activityId = Number(activityFilter.value);
  }
  if (dateRange.value) {
    // 05 LED-01：时间用 ISO-8601 +08:00；to 为闭区间，取当日最后一毫秒。
    query.from = `${dateRange.value[0]}T00:00:00+08:00`;
    query.to = `${dateRange.value[1]}T23:59:59.999+08:00`;
  }
  return query;
}

async function loadEntries(): Promise<void> {
  const seq = ++entrySeq;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<Page<LedgerEntry>>('/ledger/entries', {
      query: {
        ...buildFilterQuery(),
        keyword: keyword.value.trim(),
        page: page.value,
        pageSize: pageSize.value,
      },
    });
    if (seq !== entrySeq) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
  } catch (caught) {
    if (seq !== entrySeq) {
      return;
    }
    error.value = describeError(caught, '加载失败，请稍后重试');
  } finally {
    if (seq === entrySeq) {
      loading.value = false;
    }
  }
}

async function loadSummary(): Promise<void> {
  const seq = ++summarySeq;
  summaryLoading.value = true;
  summaryError.value = '';
  try {
    const result = await api.get<LedgerSummary>('/ledger/summary', {
      query: buildFilterQuery(),
    });
    if (seq !== summarySeq) {
      return;
    }
    summary.value = result;
  } catch (caught) {
    if (seq !== summarySeq) {
      return;
    }
    summaryError.value = describeError(caught, '汇总加载失败');
  } finally {
    if (seq === summarySeq) {
      summaryLoading.value = false;
    }
  }
}

async function refreshAll(): Promise<void> {
  await Promise.all([loadEntries(), loadSummary()]);
}

async function loadCategories(): Promise<void> {
  categoryError.value = '';
  try {
    if (canManage.value) {
      const [active, all] = await Promise.all([
        api.get<ItemsResponse<LedgerCategory>>('/ledger/categories', {
          query: { includeInactive: false },
        }),
        api.get<ItemsResponse<LedgerCategory>>('/ledger/categories'),
      ]);
      activeCategories.value = active.items;
      allCategories.value = all.items;
      clearInactiveCategoryFilter();
      return;
    }
    const result = await api.get<ItemsResponse<LedgerCategory>>('/ledger/categories/active');
    activeCategories.value = result.items;
    allCategories.value = result.items;
    clearInactiveCategoryFilter();
  } catch (caught) {
    categoryError.value = describeError(caught, '分类加载失败');
  }
}

/** 分类停用后不再出现在筛选；已选中的停用分类自动清空。 */
function clearInactiveCategoryFilter(): void {
  if (
    !isBlank(categoryFilter.value) &&
    !activeCategories.value.some((category) => category.id === Number(categoryFilter.value))
  ) {
    categoryFilter.value = '';
  }
}

async function loadPlayers(): Promise<void> {
  try {
    if (canManage.value) {
      const result = await api.get<Page<PlayerAdmin>>('/players', {
        query: { pageSize: 100, sort: 'jersey:asc' },
      });
      playerOptions.value = result.items.map((player) => ({
        id: player.id,
        name: player.jersey == null ? player.name : `${player.jersey} ${player.name}`,
      }));
      return;
    }
    const result = await publicApi.get<
      ItemsResponse<{ id: number; name: string; jersey: number | null }>
    >('/team/players', { query: { all: true } });
    playerOptions.value = result.items.map((player) => ({
      id: player.id,
      name: player.jersey == null ? player.name : `${player.jersey} ${player.name}`,
    }));
  } catch {
    playerOptions.value = [];
  }
}

async function loadActivities(): Promise<void> {
  try {
    const result = await publicApi.get<Page<ActivitySummary>>('/activities', {
      query: { scope: 'all', pageSize: 100 },
    });
    activityOptions.value = result.items.map((activity) => ({
      id: activity.id,
      title: activity.title,
    }));
  } catch {
    activityOptions.value = [];
  }
}

function onFilterChange(): void {
  page.value = 1;
  void refreshAll();
}

function onTypeFilterChange(): void {
  // 方向清空时分类不再受限；有方向时只保留该方向或通用分类。
  if (!isBlank(typeFilter.value) && !isBlank(categoryFilter.value)) {
    const selected = activeCategories.value.find(
      (category) => category.id === Number(categoryFilter.value),
    );
    if (
      !selected ||
      (selected.direction !== null && selected.direction !== Number(typeFilter.value))
    ) {
      categoryFilter.value = '';
    }
  }
  onFilterChange();
}

function resetFilters(): void {
  typeFilter.value = '';
  categoryFilter.value = '';
  playerFilter.value = '';
  activityFilter.value = '';
  dateRange.value = null;
  keyword.value = '';
  page.value = 1;
  void refreshAll();
}

function onPageChange(): void {
  void loadEntries();
}

function onSizeChange(): void {
  page.value = 1;
  void loadEntries();
}

// ---------------------------------------------------------------- entry form

function openCreateEntry(): void {
  if (!canManage.value) {
    return;
  }
  Object.assign(entryForm, defaultEntryForm());
  entryDialog.mode = 'create';
  entryDialog.entry = null;
  entryDialog.error = '';
  entryDialog.visible = true;
}

function openEditEntry(row: LedgerEntry): void {
  if (!canManage.value) {
    return;
  }
  entryForm.type = row.type;
  entryForm.categoryId = row.categoryId;
  entryForm.amount = row.amount.toFixed(2);
  entryForm.description = row.description;
  entryForm.occurredAt = toPickerDate(row.occurredAt);
  entryForm.playerId = row.playerId;
  entryForm.activityId = row.activityId;
  entryDialog.mode = 'edit';
  entryDialog.entry = row;
  entryDialog.error = '';
  entryDialog.visible = true;
}

function onFormTypeChange(): void {
  const selected = allCategories.value.find((category) => category.id === entryForm.categoryId);
  if (selected && selected.direction !== null && selected.direction !== entryForm.type) {
    entryForm.categoryId = null;
  }
}

function entryFormError(): string | null {
  if (!entryForm.categoryId) {
    return '请选择分类';
  }
  const amountError = validateLedgerAmount(entryForm.amount);
  if (amountError) {
    return amountError;
  }
  if (!entryForm.occurredAt) {
    return '请选择发生时间';
  }
  const description = entryForm.description.trim();
  if (!description) {
    return '请输入摘要';
  }
  if (description.length > 255) {
    return '摘要不能超过 255 字';
  }
  return null;
}

/** el-select 清空后可能回写 undefined/''，更新请求需显式传 null 才能清空关联。 */
function optionalId(value: unknown): number | null {
  return typeof value === 'number' ? value : null;
}

function buildEntryPayload(version?: number): LedgerEntryPayload {
  const payload: LedgerEntryPayload = {
    type: entryForm.type,
    categoryId: entryForm.categoryId!,
    amount: Number(entryForm.amount.trim()),
    description: entryForm.description.trim(),
    occurredAt: fromPickerDate(entryForm.occurredAt)!.toISOString(),
    playerId: optionalId(entryForm.playerId),
    activityId: optionalId(entryForm.activityId),
  };
  if (version !== undefined) {
    payload.version = version;
  }
  return payload;
}

async function submitEntry(): Promise<void> {
  if (entryDialog.submitting) {
    return;
  }
  const validationError = entryFormError();
  if (validationError) {
    entryDialog.error = validationError;
    return;
  }
  entryDialog.submitting = true;
  entryDialog.error = '';
  try {
    if (entryDialog.mode === 'create') {
      await api.post<LedgerEntry>('/ledger/entries', buildEntryPayload(), {
        idempotencyKey: newIdempotencyKey(),
      });
      entryDialog.visible = false;
      page.value = 1;
      ElMessage.success('流水已新增');
      await refreshAll();
      return;
    }
    const target = entryDialog.entry;
    if (!target) {
      return;
    }
    await api.patch<LedgerEntry>(
      `/ledger/entries/${target.id}`,
      buildEntryPayload(target.version),
      {
        idempotencyKey: newIdempotencyKey(),
      },
    );
    entryDialog.visible = false;
    ElMessage.success('流水已更新');
    await refreshAll();
  } catch (caught) {
    if (isVersionConflict(caught)) {
      entryDialog.visible = false;
      ElMessage.error('已被他人修改，请刷新后重试');
      await refreshAll();
      return;
    }
    entryDialog.error = describeError(caught, '保存失败，请稍后重试');
  } finally {
    entryDialog.submitting = false;
  }
}

async function deleteEntry(row: LedgerEntry): Promise<void> {
  if (!canManage.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认删除「${row.description}」（${ledgerTypeText(row.type)} ¥${formatAmount(row.amount)}）？`,
      '删除流水',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '返回' },
    );
  } catch {
    return;
  }
  try {
    await api.delete(
      '/ledger/entries/' + row.id,
      { version: row.version },
      {
        idempotencyKey: newIdempotencyKey(),
      },
    );
    ElMessage.success('流水已删除');
    if (rows.value.length === 1 && page.value > 1) {
      page.value -= 1;
    }
    await refreshAll();
  } catch (caught) {
    if (isVersionConflict(caught)) {
      ElMessage.error('已被他人修改，请刷新后重试');
      await refreshAll();
      return;
    }
    ElMessage.error(describeError(caught, '删除失败，请稍后重试'));
  }
}

// ---------------------------------------------------------------- categories

async function openCategoryDrawer(): Promise<void> {
  if (!canManage.value) {
    return;
  }
  categoryDrawer.error = '';
  categoryDrawer.visible = true;
  categoryDrawer.loading = true;
  await loadCategories();
  categoryDrawer.loading = false;
}

function openCreateCategory(): void {
  categoryForm.name = '';
  categoryForm.direction = '';
  categoryForm.sortNo = 0;
  categoryDialog.mode = 'create';
  categoryDialog.category = null;
  categoryDialog.error = '';
  categoryDialog.visible = true;
}

function openEditCategory(category: LedgerCategory): void {
  categoryForm.name = category.name;
  categoryForm.direction = category.direction === null ? '' : category.direction;
  categoryForm.sortNo = category.sortNo;
  categoryDialog.mode = 'edit';
  categoryDialog.category = category;
  categoryDialog.error = '';
  categoryDialog.visible = true;
}

function categoryFormError(): string | null {
  const name = categoryForm.name.trim();
  if (!name) {
    return '请输入分类名称';
  }
  if (name.length > 32) {
    return '分类名称不能超过 32 字';
  }
  if (
    !Number.isInteger(categoryForm.sortNo) ||
    categoryForm.sortNo < 0 ||
    categoryForm.sortNo > 65535
  ) {
    return '排序必须为 0–65535 的整数';
  }
  return null;
}

function categoryPayload(): { name: string; direction: LedgerType | null; sortNo: number } {
  return {
    name: categoryForm.name.trim(),
    direction: categoryForm.direction === '' ? null : categoryForm.direction,
    sortNo: categoryForm.sortNo,
  };
}

async function submitCategory(): Promise<void> {
  if (categoryDialog.submitting) {
    return;
  }
  const validationError = categoryFormError();
  if (validationError) {
    categoryDialog.error = validationError;
    return;
  }
  categoryDialog.submitting = true;
  categoryDialog.error = '';
  try {
    if (categoryDialog.mode === 'create') {
      await api.post<LedgerCategory>('/ledger/categories', categoryPayload(), {
        idempotencyKey: newIdempotencyKey(),
      });
    } else {
      const target = categoryDialog.category;
      if (!target) {
        return;
      }
      await api.patch<LedgerCategory>(`/ledger/categories/${target.id}`, categoryPayload(), {
        idempotencyKey: newIdempotencyKey(),
      });
    }
    categoryDialog.visible = false;
    ElMessage.success('分类已保存');
    await loadCategories();
    await refreshAll();
  } catch (caught) {
    categoryDialog.error = describeError(caught, '保存失败，请稍后重试');
  } finally {
    categoryDialog.submitting = false;
  }
}

async function toggleCategory(category: LedgerCategory): Promise<void> {
  if (!canManage.value || categoryDrawer.busy) {
    return;
  }
  if (category.isActive) {
    try {
      await ElMessageBox.confirm(
        `停用「${category.name}」后不再出现在新建与筛选下拉，历史流水仍显示原分类。`,
        '停用分类',
        { type: 'warning', confirmButtonText: '停用', cancelButtonText: '返回' },
      );
    } catch {
      return;
    }
  }
  categoryDrawer.busy = true;
  categoryDrawer.error = '';
  try {
    await api.patch<LedgerCategory>(
      `/ledger/categories/${category.id}`,
      { isActive: !category.isActive },
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success(category.isActive ? '分类已停用' : '分类已启用');
    await loadCategories();
    await refreshAll();
  } catch (caught) {
    categoryDrawer.error = describeError(caught, '操作失败，请稍后重试');
  } finally {
    categoryDrawer.busy = false;
  }
}

function directionTag(category: LedgerCategory): 'success' | 'danger' | 'info' {
  if (category.direction === 0) {
    return 'success';
  }
  return category.direction === 1 ? 'danger' : 'info';
}

onMounted(() => {
  void loadCategories();
  void loadPlayers();
  void loadActivities();
  void refreshAll();
});
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>账本管理</h2>
        <p class="muted">记录球队收支；汇总为筛选区间内的收入 − 支出，不做期初余额。</p>
      </div>
      <div class="head-actions">
        <el-button v-if="canManage" @click="openCategoryDrawer">分类管理</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreateEntry">新增流水</el-button>
      </div>
    </div>

    <el-alert
      v-if="categoryError"
      class="page-error"
      type="warning"
      :title="categoryError"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="loadCategories">重试</el-button>
    </el-alert>

    <div v-loading="summaryLoading" class="summary-cards">
      <div class="summary-card income">
        <span>收入</span>
        <strong>¥{{ formatAmount(summary.income) }}</strong>
      </div>
      <div class="summary-card expense">
        <span>支出</span>
        <strong>¥{{ formatAmount(summary.expense) }}</strong>
      </div>
      <div class="summary-card balance">
        <span>余额</span>
        <strong>¥{{ formatAmount(summary.balance) }}</strong>
      </div>
    </div>
    <el-alert
      v-if="summaryError"
      class="page-error"
      type="error"
      :title="summaryError"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="loadSummary">重试</el-button>
    </el-alert>

    <div class="toolbar">
      <el-select
        v-model="typeFilter"
        class="short-filter"
        placeholder="全部方向"
        clearable
        @change="onTypeFilterChange"
      >
        <el-option
          v-for="option in LEDGER_TYPE_OPTIONS"
          :key="option.value"
          :label="option.text"
          :value="String(option.value)"
        />
      </el-select>
      <el-select
        v-model="categoryFilter"
        class="category-filter"
        placeholder="全部分类"
        filterable
        clearable
        @change="onFilterChange"
      >
        <el-option
          v-for="option in filterCategoryOptions"
          :key="option.id"
          :label="option.name"
          :value="String(option.id)"
        />
      </el-select>
      <el-select
        v-model="playerFilter"
        class="player-filter"
        placeholder="全部球员"
        filterable
        clearable
        @change="onFilterChange"
      >
        <el-option
          v-for="option in playerOptions"
          :key="option.id"
          :label="option.name"
          :value="String(option.id)"
        />
      </el-select>
      <el-select
        v-model="activityFilter"
        class="activity-filter"
        placeholder="全部活动"
        filterable
        clearable
        @change="onFilterChange"
      >
        <el-option
          v-for="option in activityOptions"
          :key="option.id"
          :label="option.title"
          :value="String(option.id)"
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
      <el-input
        v-model="keyword"
        class="keyword-filter"
        placeholder="摘要关键字"
        maxlength="64"
        clearable
        @keyup.enter="onFilterChange"
        @clear="onFilterChange"
      />
      <el-button :disabled="!hasActiveFilters" @click="resetFilters">清空条件</el-button>
    </div>

    <el-alert
      v-if="error"
      class="page-error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="loadEntries">重试</el-button>
    </el-alert>

    <el-table v-loading="loading" :data="rows" empty-text="暂无流水" stripe>
      <el-table-column label="发生时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.occurredAt) }}</template>
      </el-table-column>
      <el-table-column label="方向" width="90">
        <template #default="{ row }">
          <el-tag :type="row.type === 0 ? 'success' : 'danger'" effect="light">
            {{ ledgerTypeText(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="分类" prop="categoryName" min-width="110">
        <template #default="{ row }">{{ row.categoryName || '—' }}</template>
      </el-table-column>
      <el-table-column label="金额" width="130" align="right">
        <template #default="{ row }">
          <span :class="row.type === 0 ? 'amount-income' : 'amount-expense'">
            {{ row.type === 0 ? '+' : '−' }}¥{{ formatAmount(row.amount) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="摘要" prop="description" min-width="200" />
      <el-table-column label="归属球员" min-width="110">
        <template #default="{ row }">{{ row.playerName || '—' }}</template>
      </el-table-column>
      <el-table-column label="关联活动" min-width="140">
        <template #default="{ row }">{{ row.activityTitle || '—' }}</template>
      </el-table-column>
      <el-table-column v-if="canManage" label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" size="small" @click="openEditEntry(row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="deleteEntry(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="entryDialog.visible" :title="entryDialogTitle" width="560px">
      <el-form label-width="96px">
        <el-form-item label="方向">
          <el-radio-group v-model="entryForm.type" @change="onFormTypeChange">
            <el-radio :value="0">收入</el-radio>
            <el-radio :value="1">支出</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="分类">
          <el-select
            v-model="entryForm.categoryId"
            class="full"
            filterable
            placeholder="请选择分类"
          >
            <el-option
              v-for="option in formCategoryOptions"
              :key="option.id"
              :label="option.isActive ? option.name : `${option.name}（已停用）`"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input
            v-model="entryForm.amount"
            class="amount-input"
            maxlength="12"
            placeholder="0.00"
          >
            <template #prefix>¥</template>
          </el-input>
          <span class="muted small">大于 0，最多两位小数。</span>
        </el-form-item>
        <el-form-item label="发生时间">
          <el-date-picker
            v-model="entryForm.occurredAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="发生时间"
            class="full"
          />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input
            v-model="entryForm.description"
            maxlength="255"
            show-word-limit
            placeholder="例如 场地费、队费收入"
          />
        </el-form-item>
        <el-form-item label="归属球员">
          <el-select
            v-model="entryForm.playerId"
            class="full"
            filterable
            clearable
            placeholder="可选"
          >
            <el-option
              v-for="option in playerOptions"
              :key="option.id"
              :label="option.name"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关联活动">
          <el-select
            v-model="entryForm.activityId"
            class="full"
            filterable
            clearable
            placeholder="可选"
          >
            <el-option
              v-for="option in activityOptions"
              :key="option.id"
              :label="option.title"
              :value="option.id"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="entryDialog.error" class="dialog-error">{{ entryDialog.error }}</p>
      <template #footer>
        <el-button @click="entryDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="entryDialog.submitting" @click="submitEntry">
          {{ entryDialog.mode === 'create' ? '新增流水' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="categoryDrawer.visible" title="分类管理" size="640px">
      <div class="category-head">
        <span class="muted small">分类只停用不删除；停用后不再出现在新建与筛选下拉。</span>
        <el-button type="primary" size="small" @click="openCreateCategory">新增分类</el-button>
      </div>
      <el-alert
        v-if="categoryDrawer.error"
        class="page-error"
        type="error"
        :title="categoryDrawer.error"
        :closable="false"
        show-icon
      />
      <el-table
        v-loading="categoryDrawer.loading"
        :data="allCategories"
        empty-text="暂无分类"
        stripe
      >
        <el-table-column label="名称" prop="name" min-width="120" />
        <el-table-column label="方向" width="90">
          <template #default="{ row }">
            <el-tag :type="directionTag(row)" effect="light">
              {{ ledgerDirectionText(row.direction) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sortNo" width="70" />
        <el-table-column label="使用次数" prop="entryCount" width="90" />
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.isActive ? 'success' : 'info'" effect="light">
              {{ row.isActive ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <div class="row-actions">
              <el-button link type="primary" size="small" @click="openEditCategory(row)">
                编辑
              </el-button>
              <el-button
                link
                :type="row.isActive ? 'danger' : 'primary'"
                size="small"
                :disabled="categoryDrawer.busy"
                @click="toggleCategory(row)"
              >
                {{ row.isActive ? '停用' : '启用' }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="categoryDialog.visible" :title="categoryDialogTitle" width="440px">
      <el-form label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="categoryForm.name" maxlength="32" show-word-limit />
        </el-form-item>
        <el-form-item label="方向">
          <el-select v-model="categoryForm.direction" class="full">
            <el-option
              v-for="option in LEDGER_DIRECTION_OPTIONS"
              :key="option.text"
              :label="option.text"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number
            v-model="categoryForm.sortNo"
            :min="0"
            :max="65535"
            controls-position="right"
          />
        </el-form-item>
      </el-form>
      <p v-if="categoryDialog.error" class="dialog-error">{{ categoryDialog.error }}</p>
      <template #footer>
        <el-button @click="categoryDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="categoryDialog.submitting" @click="submitCategory">
          保存
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-head {
  margin-bottom: 16px;
}

.head-actions {
  display: flex;
  gap: 8px;
}

.summary-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 16px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  background: var(--el-fill-color-blank);
}

.summary-card span {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.summary-card strong {
  font-size: 22px;
  font-variant-numeric: tabular-nums;
}

.summary-card.income strong {
  color: var(--el-color-success);
}

.summary-card.expense strong {
  color: var(--el-color-danger);
}

.summary-card.balance strong {
  color: var(--el-color-primary);
}

.toolbar {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.short-filter {
  width: 120px;
}

.category-filter {
  width: 150px;
}

.player-filter,
.activity-filter {
  width: 170px;
}

.date-filter {
  width: 260px;
}

.keyword-filter {
  width: 180px;
}

.amount-input {
  width: 160px;
}

.amount-income {
  color: var(--el-color-success);
  font-variant-numeric: tabular-nums;
}

.amount-expense {
  color: var(--el-color-danger);
  font-variant-numeric: tabular-nums;
}

.category-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
</style>
