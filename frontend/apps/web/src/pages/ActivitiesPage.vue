<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { ActivitySummary, Page } from '@yunhefc/shared';
import { formatDateTime, formatDateTimeRange } from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Tag } from 'vant';
import { computed, onMounted, ref } from 'vue';

import { api } from '../api/client';
import {
  ACTIVITY_PAGE_SIZE_OPTIONS,
  ACTIVITY_SCOPE_OPTIONS,
  ACTIVITY_STATUS_OPTIONS,
  ACTIVITY_TYPE_OPTIONS,
  DEFAULT_ACTIVITY_PAGE_SIZE,
  fromActivityForm,
  normalizeActivityPageSize,
  toActivityRequest,
} from '../features/activity/query';
import {
  activityCardCta,
  activitySignupText,
  activityStatusText,
  activityStatusTone,
  activityTypeText,
  formatActivityCost,
} from '../features/activity/presentation';

const scope = ref('all');
const typeFilter = ref('');
const statusFilter = ref('');
const page = ref(1);
const pageSize = ref(DEFAULT_ACTIVITY_PAGE_SIZE);
const activities = ref<ActivitySummary[]>([]);
const total = ref(0);
const totalPages = ref(0);
const loading = ref(true);
const error = ref('');
const now = ref(Date.now());
const failedImages = ref<number[]>([]);
let requestSeq = 0;

const showPagination = computed(() => total.value > pageSize.value);

function currentFilters() {
  return fromActivityForm({
    scope: scope.value,
    type: typeFilter.value,
    status: statusFilter.value,
  });
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  now.value = Date.now();
  try {
    const result = await api.get<Page<ActivitySummary>>('/activities', {
      query: toActivityRequest({
        ...currentFilters(),
        page: page.value,
        pageSize: pageSize.value,
      }),
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq) {
      return;
    }
    activities.value = result.items;
    total.value = result.total;
    totalPages.value = result.totalPages;
    page.value = result.page;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    activities.value = [];
    total.value = 0;
    totalPages.value = 0;
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

function onPageChange(next: number): void {
  page.value = next;
  void load();
}

function onPageSizeChange(event: Event): void {
  pageSize.value = normalizeActivityPageSize(Number((event.target as HTMLSelectElement).value));
  page.value = 1;
  void load();
}

function ctaOf(activity: ActivitySummary) {
  return activityCardCta(activity, now.value);
}

function imageSrc(activity: ActivitySummary): string | null {
  const first = activity.locationImageUrls[0];
  return first && !failedImages.value.includes(activity.id) ? first : null;
}

function onImageError(activityId: number): void {
  if (!failedImages.value.includes(activityId)) {
    failedImages.value = [...failedImages.value, activityId];
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="page">
    <header class="page-head">
      <p class="eyebrow">ACTIVITY</p>
      <h1>活动</h1>
    </header>

    <section class="schedule-filters">
      <label class="schedule-filter">
        <span>范围</span>
        <select v-model="scope" aria-label="活动范围" @change="reload">
          <option
            v-for="option in ACTIVITY_SCOPE_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
      <label class="schedule-filter">
        <span>类型</span>
        <select v-model="typeFilter" aria-label="活动类型" @change="reload">
          <option value="">全部</option>
          <option v-for="option in ACTIVITY_TYPE_OPTIONS" :key="option.value" :value="option.value">
            {{ option.label }}
          </option>
        </select>
      </label>
      <label class="schedule-filter">
        <span>状态</span>
        <select v-model="statusFilter" aria-label="活动状态" @change="reload">
          <option value="">全部</option>
          <option
            v-for="option in ACTIVITY_STATUS_OPTIONS"
            :key="option.value"
            :value="option.value"
          >
            {{ option.label }}
          </option>
        </select>
      </label>
    </section>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <div v-else-if="activities.length === 0" class="state">
      <Empty description="暂无活动" />
    </div>

    <template v-else>
      <div class="match-list">
        <RouterLink
          v-for="activity in activities"
          :key="activity.id"
          class="card activity-card"
          data-testid="activity-card"
          :to="`/activities/${activity.id}`"
        >
          <img
            v-if="imageSrc(activity)"
            class="activity-card__image"
            :src="imageSrc(activity) ?? undefined"
            alt=""
            @error="onImageError(activity.id)"
          />

          <div class="activity-card__body">
            <div class="activity-card__head">
              <h2>{{ activity.title }}</h2>
              <Tag class="status-tag" :class="`status-tag--${activityStatusTone(activity.status)}`">
                {{ activityStatusText(activity.status) }}
              </Tag>
            </div>

            <p class="muted small">{{ activityTypeText(activity.type) }}</p>
            <p class="muted small">
              📅 {{ formatDateTimeRange(activity.startAt, activity.endAt) }}
            </p>
            <p class="muted small">📍 {{ activity.location }}</p>
            <p class="muted small">⏰ 截止 {{ formatDateTime(activity.signupDeadline) }}</p>
            <p class="muted small">
              👥 {{ activitySignupText(activity.signupCount, activity.capacity) }}
            </p>
            <p v-if="activity.costPerPerson !== null" class="muted small">
              💰 人均 {{ formatActivityCost(activity.costPerPerson) }}
            </p>
          </div>

          <div class="activity-card__foot">
            <span class="muted small">查看详情 →</span>
            <span
              class="activity-cta"
              :class="{ 'activity-cta--disabled': ctaOf(activity).disabled }"
            >
              {{ ctaOf(activity).label }}
            </span>
          </div>
        </RouterLink>
      </div>

      <footer v-if="showPagination" class="page-footer">
        <label class="page-size">
          每页
          <select :value="pageSize" aria-label="每页条数" @change="onPageSizeChange">
            <option v-for="size in ACTIVITY_PAGE_SIZE_OPTIONS" :key="size" :value="size">
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
      <p v-else-if="totalPages > 0" class="muted small activity-count">共 {{ total }} 项</p>
    </template>
  </section>
</template>

<style scoped>
.activity-card {
  display: flex;
  flex-direction: column;
  align-self: start;
  gap: 10px;
  padding: 18px;
  color: inherit;
  text-decoration: none;
  cursor: pointer;
  transition:
    transform 0.18s ease,
    box-shadow 0.18s ease;
}

@media (hover: hover) {
  .activity-card:hover {
    box-shadow: var(--yh-shadow);
    transform: translateY(-3px);
  }
}

.activity-card__image {
  width: 100%;
  height: 140px;
  object-fit: cover;
  border-radius: 10px;
}

.activity-card__head {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  justify-content: space-between;
}

.activity-card__head h2 {
  margin: 0;
  font-size: 17px;
}

.activity-card__body p {
  margin: 4px 0;
}

.activity-card__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
}

.activity-cta {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 64px;
  height: 30px;
  padding: 0 12px;
  color: #ffffff;
  font-size: 13px;
  font-weight: 700;
  background: var(--yh-green);
  border-radius: 999px;
}

.activity-cta--disabled {
  color: var(--yh-muted);
  background: var(--yh-slate-soft);
}

.activity-count {
  margin-top: 14px;
}
</style>
