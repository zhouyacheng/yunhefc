<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { isApiError } from '@yunhefc/api-client';
import type { AuditLog, Page } from '@yunhefc/shared';
import {
  AUDIT_ACTION_TEXT,
  AUDIT_TARGET_TYPE_TEXT,
  auditActionText,
  auditTargetTypeText,
  formatDateTime,
  isEmptySnapshot,
} from '@yunhefc/shared';
import { onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';

interface AuditFilters {
  range: [Date, Date] | null;
  actor: string;
  action: string | undefined;
  targetType: string | undefined;
  targetId: number | null;
}

const ACTION_OPTIONS = Object.entries(AUDIT_ACTION_TEXT).map(([value, label]) => ({
  value,
  label,
}));
const TARGET_TYPE_OPTIONS = Object.entries(AUDIT_TARGET_TYPE_TEXT).map(([value, label]) => ({
  value,
  label,
}));

function emptyFilters(): AuditFilters {
  return {
    range: null,
    actor: '',
    action: undefined,
    targetType: undefined,
    targetId: null,
  };
}

const filters = reactive<AuditFilters>(emptyFilters());
const rows = ref<AuditLog[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

function buildQuery(): QueryParams {
  const query: QueryParams = { page: page.value, pageSize: pageSize.value };
  const actor = filters.actor.trim();
  if (actor) {
    query.actor = actor;
  }
  if (filters.action) {
    query.action = filters.action;
  }
  if (filters.targetType) {
    query.targetType = filters.targetType;
  }
  if (filters.targetId !== null) {
    query.targetId = filters.targetId;
  }
  if (filters.range) {
    query.from = filters.range[0].toISOString();
    query.to = filters.range[1].toISOString();
  }
  return query;
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<Page<AuditLog>>('/audit-logs', { query: buildQuery() });
    if (seq !== requestSeq) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

function search(): void {
  page.value = 1;
  void load();
}

function resetFilters(): void {
  Object.assign(filters, emptyFilters());
  page.value = 1;
  void load();
}

function onPageChange(): void {
  void load();
}

function onSizeChange(): void {
  page.value = 1;
  void load();
}

/** 展开行只渲染非空快照；两者都为空时显示「无变更快照」。 */
function snapshots(row: AuditLog): { label: string; value: unknown }[] {
  return [
    { label: '变更前', value: row.beforeRedacted },
    { label: '变更后', value: row.afterRedacted },
  ].filter((snapshot) => !isEmptySnapshot(snapshot.value));
}

function formatSnapshot(value: unknown): string {
  return JSON.stringify(value, null, 2);
}

/** 后端仅在操作人完全缺失时回填「系统」；有用户 ID 但无姓名时避免误标为系统。 */
function actorText(row: AuditLog): string {
  if (!row.actor) {
    return '系统';
  }
  if (row.actor.name) {
    return row.actor.name;
  }
  return row.actor.id !== null ? `用户 #${row.actor.id}` : '系统';
}

onMounted(load);
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>操作日志</h2>
        <p class="muted">只读；按时间倒序展示脱敏后的变更快照，不支持编辑、删除或导出。</p>
      </div>
    </div>

    <el-alert
      v-if="error"
      class="page-error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <el-form class="audit-filters" :inline="true" @submit.prevent="search">
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.range"
          class="filter-range"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
        />
      </el-form-item>
      <el-form-item label="操作人">
        <el-input
          v-model="filters.actor"
          class="filter-input"
          maxlength="64"
          placeholder="姓名或用户 ID"
          clearable
        />
      </el-form-item>
      <el-form-item label="动作">
        <el-select
          v-model="filters.action"
          class="filter-select"
          filterable
          allow-create
          clearable
          default-first-option
          placeholder="全部"
        >
          <el-option
            v-for="option in ACTION_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="对象类型">
        <el-select
          v-model="filters.targetType"
          class="filter-select"
          filterable
          allow-create
          clearable
          default-first-option
          placeholder="全部"
        >
          <el-option
            v-for="option in TARGET_TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="对象 ID">
        <el-input-number
          v-model="filters.targetId"
          class="filter-id"
          :min="1"
          :precision="0"
          :value-on-clear="null"
          controls-position="right"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="search">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      empty-text="没有符合条件的操作日志"
      stripe
    >
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="snapshot">
            <p v-if="!snapshots(row).length" class="muted">无变更快照</p>
            <div v-for="snapshot in snapshots(row)" :key="snapshot.label" class="snapshot-block">
              <h4>{{ snapshot.label }}</h4>
              <pre>{{ formatSnapshot(snapshot.value) }}</pre>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作人" width="130">
        <template #default="{ row }">{{ actorText(row) }}</template>
      </el-table-column>
      <el-table-column label="动作" min-width="160">
        <template #default="{ row }">{{ auditActionText(row.action) }}</template>
      </el-table-column>
      <el-table-column label="对象类型" width="120">
        <template #default="{ row }">{{ auditTargetTypeText(row.targetType) }}</template>
      </el-table-column>
      <el-table-column label="对象 ID" width="110">
        <template #default="{ row }">{{ row.targetId ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="IP" width="140">
        <template #default="{ row }">{{ row.ipMasked ?? '—' }}</template>
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
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.audit-filters {
  margin-bottom: 8px;
}

.filter-range {
  width: 380px;
}

.filter-input {
  width: 170px;
}

.filter-select {
  width: 190px;
}

.filter-id {
  width: 130px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.snapshot {
  padding: 4px 16px 12px 48px;
}

.snapshot-block + .snapshot-block {
  margin-top: 12px;
}

.snapshot-block h4 {
  margin: 0 0 6px;
  font-size: 13px;
}

.snapshot-block pre {
  margin: 0;
  padding: 10px 12px;
  overflow-x: auto;
  font-size: 12px;
  line-height: 1.5;
  background: var(--el-fill-color-light);
  border-radius: 4px;
}

.muted {
  color: var(--yh-muted);
  font-size: 13px;
}
</style>
