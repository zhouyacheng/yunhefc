<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { Leaderboard, LeaderboardEntry } from '@yunhefc/shared';
import { positionText } from '@yunhefc/shared';
import { Button, Empty, Loading } from 'vant';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import {
  parseLeaderboardQuery,
  serializeLeaderboardQuery,
  toLeaderboardRequest,
  type LeaderboardBoardKey,
  type LeaderboardQuery,
} from '../features/leaderboard/query';
import { LEADERBOARD_BOARDS, leaderboardBoard, rankEntries } from '../features/leaderboard/ranking';
import { avatarInitial, avatarSrc } from '../features/team/merge-stats';
import { mergeSeasonOptions } from '../features/team/query';

const route = useRoute();
const router = useRouter();

const query = computed(() => parseLeaderboardQuery(route.query));
const board = computed(() => leaderboardBoard(query.value.board));
// 只有赛季/口径变化才重新请求；切换榜单仅在前端重排。
const requestKey = computed(() =>
  query.value.scope === 'all' ? 'all' : `season:${query.value.season}`,
);

const data = ref<Leaderboard | null>(null);
const seasonItems = ref<string[]>([]);
const loading = ref(true);
const error = ref('');
const failedAvatars = ref<number[]>([]);
let requestSeq = 0;

const seasonOptions = computed(() => mergeSeasonOptions(seasonItems.value, query.value.season));
const scopeSelectValue = computed(() => (query.value.scope === 'all' ? 'all' : query.value.season));
const rankedRows = computed(() =>
  data.value ? rankEntries(data.value.items, query.value.board) : [],
);
const summary = computed(() => {
  if (!data.value) {
    return '';
  }
  const windowLabel = query.value.scope === 'all' ? '生涯累计' : `${query.value.season} 赛季`;
  return `${data.value.items.length} 名正式球员 · ${windowLabel} · 最多延迟 1 分钟`;
});
const teamLink = computed(() =>
  query.value.scope === 'season'
    ? { name: 'team', query: { season: query.value.season } }
    : { name: 'team' },
);

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  data.value = null;
  failedAvatars.value = [];

  try {
    const view = await api.get<Leaderboard>('/team/leaderboards', {
      query: toLeaderboardRequest(query.value),
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq) {
      return;
    }
    data.value = view;
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

async function loadSeasonOptions(): Promise<void> {
  try {
    const result = await api.get<{ items: string[] }>('/matches/seasons', {
      redirectOnUnauthorized: false,
    });
    seasonItems.value = result.items;
  } catch {
    // 选项失败不阻塞榜单；下拉至少保留当前年份与已选赛季。
    seasonItems.value = [];
  }
}

function navigate(next: LeaderboardQuery): void {
  void router.replace({ query: serializeLeaderboardQuery(next) });
}

function selectBoard(key: LeaderboardBoardKey): void {
  if (key === query.value.board) {
    return;
  }
  navigate({ ...query.value, board: key });
}

function changeScope(event: Event): void {
  const value = (event.target as HTMLSelectElement).value;
  if (value === 'all') {
    navigate({ ...query.value, scope: 'all' });
    return;
  }
  navigate({ ...query.value, scope: 'season', season: value });
}

function onAvatarError(playerId: number): void {
  if (!failedAvatars.value.includes(playerId)) {
    failedAvatars.value = [...failedAvatars.value, playerId];
  }
}

function showAvatar(entry: LeaderboardEntry): boolean {
  return avatarSrc(entry.avatarUrl) !== null && !failedAvatars.value.includes(entry.playerId);
}

watch(requestKey, () => void load(), { immediate: true });

onMounted(() => {
  void loadSeasonOptions();
});
</script>

<template>
  <section class="page">
    <header class="page-head lb-head">
      <div>
        <p class="eyebrow">LEADERBOARD</p>
        <h1>排行榜</h1>
        <p v-if="summary" class="muted small">{{ summary }}</p>
      </div>
      <div class="lb-controls">
        <label class="lb-scope">
          <span>窗口</span>
          <select aria-label="赛季或生涯累计" :value="scopeSelectValue" @change="changeScope">
            <option value="all">生涯累计</option>
            <option v-for="option in seasonOptions" :key="option" :value="option">
              {{ option }}
            </option>
          </select>
        </label>
        <RouterLink class="ghost-link" :to="teamLink">← 返回球队</RouterLink>
      </div>
    </header>

    <nav class="detail-tabs" aria-label="排行榜榜单">
      <button
        v-for="item in LEADERBOARD_BOARDS"
        :key="item.key"
        type="button"
        class="detail-tab"
        :class="{ 'detail-tab--active': item.key === query.board }"
        @click="selectBoard(item.key)"
      >
        {{ item.label }}
      </button>
    </nav>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <div v-else-if="rankedRows.length === 0" class="state">
      <Empty description="当前窗口暂无符合条件的球员" />
    </div>

    <template v-else>
      <ol class="lb-list">
        <li v-for="row in rankedRows" :key="row.entry.playerId" class="lb-row">
          <span class="lb-rank">{{ row.rank }}</span>

          <img
            v-if="showAvatar(row.entry)"
            class="lb-avatar"
            :src="avatarSrc(row.entry.avatarUrl) ?? undefined"
            alt=""
            @error="onAvatarError(row.entry.playerId)"
          />
          <span v-else class="lb-avatar lb-avatar--fallback">
            {{ avatarInitial(row.entry.name) }}
          </span>

          <span class="lb-info">
            <b class="lb-name">{{ row.entry.name }}</b>
            <span class="lb-meta small">
              #{{ row.entry.jersey ?? '—' }} · {{ positionText(row.entry.position) }}
            </span>
          </span>

          <span class="lb-value">
            <b class="lb-main">{{ board.mainText(row.entry) }}</b>
            <span class="lb-detail muted small">{{ board.secondaryText(row.entry) }}</span>
          </span>
        </li>
      </ol>
    </template>

    <p class="lb-note muted small">
      出场含临时到场、不含缺勤与取消比赛；乌龙单列且不抵进球。胜率只统计队内赛，且仅统计有分组归属的场次；并列采用标准竞赛排名（1、1、3）。
    </p>
  </section>
</template>

<style scoped>
.lb-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: flex-end;
  justify-content: space-between;
}

.lb-controls {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-start;
}

.lb-scope {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--yh-muted);
  font-size: 12px;
  font-weight: 600;
}

.lb-scope select {
  min-width: 132px;
  min-height: 34px;
  padding: 6px 9px;
  color: var(--yh-ink);
  font-size: 13px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.lb-scope select:focus {
  border-color: var(--yh-green);
  outline: none;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.14);
}

.lb-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.lb-row {
  display: grid;
  grid-template-columns: 36px 40px minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
  padding: 12px 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius-sm);
  box-shadow: var(--yh-shadow-sm);
}

.lb-rank {
  color: var(--yh-muted);
  font-size: 20px;
  font-weight: 800;
  text-align: center;
  font-variant-numeric: tabular-nums;
}

.lb-row:first-child .lb-rank {
  color: var(--yh-gold);
}

.lb-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--yh-slate-soft);
}

.lb-avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--yh-green);
  font-weight: 800;
}

.lb-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.lb-name {
  overflow: hidden;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.lb-meta {
  color: var(--yh-muted);
}

.lb-value {
  display: flex;
  flex-direction: column;
  gap: 2px;
  align-items: flex-end;
  text-align: right;
}

.lb-main {
  font-size: 18px;
  font-variant-numeric: tabular-nums;
}

.lb-detail {
  white-space: nowrap;
}

.lb-note {
  margin-top: 18px;
  line-height: 1.7;
}

@media (max-width: 760px) {
  .lb-row {
    grid-template-columns: 32px 36px minmax(0, 1fr) auto;
    gap: 10px;
    padding: 10px 12px;
  }

  .lb-avatar {
    width: 36px;
    height: 36px;
  }

  .lb-main {
    font-size: 16px;
  }

  .lb-detail {
    max-width: 150px;
    overflow: hidden;
    text-overflow: ellipsis;
  }
}
</style>
