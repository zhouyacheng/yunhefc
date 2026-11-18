<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { PlayerStats, TeamStats } from '@yunhefc/shared';
import { positionText, skillAriaLabel, skillStarText } from '@yunhefc/shared';
import { Button, Empty, Loading } from 'vant';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import {
  avatarInitial,
  avatarSrc,
  groupByPosition,
  mergeTeamStats,
  type TeamPlayer,
  type TeamPlayersView,
} from '../features/team/merge-stats';
import { mergeSeasonOptions, parseTeamSeason, serializeTeamSeason } from '../features/team/query';

const route = useRoute();
const router = useRouter();

const season = ref(parseTeamSeason(route.query.season));
const seasonOptions = ref(mergeSeasonOptions([], season.value));
const roster = ref<TeamPlayer[]>([]);
const stats = ref<PlayerStats[]>([]);
const loading = ref(true);
const error = ref('');
const failedAvatars = ref<number[]>([]);
let requestSeq = 0;

const groups = computed(() => groupByPosition(mergeTeamStats(roster.value, stats.value)));

async function load(): Promise<void> {
  const seq = ++requestSeq;
  const requestedSeason = season.value;
  loading.value = true;
  error.value = '';
  failedAvatars.value = [];
  stats.value = [];

  // 统计允许延迟：不参与首屏等待，缺失时降级为 PLR-01 出场数与「—」。
  const statsRequest = api
    .get<TeamStats>('/team/stats', {
      query: { season: requestedSeason },
      redirectOnUnauthorized: false,
    })
    .catch(() => null);

  try {
    const players = await api.get<TeamPlayersView>('/team/players', {
      query: { all: true, season: requestedSeason },
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq || requestedSeason !== season.value) {
      return;
    }
    roster.value = players.items;
    loading.value = false;
    void statsRequest.then((view) => {
      if (seq === requestSeq && requestedSeason === season.value && view) {
        stats.value = view.items;
      }
    });
  } catch (caught) {
    if (seq !== requestSeq || requestedSeason !== season.value) {
      return;
    }
    roster.value = [];
    stats.value = [];
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
    seasonOptions.value = mergeSeasonOptions(result.items, season.value);
  } catch {
    // 选项失败不阻塞名单与统计；至少保留当前年份与已选赛季。
    seasonOptions.value = mergeSeasonOptions([], season.value);
  }
}

function changeSeason(event: Event): void {
  const next = (event.target as HTMLSelectElement).value;
  if (next === season.value) {
    return;
  }
  void router.replace({ query: serializeTeamSeason(next) });
}

watch(
  () => route.query.season,
  (raw) => {
    const next = parseTeamSeason(raw);
    season.value = next;
    if (!seasonOptions.value.includes(next)) {
      seasonOptions.value = mergeSeasonOptions(seasonOptions.value, next);
    }
    void load();
  },
  { immediate: true },
);

function onAvatarError(playerId: number): void {
  if (!failedAvatars.value.includes(playerId)) {
    failedAvatars.value = [...failedAvatars.value, playerId];
  }
}

function showAvatar(player: TeamPlayer): boolean {
  return avatarSrc(player.avatarUrl) !== null && !failedAvatars.value.includes(player.id);
}

onMounted(() => {
  void loadSeasonOptions();
});
</script>

<template>
  <section class="page">
    <header class="page-head team-head">
      <div>
        <p class="eyebrow">SQUAD</p>
        <h1>球队</h1>
      </div>
      <label class="team-season">
        <span>赛季</span>
        <select aria-label="赛季" :value="season" @change="changeSeason">
          <option v-for="option in seasonOptions" :key="option" :value="option">
            {{ option }}
          </option>
        </select>
      </label>
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
          <div class="team-group__head">
            <h2 class="team-group__title">
              <span>{{ positionText(group.position) }}</span>
              <span class="muted small">{{ group.players.length }} 人</span>
            </h2>
          </div>

          <div class="team-roster">
            <article v-for="player in group.players" :key="player.id" class="player-card">
              <div class="player-card__top">
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
                <span class="player-card__jersey">#{{ player.jersey ?? '—' }}</span>
              </div>

              <div class="player-card__body">
                <h3 class="player-card__name">{{ player.name }}</h3>

                <div class="player-card__meta muted small">
                  <span>{{ positionText(player.position) }}</span>
                  <span>{{ player.region || '—' }}</span>
                </div>

                <div class="player-card__stars" :aria-label="skillAriaLabel(player.skill)">
                  {{ skillStarText(player.skill) }}
                </div>

                <div class="player-card__stats small">
                  <span>出场 {{ player.appearances }}</span>
                  <span>进球 {{ player.goals ?? '—' }}</span>
                  <span>助攻 {{ player.assists ?? '—' }}</span>
                </div>
              </div>
            </article>
          </div>
        </section>
      </div>
    </template>
  </section>
</template>

<style scoped>
.team-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px 24px;
  align-items: flex-end;
  justify-content: space-between;
}

.team-season {
  display: flex;
  flex-direction: column;
  gap: 6px;
  color: var(--yh-muted);
  font-size: 12px;
  font-weight: 600;
}

.team-season select {
  min-width: 132px;
  min-height: 34px;
  padding: 6px 9px;
  color: var(--yh-ink);
  font-size: 13px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.team-season select:focus {
  border-color: var(--yh-green);
  outline: none;
  box-shadow: 0 0 0 3px rgba(22, 163, 74, 0.14);
}

.team-groups {
  display: flex;
  flex-direction: column;
  gap: 32px;
}

.team-group {
  display: block;
}

.team-group__head {
  display: flex;
  gap: 12px;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 14px;
}

.team-group__title {
  display: flex;
  gap: 8px;
  align-items: baseline;
  margin: 0;
  font-size: 22px;
  letter-spacing: -0.02em;
}

.team-roster {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.player-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius);
  box-shadow: var(--yh-shadow-sm);
}

.player-card__top {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.player-card__avatar {
  flex: none;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--yh-slate-soft);
}

.player-card__avatar--fallback {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: var(--yh-green);
  font-weight: 800;
}

.player-card__jersey {
  color: var(--yh-line);
  font-size: 24px;
  font-weight: 900;
  letter-spacing: -0.05em;
}

.player-card__body {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.player-card__name {
  margin: 0;
  font-size: 15px;
}

.player-card__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
}

.player-card__stars {
  color: var(--yh-gold);
  font-size: 12px;
  letter-spacing: 1px;
}

.player-card__stats {
  display: flex;
  gap: 12px;
  padding-top: 10px;
  margin-top: auto;
  color: var(--yh-muted);
  border-top: 1px dashed var(--yh-line);
}

@media (max-width: 1000px) {
  .team-roster {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .team-roster {
    grid-template-columns: 1fr;
  }
}
</style>
