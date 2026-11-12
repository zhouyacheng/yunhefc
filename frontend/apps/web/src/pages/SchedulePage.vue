<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { isApiError } from '@yunhefc/api-client';
import type { MatchSummary, Page } from '@yunhefc/shared';
import { MATCH_STATUS_TEXT } from '@yunhefc/shared';
import { Button, DropdownItem, DropdownMenu, Empty, Loading, Pagination } from 'vant';
import { onMounted, ref } from 'vue';

import { api } from '../api/client';
import MatchCard from '../components/MatchCard.vue';

const statusOptions = [
  { text: '全部状态', value: 'all' },
  ...Object.entries(MATCH_STATUS_TEXT).map(([value, text]) => ({
    text,
    value: Number(value),
  })),
];

const rows = ref<MatchSummary[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const statusValue = ref<string | number>('all');
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

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
    if (statusValue.value !== 'all') {
      query.status = Number(statusValue.value);
    }
    const result = await api.get<Page<MatchSummary>>('/matches', { query });
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

function reload(): void {
  page.value = 1;
  void load();
}

function changePageSize(event: Event): void {
  pageSize.value = Number((event.target as HTMLSelectElement).value);
  reload();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>赛程</h1>
      <p class="muted">共 {{ total }} 场比赛 · 按开赛时间降序</p>
    </header>

    <DropdownMenu class="filters">
      <DropdownItem v-model="statusValue" :options="statusOptions" @change="reload" />
    </DropdownMenu>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load">重试</Button>
    </div>

    <Empty v-else-if="rows.length === 0" description="暂无符合条件的比赛" />

    <div v-else class="match-list">
      <RouterLink
        v-for="match in rows"
        :key="match.id"
        class="match-card-link"
        :to="`/matches/${match.id}`"
      >
        <MatchCard :match="match" />
      </RouterLink>
    </div>

    <footer v-if="total > 0" class="page-footer">
      <label class="page-size">
        每页
        <select :value="pageSize" @change="changePageSize">
          <option :value="10">10</option>
          <option :value="20">20</option>
          <option :value="50">50</option>
          <option :value="100">100</option>
        </select>
        条
      </label>
      <Pagination
        v-model="page"
        mode="simple"
        :total-items="total"
        :items-per-page="pageSize"
        @change="load"
      />
    </footer>
  </section>
</template>
