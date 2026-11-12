<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import type { MatchDetail, MatchSummary, Page, Registration } from '@yunhefc/shared';
import {
  formatDate,
  formatDateTime,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
} from '@yunhefc/shared';
import { Button, Empty, Loading, Tag } from 'vant';
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { api } from '../api/client';
import { homeCta, type HomeCtaResult } from '../features/home/cta';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const router = useRouter();

const matches = ref<MatchSummary[]>([]);
const hero = ref<MatchDetail | null>(null);
const loading = ref(true);
const error = ref('');
const actionError = ref('');
const actionMessage = ref('');
const registering = ref(false);
const now = ref(Date.now());
let requestSeq = 0;

const cta = computed<HomeCtaResult | null>(() => {
  const detail = hero.value;
  if (!detail) {
    return null;
  }
  return homeCta({
    authenticated: session.isAuthenticated,
    userStatus: session.user?.status ?? null,
    playerType: session.player?.type ?? null,
    matchStatus: detail.status,
    now: now.value,
    signupDeadline: detail.signupDeadline,
    capacity: detail.capacity,
    guestCapacity: detail.guestCapacity,
    waitlistCapacity: detail.waitlistCapacity,
    confirmedCount: detail.confirmedCount,
    guestCount: detail.guestCount,
    waitlistCount: detail.waitlistCount,
    myRegistration: detail.myRegistration ?? null,
  });
});

function scoreText(match: MatchSummary): string {
  if (match.scoreHome === null || match.scoreHome === undefined) {
    return 'VS';
  }
  return `${match.scoreHome} : ${match.scoreAway ?? 0}`;
}

function statusTagType(match: MatchSummary): 'primary' | 'success' | 'warning' | 'danger' {
  const tone = matchStatusTone(match.status);
  return tone === 'neutral' ? 'primary' : tone;
}

/** silent=true 用于报名后的静默刷新，不打断当前 Hero。 */
async function load(silent = false): Promise<void> {
  const seq = ++requestSeq;
  if (!silent) {
    loading.value = true;
    error.value = '';
  }
  now.value = Date.now();

  try {
    const page = await api.get<Page<MatchSummary>>('/matches', {
      query: { scope: 'upcoming', sort: 'startAt:asc', pageSize: 3 },
    });
    if (seq !== requestSeq) {
      return;
    }
    matches.value = page.items;
    const [first] = page.items;
    if (!first) {
      hero.value = null;
      return;
    }

    const detail = await api.get<MatchDetail>(`/matches/${first.id}`);
    if (seq !== requestSeq) {
      return;
    }
    hero.value = detail;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    const message = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    if (silent) {
      actionError.value = message;
    } else {
      error.value = message;
      matches.value = [];
      hero.value = null;
    }
  } finally {
    if (seq === requestSeq && !silent) {
      loading.value = false;
    }
  }
}

async function register(): Promise<void> {
  const detail = hero.value;
  if (!detail || registering.value) {
    return;
  }

  registering.value = true;
  actionError.value = '';
  actionMessage.value = '';
  try {
    const result = await api.post<{ registration: Registration }>(
      `/matches/${detail.id}/registrations`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    actionMessage.value = result.registration.status === 1 ? '已加入候补' : '报名成功';
    await load(true);
  } catch (caught) {
    // 40902/40903/40905/42901 等业务错误原样展示后端 message。
    actionError.value = isApiError(caught) ? caught.message : '报名失败，请稍后重试';
  } finally {
    registering.value = false;
  }
}

function onCtaClick(): void {
  const current = cta.value;
  if (!current || current.disabled || registering.value) {
    return;
  }
  if (current.action === 'login') {
    void router.push({ name: 'login', query: { redirect: '/' } });
  } else if (current.action === 'view-detail' && hero.value) {
    void router.push(`/matches/${hero.value.id}`);
  } else if (current.action === 'register') {
    void register();
  }
}

onMounted(() => {
  void load();
});
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>首页</h1>
      <p class="muted">下一场比赛与近期赛程</p>
    </header>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load()">重试</Button>
    </div>

    <template v-else>
      <section v-if="hero && cta" class="home-hero">
        <div class="home-hero__head">
          <span class="home-hero__eyebrow">下一场</span>
          <Tag :type="statusTagType(hero)">{{ matchStatusText(hero.status) }}</Tag>
        </div>

        <article class="home-hero__card">
          <div class="home-hero__meta-row">
            <span class="muted small">{{ matchTypeText(hero.type) }}</span>
            <span class="muted small">第 {{ hero.seasonMatchNo }} 场</span>
          </div>

          <h2 class="home-hero__title">{{ hero.title }}</h2>

          <div class="home-hero__teams">
            <span>{{ hero.homeTeamName }}</span>
            <strong>VS</strong>
            <span>{{ hero.awayTeamName }}</span>
          </div>

          <div class="home-hero__meta">
            <span>{{ formatDateTime(hero.startAt) }}</span>
            <span>{{ hero.venue }}</span>
          </div>

          <div class="hero-queue">
            <div class="hero-queue__item">
              <span>正式</span>
              <b>{{ hero.confirmedCount }} / {{ hero.capacity }}</b>
            </div>
            <div class="hero-queue__item">
              <span>外挂</span>
              <b>{{ hero.guestCount }} / {{ hero.guestCapacity }}</b>
            </div>
            <div class="hero-queue__item">
              <span>候补</span>
              <b>{{ hero.waitlistCount }} / {{ hero.waitlistCapacity }}</b>
            </div>
          </div>

          <Button
            type="primary"
            block
            :disabled="cta.disabled || registering"
            :loading="registering"
            @click="onCtaClick"
          >
            {{ cta.label }}
          </Button>

          <p v-if="cta.reason" class="hint">{{ cta.reason }}</p>
          <p v-if="actionMessage" class="hint">{{ actionMessage }}</p>
          <p v-if="actionError" class="action-error">{{ actionError }}</p>

          <RouterLink class="ghost-link" :to="`/matches/${hero.id}`">查看比赛详情 →</RouterLink>
        </article>
      </section>

      <div v-else class="card home-empty">
        <Empty description="暂无待进行比赛" />
        <p class="muted small">当前没有可报名或进行中的比赛。</p>
        <RouterLink to="/matches">
          <Button size="small" type="primary">去赛程</Button>
        </RouterLink>
      </div>

      <section v-if="matches.length > 0" class="home-section">
        <div class="card-head">
          <h2>近期赛程</h2>
          <RouterLink class="ghost-link" to="/matches">全部赛程 →</RouterLink>
        </div>

        <div class="match-list">
          <RouterLink
            v-for="match in matches"
            :key="match.id"
            class="match-card-link"
            :to="`/matches/${match.id}`"
          >
            <article class="match-card">
              <div class="match-card__top">
                <span class="match-card__date">{{ formatDate(match.startAt) }}</span>
                <Tag :type="statusTagType(match)" plain>{{ matchStatusText(match.status) }}</Tag>
                <span class="muted small">第 {{ match.seasonMatchNo }} 场</span>
              </div>

              <div class="match-card__teams">
                <span>{{ match.homeTeamName }}</span>
                <strong>{{ scoreText(match) }}</strong>
                <span>{{ match.awayTeamName }}</span>
              </div>

              <div class="match-card__meta muted small">
                <span>{{ matchTypeText(match.type) }}</span>
                <span>{{ formatDateTime(match.startAt) }}</span>
                <span>{{ match.venue }}</span>
              </div>

              <div class="match-card__queue small">
                正式 {{ match.confirmedCount }} · 外挂 {{ match.guestCount }} · 候补
                {{ match.waitlistCount }}
              </div>
            </article>
          </RouterLink>
        </div>
      </section>

      <section class="card home-firepower">
        <div class="card-head">
          <h2>球队火力</h2>
          <span class="muted small">二期上线</span>
        </div>
        <p class="hint">球队火力将在二期上线。</p>
      </section>
    </template>
  </section>
</template>
