<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { PlayerStats, TeamStats } from '@yunhefc/shared';
import { positionText } from '@yunhefc/shared';
import { Button, Empty, Loading } from 'vant';
import { computed, onMounted, ref } from 'vue';

import { api } from '../api/client';
import {
  avatarInitial,
  avatarSrc,
  canExpandGroup,
  GROUP_PREVIEW_SIZE,
  groupByPosition,
  mergeTeamStats,
  visibleGroupPlayers,
  type TeamPlayer,
  type TeamPlayersView,
  type TeamPositionGroup,
} from '../features/team/merge-stats';

const roster = ref<TeamPlayer[]>([]);
const stats = ref<PlayerStats[]>([]);
const total = ref(0);
const loading = ref(true);
const error = ref('');
const expandError = ref('');
const expandErrorPosition = ref<number | null>(null);
const expandingPosition = ref<number | null>(null);
const fullLoaded = ref(false);
const expandedPositions = ref<number[]>([]);
const failedAvatars = ref<number[]>([]);
let requestSeq = 0;

const groups = computed(() => groupByPosition(mergeTeamStats(roster.value, stats.value)));
const truncated = computed(() => roster.value.length < total.value);

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  expandError.value = '';
  expandErrorPosition.value = null;
  expandedPositions.value = [];
  fullLoaded.value = false;
  failedAvatars.value = [];

  // 统计允许延迟：不参与首屏等待，缺失时降级为 PLR-01 出场数与「—」。
  const statsRequest = api
    .get<TeamStats>('/team/stats', { redirectOnUnauthorized: false })
    .catch(() => null);

  try {
    const players = await api.get<TeamPlayersView>('/team/players', {
      query: { limit: GROUP_PREVIEW_SIZE },
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq) {
      return;
    }
    roster.value = players.items;
    total.value = players.total;
    loading.value = false;
    void statsRequest.then((view) => {
      if (seq === requestSeq && view) {
        stats.value = view.items;
      }
    });
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    roster.value = [];
    stats.value = [];
    total.value = 0;
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

/** 首次展开取全量名单并缓存；后续展开/收起只做本地切片。 */
async function loadAll(position: number): Promise<boolean> {
  expandingPosition.value = position;
  expandError.value = '';
  expandErrorPosition.value = null;
  try {
    const players = await api.get<TeamPlayersView>('/team/players', {
      query: { all: true },
      redirectOnUnauthorized: false,
    });
    roster.value = players.items;
    total.value = players.total;
    fullLoaded.value = true;
    // 全量响应会重新签头像 URL，允许之前失败的占位重新尝试。
    failedAvatars.value = [];
    return true;
  } catch (caught) {
    expandError.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    expandErrorPosition.value = position;
    return false;
  } finally {
    expandingPosition.value = null;
  }
}

async function toggleGroup(position: number): Promise<void> {
  if (expandingPosition.value !== null) {
    return;
  }
  if (expandedPositions.value.includes(position)) {
    expandedPositions.value = expandedPositions.value.filter((item) => item !== position);
    return;
  }
  if (!fullLoaded.value && !(await loadAll(position))) {
    return;
  }
  expandedPositions.value = [...expandedPositions.value, position];
}

function isExpanded(position: number): boolean {
  return expandedPositions.value.includes(position);
}

function canToggle(group: TeamPositionGroup): boolean {
  return canExpandGroup(group, { fullLoaded: fullLoaded.value, truncated: truncated.value });
}

/** 预览阶段只知每组至多 10 人，用「10+」避免把截断值当成真实人数。 */
function groupCount(group: TeamPositionGroup): string {
  const maybeMore = canToggle(group) && !isExpanded(group.position) && !fullLoaded.value;
  return `${group.players.length}${maybeMore ? '+' : ''}`;
}

function skillStars(skill: number): string {
  const filled = Math.max(0, Math.min(5, skill));
  return `${'★'.repeat(filled)}${'☆'.repeat(5 - filled)}`;
}

function onAvatarError(playerId: number): void {
  if (!failedAvatars.value.includes(playerId)) {
    failedAvatars.value = [...failedAvatars.value, playerId];
  }
}

function showAvatar(player: TeamPlayer): boolean {
  return avatarSrc(player.avatarUrl) !== null && !failedAvatars.value.includes(player.id);
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>球队</h1>
      <p class="muted">云河FC · 球员名单与本赛季数据</p>
    </header>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <div v-else-if="groups.length === 0" class="state">
      <Empty description="暂无球员" />
    </div>

    <template v-else>
      <div class="team-groups">
        <section v-for="group in groups" :key="group.position" class="team-group">
          <h2 class="team-group__title">
            <span>{{ positionText(group.position) }}</span>
            <span class="muted small">{{ groupCount(group) }} 人</span>
          </h2>

          <div class="team-roster">
            <article
              v-for="player in visibleGroupPlayers(group, isExpanded(group.position))"
              :key="player.id"
              class="player-card"
            >
              <img
                v-if="showAvatar(player)"
                class="player-card__avatar"
                :src="avatarSrc(player.avatarUrl) ?? undefined"
                alt=""
                @error="onAvatarError(player.id)"
              />
              <span v-else class="player-card__avatar player-card__avatar--fallback">
                {{ avatarInitial(player.name) }}
              </span>

              <div class="player-card__body">
                <div class="player-card__head">
                  <strong>{{ player.name }}</strong>
                  <span class="muted small">#{{ player.jersey ?? '—' }}</span>
                </div>

                <div class="player-card__meta muted small">
                  <span>{{ positionText(player.position) }}</span>
                  <span>{{ player.region || '—' }}</span>
                  <span class="player-card__stars">{{ skillStars(player.skill) }}</span>
                </div>

                <div class="player-card__stats small">
                  <span>出场 {{ player.appearances }}</span>
                  <span>进球 {{ player.goals ?? '—' }}</span>
                  <span>助攻 {{ player.assists ?? '—' }}</span>
                </div>
              </div>
            </article>
          </div>

          <p v-if="expandErrorPosition === group.position" class="muted small">
            {{ expandError }}
          </p>

          <Button
            v-if="canToggle(group)"
            class="team-group__toggle"
            size="mini"
            type="primary"
            plain
            :loading="expandingPosition === group.position"
            @click="toggleGroup(group.position)"
          >
            {{ isExpanded(group.position) ? '收起' : '展开更多' }}
          </Button>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.team-groups {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.team-group {
  padding: 14px 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 16px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
}

.team-group__title {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin: 0 0 10px;
  font-size: 17px;
}

.team-roster {
  display: flex;
  flex-direction: column;
}

.player-card {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 10px 0;
  border-top: 1px dashed var(--yh-line);
}

.player-card:first-child {
  padding-top: 0;
  border-top: 0;
}

.player-card__avatar {
  flex: none;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
  background: #e8edf5;
}

.player-card__avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--yh-green);
  font-weight: 700;
}

.player-card__body {
  flex: 1;
  min-width: 0;
}

.player-card__head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 8px;
}

.player-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.player-card__stars {
  color: var(--yh-gold);
  letter-spacing: 1px;
}

.player-card__stats {
  display: flex;
  gap: 12px;
  margin-top: 4px;
  color: var(--yh-muted);
}

.team-group__toggle {
  margin-top: 10px;
}
</style>
