<script setup lang="ts">
import { ERROR_CODES, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { GROUPING_GENERATE_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  GroupedPlayer,
  GroupingAdmin,
  GroupingGenerateRequest,
  GroupingResponse,
  GroupingTeam,
  MatchSummary,
  Page,
  RegistrationRow,
} from '@yunhefc/shared';
import {
  MATCH_STATUS_LOCKED,
  MATCH_STATUS_TEXT,
  formatDateTime,
  groupingTeamName,
  isInternalMatchType,
  positionText,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';

import { api, publicApi } from '../api/client';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const session = useSessionStore();

const matches = ref<MatchSummary[]>([]);
const selectedMatchId = ref<number | null>(null);
const grouping = ref<GroupingAdmin | null>(null);
const roster = ref<RegistrationRow[]>([]);
const loadingMatches = ref(false);
const loadingGrouping = ref(false);
const loadingRoster = ref(false);
const generating = ref(false);
const error = ref('');
const groupingError = ref('');
const rosterError = ref('');
const actionError = ref('');

const selectedMatch = computed(
  () => matches.value.find((match) => match.id === selectedMatchId.value) ?? null,
);
const hasGrouping = computed(
  () => Boolean(grouping.value && (grouping.value.teams.A.length || grouping.value.teams.B.length)),
);
const groupingReady = computed(() => grouping.value !== null);
const positionBuckets = [
  { key: 0, label: positionText(0) },
  { key: 1, label: positionText(1) },
  { key: 2, label: positionText(2) },
  { key: 3, label: positionText(3) },
  { key: -1, label: '位置未知' },
];
const canGenerate = computed(
  () =>
    hasPermission(session.permissions, GROUPING_GENERATE_ANY) &&
    selectedMatch.value?.status === MATCH_STATUS_LOCKED,
);
const generateDisabledReason = computed(() => {
  if (!selectedMatch.value) {
    return '';
  }
  if (!hasPermission(session.permissions, GROUPING_GENERATE_ANY)) {
    return '当前账号没有生成分组权限，只能只读查看。';
  }
  if (selectedMatch.value.status !== MATCH_STATUS_LOCKED) {
    return '比赛未锁定，无法生成分组。';
  }
  return '';
});
const statusLabel = computed(() =>
  grouping.value?.savedAt ? '已保存' : hasGrouping.value ? '草稿' : '未生成',
);
const groupingTeams: GroupingTeam[] = ['A', 'B'];

const confirmedRoster = computed(() => {
  const seen = new Set<number>();
  return roster.value
    .filter((row) => row.status === 0)
    .filter((row) => {
      if (seen.has(row.player.id)) {
        return false;
      }
      seen.add(row.player.id);
      return true;
    });
});
const formalRoster = computed(() => confirmedRoster.value.filter((row) => row.type === 1));
const guestRoster = computed(() => confirmedRoster.value.filter((row) => row.type === 0));

const groupedTeamByPlayer = computed(() => {
  const map = new Map<number, GroupingTeam>();
  for (const team of ['A', 'B'] as const) {
    for (const player of grouping.value?.teams[team] ?? []) {
      map.set(player.playerId, team);
    }
  }
  return map;
});
const ungroupedIds = computed(() => new Set(grouping.value?.ungroupedPlayerIds ?? []));
const candidateIds = computed(() => {
  const ids = new Set<number>(ungroupedIds.value);
  for (const id of groupedTeamByPlayer.value.keys()) {
    ids.add(id);
  }
  return ids;
});
const nonParticipating = computed(() =>
  groupingReady.value
    ? confirmedRoster.value.filter((row) => !candidateIds.value.has(row.player.id))
    : [],
);

const ungroupedPlayers = computed(() => {
  const byId = new Map(confirmedRoster.value.map((row) => [row.player.id, row.player]));
  return (grouping.value?.ungroupedPlayerIds ?? []).map((id) => ({
    id,
    name: byId.get(id)?.name ?? `球员#${id}`,
    jersey: byId.get(id)?.jersey ?? null,
    position: byId.get(id)?.position ?? -1,
  }));
});

function inBucket(position: number, bucket: number): boolean {
  return bucket === -1 ? position < 0 || position > 3 : position === bucket;
}

const positionRows = computed(() =>
  positionBuckets.map((bucket) => ({
    position: bucket.key,
    label: bucket.label,
    a: grouping.value?.teams.A.filter((player) => inBucket(player.position, bucket.key)).length ?? 0,
    b: grouping.value?.teams.B.filter((player) => inBucket(player.position, bucket.key)).length ?? 0,
  })),
);

const rosterDiffNames = computed(() => {
  const byId = new Map(confirmedRoster.value.map((row) => [row.player.id, row.player.name]));
  const diff = grouping.value?.rosterDiff;
  if (!diff) {
    return { added: [], removed: [] };
  }
  return {
    added: diff.addedPlayerIds.map((id) => byId.get(id) ?? `球员#${id}`),
    removed: diff.removedPlayerIds.map((id) => byId.get(id) ?? `球员#${id}`),
  };
});

function teamTotal(team: GroupingTeam): string {
  return (grouping.value?.scores[team] ?? 0).toFixed(2);
}

function playersByPosition(team: GroupingTeam, position: number): GroupedPlayer[] {
  return (grouping.value?.teams[team] ?? []).filter((player) =>
    inBucket(player.position, position),
  );
}

function playerLabel(player: { name: string; jersey?: number | null; position: number }): string {
  const jersey = player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`;
  return `${player.name}${jersey} · ${positionText(player.position)}`;
}

function rosterBadge(row: RegistrationRow): { label: string; type: 'success' | 'info' | 'warning' } {
  if (!groupingReady.value) {
    return { label: '—', type: 'info' };
  }
  const team = groupedTeamByPlayer.value.get(row.player.id);
  if (team) {
    return { label: `${team} 队`, type: 'success' };
  }
  if (ungroupedIds.value.has(row.player.id)) {
    return { label: '未分组', type: 'warning' };
  }
  return { label: '不参与', type: 'info' };
}

async function loadMatches(): Promise<void> {
  loadingMatches.value = true;
  error.value = '';
  try {
    // 比赛选择器读公开赛程：grouping:view:any 的只读用户不一定有 match:view:any。
    const result = await publicApi.get<Page<MatchSummary>>('/matches', {
      query: { scope: 'all', sort: 'startAt:desc', page: 1, pageSize: 100 },
    });
    matches.value = result.items.filter((match) => isInternalMatchType(match.type));
    const requested = Number(route.query.match);
    if (Number.isInteger(requested) && !matches.value.some((match) => match.id === requested)) {
      try {
        const detail = await publicApi.get<MatchSummary>(`/matches/${requested}`);
        if (isInternalMatchType(detail.type)) {
          matches.value = [detail, ...matches.value];
        }
      } catch {
        // 深链目标不存在或无权查看时回退到列表首场。
      }
    }
    if (!matches.value.some((match) => match.id === selectedMatchId.value)) {
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
  const matchId = selectedMatchId.value;
  grouping.value = null;
  roster.value = [];
  groupingError.value = '';
  rosterError.value = '';
  actionError.value = '';
  if (matchId === null) {
    return;
  }
  await Promise.all([loadGrouping(matchId), loadRoster(matchId)]);
}

function onMatchChange(): void {
  void refreshSelected();
}

async function loadGrouping(matchId: number): Promise<void> {
  loadingGrouping.value = true;
  groupingError.value = '';
  try {
    const result = await api.get<GroupingAdmin>(`/matches/${matchId}/grouping`);
    if (matchId === selectedMatchId.value) {
      grouping.value = result;
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      grouping.value = null;
      groupingError.value = isApiError(caught) ? caught.message : '分组加载失败';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingGrouping.value = false;
    }
  }
}

async function loadRoster(matchId: number): Promise<void> {
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

async function confirmOverwrite(): Promise<boolean> {
  try {
    await ElMessageBox.confirm(
      '已有分组，重新生成会覆盖当前结果并清空保存状态。',
      '重新生成分组',
      { type: 'warning', confirmButtonText: '重新生成', cancelButtonText: '取消' },
    );
    return true;
  } catch {
    return false;
  }
}

async function generate(confirm = false): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || generating.value || !canGenerate.value) {
    return;
  }
  generating.value = true;
  actionError.value = '';
  try {
    const body: GroupingGenerateRequest = confirm ? { confirm: true } : {};
    const response = await api.post<GroupingResponse>(
      `/matches/${matchId}/grouping/generate`,
      body,
      { idempotencyKey: newIdempotencyKey() },
    );
    if (matchId === selectedMatchId.value) {
      grouping.value = response.grouping;
      ElMessage.success(confirm ? '分组已重新生成' : '分组已生成');
    }
  } catch (caught) {
    if (matchId !== selectedMatchId.value) {
      return;
    }
    if (isApiError(caught) && caught.code === ERROR_CODES.CONFIRM_REQUIRED) {
      generating.value = false;
      if (await confirmOverwrite()) {
        if (matchId === selectedMatchId.value) {
          await generate(true);
        }
      }
      return;
    }
    const message = isApiError(caught) ? caught.message : '生成分组失败';
    if (isApiError(caught) && caught.code === ERROR_CODES.MATCH_STATE_CONFLICT) {
      await loadMatches();
    }
    // 放在刷新之后，避免 refreshSelected 清空错误提示。
    actionError.value = message;
  } finally {
    generating.value = false;
  }
}

async function startGenerate(): Promise<void> {
  if (hasGrouping.value && !(await confirmOverwrite())) {
    return;
  }
  await generate(hasGrouping.value);
}

function retryGrouping(): void {
  if (selectedMatchId.value !== null) {
    void loadGrouping(selectedMatchId.value);
  }
}

onMounted(loadMatches);
</script>

<template>
  <section class="grouping-page">
    <div class="page-head">
      <div>
        <h2>队内分组</h2>
        <p class="muted">仅队内赛/训练赛且已锁定；生成含随机因子，可多次重新生成。</p>
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

    <el-alert v-if="error" class="page-error" type="error" :title="error" :closable="false" show-icon>
      <el-button size="small" @click="loadMatches">重试</el-button>
    </el-alert>

    <div v-loading="loadingMatches" class="page-body">
      <el-empty
        v-if="!loadingMatches && !matches.length"
        description="暂无队内赛/训练赛，请先创建比赛"
      >
        <RouterLink to="/matches">
          <el-button type="primary">去比赛管理</el-button>
        </RouterLink>
      </el-empty>

      <template v-else-if="selectedMatch">
        <section class="toolbar card">
          <div>
            <strong>{{ selectedMatch.title }}</strong>
            <el-tag size="small" effect="light" class="status-tag">
              {{ MATCH_STATUS_TEXT[selectedMatch.status] }}
            </el-tag>
            <el-tag size="small" :type="hasGrouping ? 'success' : 'info'" effect="plain">
              {{ statusLabel }}
            </el-tag>
            <p v-if="grouping?.generatedAt" class="muted small">
              生成于 {{ formatDateTime(grouping.generatedAt) }}
              <template v-if="grouping.savedAt">
                · 保存于 {{ formatDateTime(grouping.savedAt) }}
              </template>
            </p>
          </div>
          <el-button
            type="primary"
            :disabled="!canGenerate"
            :loading="generating"
            @click="startGenerate"
          >
            {{ hasGrouping ? '重新生成' : '生成分组' }}
          </el-button>
        </section>

        <el-alert
          v-if="generateDisabledReason"
          class="notice"
          type="info"
          :closable="false"
          show-icon
          :title="generateDisabledReason"
        />
        <el-alert
          v-if="actionError"
          class="notice"
          type="error"
          :closable="false"
          show-icon
          :title="actionError"
        />

        <el-alert
          v-if="grouping?.rosterDiff.changed"
          class="notice"
          type="warning"
          :closable="false"
          show-icon
          title="本场确认名单已变化；当前分组不会自动重算，请按需重新生成。"
        >
          <p v-if="rosterDiffNames.added.length" class="small">
            新增：{{ rosterDiffNames.added.join('、') }}
          </p>
          <p v-if="rosterDiffNames.removed.length" class="small">
            移除：{{ rosterDiffNames.removed.join('、') }}
          </p>
        </el-alert>

        <div class="summary-grid">
          <div class="summary-card">
            <span>A 队总分</span>
            <b>{{ teamTotal('A') }}</b>
            <small>{{ grouping?.teams.A.length ?? 0 }} 人</small>
          </div>
          <div class="summary-card">
            <span>B 队总分</span>
            <b>{{ teamTotal('B') }}</b>
            <small>{{ grouping?.teams.B.length ?? 0 }} 人</small>
          </div>
          <div class="summary-card">
            <span>总分差</span>
            <b>{{ (grouping?.diff ?? 0).toFixed(2) }}</b>
            <small>目标：尽量接近 0</small>
          </div>
        </div>

        <el-alert
          v-for="warning in grouping?.warnings ?? []"
          :key="warning"
          class="warning-line"
          type="warning"
          :closable="false"
          show-icon
          :title="warning"
        />

        <div v-loading="loadingGrouping" class="page-body">
          <el-alert
            v-if="groupingError"
            type="error"
            :title="groupingError"
            :closable="false"
            show-icon
          >
            <el-button size="small" @click="retryGrouping">重试</el-button>
          </el-alert>

          <el-empty v-else-if="!hasGrouping" description="尚未生成分组">
            <el-button type="primary" :disabled="!canGenerate" @click="startGenerate">
              生成分组
            </el-button>
          </el-empty>

          <template v-else>
            <div class="group-admin-grid">
              <section v-for="team in groupingTeams" :key="team" class="group-admin-column">
                <header class="group-admin-head">
                  <h3>{{ team }} 队 · {{ groupingTeamName(selectedMatch, team) }}</h3>
                  <span class="muted small">{{ teamTotal(team) }} 分</span>
                </header>
                <div v-for="bucket in positionBuckets" :key="bucket.key" class="position-block">
                  <p class="position-title">{{ bucket.label }}</p>
                  <div
                    v-for="player in playersByPosition(team, bucket.key)"
                    :key="player.playerId"
                    class="group-player"
                  >
                    <strong>{{ player.name }}</strong>
                    <small>#{{ player.jersey ?? '—' }} · {{ positionText(player.position) }}</small>
                    <span class="score" :title="`手动 ${player.manualScore ?? '—'} / 自动 ${player.autoScore ?? '—'}`">
                      {{ (player.finalScore ?? 0).toFixed(2) }}
                    </span>
                  </div>
                  <p v-if="!playersByPosition(team, bucket.key).length" class="empty-inline">—</p>
                </div>
              </section>
            </div>

            <section class="card position-card">
              <h3>位置分布</h3>
              <div class="position-grid">
                <div v-for="row in positionRows" :key="row.position">
                  <span>{{ row.label }}</span>
                  <b>{{ row.a }} : {{ row.b }}</b>
                </div>
              </div>
            </section>
          </template>
        </div>

        <section v-loading="loadingRoster" class="roster-section card">
          <header class="roster-head">
            <h3>本场确认报名</h3>
            <span class="muted small">{{ confirmedRoster.length }} 人</span>
          </header>
          <p v-if="rosterError" class="hint hint--warning">{{ rosterError }}</p>
          <div class="roster-grid">
            <div>
              <p class="position-title">正式球员</p>
              <div v-for="row in formalRoster" :key="row.player.id" class="roster-row">
                <span>{{ playerLabel(row.player) }}</span>
                <el-tag size="small" :type="rosterBadge(row).type" effect="light">
                  {{ rosterBadge(row).label }}
                </el-tag>
              </div>
              <p v-if="!formalRoster.length" class="empty-inline">暂无正式球员</p>
            </div>
            <div>
              <p class="position-title">外挂球员</p>
              <div v-for="row in guestRoster" :key="row.player.id" class="roster-row">
                <span>{{ playerLabel(row.player) }}</span>
                <el-tag size="small" :type="rosterBadge(row).type" effect="light">
                  {{ rosterBadge(row).label }}
                </el-tag>
              </div>
              <p v-if="!guestRoster.length" class="empty-inline">暂无外挂球员</p>
            </div>
          </div>
        </section>

        <section v-if="ungroupedPlayers.length" class="card">
          <h3>未分组</h3>
          <div class="chip-list">
            <span v-for="player in ungroupedPlayers" :key="player.id" class="chip">
              {{ playerLabel(player) }}
            </span>
          </div>
        </section>

        <section v-if="nonParticipating.length" class="card">
          <h3>不参与分组</h3>
          <p class="muted small">含比赛不含外挂、账号不可用或不在本队的确认报名。</p>
          <div class="chip-list">
            <span v-for="row in nonParticipating" :key="row.player.id" class="chip">
              {{ playerLabel(row.player) }}
            </span>
          </div>
        </section>
      </template>
    </div>
  </section>
</template>

<style scoped>
.grouping-page {
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
  min-height: 120px;
}

.card {
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.toolbar h3,
.card h3 {
  margin: 0 0 8px;
  font-size: 15px;
}

.status-tag {
  margin: 0 6px;
}

.notice,
.warning-line {
  margin-bottom: 4px;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.summary-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.summary-card span,
.summary-card small {
  color: var(--yh-muted);
  font-size: 12px;
}

.summary-card b {
  font-size: 24px;
}

.group-admin-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.group-admin-column {
  min-height: 280px;
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.group-admin-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding-bottom: 10px;
  margin-bottom: 10px;
  border-bottom: 1px solid var(--yh-line);
}

.group-admin-head h3 {
  margin: 0;
  font-size: 15px;
}

.position-block {
  margin-bottom: 10px;
}

.position-title {
  margin: 8px 0 6px;
  color: var(--yh-muted);
  font-size: 12px;
  font-weight: 600;
}

.group-player {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.group-player small {
  margin-left: auto;
  color: var(--yh-muted);
}

.group-player .score {
  min-width: 44px;
  font-weight: 700;
  text-align: right;
}

.position-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(90px, 1fr));
  gap: 10px;
}

.position-grid div {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px;
  background: #f8fafc;
  border-radius: 9px;
}

.position-grid span {
  color: var(--yh-muted);
  font-size: 12px;
}

.roster-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 10px;
}

.roster-head h3 {
  margin: 0;
}

.roster-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.roster-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 6px 0;
  border-bottom: 1px solid var(--yh-line);
  font-size: 12px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip {
  padding: 6px 10px;
  font-size: 12px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 999px;
}

.empty-inline {
  margin: 8px 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.hint {
  margin: 8px 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.hint--warning {
  color: var(--el-color-warning);
}

.muted {
  color: var(--yh-muted);
}

.small {
  font-size: 12px;
}

@media (max-width: 960px) {
  .group-admin-grid,
  .roster-grid,
  .summary-grid {
    grid-template-columns: 1fr;
  }

  .position-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .page-head,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .match-picker {
    width: 100%;
  }
}
</style>
