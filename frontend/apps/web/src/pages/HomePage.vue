<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { hasBackendPermission } from '@yunhefc/permissions';
import type { MatchDetail, MatchSummary, Page, Registration } from '@yunhefc/shared';
import { formatDateTimeRange, matchStatusText, matchTypeText } from '@yunhefc/shared';
import { Button, Empty, Loading, Tag } from 'vant';
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';

import { api } from '../api/client';
import MatchCard from '../components/MatchCard.vue';
import { adminUrl, openAdmin } from '../features/admin-handoff/admin-handoff';
import { homeCta, type HomeCtaResult } from '../features/home/cta';
import { queuePercent, scoreText, statusTone, winnerSide } from '../features/matches/presentation';
import { useSessionStore } from '../stores/session';

const FEATURES = [
  { icon: '📈', title: '排行榜与身价', text: '年度出场、进球、助攻、胜率、缺勤与 AI 身价' },
  { icon: '💰', title: '球队账本', text: '收入/支出分类，按月/年统计' },
  { icon: '📸', title: '动态与相册', text: '比赛照片、B 站视频、点赞留言' },
  { icon: '🗳️', title: '投票', text: '标题、图片选项、多选、截止时间' },
  { icon: '📣', title: '公众号提醒', text: '赛事发布与模板消息' },
  { icon: '🏆', title: '资产与荣誉', text: '器材台账、球队里程碑' },
];

const session = useSessionStore();
const router = useRouter();

/** 后台入口仅对具备任一后台权限的账号显示；与顶部导航入口共用同一套票据跳转。 */
const canEnterAdmin = computed(
  () => session.isAuthenticated && hasBackendPermission(session.permissions),
);
const adminEntryUrl = computed(() => adminUrl({ path: '/' }));

function onAdminEntry(event: MouseEvent): void {
  event.preventDefault();
  void openAdmin({ path: '/' });
}

const matches = ref<MatchSummary[]>([]);
const hero = ref<MatchDetail | null>(null);
const loading = ref(true);
const error = ref('');
const actionError = ref('');
const actionMessage = ref('');
const refreshFailed = ref(false);
const registering = ref(false);
const now = ref(Date.now());
let requestSeq = 0;
let deadlineTimer: ReturnType<typeof setInterval> | null = null;

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
    queue: detail,
    myRegistration: detail.myRegistration ?? null,
  });
});

const heroWinner = computed(() => (hero.value ? winnerSide(hero.value) : null));

/** 仅清除静默刷新失败留下的错误，避免吞掉报名业务错误。 */
function clearRefreshError(): void {
  if (refreshFailed.value) {
    refreshFailed.value = false;
    actionError.value = '';
  }
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
      query: { sort: 'startAt:desc', pageSize: 4 },
    });
    if (seq !== requestSeq) {
      return;
    }
    // Hero 取全部比赛里开赛时间最大的一条；最近比赛取其后 3 场，不重复展示 Hero。
    const [first] = page.items;
    matches.value = page.items.filter((match) => match.id !== first?.id).slice(0, 3);
    if (!first) {
      hero.value = null;
      clearRefreshError();
      return;
    }

    const detail = await api.get<MatchDetail>(`/matches/${first.id}`);
    if (seq !== requestSeq) {
      return;
    }
    hero.value = detail;
    clearRefreshError();
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    const message = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    if (silent) {
      actionError.value = message;
      refreshFailed.value = true;
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
  refreshFailed.value = false;
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
  deadlineTimer = setInterval(() => {
    now.value = Date.now();
  }, 30_000);
});

onUnmounted(() => {
  if (deadlineTimer !== null) {
    clearInterval(deadlineTimer);
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

    <template v-else>
      <section v-if="hero && cta" class="home-hero">
        <div class="home-hero__inner">
          <div class="home-hero__copy">
            <p class="eyebrow">YUNHE FC · 2026</p>
            <h1>云河FC</h1>
            <p>一支球队的运营台，也是一群人的比赛日记</p>
            <div class="home-hero__actions">
              <RouterLink to="/matches">
                <Button type="primary">查看比赛</Button>
              </RouterLink>
              <RouterLink to="/team">
                <Button plain>球队名单</Button>
              </RouterLink>
              <a
                v-if="canEnterAdmin"
                data-testid="hero-admin-entry"
                :href="adminEntryUrl"
                @click="onAdminEntry"
              >
                <Button type="primary">管理后台</Button>
              </a>
            </div>
          </div>

          <article class="home-hero__card">
            <div class="home-hero__meta-row">
              <span class="home-hero__eyebrow">{{ matchTypeText(hero.type) }}</span>
              <Tag class="status-tag" :class="`status-tag--${statusTone(hero.status)}`">
                {{ matchStatusText(hero.status) }}
              </Tag>
            </div>

            <div class="home-hero__teams">
              <span :class="{ 'team-name--winner': heroWinner === 0 }">
                {{ hero.homeTeamName }}
              </span>
              <strong>{{ scoreText(hero) }}</strong>
              <span :class="{ 'team-name--winner': heroWinner === 1 }">
                {{ hero.awayTeamName }}
              </span>
            </div>

            <div class="home-hero__meta">
              <span>{{ formatDateTimeRange(hero.startAt, hero.endAt) }}</span>
              <span>{{ hero.venue }}</span>
              <span>第 {{ hero.seasonMatchNo }} 场</span>
            </div>

            <div class="hero-queue">
              <div class="hero-queue__item">
                <div class="hero-queue__row">
                  <span>正式</span>
                  <b>{{ hero.confirmedCount }} / {{ hero.capacity }}</b>
                </div>
                <div class="progress">
                  <i :style="{ width: `${queuePercent(hero.confirmedCount, hero.capacity)}%` }" />
                </div>
              </div>
              <div class="hero-queue__item">
                <div class="hero-queue__row">
                  <span>外挂</span>
                  <b>{{ hero.guestCount }} / {{ hero.guestCapacity }}</b>
                </div>
                <div class="progress">
                  <i :style="{ width: `${queuePercent(hero.guestCount, hero.guestCapacity)}%` }" />
                </div>
              </div>
              <div class="hero-queue__item">
                <div class="hero-queue__row">
                  <span>候补</span>
                  <b>{{ hero.waitlistCount }} / {{ hero.waitlistCapacity }}</b>
                </div>
                <div class="progress">
                  <i
                    :style="{
                      width: `${queuePercent(hero.waitlistCount, hero.waitlistCapacity)}%`,
                    }"
                  />
                </div>
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
            <p v-if="actionError" class="action-error">
              {{ actionError }}
              <Button v-if="refreshFailed" size="mini" type="primary" plain @click="load(true)">
                重试刷新
              </Button>
            </p>

            <RouterLink class="ghost-link" :to="`/matches/${hero.id}`">查看比赛详情 →</RouterLink>
          </article>
        </div>
      </section>

      <div v-else class="card home-empty">
        <Empty description="暂无比赛" />
        <p class="muted small">还没有比赛记录，等管理员排期后这里会显示最近一场。</p>
        <RouterLink to="/matches">
          <Button size="small" type="primary">去比赛</Button>
        </RouterLink>
      </div>

      <section v-if="matches.length > 0" class="home-section">
        <div class="card-head">
          <h2>最近比赛</h2>
          <RouterLink class="ghost-link" to="/matches">全部比赛 →</RouterLink>
        </div>

        <div class="match-list">
          <RouterLink
            v-for="match in matches"
            :key="match.id"
            class="match-card-link"
            :to="`/matches/${match.id}`"
          >
            <MatchCard :match="match" />
          </RouterLink>
        </div>
      </section>

      <section class="home-firepower">
        <div class="section-head">
          <div>
            <h2>二期能力</h2>
            <p>球队数据与运营能力将在二期上线。</p>
          </div>
        </div>

        <div class="feature-grid">
          <article v-for="feature in FEATURES" :key="feature.title" class="feature-card card">
            <span class="feature-icon" aria-hidden="true">{{ feature.icon }}</span>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
            <Tag class="status-tag status-tag--soon">二期</Tag>
          </article>
        </div>
      </section>
    </template>
  </section>
</template>
