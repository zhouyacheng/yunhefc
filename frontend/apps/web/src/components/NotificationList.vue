<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import type {
  Notification,
  NotificationReadAllResult,
  NotificationReadResult,
  Page,
} from '@yunhefc/shared';
import { formatDateTime, notificationTarget } from '@yunhefc/shared';
import { Empty, Loading } from 'vant';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

const NOTIFICATION_PAGE_SIZE = 10;

type FilterKey = 'all' | 'unread';
type MarkReadResult = 'read' | 'gone' | 'failed';

interface ListState {
  items: Notification[];
  page: number;
  totalPages: number;
  total: number;
  loaded: boolean;
  loading: boolean;
  error: string;
  requestSeq: number;
}

const session = useSessionStore();
const router = useRouter();

function emptyList(): ListState {
  return {
    items: [],
    page: 1,
    totalPages: 0,
    total: 0,
    loaded: false,
    loading: false,
    error: '',
    requestSeq: 0,
  };
}

// 全部/未读各自维护列表与分页状态，切换筛选互不覆盖。
const lists = reactive<Record<FilterKey, ListState>>({
  all: emptyList(),
  unread: emptyList(),
});
const unreadOnly = ref(false);
const filterKey = computed<FilterKey>(() => (unreadOnly.value ? 'unread' : 'all'));
const current = computed(() => lists[filterKey.value]);

const loadingMore = ref(false);
const markingAll = ref(false);
const busyId = ref<number | null>(null);
const actionError = ref('');
const successMessage = ref('');

function errorText(caught: unknown, fallback: string): string {
  return isApiError(caught) ? caught.message : fallback;
}

async function load(key: FilterKey = filterKey.value): Promise<void> {
  const state = lists[key];
  const seq = ++state.requestSeq;
  state.loading = true;
  state.error = '';
  if (key === filterKey.value) {
    actionError.value = '';
  }
  try {
    const result = await api.get<Page<Notification>>('/notifications', {
      query: {
        unread: key === 'unread' ? true : undefined,
        page: 1,
        pageSize: NOTIFICATION_PAGE_SIZE,
      },
    });
    if (seq !== state.requestSeq) {
      return;
    }
    state.items = result.items;
    state.page = result.page;
    state.totalPages = result.totalPages;
    state.total = result.total;
    state.loaded = true;
  } catch (caught) {
    if (seq !== state.requestSeq) {
      return;
    }
    state.items = [];
    state.error = errorText(caught, '通知加载失败，请稍后重试');
    state.loaded = true;
  } finally {
    if (seq === state.requestSeq) {
      state.loading = false;
    }
  }
}

async function loadMore(): Promise<void> {
  const key = filterKey.value;
  const state = lists[key];
  if (state.loading || loadingMore.value || state.page >= state.totalPages) {
    return;
  }
  // 记录代次：筛选切换会重置列表，旧请求结果不能 append 到新筛选。
  const seq = state.requestSeq;
  loadingMore.value = true;
  actionError.value = '';
  try {
    const result = await api.get<Page<Notification>>('/notifications', {
      query: {
        unread: key === 'unread' ? true : undefined,
        page: state.page + 1,
        pageSize: NOTIFICATION_PAGE_SIZE,
      },
    });
    if (seq !== state.requestSeq) {
      return;
    }
    state.items = [...state.items, ...result.items];
    state.page = result.page;
    state.totalPages = result.totalPages;
    state.total = result.total;
  } catch (caught) {
    if (seq === state.requestSeq) {
      actionError.value = errorText(caught, '加载更多失败，请稍后重试');
    }
  } finally {
    loadingMore.value = false;
  }
}

function setUnreadOnly(value: boolean): void {
  if (unreadOnly.value === value) {
    return;
  }
  unreadOnly.value = value;
  successMessage.value = '';
  actionError.value = '';
  const target = lists[filterKey.value];
  if (!target.loaded && !target.loading) {
    void load();
  }
}

/** 已读结果同步到两个列表；未读列表移除该条并修正计数。 */
function applyRead(id: number, readAt: string): void {
  lists.all.items.forEach((item) => {
    if (item.id === id) {
      item.readAt = readAt;
    }
  });
  const unread = lists.unread;
  const before = unread.items.length;
  unread.items = unread.items.filter((item) => item.id !== id);
  if (unread.items.length !== before) {
    unread.total = Math.max(0, unread.total - 1);
    unread.totalPages = Math.ceil(unread.total / NOTIFICATION_PAGE_SIZE);
    unread.page = Math.min(unread.page, Math.max(1, unread.totalPages));
  }
}

async function markRead(item: Notification): Promise<MarkReadResult> {
  if (item.readAt) {
    return 'read';
  }
  try {
    const result = await api.post<NotificationReadResult>(`/notifications/${item.id}/read`);
    applyRead(item.id, result.readAt);
    session.unreadCount = Math.max(0, session.unreadCount - 1);
    return 'read';
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      // 通知已被删除或不属于本人：刷新列表，保持点击前状态。
      await load();
      return 'gone';
    }
    actionError.value = errorText(caught, '标记已读失败，请稍后重试');
    return 'failed';
  }
}

async function openNotification(item: Notification): Promise<void> {
  if (busyId.value !== null) {
    return;
  }
  busyId.value = item.id;
  actionError.value = '';
  try {
    if (!item.readAt) {
      const result = await markRead(item);
      if (result === 'gone') {
        return;
      }
      // 标记已读失败不阻断 MATCH 跳转，错误已在行内提示。
    }
    const target = notificationTarget(item);
    if (target.kind === 'match') {
      await router.push(`/matches/${target.id}`);
    }
  } finally {
    busyId.value = null;
  }
}

async function markAllRead(): Promise<void> {
  if (markingAll.value) {
    return;
  }
  markingAll.value = true;
  actionError.value = '';
  successMessage.value = '';
  try {
    // updated=0 也按成功处理。
    await api.post<NotificationReadAllResult>('/notifications/read-all');
    const readAt = new Date().toISOString();
    lists.all.items.forEach((item) => {
      item.readAt ??= readAt;
    });
    const unread = lists.unread;
    unread.items = [];
    unread.page = 1;
    unread.total = 0;
    unread.totalPages = 0;
    unread.loaded = true;
    // 不立即回查 NTF-02，避免与轮询竞态；下一周期自动校正。
    session.unreadCount = 0;
    successMessage.value = '已全部标记为已读';
  } catch (caught) {
    actionError.value = errorText(caught, '全部已读失败，请稍后重试');
  } finally {
    markingAll.value = false;
  }
}

function isMatchNotification(item: Notification): boolean {
  return notificationTarget(item).kind === 'match';
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="card notifications">
    <div class="card-head">
      <h2>通知</h2>
      <span class="muted small">{{ current.total }} 条</span>
    </div>

    <div class="notifications__toolbar">
      <div class="segmented" role="group" aria-label="通知筛选">
        <button
          type="button"
          :class="{ 'segmented__item--active': !unreadOnly }"
          @click="setUnreadOnly(false)"
        >
          全部
        </button>
        <button
          type="button"
          :class="{ 'segmented__item--active': unreadOnly }"
          @click="setUnreadOnly(true)"
        >
          只看未读
        </button>
      </div>
      <button
        type="button"
        class="notifications__read-all"
        :disabled="markingAll || session.unreadCount === 0"
        @click="markAllRead"
      >
        {{ markingAll ? '处理中…' : '全部已读' }}
      </button>
    </div>

    <p v-if="successMessage" class="hint">{{ successMessage }}</p>
    <p v-if="actionError" class="action-error">{{ actionError }}</p>

    <div v-if="current.loading" class="state state--inline">
      <Loading size="20">通知加载中…</Loading>
    </div>

    <div v-else-if="current.error" class="state state--inline">
      <p class="muted">{{ current.error }}</p>
      <button type="button" class="inline-retry" @click="load()">重试</button>
    </div>

    <Empty
      v-else-if="!current.items.length"
      :description="unreadOnly ? '暂无未读通知' : '暂无通知'"
    />

    <template v-else>
      <div class="notification-list">
        <button
          v-for="item in current.items"
          :key="item.id"
          type="button"
          class="notification-item"
          :class="{ 'notification-item--unread': !item.readAt }"
          :data-id="item.id"
          :disabled="busyId === item.id"
          @click="openNotification(item)"
        >
          <span v-if="!item.readAt" class="notification-item__dot" aria-label="未读" />
          <span class="notification-item__main">
            <span class="notification-item__head">
              <strong>{{ item.title }}</strong>
            </span>
            <span class="notification-item__body">{{ item.body }}</span>
            <span class="notification-item__meta">
              {{ formatDateTime(item.createdAt) }}
              <span v-if="isMatchNotification(item)"> · 查看比赛</span>
            </span>
          </span>
        </button>
      </div>

      <button
        v-if="current.page < current.totalPages"
        type="button"
        class="inline-retry notifications__more"
        :disabled="loadingMore"
        @click="loadMore"
      >
        {{ loadingMore ? '加载中…' : '加载更多' }}
      </button>
    </template>
  </section>
</template>

<style scoped>
.notifications__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 10px;
}

.segmented {
  display: inline-flex;
  padding: 3px;
  background: #f1f5f9;
  border-radius: 10px;
}

.segmented button {
  padding: 5px 12px;
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

.notifications__read-all {
  padding: 0;
  color: var(--yh-green);
  font: inherit;
  font-size: 13px;
  background: none;
  border: 0;
  cursor: pointer;
}

.notifications__read-all:disabled {
  color: var(--yh-muted);
  cursor: not-allowed;
}

.notification-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.notification-item {
  position: relative;
  display: flex;
  gap: 10px;
  width: 100%;
  padding: 12px;
  color: inherit;
  font: inherit;
  text-align: left;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 12px;
  cursor: pointer;
}

.notification-item--unread {
  background: #f0fdf4;
  border-color: #bbf7d0;
}

.notification-item__dot {
  flex: none;
  width: 8px;
  height: 8px;
  margin-top: 6px;
  background: var(--yh-green);
  border-radius: 50%;
}

.notification-item__main {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.notification-item__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.notification-item__body {
  color: #334155;
  font-size: 13px;
  line-height: 1.45;
}

.notification-item__meta {
  color: var(--yh-muted);
  font-size: 12px;
}

.notifications__more {
  display: block;
  margin: 12px auto 0;
}

.inline-retry {
  padding: 4px 12px;
  color: var(--yh-green);
  font: inherit;
  font-size: 13px;
  background: none;
  border: 1px solid var(--yh-green);
  border-radius: 8px;
  cursor: pointer;
}
</style>
