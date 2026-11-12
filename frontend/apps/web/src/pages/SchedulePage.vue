<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { MatchSummary, Page } from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Popup } from 'vant';
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import type { LocationQuery } from 'vue-router';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import MatchCard from '../components/MatchCard.vue';
import ScheduleFilters from '../components/ScheduleFilters.vue';
import type { ScheduleForm, ScheduleQuery } from '../features/schedule/query';
import {
  PAGE_SIZE_OPTIONS,
  fromScheduleForm,
  parseScheduleQuery,
  resetScheduleQuery,
  serializeScheduleQuery,
  toMatchListQuery,
  toScheduleForm,
  withPage,
  withPageSize,
  withScheduleFilters,
} from '../features/schedule/query';

const route = useRoute();
const router = useRouter();

const appliedQuery = ref<ScheduleQuery>(parseScheduleQuery(route.query));
const form = ref<ScheduleForm>(toScheduleForm(appliedQuery.value));
const rows = ref<MatchSummary[]>([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');
const drawerOpen = ref(false);
const mobileQuery = window.matchMedia('(max-width: 640px)');
const isMobile = ref(mobileQuery.matches);
let requestSeq = 0;

function syncViewport(event: MediaQueryListEvent): void {
  isMobile.value = event.matches;
  if (!isMobile.value) {
    drawerOpen.value = false;
  }
}

const yearOptions = computed(() => {
  const currentYear = new Date().getFullYear();
  const years = Array.from({ length: 6 }, (_, index) => String(currentYear - index));
  if (appliedQuery.value.season && !years.includes(appliedQuery.value.season)) {
    years.push(appliedQuery.value.season);
  }
  return years;
});

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<Page<MatchSummary>>('/matches', {
      query: toMatchListQuery(appliedQuery.value),
    });
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

function isSameQuery(current: LocationQuery, next: Record<string, string>): boolean {
  const keys = Object.keys(current);
  if (keys.length !== Object.keys(next).length) {
    return false;
  }
  return keys.every((key) => current[key] === next[key]);
}

/** URL 是筛选与分页的唯一事实源；值未变化时直接重试当前请求。 */
function navigate(next: ScheduleQuery): void {
  const query = serializeScheduleQuery(next);
  if (isSameQuery(route.query, query)) {
    void load();
    return;
  }
  void router.replace({ query });
}

function search(): void {
  drawerOpen.value = false;
  navigate(withScheduleFilters(appliedQuery.value, fromScheduleForm(form.value)));
}

function reset(): void {
  drawerOpen.value = false;
  const next = resetScheduleQuery(appliedQuery.value.pageSize);
  // URL 已是默认值时 navigate 不会进入 watcher，需同步清空未提交的表单草稿。
  form.value = toScheduleForm(next);
  navigate(next);
}

function changePage(page: number): void {
  navigate(withPage(appliedQuery.value, page));
}

function changePageSize(event: Event): void {
  navigate(withPageSize(appliedQuery.value, Number((event.target as HTMLSelectElement).value)));
}

watch(
  () => route.query,
  (raw) => {
    appliedQuery.value = parseScheduleQuery(raw);
    form.value = toScheduleForm(appliedQuery.value);
    void load();
  },
  { immediate: true },
);

onMounted(() => {
  mobileQuery.addEventListener('change', syncViewport);
});

onBeforeUnmount(() => {
  mobileQuery.removeEventListener('change', syncViewport);
});
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>赛程</h1>
      <p class="muted">共 {{ total }} 场比赛 · 按开赛时间降序</p>
    </header>

    <Button
      v-if="isMobile"
      class="schedule-filters__trigger"
      size="small"
      icon="filter-o"
      @click="drawerOpen = true"
    >
      筛选
    </Button>

    <ScheduleFilters
      v-else
      v-model="form"
      :year-options="yearOptions"
      @search="search"
      @reset="reset"
    />

    <Popup v-if="isMobile" v-model:show="drawerOpen" position="bottom" round>
      <div class="schedule-filters__drawer">
        <div class="card-head">
          <h2>筛选</h2>
        </div>
        <ScheduleFilters
          v-model="form"
          :year-options="yearOptions"
          @search="search"
          @reset="reset"
        />
      </div>
    </Popup>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <div v-else-if="rows.length === 0" class="state schedule-empty">
      <Empty description="暂无符合条件的比赛" />
      <Button size="small" type="primary" plain @click="reset">重置筛选</Button>
    </div>

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

    <footer v-if="!loading && !error && total > 0" class="page-footer">
      <label class="page-size">
        每页
        <select :value="appliedQuery.pageSize" name="pageSize" @change="changePageSize">
          <option v-for="size in PAGE_SIZE_OPTIONS" :key="size" :value="size">{{ size }}</option>
        </select>
        条
      </label>
      <Pagination
        :model-value="appliedQuery.page"
        mode="simple"
        :total-items="total"
        :items-per-page="appliedQuery.pageSize"
        @change="changePage"
      />
    </footer>
  </section>
</template>
