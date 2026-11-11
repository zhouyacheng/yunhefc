<script setup lang="ts">
import { ERROR_CODES, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { GROUPING_GENERATE_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  GroupedPlayer,
  GroupingAdjustRequest,
  GroupingAdmin,
  GroupingClearResult,
  GroupingGenerateRequest,
  GroupingResponse,
  GroupingSaveRequest,
  GroupingSaveResponse,
  GroupingTeam,
  GroupingTeams,
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
import { computed, h, onMounted, ref, watch } from 'vue';
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
const saving = ref(false);
const clearing = ref(false);
const draftTeams = ref<GroupingTeams>({ A: [], B: [] });
const selectedPlayer = ref<{ playerId: number; team: GroupingTeam } | null>(null);
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

function teamAssignments(teams: GroupingTeams): Map<number, GroupingTeam> {
  const map = new Map<number, GroupingTeam>();
  for (const team of groupingTeams) {
    for (const player of teams[team]) {
      map.set(player.playerId, team);
    }
  }
  return map;
}

const groupedTeamByPlayer = computed(() => teamAssignments(draftTeams.value));

const dirty = computed(() => {
  const saved = grouping.value;
  if (!saved) {
    return false;
  }
  const assignments = teamAssignments(saved.teams);
  const draftSize = draftTeams.value.A.length + draftTeams.value.B.length;
  if (draftSize !== assignments.size) {
    return true;
  }
  return groupingTeams.some((team) =>
    draftTeams.value[team].some((player) => assignments.get(player.playerId) !== team),
  );
});

watch(
  () => grouping.value,
  (value) => {
    draftTeams.value = value
      ? { A: [...value.teams.A], B: [...value.teams.B] }
      : { A: [], B: [] };
    selectedPlayer.value = null;
  },
  { immediate: true },
);
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
    a: draftTeams.value.A.filter((player) => inBucket(player.position, bucket.key)).length,
    b: draftTeams.value.B.filter((player) => inBucket(player.position, bucket.key)).length,
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

function teamScore(team: GroupingTeam): number {
  return draftTeams.value[team].reduce(
    (total, player) => total + (player.finalScore ?? 0),
    0,
  );
}

function teamTotal(team: GroupingTeam): string {
  return teamScore(team).toFixed(2);
}

const teamDiff = computed(() => Math.abs(teamScore('A') - teamScore('B')).toFixed(2));

function playersByPosition(team: GroupingTeam, position: number): GroupedPlayer[] {
  return draftTeams.value[team].filter((player) => inBucket(player.position, position));
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

function toggleSelect(playerId: number, team: GroupingTeam): void {
  if (!canGenerate.value) {
    return;
  }
  selectedPlayer.value =
    selectedPlayer.value?.playerId === playerId ? null : { playerId, team };
}

function movePlayer(playerId: number, target: GroupingTeam): void {
  if (!canGenerate.value) {
    return;
  }
  const inA = draftTeams.value.A.some((player) => player.playerId === playerId);
  const inB = draftTeams.value.B.some((player) => player.playerId === playerId);
  if ((!inA && !inB) || (inA && target === 'A') || (inB && target === 'B')) {
    selectedPlayer.value = null;
    return;
  }
  const player = inA
    ? draftTeams.value.A.find((item) => item.playerId === playerId)
    : draftTeams.value.B.find((item) => item.playerId === playerId);
  if (!player) {
    return;
  }
  draftTeams.value = {
    A:
      target === 'A'
        ? [...draftTeams.value.A, player]
        : draftTeams.value.A.filter((item) => item.playerId !== playerId),
    B:
      target === 'B'
        ? [...draftTeams.value.B, player]
        : draftTeams.value.B.filter((item) => item.playerId !== playerId),
  };
  selectedPlayer.value = null;
}

function onDragStart(event: DragEvent, player: GroupedPlayer, team: GroupingTeam): void {
  if (!canGenerate.value) {
    event.preventDefault();
    return;
  }
  selectedPlayer.value = { playerId: player.playerId, team };
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', String(player.playerId));
  }
}

function onDragOver(event: DragEvent): void {
  if (canGenerate.value) {
    event.preventDefault();
  }
}

function onColumnClick(event: MouseEvent, team: GroupingTeam): void {
  const selected = selectedPlayer.value;
  if (!canGenerate.value || !selected || selected.team === team) {
    return;
  }
  const target = event.target as HTMLElement | null;
  if (target?.closest('.group-player')) {
    return;
  }
  movePlayer(selected.playerId, team);
}

function onDrop(event: DragEvent, team: GroupingTeam): void {
  event.preventDefault();
  if (!canGenerate.value) {
    return;
  }
  const raw = event.dataTransfer?.getData('text/plain');
  const playerId = raw ? Number(raw) : selectedPlayer.value?.playerId;
  if (playerId !== undefined && Number.isInteger(playerId) && playerId > 0) {
    movePlayer(playerId, team);
  }
}

function playerNames(ids: number[]): string {
  const byId = new Map(confirmedRoster.value.map((row) => [row.player.id, row.player.name]));
  return ids.map((id) => byId.get(id) ?? `球员#${id}`).join('、');
}

async function confirmSaveWarnings(view: GroupingAdmin): Promise<boolean> {
  const lines: string[] = [];
  const diff = view.rosterDiff;
  if (diff.changed) {
    const parts: string[] = [];
    if (diff.addedPlayerIds.length) {
      parts.push(`新增：${playerNames(diff.addedPlayerIds)}`);
    }
    if (diff.removedPlayerIds.length) {
      parts.push(`移除：${playerNames(diff.removedPlayerIds)}`);
    }
    lines.push(`本场确认名单已变化${parts.length ? `（${parts.join('；')}）` : ''}。`);
  }
  lines.push(...view.warnings);
  lines.push('保存后事件与阵容将按此分组约束，名单变化不会自动重算。');
  try {
    await ElMessageBox.confirm(
      h('div', lines.map((line) => h('p', line))),
      '保存分组',
      { type: 'warning', confirmButtonText: '仍然保存', cancelButtonText: '取消' },
    );
    return true;
  } catch {
    return false;
  }
}

async function postSave(
  matchId: number,
  view: GroupingAdmin,
  confirm: boolean,
): Promise<void> {
  if (!view.rosterFingerprint) {
    throw new Error('分组缺少名单指纹');
  }
  const request: GroupingSaveRequest = {
    expectedFingerprint: view.rosterFingerprint,
    confirm,
  };
  const response = await api.post<GroupingSaveResponse>(
    `/matches/${matchId}/grouping/save`,
    request,
    { idempotencyKey: newIdempotencyKey() },
  );
  if (matchId === selectedMatchId.value) {
    grouping.value = { ...view, savedAt: response.savedAt };
    ElMessage.success('分组已保存');
  }
}

async function handleGroupingError(caught: unknown): Promise<void> {
  if (isApiError(caught) && caught.code === ERROR_CODES.GROUPING_FINGERPRINT_CONFLICT) {
    actionError.value = '本场确认名单已变化，请重新生成分组后再调整或保存。';
    if (selectedMatchId.value !== null) {
      await loadGrouping(selectedMatchId.value);
    }
    return;
  }
  if (isApiError(caught) && caught.code === ERROR_CODES.MATCH_STATE_CONFLICT) {
    const message = caught.message;
    await loadMatches();
    // loadMatches 经 refreshSelected 清空提示，因此在刷新后回填。
    actionError.value = message;
    return;
  }
  actionError.value = isApiError(caught) ? caught.message : '操作失败，请稍后重试';
}

async function saveGrouping(): Promise<void> {
  const matchId = selectedMatchId.value;
  const current = grouping.value;
  if (matchId === null || !current || saving.value || !canGenerate.value) {
    return;
  }
  saving.value = true;
  actionError.value = '';
  try {
    let working = current;
    if (dirty.value) {
      if (current.savedAt) {
        try {
          await ElMessageBox.confirm(
            '当前分组已保存；调整后会回到草稿状态，需要再次保存。是否继续？',
            '调整已保存分组',
            { type: 'warning', confirmButtonText: '继续调整', cancelButtonText: '取消' },
          );
        } catch {
          return;
        }
      }
      if (!current.rosterFingerprint) {
        actionError.value = '分组缺少名单指纹，请重新生成分组';
        return;
      }
      const body: GroupingAdjustRequest = {
        teams: {
          A: draftTeams.value.A.map((player) => player.playerId),
          B: draftTeams.value.B.map((player) => player.playerId),
        },
        expectedFingerprint: current.rosterFingerprint,
        confirm: Boolean(current.savedAt),
      };
      const adjusted = await api.patch<GroupingResponse>(`/matches/${matchId}/grouping`, body, {
        idempotencyKey: newIdempotencyKey(),
      });
      if (matchId !== selectedMatchId.value) {
        return;
      }
      grouping.value = adjusted.grouping;
      working = adjusted.grouping;
    }

    const confirm = working.warnings.length > 0 || working.rosterDiff.changed;
    if (confirm && !(await confirmSaveWarnings(working))) {
      return;
    }
    try {
      await postSave(matchId, working, confirm);
    } catch (caught) {
      if (matchId !== selectedMatchId.value) {
        return;
      }
      if (isApiError(caught) && caught.code === ERROR_CODES.CONFIRM_REQUIRED) {
        // 重新读取，确保确认框列出的是服务端当前警告/名单差异。
        await loadGrouping(matchId);
        if (matchId !== selectedMatchId.value) {
          return;
        }
        const latest = grouping.value ?? working;
        if (!(await confirmSaveWarnings(latest))) {
          return;
        }
        await postSave(matchId, latest, true);
      } else {
        throw caught;
      }
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      await handleGroupingError(caught);
    }
  } finally {
    saving.value = false;
  }
}

async function clearGrouping(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || clearing.value || !canGenerate.value || !hasGrouping.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      '清除后将删除当前分组与保存快照，需要重新生成。是否继续？',
      '清除分组',
      { type: 'warning', confirmButtonText: '清除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  clearing.value = true;
  actionError.value = '';
  try {
    await api.delete<GroupingClearResult>(`/matches/${matchId}/grouping`, undefined, {
      idempotencyKey: newIdempotencyKey(),
    });
    if (matchId === selectedMatchId.value) {
      ElMessage.success('分组已清除');
      await loadGrouping(matchId);
    }
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      await handleGroupingError(caught);
    }
  } finally {
    clearing.value = false;
  }
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
            <el-tag v-if="dirty" size="small" type="warning" effect="plain" class="status-tag">
              有未保存调整
            </el-tag>
            <p v-if="grouping?.generatedAt" class="muted small">
              生成于 {{ formatDateTime(grouping.generatedAt) }}
              <template v-if="grouping.savedAt">
                · 保存于 {{ formatDateTime(grouping.savedAt) }}
              </template>
            </p>
          </div>
          <div class="toolbar-actions">
            <el-button
              v-if="hasGrouping"
              :disabled="!canGenerate || saving || clearing"
              :loading="clearing"
              @click="clearGrouping"
            >
              清除分组
            </el-button>
            <el-button
              v-if="hasGrouping"
              type="primary"
              plain
              :disabled="!canGenerate || saving || clearing"
              :loading="saving"
              @click="saveGrouping"
            >
              保存分组
            </el-button>
            <el-button
              type="primary"
              :disabled="!canGenerate || saving"
              :loading="generating"
              @click="startGenerate"
            >
              {{ hasGrouping ? '重新生成' : '生成分组' }}
            </el-button>
          </div>
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
            <small>{{ draftTeams.A.length }} 人</small>
          </div>
          <div class="summary-card">
            <span>B 队总分</span>
            <b>{{ teamTotal('B') }}</b>
            <small>{{ draftTeams.B.length }} 人</small>
          </div>
          <div class="summary-card">
            <span>总分差</span>
            <b>{{ teamDiff }}</b>
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
            <p v-if="selectedPlayer && canGenerate" class="hint">
              已选择球员；点击另一队的“移到 X 队”完成调整，桌面端也可直接拖拽。
            </p>
            <div class="group-admin-grid">
              <section
                v-for="team in groupingTeams"
                :key="team"
                class="group-admin-column"
                :class="{
                  'group-admin-column--drop':
                    canGenerate && selectedPlayer && selectedPlayer.team !== team,
                }"
                @dragover="onDragOver"
                @drop="onDrop($event, team)"
                @click="onColumnClick($event, team)"
              >
                <header class="group-admin-head">
                  <h3>{{ team }} 队 · {{ groupingTeamName(selectedMatch, team) }}</h3>
                  <div class="head-actions">
                    <span class="muted small">{{ teamTotal(team) }} 分</span>
                    <el-button
                      v-if="canGenerate && selectedPlayer && selectedPlayer.team !== team"
                      size="small"
                      type="primary"
                      plain
                      @click="movePlayer(selectedPlayer.playerId, team)"
                    >
                      移到 {{ team }} 队
                    </el-button>
                  </div>
                </header>
                <div v-for="bucket in positionBuckets" :key="bucket.key" class="position-block">
                  <p class="position-title">{{ bucket.label }}</p>
                  <div
                    v-for="player in playersByPosition(team, bucket.key)"
                    :key="player.playerId"
                    class="group-player"
                    :class="{
                      'group-player--draggable': canGenerate,
                      'group-player--selected':
                        selectedPlayer?.playerId === player.playerId,
                    }"
                    :draggable="canGenerate"
                    @dragstart="onDragStart($event, player, team)"
                    @click="toggleSelect(player.playerId, team)"
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

.toolbar-actions,
.head-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
}

.toolbar-actions {
  justify-content: flex-end;
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

.group-admin-column--drop {
  cursor: pointer;
  border-color: var(--el-color-primary);
  box-shadow: inset 0 0 0 1px var(--el-color-primary);
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

.group-player--draggable {
  cursor: grab;
}

.group-player--selected {
  background: #eff6ff;
  border-color: var(--el-color-primary);
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
