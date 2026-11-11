<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import {
  EVENT_CREATE_OWN,
  EVENT_EDIT_ANY,
  EVENT_EDIT_OWN,
  GROUPING_GENERATE_ANY,
  GROUPING_VIEW_ANY,
  hasPermission,
} from '@yunhefc/permissions';
import type {
  Attendance,
  DerivedScore,
  EventCreate,
  EventHalf,
  EventTeam,
  EventTimeline,
  EventType,
  EventUpdate,
  GroupedPlayer,
  GroupingTeam,
  GroupingView,
  MatchDetail,
  MatchEvent,
  MyMatchRegistration,
  Page,
  Registration,
  RegistrationRow,
} from '@yunhefc/shared';
import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  EVENT_TYPE_TEXT,
  REGISTRATION_STATUS_TEXT,
  checkInWindowState,
  eventEditWindowOpen,
  eventEntryOpen,
  eventHalfText,
  eventTypeText,
  formatDateTime,
  groupingTeamName,
  isInternalMatchType,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
  positionText,
} from '@yunhefc/shared';
import { Button, Empty, Loading, Popup, Tag } from 'vant';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';

import { api } from '../api/client';
import { toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const session = useSessionStore();
const matchId = Number(route.params.id);

type TabName = 'overview' | 'events' | 'grouping';

const activeTab = ref<TabName>('overview');
const match = ref<MatchDetail | null>(null);
const registration = ref<Registration | null>(null);
const attendance = ref<Attendance | null>(null);
const loading = ref(true);
const error = ref('');
const checkInLoading = ref(false);
const actionError = ref('');

const events = ref<MatchEvent[]>([]);
const eventsLoading = ref(false);
const eventsError = ref('');
const eventDerived = ref<DerivedScore | null>(null);
const roster = ref<RegistrationRow[]>([]);

const eventModalOpen = ref(false);
const editingEvent = ref<MatchEvent | null>(null);
const eventFormError = ref('');
const eventSubmitting = ref(false);

const grouping = ref<GroupingView | null>(null);
const groupingLoading = ref(false);
const groupingError = ref('');
const groupingTeams: GroupingTeam[] = ['A', 'B'];
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

const isEventAdmin = computed(() => hasPermission(session.permissions, EVENT_EDIT_ANY));
const hasEventCreate = computed(
  () =>
    hasPermission(session.permissions, EVENT_CREATE_OWN) ||
    hasPermission(session.permissions, EVENT_EDIT_ANY),
);
const eventEntryAllowed = computed(
  () =>
    Boolean(match.value) &&
    !checkInClosed.value &&
    eventEntryOpen(match.value?.status) &&
    hasEventCreate.value,
);
const playerWindowOpen = computed(() => {
  const current = match.value;
  return current !== null && eventEditWindowOpen(current.endAt);
});
const canCreateEvent = computed(
  () => eventEntryAllowed.value && (isEventAdmin.value || playerWindowOpen.value),
);
const eventDisabledReason = computed(() => {
  if (!match.value || hasEventCreate.value === false || isEventAdmin.value) {
    return '';
  }
  if (!eventEntryOpen(match.value.status)) {
    return checkInClosed.value
      ? '已取消/已延期的比赛不可录入事件'
      : '仅进行中或已结束的比赛可录入事件';
  }
  if (!playerWindowOpen.value) {
    return '已超过赛后 24 小时可编辑窗口';
  }
  return '';
});
const internalMatch = computed(() => isInternalMatchType(match.value?.type));
const teamLocked = computed(() => !isEventAdmin.value && !internalMatch.value);
const canViewGrouping = computed(
  () =>
    internalMatch.value &&
    session.isAuthenticated &&
    hasPermission(session.permissions, GROUPING_VIEW_ANY),
);
const canManageGrouping = computed(() =>
  hasPermission(session.permissions, GROUPING_GENERATE_ANY),
);
const hasGrouping = computed(
  () => Boolean(grouping.value && (grouping.value.teams.A.length || grouping.value.teams.B.length)),
);
const groupingPositionBuckets = [
  { key: 0, label: positionText(0) },
  { key: 1, label: positionText(1) },
  { key: 2, label: positionText(2) },
  { key: 3, label: positionText(3) },
  { key: -1, label: '位置未知' },
];

function groupingPositionInBucket(position: number, bucket: number): boolean {
  return bucket === -1 ? position < 0 || position > 3 : position === bucket;
}

const groupingPositionRows = computed(() =>
  groupingPositionBuckets.map((bucket) => ({
    position: bucket.key,
    label: bucket.label,
    a:
      grouping.value?.teams.A.filter((player) =>
        groupingPositionInBucket(player.position, bucket.key),
      ).length ?? 0,
    b:
      grouping.value?.teams.B.filter((player) =>
        groupingPositionInBucket(player.position, bucket.key),
      ).length ?? 0,
  })),
);
const displayedScore = computed(() => eventDerived.value ?? match.value?.derivedScore ?? null);
const showAdminLink = computed(() => session.isAuthenticated && isEventAdmin.value);
const adminBase =
  import.meta.env.VITE_ADMIN_URL ??
  (import.meta.env.DEV ? 'http://localhost:5274/admin/' : '/admin/');
const adminEventsUrl = computed(
  () => `${adminBase.endsWith('/') ? adminBase : `${adminBase}/`}events?match=${matchId}`,
);
const adminGroupingUrl = computed(
  () => `${adminBase.endsWith('/') ? adminBase : `${adminBase}/`}grouping?match=${matchId}`,
);
const goalCount = computed(() => events.value.filter((event) => event.type === 0).length);
const cardCount = computed(
  () => events.value.filter((event) => event.type === 3 || event.type === 4).length,
);
const substitutionCount = computed(() => events.value.filter((event) => event.type === 2).length);

const allowedTypes = computed<EventType[]>(() =>
  isEventAdmin.value ? [0, 1, 2, 3, 4] : [0, 1, 3, 4],
);
const rosterPlayers = computed(() =>
  roster.value
    .map((row) => row.player)
    .filter((player, index, all) => all.findIndex((item) => item.id === player.id) === index),
);
const ownPlayerId = computed(() => session.player?.id ?? null);
const playerOptions = computed(() => {
  if (teamLocked.value) {
    return rosterPlayers.value.filter((player) => player.id === ownPlayerId.value);
  }
  return rosterPlayers.value;
});

function statusTagType(): 'primary' | 'success' | 'warning' | 'danger' {
  const tone = matchStatusTone(match.value?.status);
  return tone === 'neutral' ? 'primary' : tone;
}

function scoreText(): string {
  const derived = displayedScore.value;
  if (derived) {
    return `${derived.scoreHome} : ${derived.scoreAway}`;
  }
  if (!match.value || match.value.scoreHome === null || match.value.scoreHome === undefined) {
    return 'VS';
  }
  return `${match.value.scoreHome} : ${match.value.scoreAway ?? 0}`;
}

function teamName(team: EventTeam): string {
  if (!match.value) {
    return team === 0 ? '主队' : '客队';
  }
  return team === 0 ? match.value.homeTeamName : match.value.awayTeamName;
}

function eventTime(event: MatchEvent): string {
  return `${eventHalfText(event.half)} ${event.minute}'`;
}

function eventDetail(event: MatchEvent): string {
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

function canEditEvent(event: MatchEvent): boolean {
  if (!match.value || checkInClosed.value) {
    return false;
  }
  return (
    hasPermission(session.permissions, EVENT_EDIT_OWN) &&
    event.type !== 2 &&
    event.player?.id === ownPlayerId.value &&
    playerWindowOpen.value
  );
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
    void loadEvents();
  }
}

async function loadEvents(): Promise<void> {
  eventsLoading.value = true;
  eventsError.value = '';
  try {
    const timeline = await api.get<EventTimeline>(`/matches/${matchId}/events`);
    events.value = timeline.items;
    eventDerived.value = timeline.derivedScore ?? null;
  } catch (caught) {
    eventsError.value = isApiError(caught) ? caught.message : '事件加载失败，请稍后重试';
  } finally {
    eventsLoading.value = false;
  }
}

async function loadRoster(): Promise<void> {
  if (!session.isAuthenticated) {
    roster.value = [];
    return;
  }
  try {
    const page = await api.get<Page<RegistrationRow>>(
      `/matches/${matchId}/registrations?status=0&pageSize=100`,
    );
    roster.value = page.items;
  } catch {
    // REG-05 需要 attendance:view:any；权限异常时至少保留本人，避免弹窗无球员可选。
    const player = session.player;
    roster.value =
      registration.value?.status === 0 && player
        ? [
            {
              id: registration.value.id,
              player: {
                id: player.id,
                name: player.name,
                jersey: player.jersey,
                position: player.position,
                positionLabel: player.positionLabel,
              },
              type: registration.value.type,
              status: registration.value.status,
              registeredAt: registration.value.registeredAt,
            },
          ]
        : [];
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

function resetForm(): void {
  form.type = 0;
  form.team = teamLocked.value ? ((match.value?.ourSide ?? 0) as EventTeam) : 0;
  form.playerId = teamLocked.value ? ownPlayerId.value : null;
  form.assistPlayerId = null;
  form.inPlayerId = null;
  form.outPlayerId = null;
  form.minute = null;
  form.half = 0;
  form.penalty = false;
  eventFormError.value = '';
}

async function openCreate(): Promise<void> {
  if (!canCreateEvent.value) {
    return;
  }
  editingEvent.value = null;
  resetForm();
  await loadRoster();
  const onlyPlayer = playerOptions.value.length === 1 ? playerOptions.value[0] : undefined;
  if (!teamLocked.value && form.playerId === null && onlyPlayer) {
    form.playerId = onlyPlayer.id;
  }
  eventModalOpen.value = true;
}

async function openEdit(event: MatchEvent): Promise<void> {
  if (!canEditEvent(event)) {
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
  await loadRoster();
  eventModalOpen.value = true;
}

function eventPayload(): EventCreate {
  const substitution = form.type === 2;
  const goal = form.type === 0;
  return {
    type: form.type,
    team: form.team,
    playerId: substitution ? null : form.playerId,
    assistPlayerId: goal ? form.assistPlayerId : null,
    inPlayerId: substitution ? form.inPlayerId : null,
    outPlayerId: substitution ? form.outPlayerId : null,
    minute: form.minute ?? 0,
    half: form.half,
    penalty: goal ? form.penalty : false,
  };
}

function validateEventForm(): string {
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
  if (eventSubmitting.value) {
    return;
  }
  const message = validateEventForm();
  if (message) {
    eventFormError.value = message;
    return;
  }
  eventSubmitting.value = true;
  eventFormError.value = '';
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
    eventModalOpen.value = false;
    activeTab.value = 'events';
    await loadEvents();
  } catch (caught) {
    eventFormError.value = isApiError(caught) ? caught.message : '事件保存失败，请稍后重试';
  } finally {
    eventSubmitting.value = false;
  }
}

function openEventsTab(): void {
  activeTab.value = 'events';
  if (!events.value.length && !eventsLoading.value) {
    void loadEvents();
  }
}

function groupingTeamLabel(team: GroupingTeam): string {
  return match.value ? groupingTeamName(match.value, team) : `${team} 队`;
}

function groupingTotal(team: GroupingTeam): string {
  const total = (grouping.value?.teams[team] ?? []).reduce(
    (sum, player) => sum + (player.finalScore ?? 0),
    0,
  );
  return total.toFixed(2);
}

function groupingPlayers(team: GroupingTeam, position: number): GroupedPlayer[] {
  return (grouping.value?.teams[team] ?? []).filter((player) =>
    groupingPositionInBucket(player.position, position),
  );
}

function groupingPlayerLabel(player: GroupedPlayer): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey}`;
}

async function loadGrouping(): Promise<void> {
  if (!canViewGrouping.value) {
    return;
  }
  groupingLoading.value = true;
  groupingError.value = '';
  try {
    grouping.value = await api.get<GroupingView>(`/matches/${matchId}/grouping`);
  } catch (caught) {
    grouping.value = null;
    groupingError.value = isApiError(caught) ? caught.message : '分组加载失败，请稍后重试';
  } finally {
    groupingLoading.value = false;
  }
}

function openGroupingTab(): void {
  activeTab.value = 'grouping';
  if (!grouping.value && !groupingLoading.value) {
    void loadGrouping();
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

      <nav class="detail-tabs" aria-label="比赛详情分区">
        <button
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'overview' }"
          @click="activeTab = 'overview'"
        >
          概览
        </button>
        <button
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'events' }"
          @click="openEventsTab"
        >
          事件
        </button>
        <button
          v-if="canViewGrouping"
          type="button"
          class="detail-tab"
          :class="{ 'detail-tab--active': activeTab === 'grouping' }"
          @click="openGroupingTab"
        >
          分组
        </button>
      </nav>

      <section v-if="activeTab === 'overview'" class="card registration-card">
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

      <template v-else-if="activeTab === 'events'">
        <section class="card events-summary">
          <div class="card-head">
            <h2>比赛事件</h2>
            <span class="muted small">{{ events.length }} 条</span>
          </div>
          <div class="stat-grid">
            <div>
              <b>{{ goalCount }}</b
              ><span>进球</span>
            </div>
            <div>
              <b>{{ cardCount }}</b
              ><span>牌</span>
            </div>
            <div>
              <b>{{ substitutionCount }}</b
              ><span>换人</span>
            </div>
          </div>
          <p v-if="match.status === 4" class="muted small">
            存储比分 {{ match.scoreHome ?? 0 }} : {{ match.scoreAway ?? 0 }}
          </p>
        </section>

        <section class="card events-panel">
          <div v-if="!checkInClosed && hasEventCreate" class="event-actions">
            <Button
              size="small"
              type="primary"
              :disabled="!canCreateEvent"
              :title="eventDisabledReason"
              @click="openCreate"
            >
              录入事件
            </Button>
            <a v-if="showAdminLink" class="ghost-link" :href="adminEventsUrl">去管理后台</a>
          </div>

          <div v-if="eventsLoading" class="state state--inline">
            <Loading size="20">事件加载中…</Loading>
          </div>

          <div v-else-if="eventsError" class="state state--inline">
            <p class="muted">{{ eventsError }}</p>
            <Button size="small" @click="loadEvents">重试</Button>
          </div>

          <Empty v-else-if="!events.length" description="还没有事件记录" />

          <div v-else class="team-timeline">
            <div class="timeline-head">
              <span>{{ match.homeTeamName }}</span>
              <span class="muted small">时间</span>
              <span>{{ match.awayTeamName }}</span>
            </div>
            <div
              v-for="event in events"
              :key="event.id"
              class="timeline-row"
              :class="`timeline-row--${event.team === 0 ? 'home' : 'away'}`"
            >
              <div class="timeline-side timeline-side--home">
                <div v-if="event.team === 0" class="event-card">
                  <div class="event-card__head">
                    <strong>{{ eventTypeText(event.type) }}</strong>
                    <button
                      v-if="canEditEvent(event)"
                      type="button"
                      class="event-edit"
                      @click="openEdit(event)"
                    >
                      编辑
                    </button>
                  </div>
                  <p>{{ eventDetail(event) }}</p>
                  <small class="muted">{{ teamName(event.team) }}</small>
                </div>
              </div>
              <div class="timeline-axis">
                <span class="timeline-time">{{ eventTime(event) }}</span>
                <span class="timeline-dot" />
              </div>
              <div class="timeline-side timeline-side--away">
                <div v-if="event.team === 1" class="event-card">
                  <div class="event-card__head">
                    <strong>{{ eventTypeText(event.type) }}</strong>
                    <button
                      v-if="canEditEvent(event)"
                      type="button"
                      class="event-edit"
                      @click="openEdit(event)"
                    >
                      编辑
                    </button>
                  </div>
                  <p>{{ eventDetail(event) }}</p>
                  <small class="muted">{{ teamName(event.team) }}</small>
                </div>
              </div>
            </div>
          </div>
        </section>
      </template>

      <template v-else-if="activeTab === 'grouping'">
        <section class="card grouping-summary">
          <div class="card-head">
            <h2>队内分组</h2>
            <span class="muted small">
              {{ grouping?.savedAt ? '已保存' : hasGrouping ? '草稿' : '尚未生成' }}
            </span>
          </div>
          <div class="stat-grid">
            <div>
              <b>{{ groupingTotal('A') }}</b
              ><span>A 队总分</span>
            </div>
            <div>
              <b>{{ groupingTotal('B') }}</b
              ><span>B 队总分</span>
            </div>
            <div>
              <b>{{ (grouping?.diff ?? 0).toFixed(2) }}</b
              ><span>总分差</span>
            </div>
          </div>
          <a v-if="canManageGrouping" class="ghost-link" :href="adminGroupingUrl">去管理后台</a>
        </section>

        <section class="card grouping-panel">
          <div v-if="groupingLoading" class="state state--inline">
            <Loading size="20">分组加载中…</Loading>
          </div>

          <div v-else-if="groupingError" class="state state--inline">
            <p class="muted">{{ groupingError }}</p>
            <Button size="small" @click="loadGrouping">重试</Button>
          </div>

          <Empty v-else-if="!hasGrouping" description="尚未生成分组" />

          <template v-else>
            <div class="grouping-warnings">
              <p v-for="warning in grouping?.warnings ?? []" :key="warning" class="grouping-warning">
                {{ warning }}
              </p>
            </div>

            <div class="grouping-board">
              <section v-for="team in groupingTeams" :key="team" class="grouping-column">
                <header class="grouping-head">
                  <h3>{{ team }} 队 · {{ groupingTeamLabel(team) }}</h3>
                  <span class="muted small">{{ groupingTotal(team) }} 分</span>
                </header>
                <div
                  v-for="bucket in groupingPositionBuckets"
                  :key="bucket.key"
                  class="grouping-position"
                >
                  <p class="grouping-position__title">{{ bucket.label }}</p>
                  <div
                    v-for="player in groupingPlayers(team, bucket.key)"
                    :key="player.playerId"
                    class="grouping-row"
                  >
                    <span>{{ groupingPlayerLabel(player) }}</span>
                    <small>{{ (player.finalScore ?? 0).toFixed(2) }}</small>
                  </div>
                  <p v-if="!groupingPlayers(team, bucket.key).length" class="muted small">—</p>
                </div>
              </section>
            </div>

            <div class="grouping-distribution">
              <div v-for="row in groupingPositionRows" :key="row.position">
                <span>{{ row.label }}</span>
                <b>{{ row.a }} : {{ row.b }}</b>
              </div>
            </div>
          </template>
        </section>
      </template>
    </template>

    <Popup v-model:show="eventModalOpen" position="bottom" round :style="{ maxHeight: '90%' }">
      <div class="event-modal">
        <div class="card-head">
          <h2>{{ editingEvent ? '编辑事件' : '录入事件' }}</h2>
        </div>

        <label class="event-field">
          <span>类型</span>
          <select v-model.number="form.type">
            <option v-for="type in allowedTypes" :key="type" :value="type">
              {{ EVENT_TYPE_TEXT[type] }}
            </option>
          </select>
        </label>

        <label class="event-field">
          <span>球队</span>
          <select v-model.number="form.team" :disabled="teamLocked">
            <option :value="0">{{ teamName(0) }}</option>
            <option :value="1">{{ teamName(1) }}</option>
          </select>
        </label>

        <template v-if="form.type === 2">
          <label class="event-field">
            <span>换上</span>
            <select v-model.number="form.inPlayerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label class="event-field">
            <span>换下</span>
            <select v-model.number="form.outPlayerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
        </template>

        <template v-else>
          <label class="event-field">
            <span>球员</span>
            <select v-model.number="form.playerId">
              <option :value="null">请选择</option>
              <option v-for="player in playerOptions" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label v-if="form.type === 0" class="event-field">
            <span>助攻（可选）</span>
            <select v-model.number="form.assistPlayerId">
              <option :value="null">无</option>
              <option v-for="player in rosterPlayers" :key="player.id" :value="player.id">
                {{ player.name }} #{{ player.jersey }}
              </option>
            </select>
          </label>
          <label v-if="form.type === 0" class="event-field event-field--inline">
            <span>点球</span>
            <input v-model="form.penalty" type="checkbox" />
          </label>
        </template>

        <label class="event-field">
          <span>半场</span>
          <select v-model.number="form.half">
            <option :value="0">上半场</option>
            <option :value="1">下半场</option>
          </select>
        </label>

        <label class="event-field">
          <span>时间（分钟）</span>
          <input v-model.number="form.minute" type="number" min="1" max="120" placeholder="1–120" />
        </label>

        <p v-if="eventFormError" class="action-error">{{ eventFormError }}</p>

        <Button
          type="primary"
          block
          :loading="eventSubmitting"
          :disabled="eventSubmitting"
          @click="submitEvent"
        >
          {{ editingEvent ? '保存修改' : '保存事件' }}
        </Button>
      </div>
    </Popup>
  </section>
</template>
