<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { MatchSummary, Page } from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Popup } from 'vant';
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
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
/** 最近一次由 URL 决定的表单快照；用于区分用户编辑与程序性回写。 */
let appliedForm: ScheduleForm = { ...form.value };
const rows = ref<MatchSummary[]>([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');
const drawerOpen = ref(false);
const mobileQuery = window.matchMedia('(max-width: 760px)');
const isMobile = ref(mobileQuery.matches);
const AUTO_SEARCH_DELAY = 300;
let requestSeq = 0;
let autoSearchTimer: number | undefined;

function syncViewport(event: MediaQueryListEvent): void {
  isMobile.value = event.matches;
  if (!isMobile.value) {
    drawerOpen.value = false;
  }
}

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

/** URL 是筛选与分页的唯一事实源；值未变化时不重复请求。 */
function navigate(next: ScheduleQuery): void {
  const query = serializeScheduleQuery(next);
  if (isSameQuery(route.query, query)) {
    return;
  }
  void router.replace({ query });
}

function sameForm(a: ScheduleForm, b: ScheduleForm): boolean {
  return (
    a.type === b.type &&
    a.venue === b.venue &&
    a.status === b.status &&
    a.month === b.month &&
    a.startDate === b.startDate &&
    a.endDate === b.endDate
  );
}

function cancelAutoSearch(): void {
  if (autoSearchTimer !== undefined) {
    window.clearTimeout(autoSearchTimer);
    autoSearchTimer = undefined;
  }
}

/** 条件变更即生效；移动端抽屉保持打开，由遮罩关闭。 */
function applyFilters(): void {
  navigate(withScheduleFilters(appliedQuery.value, fromScheduleForm(form.value)));
}

/** 回车立即生效，取消待执行的防抖。 */
function search(): void {
  cancelAutoSearch();
  applyFilters();
}

function reset(): void {
  const next = resetScheduleQuery(appliedQuery.value.pageSize);
  const nextForm = toScheduleForm(next);
  // URL 已是默认值时 navigate 不会进入 watcher，需同步清空未提交的表单草稿。
  appliedForm = nextForm;
  form.value = { ...nextForm };
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
    cancelAutoSearch();
    appliedQuery.value = parseScheduleQuery(raw);
    const nextForm = toScheduleForm(appliedQuery.value);
    appliedForm = nextForm;
    form.value = { ...nextForm };
    void load();
  },
  { immediate: true },
);

// 只有用户改动草稿才自动搜索；路由回写与重置只更新基线，不触发查询。
watch(
  form,
  () => {
    cancelAutoSearch();
    if (sameForm(form.value, appliedForm)) {
      return;
    }
    autoSearchTimer = window.setTimeout(() => {
      autoSearchTimer = undefined;
      applyFilters();
    }, AUTO_SEARCH_DELAY);
  },
  { deep: true },
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
      <p class="eyebrow">MATCHDAY</p>
      <h1>比赛</h1>
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

    <ScheduleFilters v-else v-model="form" @search="search" @reset="reset" />

    <Popup v-if="isMobile" v-model:show="drawerOpen" position="bottom" round>
      <div class="schedule-filters__drawer">
        <div class="card-head">
          <h2>筛选</h2>
        </div>
        <ScheduleFilters v-model="form" @search="search" @reset="reset" />
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
