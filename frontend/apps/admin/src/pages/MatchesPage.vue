<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { isApiError } from '@yunhefc/api-client';
import type { MatchAdmin, Page } from '@yunhefc/shared';
import {
  MATCH_STATUS_TEXT,
  formatDateTime,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
} from '@yunhefc/shared';
import { onMounted, ref } from 'vue';

import { api } from '../api/client';

const statusOptions = Object.entries(MATCH_STATUS_TEXT).map(([value, text]) => ({
  value: String(value),
  text,
}));

const rows = ref<MatchAdmin[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const status = ref<string | undefined>(undefined);
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

function statusTag(statusCode: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const tone = matchStatusTone(statusCode);
  return tone === 'neutral' ? 'info' : tone;
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const query: QueryParams = {
      scope: 'all',
      sort: 'startAt:desc',
      page: page.value,
      pageSize: pageSize.value,
    };
    if (status.value !== undefined && status.value !== '') {
      query.status = Number(status.value);
    }
    const result = await api.get<Page<MatchAdmin>>('/matches', { query });
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

function onStatusChange(): void {
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

onMounted(load);
</script>

<template>
  <section>
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

    <div class="toolbar">
      <el-select
        v-model="status"
        class="status-filter"
        placeholder="全部状态"
        clearable
        @change="onStatusChange"
      >
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.text"
          :value="option.value"
        />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无比赛" stripe>
      <el-table-column label="比赛" prop="title" min-width="200" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ matchTypeText(row.type) }}</template>
      </el-table-column>
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" effect="light">
            {{ matchStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报名（正式/外挂/候补）" min-width="180">
        <template #default="{ row }">
          {{ row.confirmedCount }} / {{ row.guestCount }} / {{ row.waitlistCount }}
        </template>
      </el-table-column>
      <el-table-column label="场地" prop="venue" min-width="150" />
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
