<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import type { Attendance, MatchDetail, MyMatchRegistration, Registration } from '@yunhefc/shared';
import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  REGISTRATION_STATUS_TEXT,
  checkInWindowState,
  formatDateTime,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
} from '@yunhefc/shared';
import { Button, Empty, Loading, Tag } from 'vant';
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { api } from '../api/client';
import { toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const session = useSessionStore();
const matchId = Number(route.params.id);

const match = ref<MatchDetail | null>(null);
const registration = ref<Registration | null>(null);
const attendance = ref<Attendance | null>(null);
const loading = ref(true);
const error = ref('');
const checkInLoading = ref(false);
const actionError = ref('');

const isCancelled = computed(() => match.value?.status === 6);
const isPostponed = computed(() => match.value?.status === 5);
const checkInClosed = computed(() => isCancelled.value || isPostponed.value);
const windowState = computed(() =>
  match.value ? checkInWindowState(match.value.startAt) : 'after',
);
const confirmed = computed(() => registration.value?.status === 0);
const canCheckIn = computed(
  () => confirmed.value && !checkInClosed.value && windowState.value === 'open',
);
const checkInHint = computed(() => {
  if (!confirmed.value) {
    return '仅确认报名的球员可以签到';
  }
  if (windowState.value === 'before') {
    return '签到未开始：开赛前 4 小时开放';
  }
  if (windowState.value === 'after') {
    return '签到已结束：比赛当天 23:59 截止';
  }
  return '签到窗口开放中：开赛前 4 小时至比赛当天 23:59';
});
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: toBrowserPath(route.fullPath) },
}));

function statusTagType(): 'primary' | 'success' | 'warning' | 'danger' {
  const tone = matchStatusTone(match.value?.status);
  return tone === 'neutral' ? 'primary' : tone;
}

function scoreText(): string {
  if (!match.value || match.value.scoreHome === null || match.value.scoreHome === undefined) {
    return 'VS';
  }
  return `${match.value.scoreHome} : ${match.value.scoreAway ?? 0}`;
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    match.value = await api.get<MatchDetail>(`/matches/${matchId}`);
    await session.ensureLoaded().catch(() => undefined);
    if (session.isAuthenticated) {
      const mine = await api.get<MyMatchRegistration>(`/matches/${matchId}/registrations/me`);
      registration.value = mine.registration ?? null;
      attendance.value = mine.attendance ?? null;
    } else {
      registration.value = match.value.myRegistration ?? null;
    }
  } catch (caught) {
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

async function checkIn(): Promise<void> {
  if (!canCheckIn.value || checkInLoading.value) {
    return;
  }
  checkInLoading.value = true;
  actionError.value = '';
  try {
    const result = await api.post<{ attendance: Attendance }>(
      `/matches/${matchId}/check-in`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    attendance.value = result.attendance;
  } catch (caught) {
    actionError.value = isApiError(caught) ? caught.message : '签到失败，请稍后重试';
  } finally {
    checkInLoading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load">重试</Button>
    </div>

    <template v-else-if="match">
      <header class="card match-hero">
        <div class="match-hero__top">
          <span class="small muted">{{ matchTypeText(match.type) }}</span>
          <Tag :type="statusTagType()" plain>{{ matchStatusText(match.status) }}</Tag>
          <span class="muted small">第 {{ match.seasonMatchNo }} 场</span>
        </div>
        <div class="match-hero__teams">
          <span>{{ match.homeTeamName }}</span>
          <strong>{{ scoreText() }}</strong>
          <span>{{ match.awayTeamName }}</span>
        </div>
        <div class="match-hero__meta muted small">
          <span>{{ formatDateTime(match.startAt) }}</span>
          <span>{{ match.venue }}</span>
        </div>
        <p v-if="isCancelled" class="notice notice--danger">比赛已取消，签到不开放</p>
        <p v-else-if="isPostponed" class="notice">比赛已延期，签到不开放</p>
      </header>

      <section class="card registration-card">
        <div class="card-head">
          <h2>我的报名</h2>
          <Tag v-if="registration" size="medium" type="primary" plain>
            {{ REGISTRATION_STATUS_TEXT[registration.status] }}
          </Tag>
        </div>

        <template v-if="!session.isAuthenticated">
          <p class="muted">登录后可报名与签到</p>
          <RouterLink :to="loginTarget">
            <Button type="primary" block>去登录</Button>
          </RouterLink>
        </template>

        <Empty v-else-if="!registration" description="你尚未报名本场比赛" />

        <template v-else>
          <div v-if="attendance" class="attendance-status">
            <p>
              考勤状态：<strong>{{ ATTENDANCE_STATUS_TEXT[attendance.status] }}</strong>
            </p>
            <p class="muted small">
              来源：{{ ATTENDANCE_SOURCE_TEXT[attendance.source] }} ·
              {{ formatDateTime(attendance.recordedAt) }}
            </p>
          </div>

          <template v-else-if="!checkInClosed">
            <Button
              v-if="confirmed"
              type="primary"
              block
              :disabled="!canCheckIn"
              :loading="checkInLoading"
              @click="checkIn"
            >
              签到
            </Button>
            <p v-if="checkInHint" class="hint">{{ checkInHint }}</p>
          </template>
        </template>

        <p v-if="actionError" class="action-error">{{ actionError }}</p>
      </section>
    </template>
  </section>
</template>
