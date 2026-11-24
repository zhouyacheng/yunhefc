<script setup lang="ts">
import { ERROR_CODES, describeError, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { ACTIVITY_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { ActivityDetail, ActivitySignup, ActivityStatus } from '@yunhefc/shared';
import { activityEditable, formatDateTime, formatDayTimeRange } from '@yunhefc/shared';
import {
  Button,
  Checkbox,
  Field,
  Loading,
  Popup,
  Swipe,
  SwipeItem,
  Tag,
  showToast,
} from 'vant';
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import { isPending } from '../features/auth/user-status';
import {
  ACTIVITY_CANCELLED,
  ACTIVITY_FINISHED,
  ACTIVITY_SIGNUP_ACTIVE,
  activityCancelState,
  activityCapacityPercent,
  activityCta,
  activityFamilyText,
  activitySignupState,
  activitySignupText,
  activityStatusText,
  activityStatusTone,
  activityTypeText,
  formatActivityCost,
} from '../features/activity/presentation';
import {
  activityCancelReasonError,
  activityStatusChoice,
  activityStatusOptions,
} from '../features/activity/status-options';
import { toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';
import { hasSessionHint } from '../stores/session-hint';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const activityId = Number(route.params.id);

const activity = ref<ActivityDetail | null>(null);
const loading = ref(true);
const error = ref('');
const actionError = ref('');
const actionMessage = ref('');
const imageFailed = ref(false);
const now = ref(Date.now());
let requestSeq = 0;
let nowTimer: ReturnType<typeof setInterval> | null = null;

const popupOpen = ref(false);
const withFamily = ref(false);
const familyNote = ref('');
const submitting = ref(false);
const cancelling = ref(false);

const statusBusy = ref(false);
const statusActionError = ref('');
const cancelDialogOpen = ref(false);
const cancelReason = ref('');
const cancelError = ref('');
const cancelSubmitting = ref(false);

const pending = computed(() => isPending(session.user?.status ?? -1));
const canChangeStatus = computed(
  () => session.isAuthenticated && hasPermission(session.permissions, ACTIVITY_MANAGE_ANY),
);
const statusOptionList = computed(() =>
  activity.value ? activityStatusOptions(activity.value.status) : [],
);
const mySignup = computed<ActivitySignup | null>(() => activity.value?.mySignup ?? null);
const isSignedUp = computed(() => mySignup.value?.status === ACTIVITY_SIGNUP_ACTIVE);
const activityClosed = computed(
  () =>
    activity.value !== null &&
    (activity.value.status === ACTIVITY_FINISHED || activity.value.status === ACTIVITY_CANCELLED),
);
const signupState = computed(() => {
  const current = activity.value;
  if (!current) {
    return null;
  }
  return activitySignupState({
    ...current,
    authenticated: session.isAuthenticated,
    pending: pending.value,
    mySignupStatus: mySignup.value?.status ?? null,
    now: now.value,
  });
});
const cancelState = computed(() => {
  const current = activity.value;
  if (!current) {
    return null;
  }
  return activityCancelState({
    ...current,
    mySignupStatus: mySignup.value?.status ?? null,
    now: now.value,
  });
});
const cta = computed(() => (activity.value ? activityCta(activity.value, now.value) : null));
const signupText = computed(() =>
  activity.value ? activitySignupText(activity.value.signupCount, activity.value.capacity) : '',
);
/** 时间用完整文本承载：日期 + 星期 + 时段。 */
const dayTimeText = computed(() =>
  activity.value ? formatDayTimeRange(activity.value.startAt, activity.value.endAt) : '—',
);
const capacityPercent = computed(() => {
  const current = activity.value;
  return current ? activityCapacityPercent(current.signupCount, current.capacity) : 0;
});
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: toBrowserPath(route.fullPath) },
}));
const imageUrls = computed<string[]>(() => activity.value?.locationImageUrls ?? []);

const REFRESH_CONFLICT_CODES: readonly number[] = [
  ERROR_CODES.VERSION_CONFLICT,
  ERROR_CODES.ACTIVITY_SIGNUP_CONFLICT,
  ERROR_CODES.ACTIVITY_STATE_CONFLICT,
  ERROR_CODES.ACTIVITY_FULL,
];

function onConflict(caught: unknown): boolean {
  return isApiError(caught) && REFRESH_CONFLICT_CODES.includes(caught.code);
}

async function load(silent = false): Promise<void> {
  const seq = ++requestSeq;
  if (!silent) {
    loading.value = true;
    error.value = '';
  }
  now.value = Date.now();
  try {
    if (!Number.isFinite(activityId) || activityId <= 0) {
      await router.replace('/404');
      return;
    }
    if (!session.loaded && hasSessionHint()) {
      await session.ensureLoaded().catch(() => undefined);
    }
    const detail = await api.get<ActivityDetail>(`/activities/${activityId}`, {
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq) {
      return;
    }
    activity.value = detail;
    imageFailed.value = false;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      await router.replace('/404');
      return;
    }
    error.value = describeError(caught, '加载失败，请稍后重试');
    if (!silent) {
      activity.value = null;
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

function onSignupClick(): void {
  const state = signupState.value;
  if (!state || state.disabled) {
    return;
  }
  if (state.action === 'login') {
    void router.push(loginTarget.value);
    return;
  }
  if (state.action !== 'signup') {
    return;
  }
  actionError.value = '';
  actionMessage.value = '';
  withFamily.value = false;
  familyNote.value = '';
  popupOpen.value = true;
}

async function submitSignup(): Promise<void> {
  if (submitting.value) {
    return;
  }
  submitting.value = true;
  actionError.value = '';
  const familyAllowed = activity.value?.familyAllowed === true;
  try {
    await api.post<{ signup: ActivitySignup }>(
      `/activities/${activityId}/signups`,
      {
        withFamily: familyAllowed && withFamily.value,
        familyNote:
          familyAllowed && withFamily.value ? familyNote.value.trim() || null : null,
      },
      { idempotencyKey: newIdempotencyKey() },
    );
    popupOpen.value = false;
    actionMessage.value = '报名成功';
    await load(true);
  } catch (caught) {
    if (onConflict(caught)) {
      popupOpen.value = false;
      actionMessage.value = describeError(caught, '报名失败，请稍后重试');
      await load(true);
    } else {
      actionError.value = describeError(caught, '报名失败，请稍后重试');
    }
  } finally {
    submitting.value = false;
  }
}

async function cancelSignup(): Promise<void> {
  const state = cancelState.value;
  if (!state || !state.visible || state.disabled || cancelling.value) {
    return;
  }
  cancelling.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await api.delete<{ cancelled: boolean }>(
      `/activities/${activityId}/signups/me`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    actionMessage.value = result.cancelled ? '已取消报名' : '报名已取消';
    await load(true);
  } catch (caught) {
    actionError.value = describeError(caught, '取消报名失败，请稍后重试');
    if (onConflict(caught)) {
      await load(true);
    }
  } finally {
    cancelling.value = false;
  }
}

/** ACT-13 统一写入口；成功返回 null，失败返回展示文案并处理冲突重载。 */
async function postStatusChange(
  status: ActivityStatus,
  reason?: string,
  fallback = '状态修改失败，请稍后重试',
): Promise<string | null> {
  const current = activity.value;
  if (!current) {
    return '活动不存在';
  }
  try {
    await api.post<unknown>(
      `/activities/${activityId}/status`,
      { status, version: current.version, ...(reason ? { reason } : {}) },
      { idempotencyKey: newIdempotencyKey() },
    );
    return null;
  } catch (caught) {
    if (onConflict(caught)) {
      await load(true);
    }
    return describeError(caught, fallback);
  }
}

async function changeStatus(target: ActivityStatus): Promise<void> {
  if (!activity.value || statusBusy.value) {
    return;
  }
  statusBusy.value = true;
  statusActionError.value = '';
  actionMessage.value = '';
  try {
    const error = await postStatusChange(target);
    if (error) {
      statusActionError.value = error;
      return;
    }
    actionMessage.value = '活动状态已更新';
    await load(true);
  } finally {
    statusBusy.value = false;
  }
}

function onStatusSelect(event: Event): void {
  const select = event.target as HTMLSelectElement;
  const current = activity.value;
  if (!current) {
    return;
  }
  statusActionError.value = '';
  const choice = activityStatusChoice(statusOptionList.value, current.status, Number(select.value));
  select.value = String(current.status);
  switch (choice.kind) {
    case 'ignore':
      return;
    case 'cancel':
      cancelReason.value = '';
      cancelError.value = '';
      cancelDialogOpen.value = true;
      return;
    case 'change':
      void changeStatus(choice.status);
      return;
  }
}

async function submitCancelStatus(): Promise<void> {
  if (!activity.value || cancelSubmitting.value) {
    return;
  }
  statusActionError.value = '';
  const message = activityCancelReasonError(cancelReason.value);
  if (message) {
    cancelError.value = message;
    return;
  }
  cancelSubmitting.value = true;
  cancelError.value = '';
  try {
    const error = await postStatusChange(
      ACTIVITY_CANCELLED,
      cancelReason.value.trim(),
      '取消活动失败，请稍后重试',
    );
    if (error) {
      cancelError.value = error;
      return;
    }
    cancelDialogOpen.value = false;
    actionMessage.value = '活动已取消';
    await load(true);
  } finally {
    cancelSubmitting.value = false;
  }
}

async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // 回退到 execCommand。
  }
  try {
    const textarea = document.createElement('textarea');
    textarea.value = text;
    textarea.style.position = 'fixed';
    textarea.style.opacity = '0';
    document.body.appendChild(textarea);
    textarea.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(textarea);
    return copied;
  } catch {
    return false;
  }
}

async function copyLink(): Promise<void> {
  const url = new URL(`/activities/${activityId}`, window.location.origin).toString();
  const copied = await copyText(url);
  if (copied) {
    showToast('链接已复制');
  } else {
    actionError.value = '复制失败，请手动复制链接';
  }
}

onMounted(() => {
  void load();
  nowTimer = setInterval(() => {
    now.value = Date.now();
  }, 30_000);
});

onBeforeUnmount(() => {
  if (nowTimer !== null) {
    clearInterval(nowTimer);
    nowTimer = null;
  }
});
</script>

<template>
  <section class="page">
    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <template v-else-if="activity">
      <header class="card match-hero">
        <div class="match-hero__top">
          <span class="small muted">{{ activityTypeText(activity.type) }}</span>
          <Tag class="status-tag" :class="`status-tag--${activityStatusTone(activity.status)}`">
            {{ activityStatusText(activity.status) }}
          </Tag>
        </div>

        <h1 class="activity-hero__title">{{ activity.title }}</h1>

        <div class="match-hero__actions">
          <select
            v-if="canChangeStatus && activityEditable(activity.status)"
            class="status-select"
            data-testid="activity-status-select"
            aria-label="修改活动状态"
            :disabled="statusBusy"
            @change="onStatusSelect"
          >
            <option
              v-for="option in statusOptionList"
              :key="option.status"
              :value="option.status"
              :selected="option.status === activity.status"
              :disabled="option.disabled"
              :title="option.reason"
            >
              {{ option.label }}
            </option>
          </select>
          <Button size="small" plain type="primary" @click="copyLink">复制链接</Button>
        </div>

        <p v-if="statusActionError" class="action-error" data-testid="activity-status-error">
          {{ statusActionError }}
        </p>

        <div class="match-hero__meta muted small">
          <span>{{ dayTimeText }}</span>
          <span>📍 {{ activity.location }}</span>
          <span>⏰ 报名截止 {{ formatDateTime(activity.signupDeadline) }}</span>
          <span>{{ signupText }}</span>
          <span v-if="activity.costPerPerson !== null">
            人均 {{ formatActivityCost(activity.costPerPerson) }}
          </span>
        </div>

        <p v-if="!cta?.visible" class="notice" data-testid="activity-closed-banner">
          活动{{ activityStatusText(activity.status) }}，不再接受报名。
        </p>
      </header>

      <section v-if="imageUrls.length && !imageFailed" class="card activity-gallery">
        <div class="card-head">
          <h2>场地环境</h2>
        </div>
        <Swipe class="activity-gallery__swipe" :autoplay="0" indicator-dots>
          <SwipeItem v-for="(url, index) in imageUrls" :key="`${index}-${url}`">
            <img
              class="activity-gallery__image"
              :src="url"
              alt="场地环境图片"
              @error="imageFailed = true"
            />
          </SwipeItem>
        </Swipe>
      </section>

      <div class="overview-grid">
        <section class="card activity-description">
          <div class="card-head">
            <h2>活动说明</h2>
          </div>
          <p v-if="activity.description" class="activity-description__text">
            {{ activity.description }}
          </p>
          <p v-else class="muted">暂无补充说明</p>
        </section>

        <section class="card activity-signup" data-testid="activity-signup-card">
          <div class="card-head">
            <h2>报名</h2>
            <Tag
              v-if="mySignup"
              class="status-tag"
              :class="isSignedUp ? 'status-tag--open' : 'status-tag--muted'"
            >
              {{ isSignedUp ? '已报名' : '已取消' }}
            </Tag>
          </div>

          <div v-if="activity.capacity !== null" class="signup-capacity">
            <div class="signup-capacity__track">
              <div class="signup-capacity__fill" :style="{ width: `${capacityPercent}%` }"></div>
            </div>
          </div>

          <template v-if="isSignedUp">
            <p class="muted small">
              报名时间 {{ formatDateTime(mySignup?.signedUpAt) }} ·
              {{ mySignup ? activityFamilyText(mySignup) : '' }}
            </p>
            <template v-if="!activityClosed">
              <Button
                type="primary"
                plain
                block
                data-testid="activity-cancel"
                :disabled="cancelState?.disabled"
                :loading="cancelling"
                @click="cancelSignup"
              >
                {{ cancelState?.label ?? '取消报名' }}
              </Button>
              <p v-if="cancelState?.reason" class="hint">{{ cancelState.reason }}</p>
            </template>
            <p v-else class="muted small">
              活动{{ activityStatusText(activity.status) }}，报名记录仅保留。
            </p>
          </template>

          <template v-else-if="signupState?.visible">
            <Button
              type="primary"
              block
              data-testid="activity-signup"
              :disabled="signupState.disabled"
              @click="onSignupClick"
            >
              {{ signupState.label }}
            </Button>
            <p v-if="signupState.reason" class="hint">{{ signupState.reason }}</p>
          </template>

          <p v-else-if="signupState?.reason" class="notice">{{ signupState.reason }}</p>

          <p v-else class="muted small">{{ signupState?.label || '当前不可报名' }}</p>

          <p v-if="actionMessage" class="hint" data-testid="activity-message">
            {{ actionMessage }}
          </p>
          <p v-if="actionError" class="action-error" data-testid="activity-error">
            {{ actionError }}
          </p>
        </section>
      </div>
    </template>

    <Popup v-model:show="cancelDialogOpen" position="bottom" round>
      <div class="activity-signup-popup">
        <div class="card-head">
          <h2>取消活动</h2>
        </div>

        <p class="muted small">取消后已报名记录保留但不再有效，且不可恢复。</p>

        <Field
          v-model="cancelReason"
          label="取消原因"
          type="textarea"
          rows="3"
          maxlength="255"
          show-word-limit
          placeholder="请填写取消原因（最多 255 字）"
          :disabled="cancelSubmitting"
        />

        <p v-if="cancelError" class="action-error" data-testid="activity-cancel-error">
          {{ cancelError }}
        </p>

        <Button
          type="primary"
          block
          :loading="cancelSubmitting"
          :disabled="cancelSubmitting"
          @click="submitCancelStatus"
        >
          确认取消
        </Button>
      </div>
    </Popup>

    <Popup v-model:show="popupOpen" position="bottom" round>
      <div class="activity-signup-popup">
        <div class="card-head">
          <h2>报名活动</h2>
        </div>

        <template v-if="activity?.familyAllowed">
          <Checkbox v-model="withFamily" shape="square">携带家属</Checkbox>

          <Field
            v-model="familyNote"
            label="家属备注"
            type="textarea"
            rows="2"
            maxlength="255"
            show-word-limit
            placeholder="如：带 1 位家属，儿童餐"
            :disabled="!withFamily"
          />
        </template>

        <p v-if="actionError" class="action-error">{{ actionError }}</p>

        <Button
          type="primary"
          block
          :loading="submitting"
          :disabled="submitting"
          @click="submitSignup"
        >
          确认报名
        </Button>
      </div>
    </Popup>
  </section>
</template>

<style scoped>
/* Hero 复用 C03 的 .match-hero；此处仅补活动标题排版。 */
.status-select {
  flex: 0 0 auto;
  height: var(--van-button-small-height);
  padding: 0 10px;
  color: #ffffff;
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  text-align: center;
  text-align-last: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.22);
  border-radius: var(--van-button-radius);
}

.status-select option {
  color: var(--yh-ink);
  background: var(--yh-surface);
}

.activity-hero__title {
  margin: 0;
  color: #ffffff;
  font-size: clamp(24px, 4vw, 40px);
  font-weight: 800;
  line-height: 1.15;
  letter-spacing: -0.02em;
}

.signup-capacity {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.signup-capacity__track {
  height: 6px;
  overflow: hidden;
  background: var(--yh-line);
  border-radius: 999px;
}

.signup-capacity__fill {
  height: 100%;
  background: linear-gradient(90deg, var(--yh-green), var(--yh-green-bright));
  border-radius: inherit;
  transition: width 0.2s ease;
}

.activity-gallery {
  margin-bottom: 18px;
}

.activity-gallery__swipe,
.activity-gallery__image {
  width: 100%;
  height: 220px;
  overflow: hidden;
  border-radius: 12px;
}

.activity-gallery__image {
  display: block;
  object-fit: cover;
}

.activity-description__text {
  white-space: pre-wrap;
}

.activity-signup {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.activity-signup-popup {
  display: flex;
  flex-direction: column;
  gap: 14px;
  padding: 20px 18px 28px;
}
</style>
