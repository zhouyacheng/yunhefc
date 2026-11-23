<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { Leaderboard, LeaderboardEntry } from '@yunhefc/shared';
import { leaderboardMainLabel } from '@yunhefc/shared';
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
import { LEADERBOARD_BOARDS } from '../features/leaderboard/ranking';
import { buildLeaderboardRows, type LeaderboardRow } from '../features/leaderboard/rows';
import { avatarInitial, avatarSrc } from '../features/team/merge-stats';
import { currentShanghaiSeason, mergeSeasonOptions } from '../features/team/query';

const route = useRoute();
const router = useRouter();

const query = computed(() => parseLeaderboardQuery(route.query));
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
const mainLabel = computed(() =>
  leaderboardMainLabel(query.value.scope, query.value.season, currentShanghaiSeason()),
);
const rankedRows = computed<LeaderboardRow[]>(() =>
  data.value ? buildLeaderboardRows(data.value.items, query.value.board) : [],
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
      <div class="lb-side">
        <label class="lb-scope">
          <span>赛季</span>
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
      <div class="lb-board" :class="{ 'lb-board--totals': query.scope === 'season' }">
        <div class="lb-board-head">
          <span class="lb-head-rank">名次</span>
          <span class="lb-head-player">球员</span>
          <span class="lb-head-stats">
            <span class="lb-head-stat lb-head-stat--current">{{ mainLabel }}</span>
            <template v-if="query.scope === 'season'">
              <span class="lb-head-stat">历史累计</span>
              <span class="lb-head-stat">生涯累计</span>
            </template>
          </span>
        </div>

        <ol class="lb-list">
          <li v-for="row in rankedRows" :key="row.entry.playerId" class="lb-row">
            <span class="lb-rank">{{ row.rank }}</span>

            <span class="lb-player">
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
              </span>
            </span>

            <span class="lb-stats">
              <span class="lb-stat lb-stat--current" :aria-label="`${mainLabel} ${row.currentText}`">
                <b
                  class="lb-stat-value"
                  :class="{ 'lb-stat-value--zero': row.currentZero }"
                >{{ row.currentText }}</b>
              </span>
              <span
                v-if="row.historyText !== null"
                class="lb-stat lb-stat--history"
                :aria-label="`历史累计 ${row.historyText}`"
              >
                <b
                  class="lb-stat-value"
                  :class="{ 'lb-stat-value--zero': row.historyZero }"
                >{{ row.historyText }}</b>
              </span>
              <span
                v-if="row.careerText !== null"
                class="lb-stat lb-stat--career"
                :aria-label="`生涯累计 ${row.careerText}`"
              >
                <b
                  class="lb-stat-value"
                  :class="{ 'lb-stat-value--zero': row.careerZero }"
                >{{ row.careerText }}</b>
              </span>
            </span>
          </li>
        </ol>
      </div>
    </template>

    <div class="lb-note muted small">
      <b class="lb-note-title">注意事项</b>
      <p>出场含临时到场、不含缺勤与取消比赛；乌龙单列且不抵进球。胜率只统计队内赛，且仅统计有分组归属的场次；并列采用标准竞赛排名（1、1、3）。</p>
      <p>生涯累计汇总当前赛季与历史累计（不晚于当前自然年，未来赛季不计），历史累计只汇总早于当前年份的赛季（不含当前赛季）；两者固定不随所选赛季与榜单变化。</p>
    </div>
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

.lb-side {
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: flex-end;
  margin-left: auto;
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

.lb-board {
  overflow: hidden;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius);
  box-shadow: var(--yh-shadow-sm);
}

.lb-board-head,
.lb-row {
  display: grid;
  grid-template-columns: 44px minmax(0, 1fr) auto;
  column-gap: 20px;
  align-items: center;
}

.lb-board-head {
  padding: 10px 16px;
  color: var(--yh-muted);
  font-size: 12px;
  font-weight: 600;
  background: var(--yh-surface-soft);
  border-bottom: 1px solid var(--yh-line);
}

.lb-head-rank {
  text-align: center;
  visibility: hidden;
}

.lb-head-stats,
.lb-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(64px, 88px));
  column-gap: 18px;
  justify-items: center;
  align-items: center;
  padding-left: 18px;
  border-left: 1px solid var(--yh-line);
}

.lb-board:not(.lb-board--totals) .lb-head-stats,
.lb-board:not(.lb-board--totals) .lb-stats {
  grid-template-columns: minmax(64px, 88px);
}

.lb-head-stat {
  text-align: center;
  white-space: nowrap;
}

.lb-head-stat--current {
  color: var(--yh-green);
}

.lb-list {
  display: block;
  padding: 0;
  margin: 0;
  list-style: none;
}

.lb-row {
  padding: 12px 16px;
  transition: background-color 0.15s ease;
}

.lb-row + .lb-row {
  border-top: 1px solid var(--yh-line);
}

.lb-row:hover {
  background: var(--yh-surface-soft);
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

.lb-player {
  display: flex;
  gap: 10px;
  align-items: center;
  min-width: 0;
}

.lb-avatar {
  flex: none;
  width: 36px;
  height: 36px;
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
  gap: 8px;
  align-items: baseline;
  min-width: 0;
}

.lb-name {
  overflow: hidden;
  min-width: 0;
  font-size: 15px;
  text-overflow: ellipsis;
  white-space: nowrap;
}


.lb-stat {
  text-align: center;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}

.lb-stat-value {
  font-size: 16px;
  line-height: 1.1;
}

.lb-stat--current .lb-stat-value {
  color: var(--yh-green);
  font-size: 20px;
  font-weight: 800;
}

.lb-stat--history .lb-stat-value {
  color: var(--yh-muted);
  font-size: 15px;
  font-weight: 600;
}

.lb-stat--career .lb-stat-value {
  color: var(--yh-ink);
  font-size: 17px;
  font-weight: 800;
}

.lb-stat-value.lb-stat-value--zero {
  color: var(--yh-muted);
  font-weight: 500;
}

.lb-note {
  margin-top: 18px;
  line-height: 1.7;
}

.lb-note-title {
  display: block;
  margin-bottom: 4px;
  color: var(--yh-ink);
  font-size: 13px;
  font-weight: 600;
}

.lb-note p {
  margin: 0;
}

@media (max-width: 900px) {
  .lb-board-head {
    grid-template-columns: 1fr;
    padding: 8px 16px;
  }

  .lb-head-rank,
  .lb-head-player {
    display: none;
  }

  .lb-head-stats,
  .lb-stats {
    grid-template-columns: repeat(3, minmax(0, 1fr));
    width: 100%;
    padding-left: 0;
    border-left: 0;
  }

  .lb-board:not(.lb-board--totals) .lb-head-stats,
  .lb-board:not(.lb-board--totals) .lb-stats {
    grid-template-columns: 1fr;
  }

  .lb-row {
    grid-template-columns: 32px minmax(0, 1fr);
    row-gap: 10px;
  }

  .lb-rank {
    grid-row: 1;
    grid-column: 1;
  }

  .lb-player {
    grid-row: 1;
    grid-column: 2;
  }

  .lb-stats {
    grid-row: 2;
    grid-column: 1 / -1;
    padding-top: 10px;
    border-top: 1px solid var(--yh-line);
  }
}

@media (max-width: 760px) {
  .lb-board-head {
    padding: 8px 12px;
  }

  .lb-row {
    padding: 10px 12px;
  }

  .lb-avatar {
    width: 32px;
    height: 32px;
  }

  .lb-stat--current .lb-stat-value {
    font-size: 18px;
  }
}
</style>
