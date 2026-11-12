<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { ATTENDANCE_MARK_ANY, REGISTRATION_REMOVE_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  Attendance,
  AttendanceRow,
  MatchDetail,
  MatchSummary,
  Page,
  PlayerAdmin,
  RegistrationRow,
  RemovalResult,
} from '@yunhefc/shared';
import {
  ATTENDANCE_SOURCE_TEXT,
  ATTENDANCE_STATUS_TEXT,
  MATCH_STATUS_CANCELLED,
  MATCH_STATUS_FINISHED,
  MATCH_STATUS_POSTPONED,
  MATCH_STATUS_TEXT,
  REGISTRATION_STATUS_TEXT,
  formatDateTime,
  positionText,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, ref } from 'vue';

import { api, publicApi } from '../api/client';
import { useSessionStore } from '../stores/session';

interface RosterGroup {
  key: string;
  title: string;
  rows: RegistrationRow[];
  allowMark: boolean;
  empty: string;
}

/** 公开赛程列表不含容量，选中后从公开详情补齐。 */
interface RegistrationsMatch extends MatchSummary {
  capacity?: number;
  guestCapacity?: number;
  waitlistCapacity?: number;
}

const session = useSessionStore();

const matches = ref<RegistrationsMatch[]>([]);
const selectedMatchId = ref<number | null>(null);
const registrations = ref<RegistrationRow[]>([]);
const attendance = ref<AttendanceRow[]>([]);
const loadingMatches = ref(false);
const loadingRoster = ref(false);
const error = ref('');
const actionBusy = ref<string | null>(null);

const tempDialogVisible = ref(false);
const tempPlayers = ref<PlayerAdmin[]>([]);
const tempPlayerId = ref<number | null>(null);
const tempLoading = ref(false);
const tempSubmitting = ref(false);

const selectedMatch = computed(
  () => matches.value.find((match) => match.id === selectedMatchId.value) ?? null,
);
const matchStatus = computed(() => selectedMatch.value?.status ?? null);
const postponedOrCancelled = computed(
  () =>
    matchStatus.value === MATCH_STATUS_POSTPONED || matchStatus.value === MATCH_STATUS_CANCELLED,
);
const cancelled = computed(() => matchStatus.value === MATCH_STATUS_CANCELLED);
const canMark = computed(
  () => !postponedOrCancelled.value && hasPermission(session.permissions, ATTENDANCE_MARK_ANY),
);
const canRemove = computed(
  () =>
    hasPermission(session.permissions, REGISTRATION_REMOVE_ANY) &&
    matchStatus.value !== null &&
    matchStatus.value !== MATCH_STATUS_FINISHED &&
    matchStatus.value !== MATCH_STATUS_CANCELLED,
);

const attendanceByPlayer = computed(
  () => new Map(attendance.value.map((item) => [item.player.id, item])),
);
const formalRows = computed(() =>
  registrations.value.filter((row) => row.type === 1 && row.status === 0),
);
const waitlistRows = computed(() => registrations.value.filter((row) => row.status === 1));
const guestRows = computed(() =>
  registrations.value.filter((row) => row.type === 0 && row.status === 0),
);
const tempRows = computed(() => attendance.value.filter((row) => row.status === 2));

const groups = computed<RosterGroup[]>(() => [
  {
    key: 'formal',
    title: '正式球员',
    rows: formalRows.value,
    allowMark: true,
    empty: '暂无正式球员报名',
  },
  {
    key: 'waitlist',
    title: '候补（正式）',
    rows: waitlistRows.value,
    allowMark: false,
    empty: '暂无候补',
  },
  {
    key: 'guest',
    title: '外挂球员',
    rows: guestRows.value,
    allowMark: true,
    empty: '暂无外挂报名',
  },
]);

function attendanceOf(row: RegistrationRow): AttendanceRow | undefined {
  return attendanceByPlayer.value.get(row.player.id);
}

function attendanceTagType(status: number): 'success' | 'danger' | 'warning' | 'info' {
  if (status === 0) {
    return 'success';
  }
  if (status === 1) {
    return 'danger';
  }
  return status === 2 ? 'warning' : 'info';
}

function registrationTagType(status: number): 'success' | 'warning' | 'info' {
  if (status === 0) {
    return 'success';
  }
  return status === 1 ? 'warning' : 'info';
}

async function loadMatchCapacity(matchId: number | null): Promise<void> {
  if (matchId === null) {
    return;
  }
  try {
    const detail = await publicApi.get<MatchDetail>(`/matches/${matchId}`);
    const target = matches.value.find((match) => match.id === matchId);
    if (target) {
      target.capacity = detail.capacity;
      target.guestCapacity = detail.guestCapacity;
      target.waitlistCapacity = detail.waitlistCapacity;
    }
  } catch {
    // 容量只是统计辅助信息；失败时按未知展示。
  }
}

async function loadMatches(): Promise<void> {
  loadingMatches.value = true;
  error.value = '';
  try {
    // 比赛选择器读公开赛程：attendance:view:any 的只读用户不一定有 match:view:any。
    const result = await publicApi.get<Page<MatchSummary>>('/matches', {
      query: { scope: 'all', sort: 'startAt:desc', page: 1, pageSize: 100 },
    });
    matches.value = result.items;
    if (!matches.value.some((match) => match.id === selectedMatchId.value)) {
      selectedMatchId.value = matches.value[0]?.id ?? null;
    }
    await Promise.all([loadMatchCapacity(selectedMatchId.value), loadRoster()]);
  } catch (caught) {
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    loadingMatches.value = false;
  }
}

async function loadRoster(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null) {
    registrations.value = [];
    attendance.value = [];
    return;
  }
  loadingRoster.value = true;
  error.value = '';
  try {
    const [roster, attendancePage] = await Promise.all([
      api.get<Page<RegistrationRow>>(`/matches/${matchId}/registrations`, {
        query: { page: 1, pageSize: 100 },
      }),
      api.get<Page<AttendanceRow>>(`/matches/${matchId}/attendance`, {
        query: { page: 1, pageSize: 100 },
      }),
    ]);
    if (matchId !== selectedMatchId.value) {
      return;
    }
    registrations.value = roster.items;
    attendance.value = attendancePage.items;
  } catch (caught) {
    if (matchId === selectedMatchId.value) {
      registrations.value = [];
      attendance.value = [];
      error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    }
  } finally {
    if (matchId === selectedMatchId.value) {
      loadingRoster.value = false;
    }
  }
}

function onMatchChange(): void {
  void Promise.all([loadMatchCapacity(selectedMatchId.value), loadRoster()]);
}

async function mark(row: RegistrationRow, status: number): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || !canMark.value || actionBusy.value) {
    return;
  }
  const key = `mark:${row.player.id}`;
  actionBusy.value = key;
  try {
    await api.put<{ attendance: Attendance }>(
      `/matches/${matchId}/attendance/${row.player.id}`,
      { status },
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success('考勤已更新');
    await loadRoster();
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '考勤更新失败');
  } finally {
    if (actionBusy.value === key) {
      actionBusy.value = null;
    }
  }
}

async function removeRegistration(row: RegistrationRow): Promise<void> {
  if (!canRemove.value || actionBusy.value) {
    return;
  }
  try {
    await ElMessageBox.confirm(
      `确认移除 ${row.player.name} 的报名？若为已确认报名，候补会自动递补。`,
      '移除报名',
      { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  const key = `remove:${row.id}`;
  actionBusy.value = key;
  try {
    const result = await api.delete<RemovalResult>(`/registrations/${row.id}`, undefined, {
      idempotencyKey: newIdempotencyKey(),
    });
    const promotedName = result.promotedPlayerId
      ? registrations.value.find((item) => item.player.id === result.promotedPlayerId)?.player.name
      : undefined;
    ElMessage.success(
      result.promoted ? `已移除，${promotedName ?? '候补球员'} 已递补` : '已移除，未触发递补',
    );
    await loadRoster();
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '移除失败');
  } finally {
    if (actionBusy.value === key) {
      actionBusy.value = null;
    }
  }
}

async function openTempDialog(): Promise<void> {
  tempDialogVisible.value = true;
  tempPlayerId.value = null;
  tempLoading.value = true;
  try {
    const players = await api.get<Page<PlayerAdmin>>('/players', {
      query: { status: 1, page: 1, pageSize: 100 },
    });
    const confirmedIds = new Set(
      registrations.value.filter((row) => row.status === 0).map((row) => row.player.id),
    );
    const tempIds = new Set(tempRows.value.map((row) => row.player.id));
    tempPlayers.value = players.items.filter(
      (player) => !confirmedIds.has(player.id) && !tempIds.has(player.id),
    );
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '球员列表加载失败');
  } finally {
    tempLoading.value = false;
  }
}

async function submitTemp(): Promise<void> {
  const matchId = selectedMatchId.value;
  if (matchId === null || tempPlayerId.value === null || tempSubmitting.value) {
    return;
  }
  tempSubmitting.value = true;
  try {
    await api.post<{ attendance: Attendance }>(
      `/matches/${matchId}/attendance/temp`,
      { playerId: tempPlayerId.value },
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success('已补录临时到场');
    tempDialogVisible.value = false;
    await loadRoster();
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '补录失败');
  } finally {
    tempSubmitting.value = false;
  }
}

onMounted(loadMatches);
</script>

<template>
  <section class="registrations-page">
    <div class="page-head">
      <div>
        <h2>报名 / 考勤</h2>
        <p class="muted">移除已确认报名会自动递补；临时到场不占名额。</p>
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
        <div class="stat-cards">
          <div class="stat-card">
            <span>正式报名</span>
            <b>{{ formalRows.length }}</b>
            <small>{{ cancelled ? '已作废' : `名额 ${selectedMatch.capacity ?? '—'}` }}</small>
          </div>
          <div class="stat-card">
            <span>外挂报名</span>
            <b>{{ guestRows.length }}</b>
            <small>{{ cancelled ? '已作废' : `名额 ${selectedMatch.guestCapacity ?? '—'}` }}</small>
          </div>
          <div class="stat-card">
            <span>候补（正式）</span>
            <b>{{ waitlistRows.length }}</b>
            <small>{{
              cancelled ? '已作废' : `上限 ${selectedMatch.waitlistCapacity ?? '—'}`
            }}</small>
          </div>
          <div v-if="!cancelled" class="stat-card">
            <span>临时到场</span>
            <b>{{ tempRows.length }}</b>
            <small>管理员补录</small>
          </div>
        </div>

        <el-alert
          v-if="cancelled"
          class="notice"
          type="warning"
          :closable="false"
          show-icon
          title="比赛已取消，报名记录保留但已作废，不计考勤。"
        />
        <el-alert
          v-else-if="matchStatus === MATCH_STATUS_POSTPONED"
          class="notice"
          type="info"
          :closable="false"
          show-icon
          title="比赛已延期，暂不可标记或补录考勤。"
        />

        <div v-loading="loadingRoster" class="roster-grid">
          <section v-for="group in groups" :key="group.key" class="roster-column">
            <header class="roster-head">
              <h3>{{ group.title }}</h3>
              <span class="muted">{{ group.rows.length }} 人</span>
            </header>

            <div v-if="!group.rows.length" class="empty-inline">{{ group.empty }}</div>

            <div v-for="row in group.rows" :key="row.id" class="roster-row">
              <div class="roster-player">
                <strong>{{ row.player.name }}</strong>
                <small>
                  #{{ row.player.jersey ?? '—' }} · {{ positionText(row.player.position) }}
                </small>
              </div>

              <div class="roster-badges">
                <el-tag size="small" :type="registrationTagType(row.status)" effect="light">
                  {{ REGISTRATION_STATUS_TEXT[row.status] }}
                </el-tag>
                <el-tag
                  v-if="attendanceOf(row)"
                  class="attendance-state"
                  size="small"
                  :type="attendanceTagType(attendanceOf(row)!.status)"
                  effect="light"
                >
                  {{ ATTENDANCE_STATUS_TEXT[attendanceOf(row)!.status] }} ·
                  {{ ATTENDANCE_SOURCE_TEXT[attendanceOf(row)!.source] }}
                </el-tag>
                <span v-else class="muted small">未记录</span>
              </div>

              <div class="roster-actions">
                <el-button-group v-if="group.allowMark">
                  <el-button
                    size="small"
                    :type="attendanceOf(row)?.status === 0 ? 'primary' : 'default'"
                    :disabled="!canMark"
                    :loading="actionBusy === `mark:${row.player.id}`"
                    @click="mark(row, 0)"
                  >
                    正常
                  </el-button>
                  <el-button
                    size="small"
                    :type="attendanceOf(row)?.status === 1 ? 'danger' : 'default'"
                    :disabled="!canMark"
                    :loading="actionBusy === `mark:${row.player.id}`"
                    @click="mark(row, 1)"
                  >
                    缺勤
                  </el-button>
                </el-button-group>
                <el-button
                  v-if="canRemove"
                  size="small"
                  type="danger"
                  plain
                  :loading="actionBusy === `remove:${row.id}`"
                  @click="removeRegistration(row)"
                >
                  移除
                </el-button>
              </div>
            </div>
          </section>
        </div>

        <section v-if="!cancelled" class="temp-card">
          <header class="roster-head">
            <h3>临时到场</h3>
            <el-button v-if="canMark" size="small" @click="openTempDialog">
              + 补录临时到场
            </el-button>
          </header>
          <div v-if="!tempRows.length" class="empty-inline">暂无临时到场</div>
          <div v-for="row in tempRows" :key="row.id" class="temp-row">
            <strong>{{ row.player.name }}</strong>
            <span class="muted small">
              #{{ row.player.jersey ?? '—' }} · {{ positionText(row.player.position) }}
            </span>
            <el-tag size="small" type="warning" effect="light">
              临时到场 · {{ ATTENDANCE_SOURCE_TEXT[row.source] }}
            </el-tag>
            <span class="muted small">{{ formatDateTime(row.recordedAt) }}</span>
          </div>
        </section>
      </template>
    </div>

    <el-dialog v-model="tempDialogVisible" title="补录临时到场" width="480px">
      <div v-loading="tempLoading">
        <el-select
          v-model="tempPlayerId"
          filterable
          placeholder="选择未确认报名的本队球员"
          style="width: 100%"
          :no-data-text="'没有可补录的球员'"
        >
          <el-option
            v-for="player in tempPlayers"
            :key="player.id"
            :value="player.id"
            :label="`${player.name}${player.jersey === null || player.jersey === undefined ? '' : ` #${player.jersey}`} · ${positionText(player.position)}`"
          />
        </el-select>
        <p class="hint">仅可补录本队未注销且本场无确认报名的球员；临时到场不占名额、不计缺勤。</p>
      </div>
      <template #footer>
        <el-button @click="tempDialogVisible = false">取消</el-button>
        <el-button
          type="primary"
          :disabled="tempPlayerId === null"
          :loading="tempSubmitting"
          @click="submitTemp"
        >
          补录
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.registrations-page {
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

.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(150px, 1fr));
  gap: 12px;
  margin-bottom: 16px;
}

.stat-card {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 14px 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.stat-card span {
  color: var(--yh-muted);
  font-size: 12px;
}

.stat-card b {
  font-size: 24px;
  line-height: 1.1;
}

.stat-card small {
  color: var(--yh-muted);
  font-size: 11px;
}

.notice {
  margin-bottom: 16px;
}

.roster-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.roster-column,
.temp-card {
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.roster-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.roster-head h3 {
  margin: 0;
  font-size: 15px;
}

.roster-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 10px 0;
  border-bottom: 1px solid var(--yh-line);
}

.roster-row:last-child {
  border-bottom: none;
}

.roster-player {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 110px;
}

.roster-player small {
  color: var(--yh-muted);
  font-size: 12px;
}

.roster-badges {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}

.roster-actions {
  display: flex;
  gap: 6px;
  margin-left: auto;
}

.temp-card {
  margin-top: 16px;
}

.temp-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 12px;
  padding: 8px 0;
  border-bottom: 1px solid var(--yh-line);
}

.temp-row:last-child {
  border-bottom: none;
}

.empty-inline {
  padding: 12px 0;
  color: var(--yh-muted);
  font-size: 13px;
}

.hint {
  margin: 10px 0 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.muted {
  color: var(--yh-muted);
}

.small {
  font-size: 12px;
}

@media (max-width: 1200px) {
  .roster-grid {
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
