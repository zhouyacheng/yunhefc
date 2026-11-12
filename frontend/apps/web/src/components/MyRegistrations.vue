<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { MyRegistration, Page } from '@yunhefc/shared';
import { MATCH_STATUS_CANCELLED, REGISTRATION_STATUS_TEXT, formatDateTime } from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Tag } from 'vant';
import { computed, onMounted, reactive, ref, watch } from 'vue';

import { api } from '../api/client';

const PAGE_SIZE_OPTIONS = [10, 20, 50, 100] as const;

type Scope = 'active' | 'history';

interface ScopeState {
  items: MyRegistration[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  loading: boolean;
  loaded: boolean;
  error: string;
}

const props = defineProps<{ pending: boolean }>();

const scope = ref<Scope>('active');
const states = reactive<Record<Scope, ScopeState>>({
  active: emptyState(),
  history: emptyState(),
});
const state = computed(() => states[scope.value]);

function emptyState(): ScopeState {
  return {
    items: [],
    page: 1,
    pageSize: 10,
    total: 0,
    totalPages: 0,
    loading: false,
    loaded: false,
    error: '',
  };
}

async function load(target: Scope = scope.value): Promise<void> {
  if (props.pending) {
    return;
  }
  const current = states[target];
  current.loading = true;
  current.error = '';
  try {
    const result = await api.get<Page<MyRegistration>>('/me/registrations', {
      query: { scope: target, page: current.page, pageSize: current.pageSize },
    });
    current.items = result.items;
    current.total = result.total;
    current.totalPages = result.totalPages;
    current.page = result.page;
    current.loaded = true;
  } catch (caught) {
    current.items = [];
    current.error = isApiError(caught) ? caught.message : '报名记录加载失败，请稍后重试';
  } finally {
    current.loading = false;
  }
}

function selectScope(next: Scope): void {
  scope.value = next;
  const current = states[next];
  if (!current.loaded && !current.loading) {
    void load(next);
  }
}

function changePage(next: number): void {
  state.value.page = next;
  void load();
}

function changePageSize(event: Event): void {
  state.value.pageSize = Number((event.target as HTMLSelectElement).value);
  state.value.page = 1;
  void load();
}

function isVoided(row: MyRegistration): boolean {
  return row.match.status === MATCH_STATUS_CANCELLED;
}

function statusText(row: MyRegistration): string {
  if (isVoided(row)) {
    return '已作废';
  }
  // 后端 statusLabel 是英文枚举码，展示层优先使用中文映射。
  return REGISTRATION_STATUS_TEXT[row.registration.status] ?? row.registration.statusLabel ?? '—';
}

function emptyText(): string {
  return scope.value === 'active' ? '暂无进行中报名' : '暂无历史报名';
}

onMounted(() => {
  if (!props.pending) {
    void load();
  }
});

watch(
  () => props.pending,
  (pending) => {
    if (!pending && !state.value.loaded && !state.value.loading) {
      void load();
    }
  },
);
</script>

<template>
  <section class="card my-registrations">
    <div class="card-head">
      <h2>我的报名</h2>
      <span v-if="!pending" class="muted small">{{ state.total }} 条</span>
    </div>

    <p v-if="pending" class="hint">审核通过后可查看我的报名</p>

    <template v-else>
      <div class="segmented" role="tablist" aria-label="我的报名范围">
        <button
          type="button"
          role="tab"
          :aria-selected="scope === 'active'"
          :class="{ 'segmented__item--active': scope === 'active' }"
          @click="selectScope('active')"
        >
          进行中
        </button>
        <button
          type="button"
          role="tab"
          :aria-selected="scope === 'history'"
          :class="{ 'segmented__item--active': scope === 'history' }"
          @click="selectScope('history')"
        >
          历史
        </button>
      </div>

      <div v-if="state.loading" class="state state--inline">
        <Loading size="20">报名记录加载中…</Loading>
      </div>

      <div v-else-if="state.error" class="state state--inline">
        <p class="muted">{{ state.error }}</p>
        <Button size="small" @click="load()">重试</Button>
      </div>

      <Empty v-else-if="!state.items.length" :description="emptyText()" />

      <template v-else>
        <div class="registration-list">
          <RouterLink
            v-for="row in state.items"
            :key="row.registration.id"
            class="registration-item"
            :to="`/matches/${row.match.id}`"
          >
            <div class="registration-item__head">
              <strong>{{ row.match.title }}</strong>
              <Tag :type="isVoided(row) ? 'default' : 'primary'" plain>{{ statusText(row) }}</Tag>
            </div>
            <p class="muted small">
              {{ formatDateTime(row.match.startAt) }} · {{ row.match.venue }}
            </p>
            <p class="muted small">{{ row.match.homeTeamName }} VS {{ row.match.awayTeamName }}</p>
          </RouterLink>
        </div>

        <footer class="registrations__footer">
          <label class="page-size">
            每页
            <select :value="state.pageSize" aria-label="每页条数" @change="changePageSize">
              <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
            条
          </label>
          <Pagination
            :model-value="state.page"
            mode="simple"
            :total-items="state.total"
            :items-per-page="state.pageSize"
            @change="changePage"
          />
        </footer>
      </template>
    </template>
  </section>
</template>

<style scoped>
.segmented {
  display: inline-flex;
  margin-bottom: 10px;
  padding: 3px;
  background: #f1f5f9;
  border-radius: 10px;
}

.segmented button {
  padding: 5px 14px;
  color: var(--yh-muted);
  font: inherit;
  font-size: 13px;
  background: none;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.segmented button.segmented__item--active {
  color: var(--yh-ink);
  background: var(--yh-surface);
  box-shadow: 0 1px 4px rgba(15, 23, 42, 0.12);
}

.registration-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.registration-item {
  display: block;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.registration-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 6px;
}

.registration-item p {
  margin: 2px 0;
}

.registrations__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 12px;
}
</style>
