<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import {
  ERROR_CODES,
  describeError,
  isApiError,
  isVersionConflict,
  newIdempotencyKey,
} from '@yunhefc/api-client';
import {
  MATCH_CREATE_ANY,
  MATCH_EDIT_ANY,
  MATCH_STATUS_ANY,
  SETTINGS_VIEW_ANY,
  hasPermission,
} from '@yunhefc/permissions';
import type {
  CapacityDefaults,
  MatchAdmin,
  MatchCreate,
  MatchSide,
  MatchType,
  MatchUpdate,
  Page,
  SystemSettings,
} from '@yunhefc/shared';
import {
  DEFAULT_CAPACITY_DEFAULTS,
  MATCH_STATUS_CANCELLED,
  MATCH_STATUS_POSTPONED,
  MATCH_STATUS_TEXT,
  MATCH_TYPE_TEXT,
  capacityDefaults,
  formatDateTime,
  matchCancellable,
  matchCapacityError,
  matchEditable,
  matchPostponable,
  matchReschedulable,
  matchScheduleError,
  matchScorePairError,
  matchSeasonError,
  matchStatusText,
  matchStatusTone,
  matchTypeText,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

type MatchDialogMode = 'create' | 'edit';
type ScheduleDialogMode = 'postpone' | 'reschedule';

interface MatchFormState {
  title: string;
  type: MatchType;
  startAt: Date | null;
  endAt: Date | null;
  venue: string;
  competition: string;
  season: string;
  round: number | null;
  homeTeamName: string;
  awayTeamName: string;
  ourSide: MatchSide;
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
  includeGuests: boolean;
  scoreHome: number | null;
  scoreAway: number | null;
}

interface ScheduleFormState {
  startAt: Date | null;
  endAt: Date | null;
}

const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const TWO_HOURS_MS = 2 * 60 * 60 * 1000;

const session = useSessionStore();
const canCreate = computed(() => hasPermission(session.permissions, MATCH_CREATE_ANY));
const canEdit = computed(() => hasPermission(session.permissions, MATCH_EDIT_ANY));
const canStatus = computed(() => hasPermission(session.permissions, MATCH_STATUS_ANY));
const canViewSettings = computed(() => hasPermission(session.permissions, SETTINGS_VIEW_ANY));

const statusOptions = Object.entries(MATCH_STATUS_TEXT).map(([value, text]) => ({
  value: String(value),
  text,
}));
const statusChoices = Object.entries(MATCH_STATUS_TEXT).map(([value, text]) => ({
  value: Number(value),
  text,
}));
const typeOptions = Object.entries(MATCH_TYPE_TEXT).map(([value, text]) => ({
  value: Number(value) as MatchType,
  text,
}));

const rows = ref<MatchAdmin[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const status = ref<string | undefined>(undefined);
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

const matchDialog = reactive({
  visible: false,
  mode: 'create' as MatchDialogMode,
  submitting: false,
  error: '',
  match: null as MatchAdmin | null,
});
const matchForm = reactive<MatchFormState>(defaultMatchForm());

const scheduleDialog = reactive({
  visible: false,
  mode: 'postpone' as ScheduleDialogMode,
  submitting: false,
  error: '',
  match: null as MatchAdmin | null,
});
const scheduleForm = reactive<ScheduleFormState>({ startAt: null, endAt: null });

const cancelDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  match: null as MatchAdmin | null,
});
const cancelForm = reactive({ reason: '' });

const actionBusy = ref<string | null>(null);
let matchSubmitKey: string | null = null;

const totalCapacity = computed(() => {
  const capacity = Number.isFinite(matchForm.capacity) ? matchForm.capacity : 0;
  const guestCapacity = Number.isFinite(matchForm.guestCapacity) ? matchForm.guestCapacity : 0;
  return capacity + guestCapacity;
});
const ourSideNeeded = computed(() => matchForm.type === 0 || matchForm.type === 3);
const matchDialogTitle = computed(() =>
  matchDialog.mode === 'create' ? '创建比赛' : '编辑比赛',
);

function statusTag(statusCode: number): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  const tone = matchStatusTone(statusCode);
  return tone === 'neutral' ? 'info' : tone;
}

function shanghaiDayAt(daysAhead: number, hour: number): Date {
  const shanghaiNow = new Date(Date.now() + SHANGHAI_OFFSET_MS);
  return new Date(
    Date.UTC(
      shanghaiNow.getUTCFullYear(),
      shanghaiNow.getUTCMonth(),
      shanghaiNow.getUTCDate() + daysAhead,
      hour - 8,
      0,
      0,
    ),
  );
}

function currentShanghaiSeason(): string {
  return String(new Date(Date.now() + SHANGHAI_OFFSET_MS).getUTCFullYear());
}

function defaultMatchForm(): MatchFormState {
  const startAt = toPickerDate(shanghaiDayAt(1, 20).toISOString())!;
  return {
    title: '橙队VS兰队',
    type: 1,
    startAt,
    endAt: new Date(startAt.getTime() + TWO_HOURS_MS),
    venue: '聚龙足球场',
    competition: '',
    season: currentShanghaiSeason(),
    round: null,
    homeTeamName: '橙队',
    awayTeamName: '兰队',
    ourSide: 0,
    capacity: DEFAULT_CAPACITY_DEFAULTS.capacity,
    guestCapacity: DEFAULT_CAPACITY_DEFAULTS.guestCapacity,
    waitlistCapacity: DEFAULT_CAPACITY_DEFAULTS.waitlistCapacity,
    includeGuests: true,
    scoreHome: 0,
    scoreAway: 0,
  };
}

function resetMatchForm(source?: MatchFormState): void {
  Object.assign(matchForm, source ?? defaultMatchForm());
}

function toDate(value: string | null | undefined): Date | null {
  if (!value) {
    return null;
  }
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * el-date-picker 按浏览器本地时区解释 Date，而页面统一按 Asia/Shanghai 展示。
 * 这两个函数把真实时刻与「上海墙钟」互相映射，保证非 UTC+8 浏览器也显示同一时间。
 */
function toPickerDate(value: string | null | undefined): Date | null {
  const instant = toDate(value);
  if (!instant) {
    return null;
  }
  return new Date(
    instant.getTime() + SHANGHAI_OFFSET_MS + instant.getTimezoneOffset() * 60_000,
  );
}

function fromPickerDate(value: Date | null): Date | null {
  if (!value) {
    return null;
  }
  return new Date(
    value.getTime() - SHANGHAI_OFFSET_MS - value.getTimezoneOffset() * 60_000,
  );
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const query: QueryParams = {
      scope: 'all',
      sort: 'startAt:desc',
      page: page.value,
      pageSize: pageSize.value,
    };
    if (status.value !== undefined && status.value !== '') {
      query.status = Number(status.value);
    }
    const result = await api.get<Page<MatchAdmin>>('/matches', { query });
    if (seq !== requestSeq) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    error.value = describeError(caught, '加载失败，请稍后重试');
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function fetchAuthoritative(id: number): Promise<MatchAdmin> {
  return api.get<MatchAdmin>(`/matches/${id}`);
}

function closeAllDialogs(): void {
  matchDialog.visible = false;
  scheduleDialog.visible = false;
  cancelDialog.visible = false;
}

async function reportWriteError(
  caught: unknown,
  inlineError?: (message: string) => void,
): Promise<void> {
  if (isVersionConflict(caught)) {
    closeAllDialogs();
    ElMessage.error('已被他人修改，请刷新后重试');
    await load();
    return;
  }
  if (isApiError(caught) && caught.code === ERROR_CODES.MATCH_STATE_CONFLICT) {
    closeAllDialogs();
    ElMessage.error(caught.message);
    await load();
    return;
  }
  const message = describeError(caught, '操作失败，请稍后重试');
  if (inlineError) {
    inlineError(message);
  } else {
    ElMessage.error(message);
  }
}

function onStatusChange(): void {
  page.value = 1;
  void load();
}

function onPageChange(): void {
  void load();
}

function onSizeChange(): void {
  page.value = 1;
  void load();
}

// ---------------------------------------------------------------- dialogs

async function loadCapacityDefaults(): Promise<CapacityDefaults> {
  if (!canViewSettings.value) {
    return capacityDefaults();
  }
  try {
    const result = await api.get<{ settings: SystemSettings }>('/settings');
    return capacityDefaults(result.settings);
  } catch {
    return capacityDefaults();
  }
}

async function openCreate(): Promise<void> {
  if (!canCreate.value) {
    return;
  }
  const defaults = await loadCapacityDefaults();
  resetMatchForm({ ...defaultMatchForm(), ...defaults });
  matchDialog.mode = 'create';
  matchDialog.match = null;
  matchDialog.error = '';
  matchSubmitKey = newIdempotencyKey();
  matchDialog.visible = true;
}

async function openEdit(row: MatchAdmin): Promise<void> {
  if (!canEdit.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `edit:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    if (!matchEditable(detail.status)) {
      ElMessage.warning('终态或已延期比赛不可编辑；已延期请使用重新排期。');
      await load();
      return;
    }
    resetMatchForm({
      title: detail.title,
      type: detail.type,
      startAt: toPickerDate(detail.startAt),
      endAt: toPickerDate(detail.endAt),
      venue: detail.venue,
      competition: detail.competition ?? '',
      season: detail.season,
      round: detail.round ?? null,
      homeTeamName: detail.homeTeamName,
      awayTeamName: detail.awayTeamName,
      ourSide: (detail.ourSide ?? 0) as MatchSide,
      capacity: detail.capacity,
      guestCapacity: detail.guestCapacity,
      waitlistCapacity: detail.waitlistCapacity,
      includeGuests: detail.includeGuests,
      scoreHome: detail.scoreHome ?? null,
      scoreAway: detail.scoreAway ?? null,
    });
    matchDialog.mode = 'edit';
    matchDialog.match = detail;
    matchDialog.error = '';
    matchDialog.visible = true;
  } catch (caught) {
    ElMessage.error(describeError(caught, '比赛详情加载失败'));
  } finally {
    actionBusy.value = null;
  }
}

async function openSchedule(row: MatchAdmin, mode: ScheduleDialogMode): Promise<void> {
  if (!canEdit.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `${mode}:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    if (mode === 'postpone' && !matchPostponable(detail.status)) {
      ElMessage.warning('当前状态不可延期。');
      await load();
      return;
    }
    if (mode === 'reschedule' && !matchReschedulable(detail.status)) {
      ElMessage.warning('仅已延期比赛可重新排期。');
      await load();
      return;
    }
    scheduleDialog.match = detail;
    scheduleDialog.mode = mode;
    scheduleDialog.error = '';
    scheduleForm.startAt = toPickerDate(detail.startAt);
    scheduleForm.endAt = toPickerDate(detail.endAt);
    scheduleDialog.visible = true;
  } catch (caught) {
    ElMessage.error(describeError(caught, '比赛详情加载失败'));
  } finally {
    actionBusy.value = null;
  }
}

function openCancel(row: MatchAdmin): void {
  if (!canStatus.value || !matchCancellable(row.status)) {
    return;
  }
  cancelDialog.match = row;
  cancelForm.reason = '';
  cancelDialog.error = '';
  cancelDialog.visible = true;
}

// ---------------------------------------------------------------- validation

function validateMatchForm(mode: MatchDialogMode): string | null {
  if (!matchForm.title.trim()) {
    return '请输入比赛名称';
  }
  if (!matchForm.venue.trim()) {
    return '请输入比赛场地';
  }
  if (!matchForm.homeTeamName.trim()) {
    return '请输入主队名称';
  }
  if (!matchForm.awayTeamName.trim()) {
    return '请输入客队名称';
  }
  const startAt = fromPickerDate(matchForm.startAt);
  const endAt = fromPickerDate(matchForm.endAt);
  if (!startAt || !endAt) {
    return '请选择开始与结束时间';
  }
  const scheduleError = matchScheduleError(startAt.toISOString(), endAt.toISOString());
  if (scheduleError) {
    return scheduleError;
  }
  if (mode === 'create') {
    const seasonError = matchSeasonError(matchForm.season.trim());
    if (seasonError) {
      return seasonError;
    }
  }
  if (!Number.isInteger(matchForm.capacity) || matchForm.capacity < 1) {
    return '正式名额必须为正整数';
  }
  if (!Number.isInteger(matchForm.guestCapacity) || matchForm.guestCapacity < 0) {
    return '外挂名额不能为负数';
  }
  if (!Number.isInteger(matchForm.waitlistCapacity) || matchForm.waitlistCapacity < 0) {
    return '候补名额不能为负数';
  }
  if (
    matchForm.round !== null &&
    (!Number.isInteger(matchForm.round) || matchForm.round < 0)
  ) {
    return '轮次必须为非负整数';
  }
  if (
    matchForm.scoreHome !== null &&
    (!Number.isInteger(matchForm.scoreHome) || matchForm.scoreHome < 0)
  ) {
    return '主队比分必须为非负整数';
  }
  if (
    matchForm.scoreAway !== null &&
    (!Number.isInteger(matchForm.scoreAway) || matchForm.scoreAway < 0)
  ) {
    return '客队比分必须为非负整数';
  }
  return matchScorePairError(matchForm.scoreHome, matchForm.scoreAway);
}

function buildCreatePayload(): MatchCreate {
  const payload: MatchCreate = {
    title: matchForm.title.trim(),
    type: matchForm.type,
    startAt: fromPickerDate(matchForm.startAt)!.toISOString(),
    endAt: fromPickerDate(matchForm.endAt)!.toISOString(),
    venue: matchForm.venue.trim(),
    homeTeamName: matchForm.homeTeamName.trim(),
    awayTeamName: matchForm.awayTeamName.trim(),
    format: '8人制',
    competition: matchForm.competition.trim() || null,
    season: matchForm.season.trim(),
    round: matchForm.round ?? null,
    capacity: matchForm.capacity,
    guestCapacity: matchForm.guestCapacity,
    waitlistCapacity: matchForm.waitlistCapacity,
    includeGuests: matchForm.includeGuests,
  };
  if (ourSideNeeded.value) {
    payload.ourSide = matchForm.ourSide;
  }
  if (matchForm.scoreHome !== null && matchForm.scoreAway !== null) {
    payload.scoreHome = matchForm.scoreHome;
    payload.scoreAway = matchForm.scoreAway;
  }
  return payload;
}

function buildUpdatePayload(version: number): MatchUpdate {
  return {
    title: matchForm.title.trim(),
    startAt: fromPickerDate(matchForm.startAt)!.toISOString(),
    endAt: fromPickerDate(matchForm.endAt)!.toISOString(),
    venue: matchForm.venue.trim(),
    competition: matchForm.competition.trim() || null,
    round: matchForm.round ?? null,
    homeTeamName: matchForm.homeTeamName.trim(),
    awayTeamName: matchForm.awayTeamName.trim(),
    capacity: matchForm.capacity,
    guestCapacity: matchForm.guestCapacity,
    waitlistCapacity: matchForm.waitlistCapacity,
    includeGuests: matchForm.includeGuests,
    version,
  };
}

// ---------------------------------------------------------------- submit

async function submitMatch(): Promise<void> {
  if (matchDialog.submitting) {
    return;
  }
  const mode = matchDialog.mode;
  const validationError = validateMatchForm(mode);
  if (validationError) {
    matchDialog.error = validationError;
    return;
  }
  matchDialog.submitting = true;
  matchDialog.error = '';
  try {
    if (mode === 'create') {
      // 网络超时后重试沿用同一幂等键，避免服务端已落库却重复建赛。
      const idempotencyKey = matchSubmitKey ?? newIdempotencyKey();
      await api.post<MatchAdmin>('/matches', buildCreatePayload(), { idempotencyKey });
      matchSubmitKey = null;
      matchDialog.visible = false;
      status.value = undefined;
      page.value = 1;
      ElMessage.success('比赛已创建');
      await load();
      return;
    }

    const target = matchDialog.match;
    if (!target) {
      return;
    }
    const detail = await fetchAuthoritative(target.id);
    if (!matchEditable(detail.status)) {
      matchDialog.error = '当前状态不可编辑；已延期请使用重新排期。';
      await load();
      return;
    }
    const capacityError = matchCapacityError({
      capacity: matchForm.capacity,
      guestCapacity: matchForm.guestCapacity,
      confirmedCount: detail.confirmedCount,
      guestCount: detail.guestCount,
    });
    if (capacityError) {
      matchDialog.error = capacityError;
      return;
    }
    await api.patch<MatchAdmin>(`/matches/${detail.id}`, buildUpdatePayload(detail.version));
    matchDialog.visible = false;
    ElMessage.success('比赛已更新');
    await load();
  } catch (caught) {
    await reportWriteError(caught, (message) => {
      matchDialog.error = message;
    });
  } finally {
    matchDialog.submitting = false;
  }
}

function statusItemDisabled(row: MatchAdmin, choice: number): boolean {
  if (actionBusy.value !== null) {
    return true;
  }
  if (choice === row.status) {
    return true;
  }
  if (choice === MATCH_STATUS_POSTPONED) {
    return !(canEdit.value && matchPostponable(row.status));
  }
  if (choice === MATCH_STATUS_CANCELLED) {
    return !(canStatus.value && matchCancellable(row.status));
  }
  return !canStatus.value;
}

async function onStatusCommand(row: MatchAdmin, command: number | string): Promise<void> {
  const targetStatus = Number(command);
  if (targetStatus === MATCH_STATUS_POSTPONED) {
    await openSchedule(row, 'postpone');
    return;
  }
  if (targetStatus === MATCH_STATUS_CANCELLED) {
    openCancel(row);
    return;
  }
  await changeStatus(row, targetStatus);
}

async function changeStatus(row: MatchAdmin, targetStatus: number): Promise<void> {
  if (!canStatus.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `status:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    await api.post<MatchAdmin>(
      `/matches/${detail.id}/status`,
      { status: targetStatus, version: detail.version },
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success('比赛状态已更新');
    await load();
  } catch (caught) {
    await reportWriteError(caught);
  } finally {
    actionBusy.value = null;
  }
}

async function submitSchedule(): Promise<void> {
  if (scheduleDialog.submitting) {
    return;
  }
  const startAt = fromPickerDate(scheduleForm.startAt);
  const endAt = fromPickerDate(scheduleForm.endAt);
  if (!startAt || !endAt) {
    scheduleDialog.error = '请选择开始与结束时间';
    return;
  }
  const scheduleError = matchScheduleError(startAt.toISOString(), endAt.toISOString());
  if (scheduleError) {
    scheduleDialog.error = scheduleError;
    return;
  }
  const target = scheduleDialog.match;
  if (!target) {
    return;
  }
  scheduleDialog.submitting = true;
  scheduleDialog.error = '';
  try {
    const detail = await fetchAuthoritative(target.id);
    const mode = scheduleDialog.mode;
    if (mode === 'postpone' && !matchPostponable(detail.status)) {
      scheduleDialog.error = '当前状态不可延期。';
      await load();
      return;
    }
    if (mode === 'reschedule' && !matchReschedulable(detail.status)) {
      scheduleDialog.error = '仅已延期比赛可重新排期。';
      await load();
      return;
    }
    await api.post<MatchAdmin>(
      `/matches/${detail.id}/${mode === 'postpone' ? 'postpone' : 'reschedule'}`,
      {
        startAt: startAt.toISOString(),
        endAt: endAt.toISOString(),
        version: detail.version,
      },
      { idempotencyKey: newIdempotencyKey() },
    );
    scheduleDialog.visible = false;
    ElMessage.success(mode === 'postpone' ? '比赛已延期' : '比赛已重新排期');
    await load();
  } catch (caught) {
    await reportWriteError(caught, (message) => {
      scheduleDialog.error = message;
    });
  } finally {
    scheduleDialog.submitting = false;
  }
}

async function submitCancel(): Promise<void> {
  if (cancelDialog.submitting) {
    return;
  }
  const reason = cancelForm.reason.trim();
  if (!reason) {
    cancelDialog.error = '请输入取消原因';
    return;
  }
  if (reason.length > 200) {
    cancelDialog.error = '取消原因不能超过 200 字';
    return;
  }
  const target = cancelDialog.match;
  if (!target) {
    return;
  }
  // 先置位再弹确认框，避免快速双击弹出多个二次确认。
  cancelDialog.submitting = true;
  try {
    await ElMessageBox.confirm(
      `确认取消「${target.title}」？取消后全部报名将作废。`,
      '二次确认',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '保留比赛',
      },
    );
  } catch {
    cancelDialog.submitting = false;
    return;
  }
  cancelDialog.error = '';
  try {
    const detail = await fetchAuthoritative(target.id);
    if (!matchCancellable(detail.status)) {
      cancelDialog.error = '当前状态不可取消。';
      await load();
      return;
    }
    await api.post<MatchAdmin>(
      `/matches/${detail.id}/cancel`,
      { reason, version: detail.version },
      { idempotencyKey: newIdempotencyKey() },
    );
    cancelDialog.visible = false;
    ElMessage.success('比赛已取消');
    await load();
  } catch (caught) {
    await reportWriteError(caught, (message) => {
      cancelDialog.error = message;
    });
  } finally {
    cancelDialog.submitting = false;
  }
}

onMounted(load);
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>比赛管理</h2>
        <p class="muted">创建与维护赛程；状态重置会停止自动流转，延期后需重新排期。</p>
      </div>
      <el-button v-if="canCreate" type="primary" @click="openCreate">新建比赛</el-button>
    </div>

    <el-alert
      v-if="error"
      class="page-error"
      type="error"
      :title="error"
      :closable="false"
      show-icon
    >
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <div class="toolbar">
      <el-select
        v-model="status"
        class="status-filter"
        placeholder="全部状态"
        clearable
        @change="onStatusChange"
      >
        <el-option
          v-for="option in statusOptions"
          :key="option.value"
          :label="option.text"
          :value="option.value"
        />
      </el-select>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无比赛" stripe>
      <el-table-column label="比赛" prop="title" min-width="200" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ matchTypeText(row.type) }}</template>
      </el-table-column>
      <el-table-column label="时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" effect="light">
            {{ matchStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="报名（正式/外挂/候补）" min-width="180">
        <template #default="{ row }">
          {{ row.confirmedCount }} / {{ row.guestCount }} / {{ row.waitlistCount }}
        </template>
      </el-table-column>
      <el-table-column label="场地" prop="venue" min-width="150" />
      <el-table-column v-if="canCreate || canEdit || canStatus" label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-tooltip
              v-if="canEdit"
              :disabled="matchEditable(row.status)"
              content="终态或已延期比赛不可编辑；已延期请使用重新排期"
            >
              <span>
                <el-button
                  link
                  type="primary"
                  size="small"
                  :disabled="!matchEditable(row.status) || actionBusy !== null"
                  @click="openEdit(row)"
                >
                  编辑
                </el-button>
              </span>
            </el-tooltip>
            <el-dropdown
              v-if="canStatus || canEdit"
              trigger="click"
              @command="onStatusCommand(row, $event)"
            >
              <el-button link type="primary" size="small" :disabled="actionBusy !== null">
                状态
              </el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item
                    v-for="choice in statusChoices"
                    :key="choice.value"
                    :command="choice.value"
                    :disabled="statusItemDisabled(row, choice.value)"
                  >
                    {{ choice.text }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button
              v-if="canEdit && matchReschedulable(row.status)"
              link
              type="primary"
              size="small"
              :disabled="actionBusy !== null"
              @click="openSchedule(row, 'reschedule')"
            >
              重新排期
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <div class="table-footer">
      <el-pagination
        v-model:current-page="page"
        v-model:page-size="pageSize"
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>

    <el-dialog v-model="matchDialog.visible" :title="matchDialogTitle" width="640px">
      <el-form label-width="96px">
        <el-form-item label="比赛名称">
          <el-input
            v-model="matchForm.title"
            maxlength="128"
            show-word-limit
            placeholder="例如 橙队VS兰队"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select
            v-if="matchDialog.mode === 'create'"
            v-model="matchForm.type"
            class="full"
          >
            <el-option
              v-for="item in typeOptions"
              :key="item.value"
              :label="item.text"
              :value="item.value"
            />
          </el-select>
          <span v-else>{{ matchTypeText(matchForm.type) }}（创建后不可修改）</span>
        </el-form-item>
        <el-form-item label="赛制">
          <span>8 人制</span>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="matchForm.startAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="开始时间"
            class="full"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="matchForm.endAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="结束时间"
            class="full"
          />
        </el-form-item>
        <el-form-item label="场地">
          <el-input v-model="matchForm.venue" maxlength="128" placeholder="比赛场地" />
        </el-form-item>
        <el-form-item label="赛事">
          <el-input
            v-model="matchForm.competition"
            maxlength="64"
            placeholder="可选，例如 春季联赛"
          />
        </el-form-item>
        <el-form-item label="主队">
          <el-input v-model="matchForm.homeTeamName" maxlength="64" placeholder="主队名称" />
        </el-form-item>
        <el-form-item label="客队">
          <el-input v-model="matchForm.awayTeamName" maxlength="64" placeholder="客队名称" />
        </el-form-item>
        <el-form-item v-if="ourSideNeeded" label="我方主客">
          <el-radio-group
            v-model="matchForm.ourSide"
            :disabled="matchDialog.mode === 'edit'"
          >
            <el-radio :value="0">主队</el-radio>
            <el-radio :value="1">客队</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-else-if="matchDialog.mode === 'edit'" label="我方主客">
          <span class="muted">—（队内/训练赛无主客）</span>
        </el-form-item>
        <el-form-item label="赛季">
          <el-input
            v-if="matchDialog.mode === 'create'"
            v-model="matchForm.season"
            class="season-input"
            maxlength="4"
            placeholder="如 2026"
          />
          <span v-else>{{ matchForm.season }}（创建后不可修改）</span>
        </el-form-item>
        <el-form-item label="轮次">
          <el-input-number
            v-model="matchForm.round"
            :min="0"
            :max="65535"
            :precision="0"
            :value-on-clear="null"
            controls-position="right"
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item label="正式名额">
          <el-input-number
            v-model="matchForm.capacity"
            :min="1"
            :max="65535"
            controls-position="right"
          />
          <span class="muted small">总名额 {{ totalCapacity }}（正式 + 外挂）</span>
        </el-form-item>
        <el-form-item label="外挂名额">
          <el-input-number
            v-model="matchForm.guestCapacity"
            :min="0"
            :max="65535"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="候补名额">
          <el-input-number
            v-model="matchForm.waitlistCapacity"
            :min="0"
            :max="65535"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="含外挂">
          <el-switch v-model="matchForm.includeGuests" />
        </el-form-item>
        <el-form-item v-if="matchDialog.mode === 'edit' && matchDialog.match" label="已确认">
          <span class="muted small">
            正式 {{ matchDialog.match.confirmedCount }} 人 / 外挂
            {{ matchDialog.match.guestCount }} 人；名额不得低于已确认人数。
          </span>
        </el-form-item>
        <el-form-item v-if="matchDialog.mode === 'create'" label="比分">
          <div class="score-inputs">
            <el-input-number
              v-model="matchForm.scoreHome"
              :min="0"
              :max="255"
              :precision="0"
              :value-on-clear="null"
              controls-position="right"
            />
            <span>:</span>
            <el-input-number
              v-model="matchForm.scoreAway"
              :min="0"
              :max="255"
              :precision="0"
              :value-on-clear="null"
              controls-position="right"
            />
          </div>
        </el-form-item>
        <el-form-item v-else label="比分">
          <span class="muted small">比分请到事件/比分页录入；编辑不会改动比分。</span>
        </el-form-item>
      </el-form>
      <p v-if="matchDialog.error" class="dialog-error">{{ matchDialog.error }}</p>
      <template #footer>
        <el-button @click="matchDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="matchDialog.submitting" @click="submitMatch">
          {{ matchDialog.mode === 'create' ? '创建比赛' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="scheduleDialog.visible"
      :title="scheduleDialog.mode === 'postpone' ? '延期比赛' : '重新排期'"
      width="480px"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        :title="
          scheduleDialog.mode === 'postpone'
            ? '延期后状态变为已延期；报名截止与取消锁定按新时间重算，需重新排期后恢复报名。'
            : '重新排期后状态恢复为报名中；报名截止与取消锁定按新时间重算。'
        "
      />
      <el-form label-width="96px">
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="scheduleForm.startAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="开始时间"
            class="full"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="scheduleForm.endAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="结束时间"
            class="full"
          />
        </el-form-item>
      </el-form>
      <p v-if="scheduleDialog.error" class="dialog-error">{{ scheduleDialog.error }}</p>
      <template #footer>
        <el-button @click="scheduleDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="scheduleDialog.submitting"
          @click="submitSchedule"
        >
          {{ scheduleDialog.mode === 'postpone' ? '确认延期' : '确认重新排期' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialog.visible" title="取消比赛" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`确认取消「${cancelDialog.match?.title ?? '该比赛'}」？取消后将作废全部报名，报名记录保留但不再有效。`"
      />
      <el-form label-width="96px">
        <el-form-item label="取消原因">
          <el-input
            v-model="cancelForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="取消原因（将记入审计）"
          />
        </el-form-item>
      </el-form>
      <p v-if="cancelDialog.error" class="dialog-error">{{ cancelDialog.error }}</p>
      <template #footer>
        <el-button @click="cancelDialog.visible = false">返回</el-button>
        <el-button
          type="danger"
          :loading="cancelDialog.submitting"
          @click="submitCancel"
        >
          确认取消
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.page-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.page-head h2 {
  margin: 0 0 4px;
  font-size: 20px;
}

.page-head p {
  margin: 0;
  font-size: 13px;
}

.row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.row-actions .el-button + .el-button {
  margin-left: 0;
}

.full {
  width: 100%;
}

.season-input {
  width: 120px;
}

.score-inputs {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dialog-error {
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

.muted {
  color: var(--yh-muted);
}

.small {
  font-size: 12px;
}
</style>
