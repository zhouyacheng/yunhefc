<script setup lang="ts">
import { ERROR_CODES, isApiError, isVersionConflict, newIdempotencyKey } from '@yunhefc/api-client';
import { EVENT_DELETE_ANY, EVENT_EDIT_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  DerivedScore,
  EventCreate,
  EventDeleteResult,
  EventHalf,
  EventTeam,
  EventTimeline,
  EventType,
  EventUpdate,
  MatchAdmin,
  MatchEvent,
  Page,
  RegistrationRow,
  ScoreResponse,
} from '@yunhefc/shared';
import {
  EVENT_HALF_TEXT,
  EVENT_TYPE_TEXT,
  MATCH_STATUS_CANCELLED,
  MATCH_STATUS_POSTPONED,
  MATCH_STATUS_TEXT,
  eventHalfText,
  eventTypeText,
  formatDateTime,
  positionText,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { api, publicApi } from '../api/client';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const session = useSessionStore();

const matches = ref<MatchAdmin[]>([]);
const selectedMatchId = ref<number | null>(null);
const loadingMatches = ref(false);
const error = ref('');

const events = ref<MatchEvent[]>([]);
const derived = ref<DerivedScore | null>(null);
const loadingTimeline = ref(false);
const timelineError = ref('');

const roster = ref<RegistrationRow[]>([]);
const rosterError = ref('');
const loadingRoster = ref(false);

const scoreHome = ref<number | null>(0);
const scoreAway = ref<number | null>(0);
const scoreSaving = ref(false);

const dialogVisible = ref(false);
const editingEvent = ref<MatchEvent | null>(null);
const eventSubmitting = ref(false);
const eventFormError = ref('');
const selectorError = ref('');
const actionBusy = ref<string | null>(null);

const form = reactive({
  type: 0 as EventType,
  team: 0 as EventTeam,
  playerId: null as number | null,
  assistPlayerId: null as number | null,
  inPlayerId: null as number | null,
  outPlayerId: null as number | null,
  minute: null as number | null,
  half: 0 as EventHalf,
  penalty: false,
});

const canEdit = computed(() => hasPermission(session.permissions, EVENT_EDIT_ANY));
const canDelete = computed(() => hasPermission(session.permissions, EVENT_DELETE_ANY));
const selectedMatch = computed(
  () => matches.value.find((match) => match.id === selectedMatchId.value) ?? null,
);
const readOnly = computed(
  () =>
    selectedMatch.value?.status === MATCH_STATUS_CANCELLED ||
    selectedMatch.value?.status === MATCH_STATUS_POSTPONED,
);

const typeOptions = Object.entries(EVENT_TYPE_TEXT).map(([value, label]) => ({
  value: Number(value) as EventType,
  label,
}));
const halfOptions = Object.entries(EVENT_HALF_TEXT).map(([value, label]) => ({
  value: Number(value) as EventHalf,
  label,
}));

const teamOptions = computed(() => {
  const match = selectedMatch.value;
  return [
    { value: 0 as EventTeam, label: match?.homeTeamName ?? '主队' },
    { value: 1 as EventTeam, label: match?.awayTeamName ?? '客队' },
  ];
});

const rosterPlayers = computed(() =>
  roster.value
    .filter((row) => row.status === 0)
    .map((row) => row.player)
    .filter((player, index, all) => all.findIndex((item) => item.id === player.id) === index),
);

const derivedText = computed(() => {
  if (!derived.value) {
    return '暂无有效事件';
  }
  return `${derived.value.scoreHome} : ${derived.value.scoreAway}`;
});

const storedText = computed(() => {
  const match = selectedMatch.value;
  if (!match || match.scoreHome === null || match.scoreHome === undefined) {
    return '未录入';
  }
  return `${match.scoreHome} : ${match.scoreAway ?? 0}`;
});

const scoreMismatch = computed(() => {
  const match = selectedMatch.value;
  if (!match || !derived.value) {
    return false;
  }
  if (match.scoreHome === null || match.scoreHome === undefined) {
    return false;
  }
  return (
    match.scoreHome !== derived.value.scoreHome ||
    (match.scoreAway ?? 0) !== derived.value.scoreAway
  );
});

function playerLabel(player: { name: string; jersey?: number | null; position: number }): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey} · ${positionText(player.position)}`;
}

function eventPlayerLabel(event: MatchEvent): string {
  if (event.type === 2) {
    return `${event.inPlayer?.name ?? '—'} 换上 · ${event.outPlayer?.name ?? '—'} 换下`;
  }
  const parts = [event.player?.name ?? '—'];
  if (event.assistPlayer) {
    parts.push(`助攻 ${event.assistPlayer.name}`);
  }
  if (event.penalty) {
    parts.push('点球');
  }
  return parts.join(' · ');
}

function teamName(team: EventTeam): string {
  const match = selectedMatch.value;
  if (!match) {
    return team === 0 ? '主队' : '客队';
  }
  return team === 0 ? match.homeTeamName : match.awayTeamName;
}

function needsRefresh(caught: unknown): boolean {
  return (
    isVersionConflict(caught) ||
    (isApiError(caught) && caught.code === ERROR_CODES.MATCH_STATE_CONFLICT)
  );
}

function syncScoreInputs(): void {
  const match = selectedMatch.value;
  scoreHome.value = match?.scoreHome ?? 0;
  scoreAway.value = match?.scoreAway ?? 0;
}

async function loadMatches(): Promise<void> {
  loadingMatches.value = true;
  error.value = '';
  try {
    const result = await api.get<Page<MatchAdmin>>('/matches', {
      query: { scope: 'all', sort: 'startAt:desc', page: 1, pageSize: 100 },
    });
    matches.value = result.items;
    const requested = Number(route.query.match);
    if (Number.isInteger(requested) && !matches.value.some((match) => match.id === requested)) {
      // C03 深链可能指向第一页之外的旧比赛，按 id 补取，避免静默落到最新一场。
      try {
        const detail = await api.get<MatchAdmin>(`/matches/${requested}`);
        matches.value = [detail, ...matches.value];
      } catch {
        // 不存在或无权查看时回退到列表首场。
      }
    }
    if (
      selectedMatchId.value === null ||
      !matches.value.some((match) => match.id === selectedMatchId.value)
    ) {
      selectedMatchId.value = matches.value.some((match) => match.id === requested)
        ? requested
        : (matches.value[0]?.id ?? null);
    }
    await refreshSelected();
  } catch (caught) {
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    loadingMatches.value = false;
  }
}

async function refreshSelected(): Promise<void> {
  syncScoreInputs();
  // 切换比赛时先清空上一场数据，避免慢请求期间展示错误的推导比分与时间线。
  events.value = [];
  derived.value = null;
  timelineError.value = '';
  roster.value = [];
  rosterError.value = '';
  await Promise.all([loadTimeline(), loadRoster()]);
}

function onMatchChange(): void {
  void refreshSelected();
}

async function loadTimeline(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null) {
    events.value = [];
    derived.value = null;
    return;
  }
  loadingTimeline.value = true;
  timelineError.value = '';
  try {
    const timeline = await publicApi.get<EventTimeline>(`/matches/${matchId}/events`);
    if (matchId !== selectedMatchId.value) {
      return;
    }
    events.value = timeline.items;
    derived.value = timeline.derivedScore ?? null;
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      events.value = [];
      derived.value = null;
      timelineError.value = isApiError(caught) ? caught.message : '事件加载失败，请稍后重试';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingTimeline.value = false;
    }
  }
}

async function loadRoster(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null) {
    roster.value = [];
    return;
  }
  loadingRoster.value = true;
  rosterError.value = '';
  try {
    const result = await api.get<Page<RegistrationRow>>(`/matches/${matchId}/registrations`, {
      query: { status: 0, page: 1, pageSize: 100 },
    });
    if (matchId === selectedMatchId.value) {
      roster.value = result.items;
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      roster.value = [];
      rosterError.value = isApiError(caught) ? caught.message : '名单加载失败';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingRoster.value = false;
    }
  }
}

async function saveScore(): Promise<void> {
  const match = selectedMatch.value;
  if (!match || readOnly.value || scoreSaving.value) {
    return;
  }
  const home = scoreHome.value;
  const away = scoreAway.value;
  if (
    home === null ||
    away === null ||
    !Number.isInteger(home) ||
    !Number.isInteger(away) ||
    home < 0 ||
    away < 0 ||
    home > 255 ||
    away > 255
  ) {
    ElMessage.error('比分必须为 0–255 的整数');
    return;
  }
  scoreSaving.value = true;
  try {
    const response = await api.patch<ScoreResponse>(
      `/matches/${match.id}/score`,
      { scoreHome: home, scoreAway: away, version: match.version },
      { idempotencyKey: newIdempotencyKey() },
    );
    // 服务端版本固定 +1；用响应就地更新，避免重拉整页比赛与时间线。
    match.scoreHome = response.score.scoreHome;
    match.scoreAway = response.score.scoreAway;
    match.version += 1;
    derived.value = response.score.derived ?? null;
    syncScoreInputs();
    const derivedScore = response.score.derived;
    ElMessage.success(
      derivedScore &&
        (derivedScore.scoreHome !== response.score.scoreHome ||
          derivedScore.scoreAway !== response.score.scoreAway)
        ? '比分已保存；与事件推导比分不一致，仅提示不阻断'
        : '比分已保存',
    );
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '比分保存失败');
    if (needsRefresh(caught)) {
      await loadMatches();
    }
  } finally {
    scoreSaving.value = false;
  }
}

function resetForm(): void {
  form.type = 0;
  form.team = 0;
  form.playerId = null;
  form.assistPlayerId = null;
  form.inPlayerId = null;
  form.outPlayerId = null;
  form.minute = null;
  form.half = 0;
  form.penalty = false;
  eventFormError.value = '';
  selectorError.value = '';
}

async function openCreate(): Promise<void> {
  if (readOnly.value) {
    return;
  }
  editingEvent.value = null;
  resetForm();
  // 弹窗打开前刷新名单，避免展示已被移除或遗漏的确认报名球员。
  await loadRoster();
  dialogVisible.value = true;
}

async function openEdit(event: MatchEvent): Promise<void> {
  if (readOnly.value) {
    return;
  }
  editingEvent.value = event;
  form.type = event.type;
  form.team = event.team;
  form.playerId = event.player?.id ?? null;
  form.assistPlayerId = event.assistPlayer?.id ?? null;
  form.inPlayerId = event.inPlayer?.id ?? null;
  form.outPlayerId = event.outPlayer?.id ?? null;
  form.minute = event.minute;
  form.half = event.half;
  form.penalty = event.penalty;
  eventFormError.value = '';
  selectorError.value = '';
  await loadRoster();
  dialogVisible.value = true;
}

function eventPayload(): EventCreate {
  const substitution = form.type === 2;
  const goal = form.type === 0;
  // 显式 null 表示清空；避免 undefined 被 JSON 丢弃后退化为“未提供”。
  return {
    type: form.type,
    team: form.team,
    playerId: substitution ? null : (form.playerId ?? null),
    assistPlayerId: goal ? (form.assistPlayerId ?? null) : null,
    inPlayerId: substitution ? (form.inPlayerId ?? null) : null,
    outPlayerId: substitution ? (form.outPlayerId ?? null) : null,
    minute: form.minute ?? 0,
    half: form.half,
    penalty: goal ? form.penalty : false,
  };
}

function validateForm(): string {
  if (!form.minute || form.minute < 1 || form.minute > 120) {
    return '请填写 1–120 的比赛分钟';
  }
  if (form.type === 2) {
    if (!form.inPlayerId || !form.outPlayerId) {
      return '换人事件必须选择换上和换下球员';
    }
    if (form.inPlayerId === form.outPlayerId) {
      return '换上和换下球员不能相同';
    }
    return '';
  }
  if (!form.playerId) {
    return '请选择球员';
  }
  if (form.type === 0 && form.assistPlayerId === form.playerId) {
    return '助攻球员不能是进球球员本人';
  }
  return '';
}

async function submitEvent(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || eventSubmitting.value || readOnly.value) {
    return;
  }
  const message = validateForm();
  if (message) {
    eventFormError.value = message;
    return;
  }
  eventSubmitting.value = true;
  eventFormError.value = '';
  selectorError.value = '';
  try {
    if (editingEvent.value) {
      const update: EventUpdate = {
        ...eventPayload(),
        version: editingEvent.value.version,
      };
      await api.patch(`/events/${editingEvent.value.id}`, update, {
        idempotencyKey: newIdempotencyKey(),
      });
    } else {
      await api.post<{ event: MatchEvent }>(`/matches/${matchId}/events`, eventPayload(), {
        idempotencyKey: newIdempotencyKey(),
      });
    }
    dialogVisible.value = false;
    ElMessage.success(editingEvent.value ? '事件已更新' : '事件已录入');
    await loadTimeline();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.GROUPING_MISMATCH) {
      // 行内提示违规字段与球员，并引导到 B06 重新生成分组。
      selectorError.value = caught.message;
    } else if (needsRefresh(caught)) {
      dialogVisible.value = false;
      ElMessage.error(isApiError(caught) ? caught.message : '状态或版本已变更，请重试');
      await loadMatches();
    } else {
      eventFormError.value = isApiError(caught) ? caught.message : '事件保存失败，请稍后重试';
    }
  } finally {
    eventSubmitting.value = false;
  }
}

async function deleteEvent(event: MatchEvent): Promise<void> {
  if (readOnly.value || actionBusy.value) {
    return;
  }
  try {
    await ElMessageBox.confirm('删除后时间线与推导比分不再计入，原记录仍可追溯。', '删除事件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  const key = `delete:${event.id}`;
  actionBusy.value = key;
  try {
    await api.delete<EventDeleteResult>(
      `/events/${event.id}`,
      { version: event.version },
      {
        idempotencyKey: newIdempotencyKey(),
      },
    );
    ElMessage.success('事件已删除');
    await loadTimeline();
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '删除失败');
    if (needsRefresh(caught)) {
      await loadMatches();
    }
  } finally {
    if (actionBusy.value === key) {
      actionBusy.value = null;
    }
  }
}

onMounted(loadMatches);
</script>

<template>
  <section class="events-page">
    <div class="page-head">
      <div>
        <h2>事件 / 比分</h2>
        <p class="muted">存储比分与事件独立；不一致只提示。已取消/已延期比赛只读。</p>
      </div>
      <el-select
        v-if="matches.length"
        v-model="selectedMatchId"
        class="match-picker"
        filterable
        placeholder="选择比赛"
        @change="onMatchChange"
      >
        <el-option
          v-for="match in matches"
          :key="match.id"
          :value="match.id"
          :label="`${match.title} · ${formatDateTime(match.startAt)} · ${MATCH_STATUS_TEXT[match.status]}`"
        />
      </el-select>
    </div>

    <el-alert
      v-if="error"
      class="page-error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="loadMatches">重试</el-button>
    </el-alert>

    <div v-loading="loadingMatches" class="page-body">
      <el-empty v-if="!loadingMatches && !matches.length" description="暂无比赛，请先创建比赛">
        <RouterLink to="/matches">
          <el-button type="primary">去比赛管理</el-button>
        </RouterLink>
      </el-empty>

      <template v-else-if="selectedMatch">
        <el-alert
          v-if="readOnly"
          class="notice"
          type="warning"
          :closable="false"
          show-icon
          title="比赛已取消或已延期，事件与比分只读。"
        />

        <div class="board">
          <section class="card score-card">
            <header class="card-head">
              <h3>比分</h3>
              <el-tag size="small" effect="light">{{
                MATCH_STATUS_TEXT[selectedMatch.status]
              }}</el-tag>
            </header>

            <div class="score-row">
              <span class="team-name">{{ selectedMatch.homeTeamName }}</span>
              <el-input-number
                v-model="scoreHome"
                :min="0"
                :max="255"
                :disabled="readOnly"
                controls-position="right"
              />
              <span class="score-colon">:</span>
              <el-input-number
                v-model="scoreAway"
                :min="0"
                :max="255"
                :disabled="readOnly"
                controls-position="right"
              />
              <span class="team-name">{{ selectedMatch.awayTeamName }}</span>
            </div>

            <dl class="score-meta">
              <div>
                <dt>存储比分</dt>
                <dd>{{ storedText }}</dd>
              </div>
              <div>
                <dt>事件推导</dt>
                <dd>{{ derivedText }}</dd>
              </div>
            </dl>

            <p v-if="scoreMismatch" class="hint hint--warning">
              存储比分与事件推导比分不一致；保存不阻断，仅提示。
            </p>

            <el-button
              type="primary"
              :disabled="readOnly || !canEdit"
              :loading="scoreSaving"
              @click="saveScore"
            >
              保存比分
            </el-button>
          </section>

          <section class="card timeline-card">
            <header class="card-head">
              <h3>事件时间线</h3>
              <el-button
                size="small"
                type="primary"
                :disabled="readOnly || !canEdit"
                @click="openCreate"
              >
                + 录入事件
              </el-button>
            </header>

            <p v-if="rosterError" class="hint hint--warning">{{ rosterError }}</p>

            <div v-loading="loadingTimeline" class="timeline">
              <el-alert
                v-if="timelineError"
                type="error"
                :title="timelineError"
                :closable="false"
                show-icon
              >
                <el-button size="small" @click="loadTimeline">重试</el-button>
              </el-alert>
              <el-empty
                v-else-if="!loadingTimeline && !events.length"
                description="还没有事件记录"
              />
              <div v-for="event in events" :key="event.id" class="event-row">
                <div class="event-time">
                  {{ eventHalfText(event.half) }}<br />{{ event.minute }}'
                </div>
                <div class="event-main">
                  <div class="event-title">
                    <el-tag size="small" effect="light">{{ eventTypeText(event.type) }}</el-tag>
                    <strong>{{ teamName(event.team) }}</strong>
                    <span>{{ eventPlayerLabel(event) }}</span>
                  </div>
                  <small class="muted">版本 {{ event.version }}</small>
                </div>
                <div class="event-actions">
                  <el-button size="small" :disabled="readOnly || !canEdit" @click="openEdit(event)">
                    编辑
                  </el-button>
                  <el-button
                    v-if="canDelete"
                    size="small"
                    type="danger"
                    plain
                    :disabled="readOnly"
                    :loading="actionBusy === `delete:${event.id}`"
                    @click="deleteEvent(event)"
                  >
                    删除
                  </el-button>
                </div>
              </div>
            </div>
          </section>
        </div>
      </template>
    </div>

    <el-dialog
      v-model="dialogVisible"
      :title="editingEvent ? '编辑事件' : '录入事件'"
      width="560px"
    >
      <el-form label-width="88px">
        <el-form-item label="类型">
          <el-select v-model="form.type">
            <el-option
              v-for="option in typeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="球队">
          <el-radio-group v-model="form.team">
            <el-radio-button
              v-for="option in teamOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <template v-if="form.type === 2">
          <el-form-item label="换上球员">
            <el-select v-model="form.inPlayerId" filterable placeholder="选择换上球员">
              <el-option
                v-for="player in rosterPlayers"
                :key="player.id"
                :value="player.id"
                :label="playerLabel(player)"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="换下球员">
            <el-select v-model="form.outPlayerId" filterable placeholder="选择换下球员">
              <el-option
                v-for="player in rosterPlayers"
                :key="player.id"
                :value="player.id"
                :label="playerLabel(player)"
              />
            </el-select>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="球员">
            <el-select v-model="form.playerId" filterable placeholder="选择本场确认报名球员">
              <el-option
                v-for="player in rosterPlayers"
                :key="player.id"
                :value="player.id"
                :label="playerLabel(player)"
              />
            </el-select>
          </el-form-item>

          <template v-if="form.type === 0">
            <el-form-item label="助攻球员">
              <el-select v-model="form.assistPlayerId" filterable clearable placeholder="可选">
                <el-option
                  v-for="player in rosterPlayers"
                  :key="player.id"
                  :value="player.id"
                  :label="playerLabel(player)"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="点球">
              <el-switch v-model="form.penalty" />
            </el-form-item>
          </template>
        </template>

        <el-form-item label="半场">
          <el-radio-group v-model="form.half">
            <el-radio-button
              v-for="option in halfOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio-button>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="分钟">
          <el-input-number v-model="form.minute" :min="1" :max="120" controls-position="right" />
        </el-form-item>

        <p v-if="selectorError" class="form-error selector-error" role="alert">
          {{ selectorError }}
          <router-link
            v-if="selectedMatchId !== null"
            class="selector-error__link"
            :to="{ path: '/grouping', query: { match: selectedMatchId } }"
          >
            去 B06 重新生成分组
          </router-link>
        </p>
        <p v-if="eventFormError" class="form-error">{{ eventFormError }}</p>
        <p v-if="!loadingRoster && !rosterPlayers.length" class="hint">
          本场暂无确认报名球员，请先到报名/考勤确认名单。
        </p>
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="eventSubmitting" @click="submitEvent">
          保存事件
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.events-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.match-picker {
  width: 380px;
  max-width: 100%;
}

.page-body {
  min-height: 160px;
}

.notice {
  margin-bottom: 16px;
}

.board {
  display: grid;
  grid-template-columns: minmax(280px, 1fr) minmax(0, 2fr);
  align-items: start;
  gap: 16px;
}

.card {
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.card-head h3 {
  margin: 0;
  font-size: 15px;
}

.score-row {
  display: grid;
  grid-template-columns: 1fr auto auto auto 1fr;
  align-items: center;
  gap: 8px;
}

.score-row .team-name {
  font-size: 13px;
  color: var(--yh-muted);
  text-align: center;
}

.score-colon {
  font-weight: 700;
}

.score-meta {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 14px 0;
}

.score-meta dt {
  color: var(--yh-muted);
  font-size: 12px;
}

.score-meta dd {
  margin: 2px 0 0;
  font-size: 18px;
  font-weight: 700;
}

.timeline {
  min-height: 100px;
}

.event-row {
  display: grid;
  grid-template-columns: 70px minmax(0, 1fr) auto;
  align-items: center;
  gap: 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--yh-line);
}

.event-row:last-child {
  border-bottom: none;
}

.event-time {
  color: var(--yh-muted);
  font-size: 12px;
  line-height: 1.4;
  text-align: center;
}

.event-title {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.event-actions {
  display: flex;
  gap: 6px;
}

.hint {
  margin: 8px 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.hint--warning {
  color: var(--el-color-warning);
}

.form-error {
  margin: 0 0 12px;
  color: var(--el-color-danger);
  font-size: 12px;
}

.selector-error {
  padding: 8px 10px;
  background: var(--el-color-danger-light-9);
  border-radius: 6px;
}

.selector-error__link {
  display: inline-block;
  margin-top: 4px;
  color: var(--el-color-primary);
  text-decoration: underline;
}

.muted {
  color: var(--yh-muted);
}

@media (max-width: 960px) {
  .board {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
  }

  .match-picker {
    width: 100%;
  }
}
</style>
