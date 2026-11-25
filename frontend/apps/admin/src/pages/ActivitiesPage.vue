<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import {
  ERROR_CODES,
  describeError,
  isApiError,
  isVersionConflict,
  newIdempotencyKey,
} from '@yunhefc/api-client';
import { ACTIVITY_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  ActivityAdmin,
  ActivitySignupRow,
  ActivityStatus,
  ActivityType,
  Page,
} from '@yunhefc/shared';
import { formatDateTime } from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { fromPickerDate, shanghaiDayAt, toPickerDate } from '../features/time/picker';
import {
  ACTIVITY_IMAGE_COUNT_ERROR,
  ACTIVITY_IMAGE_MAX_COUNT,
  ACTIVITY_IMAGE_UPLOAD_ERROR,
  ACTIVITY_STATUS_OPTIONS,
  ACTIVITY_TYPE_OPTIONS,
  activityEditable,
  activityFormError,
  activityStatusText,
  activityStatusTone,
  activityTypeText,
  validateActivityImage,
} from '../features/activity/form';
import { useSessionStore } from '../stores/session';

type ActivityDialogMode = 'create' | 'edit';

interface ActivityFormState {
  title: string;
  type: ActivityType;
  startAt: Date | null;
  endAt: Date | null;
  signupDeadline: Date | null;
  capacity: number | null;
  familyAllowed: boolean;
  costPerPerson: string;
  location: string;
  description: string;
  locationImageKeys: string[];
}

interface ActivityPayload {
  title: string;
  type: ActivityType;
  startAt: string;
  endAt: string | null;
  signupDeadline: string;
  capacity: number | null;
  familyAllowed: boolean;
  costPerPerson: number | null;
  location: string;
  locationImageKeys: string[];
  description: string | null;
  version?: number;
}

interface ActivityImageUploadView {
  objectKey: string;
  signedUrl: string;
}

const TWO_HOURS_MS = 2 * 60 * 60 * 1000;
const STATUS_CLOSED: ActivityStatus = 1;
const STATUS_FINISHED: ActivityStatus = 2;
const STATUS_CANCELLED: ActivityStatus = 3;

const session = useSessionStore();
const canManage = computed(() => hasPermission(session.permissions, ACTIVITY_MANAGE_ANY));

const rows = ref<ActivityAdmin[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const typeFilter = ref('');
const statusFilter = ref('');
const dateRange = ref<[string, string] | null>(null);
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

const hasActiveFilters = computed(
  () => !isBlank(typeFilter.value) || !isBlank(statusFilter.value) || dateRange.value !== null,
);

/** el-select 清空后可能回写 undefined/null，统一视为未选择。 */
function isBlank(value: unknown): boolean {
  return value === '' || value === null || value === undefined;
}

const activityDialog = reactive({
  visible: false,
  mode: 'create' as ActivityDialogMode,
  submitting: false,
  error: '',
  activity: null as ActivityAdmin | null,
});
const activityForm = reactive<ActivityFormState>(defaultActivityForm());
const imageInput = ref<HTMLInputElement | null>(null);
const imagePreviews = ref<string[]>([]);
const imageUploading = ref(false);
const imageError = ref('');
const localPreviewUrls = new Set<string>();
let imageUploadSeq = 0;
let activitySubmitKey: string | null = null;

const cancelDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  activity: null as ActivityAdmin | null,
});
const cancelReason = ref('');

const rosterDrawer = reactive({
  visible: false,
  activity: null as ActivityAdmin | null,
  rows: [] as ActivitySignupRow[],
  total: 0,
  page: 1,
  pageSize: 10,
  loading: false,
  error: '',
  busy: false,
});
let rosterSeq = 0;

const actionBusy = ref<string | null>(null);

const activityDialogTitle = computed(() =>
  activityDialog.mode === 'create' ? '创建活动' : '编辑活动',
);

function defaultActivityForm(): ActivityFormState {
  const startAt = toPickerDate(shanghaiDayAt(1, 18).toISOString())!;
  return {
    title: '',
    type: 0,
    startAt,
    endAt: new Date(startAt.getTime() + TWO_HOURS_MS),
    signupDeadline: new Date(startAt.getTime() - TWO_HOURS_MS),
    capacity: null,
    familyAllowed: false,
    costPerPerson: '',
    location: '',
    description: '',
    locationImageKeys: [],
  };
}

/** 仅接受后端/图片代理现签的 http(s) URL；mock 对象键回落空态。 */
function displayableImageUrl(url: string | null | undefined): string {
  return typeof url === 'string' && /^https?:\/\//i.test(url) ? url : '';
}

function signupText(row: ActivityAdmin): string {
  const capacity = row.capacity === null ? '不限' : String(row.capacity);
  return `${row.signupCount} / ${capacity}`;
}

function revokeLocalPreviews(): void {
  for (const url of localPreviewUrls) {
    URL.revokeObjectURL(url);
  }
  localPreviewUrls.clear();
}

function resetImageState(): void {
  imageUploadSeq += 1;
  revokeLocalPreviews();
  imagePreviews.value = [];
  imageUploading.value = false;
  imageError.value = '';
}

function fillForm(activity: ActivityAdmin): void {
  activityForm.title = activity.title;
  activityForm.type = activity.type;
  activityForm.startAt = toPickerDate(activity.startAt);
  activityForm.endAt = toPickerDate(activity.endAt);
  activityForm.signupDeadline = toPickerDate(activity.signupDeadline);
  activityForm.capacity = activity.capacity;
  activityForm.familyAllowed = activity.familyAllowed;
  activityForm.costPerPerson =
    activity.costPerPerson === null ? '' : activity.costPerPerson.toFixed(2);
  activityForm.location = activity.location;
  activityForm.description = activity.description ?? '';
  activityForm.locationImageKeys = [...activity.locationImageKeys];
  imagePreviews.value = activity.locationImageUrls.map(displayableImageUrl);
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const query: QueryParams = {
      page: page.value,
      pageSize: pageSize.value,
    };
    if (!isBlank(typeFilter.value)) {
      query.type = Number(typeFilter.value);
    }
    if (!isBlank(statusFilter.value)) {
      query.status = Number(statusFilter.value);
    }
    if (dateRange.value) {
      query.startDate = dateRange.value[0];
      query.endDate = dateRange.value[1];
    }
    const result = await api.get<Page<ActivityAdmin>>('/activities', { query });
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

function onFilterChange(): void {
  page.value = 1;
  void load();
}

function resetFilters(): void {
  typeFilter.value = '';
  statusFilter.value = '';
  dateRange.value = null;
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

async function fetchAuthoritative(id: number): Promise<ActivityAdmin> {
  return api.get<ActivityAdmin>(`/activities/${id}`);
}

// ---------------------------------------------------------------- image

function pickImage(): void {
  if (!imageUploading.value) {
    imageInput.value?.click();
  }
}

function removeImage(index: number): void {
  const localUrl = imagePreviews.value[index];
  if (localUrl?.startsWith('blob:')) {
    URL.revokeObjectURL(localUrl);
    localPreviewUrls.delete(localUrl);
  }
  imagePreviews.value.splice(index, 1);
  activityForm.locationImageKeys.splice(index, 1);
}

async function onImageChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  imageError.value = '';
  if (activityForm.locationImageKeys.length >= ACTIVITY_IMAGE_MAX_COUNT) {
    imageError.value = ACTIVITY_IMAGE_COUNT_ERROR;
    return;
  }
  const invalid = validateActivityImage(file);
  if (invalid) {
    imageError.value = invalid;
    return;
  }

  const seq = ++imageUploadSeq;
  const localUrl = URL.createObjectURL(file);
  localPreviewUrls.add(localUrl);
  const pendingIndex = imagePreviews.value.length;
  imagePreviews.value.push(localUrl);
  imageUploading.value = true;
  try {
    const body = new FormData();
    body.append('file', file);
    const uploaded = await api.postForm<ActivityImageUploadView>('/files/activity-image', body);
    if (seq !== imageUploadSeq) {
      return;
    }
    activityForm.locationImageKeys.push(uploaded.objectKey);
    if (displayableImageUrl(uploaded.signedUrl)) {
      URL.revokeObjectURL(localUrl);
      localPreviewUrls.delete(localUrl);
      imagePreviews.value[pendingIndex] = uploaded.signedUrl;
    }
  } catch (caught) {
    if (seq !== imageUploadSeq) {
      return;
    }
    imagePreviews.value.splice(pendingIndex, 1);
    URL.revokeObjectURL(localUrl);
    localPreviewUrls.delete(localUrl);
    imageError.value = describeError(caught, ACTIVITY_IMAGE_UPLOAD_ERROR);
  } finally {
    if (seq === imageUploadSeq) {
      imageUploading.value = false;
    }
  }
}

// ---------------------------------------------------------------- dialogs

function openCreate(): void {
  if (!canManage.value) {
    return;
  }
  Object.assign(activityForm, defaultActivityForm());
  resetImageState();
  activityDialog.mode = 'create';
  activityDialog.activity = null;
  activityDialog.error = '';
  activitySubmitKey = newIdempotencyKey();
  activityDialog.visible = true;
}

async function openEdit(row: ActivityAdmin): Promise<void> {
  if (!canManage.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `edit:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    if (!activityEditable(detail.status)) {
      ElMessage.warning('已结束或已取消的活动不可编辑。');
      await load();
      return;
    }
    resetImageState();
    fillForm(detail);
    activityDialog.mode = 'edit';
    activityDialog.activity = detail;
    activityDialog.error = '';
    activityDialog.visible = true;
  } catch (caught) {
    ElMessage.error(describeError(caught, '活动详情加载失败'));
  } finally {
    actionBusy.value = null;
  }
}

async function openCopy(row: ActivityAdmin): Promise<void> {
  if (!canManage.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `copy:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    resetImageState();
    fillForm(detail);
    activityDialog.mode = 'create';
    activityDialog.activity = null;
    activityDialog.error = '';
    activitySubmitKey = newIdempotencyKey();
    activityDialog.visible = true;
  } catch (caught) {
    ElMessage.error(describeError(caught, '活动详情加载失败'));
  } finally {
    actionBusy.value = null;
  }
}

function closeActivityDialog(): void {
  activityDialog.visible = false;
  revokeLocalPreviews();
}

async function reportWriteError(
  caught: unknown,
  inlineError?: (message: string) => void,
): Promise<void> {
  if (isVersionConflict(caught)) {
    activityDialog.visible = false;
    cancelDialog.visible = false;
    ElMessage.error('已被他人修改，请刷新后重试');
    await load();
    return;
  }
  if (isApiError(caught) && caught.code === ERROR_CODES.ACTIVITY_STATE_CONFLICT) {
    activityDialog.visible = false;
    cancelDialog.visible = false;
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

// ---------------------------------------------------------------- submit

function buildPayload(version?: number): ActivityPayload {
  const startAt = fromPickerDate(activityForm.startAt)!;
  const endAt = fromPickerDate(activityForm.endAt);
  const signupDeadline = fromPickerDate(activityForm.signupDeadline)!;
  const cost = activityForm.costPerPerson.trim();
  const payload: ActivityPayload = {
    title: activityForm.title.trim(),
    type: activityForm.type,
    startAt: startAt.toISOString(),
    endAt: endAt ? endAt.toISOString() : null,
    signupDeadline: signupDeadline.toISOString(),
    capacity: typeof activityForm.capacity === 'number' ? activityForm.capacity : null,
    familyAllowed: activityForm.familyAllowed,
    costPerPerson: cost ? Number(cost) : null,
    location: activityForm.location.trim(),
    locationImageKeys: [...activityForm.locationImageKeys],
    description: activityForm.description.trim() || null,
  };
  if (version !== undefined) {
    payload.version = version;
  }
  return payload;
}

async function submitActivity(): Promise<void> {
  if (activityDialog.submitting || imageUploading.value) {
    if (imageUploading.value) {
      ElMessage.warning('图片上传中，请稍候');
    }
    return;
  }
  const validationError = activityFormError(activityForm);
  if (validationError) {
    activityDialog.error = validationError;
    return;
  }
  activityDialog.submitting = true;
  activityDialog.error = '';
  try {
    if (activityDialog.mode === 'create') {
      const idempotencyKey = activitySubmitKey ?? newIdempotencyKey();
      await api.post<ActivityAdmin>('/activities', buildPayload(), { idempotencyKey });
      activitySubmitKey = null;
      activityDialog.visible = false;
      revokeLocalPreviews();
      page.value = 1;
      ElMessage.success('活动已创建');
      await load();
      return;
    }

    const target = activityDialog.activity;
    if (!target) {
      return;
    }
    const detail = await fetchAuthoritative(target.id);
    if (!activityEditable(detail.status)) {
      activityDialog.error = '已结束或已取消的活动不可编辑。';
      await load();
      return;
    }
    await api.patch<ActivityAdmin>(`/activities/${detail.id}`, buildPayload(detail.version), {
      idempotencyKey: newIdempotencyKey(),
    });
    activityDialog.visible = false;
    revokeLocalPreviews();
    ElMessage.success('活动已更新');
    await load();
  } catch (caught) {
    if (isVersionConflict(caught) && activityDialog.mode === 'edit') {
      const target = activityDialog.activity;
      if (target) {
        try {
          const latest = await fetchAuthoritative(target.id);
          activityDialog.activity = latest;
          activityDialog.error = '已被他人修改，已载入最新版本，请确认后再次保存。';
          await load();
          return;
        } catch {
          // 拉取失败时按通用冲突处理。
        }
      }
    }
    await reportWriteError(caught, (message) => {
      activityDialog.error = message;
    });
  } finally {
    activityDialog.submitting = false;
  }
}

// ---------------------------------------------------------------- status

function canCloseSignup(row: ActivityAdmin): boolean {
  return row.status === 0;
}

function canFinish(row: ActivityAdmin): boolean {
  return activityEditable(row.status);
}

function canCancel(row: ActivityAdmin): boolean {
  return activityEditable(row.status);
}

async function changeStatus(row: ActivityAdmin, target: ActivityStatus): Promise<void> {
  if (!canManage.value || actionBusy.value !== null) {
    return;
  }
  actionBusy.value = `status:${row.id}`;
  try {
    const detail = await fetchAuthoritative(row.id);
    await api.post<ActivityAdmin>(
      `/activities/${detail.id}/status`,
      { status: target, version: detail.version },
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success('活动状态已更新');
    await load();
  } catch (caught) {
    await reportWriteError(caught);
  } finally {
    actionBusy.value = null;
  }
}

function openCancel(row: ActivityAdmin): void {
  if (!canManage.value || !canCancel(row)) {
    return;
  }
  cancelDialog.activity = row;
  cancelReason.value = '';
  cancelDialog.error = '';
  cancelDialog.visible = true;
}

type RowAction = 'edit' | 'close' | 'finish' | 'cancel';

function onRowCommand(row: ActivityAdmin, command: RowAction): void {
  switch (command) {
    case 'edit':
      void openEdit(row);
      return;
    case 'close':
      void changeStatus(row, STATUS_CLOSED);
      return;
    case 'finish':
      void changeStatus(row, STATUS_FINISHED);
      return;
    case 'cancel':
      openCancel(row);
      return;
  }
}

async function submitCancel(): Promise<void> {
  if (cancelDialog.submitting) {
    return;
  }
  const reason = cancelReason.value.trim();
  if (!reason) {
    cancelDialog.error = '请输入取消原因';
    return;
  }
  if (reason.length > 255) {
    cancelDialog.error = '取消原因不能超过 255 字';
    return;
  }
  const target = cancelDialog.activity;
  if (!target) {
    return;
  }
  cancelDialog.submitting = true;
  cancelDialog.error = '';
  try {
    const detail = await fetchAuthoritative(target.id);
    await api.post<ActivityAdmin>(
      `/activities/${detail.id}/status`,
      { status: STATUS_CANCELLED, version: detail.version, reason },
      { idempotencyKey: newIdempotencyKey() },
    );
    cancelDialog.visible = false;
    ElMessage.success('活动已取消');
    await load();
  } catch (caught) {
    await reportWriteError(caught, (message) => {
      cancelDialog.error = message;
    });
  } finally {
    cancelDialog.submitting = false;
  }
}

// ---------------------------------------------------------------- roster

function openRoster(row: ActivityAdmin): void {
  rosterDrawer.activity = row;
  rosterDrawer.rows = [];
  rosterDrawer.total = 0;
  rosterDrawer.page = 1;
  rosterDrawer.error = '';
  rosterDrawer.visible = true;
  void loadRoster();
}

async function loadRoster(): Promise<void> {
  const activity = rosterDrawer.activity;
  if (!activity) {
    return;
  }
  const seq = ++rosterSeq;
  rosterDrawer.loading = true;
  rosterDrawer.error = '';
  try {
    const result = await api.get<Page<ActivitySignupRow>>(`/activities/${activity.id}/signups`, {
      query: { page: rosterDrawer.page, pageSize: rosterDrawer.pageSize },
    });
    if (seq !== rosterSeq) {
      return;
    }
    rosterDrawer.rows = result.items;
    rosterDrawer.total = result.total;
  } catch (caught) {
    if (seq !== rosterSeq) {
      return;
    }
    rosterDrawer.error = describeError(caught, '名单加载失败，请稍后重试');
  } finally {
    if (seq === rosterSeq) {
      rosterDrawer.loading = false;
    }
  }
}

function onRosterPageChange(): void {
  void loadRoster();
}

function onRosterSizeChange(): void {
  rosterDrawer.page = 1;
  void loadRoster();
}

async function removeSignup(row: ActivitySignupRow): Promise<void> {
  const activity = rosterDrawer.activity;
  if (!activity || rosterDrawer.busy) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认移除「${row.playerName}」的报名？`, '移除报名', {
      type: 'warning',
      confirmButtonText: '移除',
      cancelButtonText: '返回',
    });
  } catch {
    return;
  }
  rosterDrawer.busy = true;
  try {
    const result = await api.delete<{ removed: boolean }>(
      `/activities/${activity.id}/signups/${row.id}`,
      undefined,
      { idempotencyKey: newIdempotencyKey() },
    );
    ElMessage.success(result.removed ? '报名已移除' : '该报名已不存在');
    if (rosterDrawer.rows.length === 1 && rosterDrawer.page > 1) {
      rosterDrawer.page -= 1;
    }
    await loadRoster();
    await load();
    try {
      rosterDrawer.activity = await fetchAuthoritative(activity.id);
    } catch {
      // 移除已成功；头部计数刷新失败不影响名单结果。
    }
  } catch (caught) {
    await reportWriteError(caught);
  } finally {
    rosterDrawer.busy = false;
  }
}

onMounted(load);
onBeforeUnmount(() => {
  revokeLocalPreviews();
});
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>活动管理</h2>
        <p class="muted">组织聚餐、年会等队内活动；已结束可在 C 端详情重新开放为报名中/报名截止，已取消为终态，需要重新组织时用复制。</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openCreate">新建活动</el-button>
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
        v-model="typeFilter"
        class="type-filter"
        placeholder="全部类型"
        clearable
        @change="onFilterChange"
      >
        <el-option
          v-for="option in ACTIVITY_TYPE_OPTIONS"
          :key="option.value"
          :label="option.text"
          :value="String(option.value)"
        />
      </el-select>
      <el-select
        v-model="statusFilter"
        class="status-filter"
        placeholder="全部状态"
        clearable
        @change="onFilterChange"
      >
        <el-option
          v-for="option in ACTIVITY_STATUS_OPTIONS"
          :key="option.value"
          :label="option.text"
          :value="option.value"
        />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        class="date-filter"
        type="daterange"
        value-format="YYYY-MM-DD"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        clearable
        @change="onFilterChange"
      />
      <el-button :disabled="!hasActiveFilters" @click="resetFilters">清空条件</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" empty-text="暂无活动" stripe>
      <el-table-column label="活动名" prop="title" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ activityTypeText(row.type) }}</template>
      </el-table-column>
      <el-table-column label="活动时间" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.startAt) }}</template>
      </el-table-column>
      <el-table-column label="报名截止" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.signupDeadline) }}</template>
      </el-table-column>
      <el-table-column label="人数" width="110">
        <template #default="{ row }">{{ signupText(row) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="activityStatusTone(row.status)" effect="light">
            {{ activityStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button link type="primary" size="small" @click="openRoster(row)">名单</el-button>
            <template v-if="canManage">
              <el-button
                link
                type="primary"
                size="small"
                :disabled="actionBusy !== null"
                @click="openCopy(row)"
              >
                复制
              </el-button>
              <el-dropdown trigger="click" @command="onRowCommand(row, $event)">
                <el-button link type="primary" size="small" :disabled="actionBusy !== null">
                  更多
                </el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item
                      command="edit"
                      :disabled="actionBusy !== null || !activityEditable(row.status)"
                    >
                      编辑
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="close"
                      :disabled="actionBusy !== null || !canCloseSignup(row)"
                    >
                      报名截止
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="finish"
                      :disabled="actionBusy !== null || !canFinish(row)"
                    >
                      已结束
                    </el-dropdown-item>
                    <el-dropdown-item
                      command="cancel"
                      :disabled="actionBusy !== null || !canCancel(row)"
                    >
                      取消
                    </el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
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

    <el-dialog
      v-model="activityDialog.visible"
      :title="activityDialogTitle"
      width="640px"
      @closed="closeActivityDialog"
    >
      <el-form label-width="96px">
        <el-form-item label="活动名称">
          <el-input
            v-model="activityForm.title"
            maxlength="128"
            show-word-limit
            placeholder="例如 冬季聚餐"
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="activityForm.type" class="full">
            <el-option
              v-for="option in ACTIVITY_TYPE_OPTIONS"
              :key="option.value"
              :label="option.text"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="活动时间">
          <el-date-picker
            v-model="activityForm.startAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="活动时间"
            class="full"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="activityForm.endAt"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="可选"
            class="full"
            clearable
          />
        </el-form-item>
        <el-form-item label="报名截止">
          <el-date-picker
            v-model="activityForm.signupDeadline"
            type="datetime"
            format="YYYY-MM-DD HH:mm"
            placeholder="报名截止"
            class="full"
          />
        </el-form-item>
        <el-form-item label="总人数上限">
          <el-input-number
            v-model="activityForm.capacity"
            :min="1"
            :max="65535"
            :value-on-clear="null"
            controls-position="right"
            placeholder="不限"
          />
          <span class="muted small">留空表示不限；只计报名人数，不含家属。</span>
        </el-form-item>
        <el-form-item label="是否携带家属">
          <el-switch v-model="activityForm.familyAllowed" />
          <span class="muted small">开启后报名人才可选择携带家属；默认关闭。</span>
        </el-form-item>
        <el-form-item label="人均消费">
          <el-input
            v-model="activityForm.costPerPerson"
            class="cost-input"
            maxlength="11"
            placeholder="可选，如 88.50"
          >
            <template #prefix>¥</template>
          </el-input>
        </el-form-item>
        <el-form-item label="活动地址">
          <el-input v-model="activityForm.location" maxlength="128" placeholder="活动地址" />
        </el-form-item>
        <el-form-item label="场地环境">
          <div class="image-field">
            <div v-if="imagePreviews.length === 0" class="image-placeholder">
              未上传场地环境图片
            </div>
            <div
              v-for="(url, index) in imagePreviews"
              :key="`${index}-${url}`"
              class="image-item"
            >
              <el-image v-if="url" :src="url" fit="cover" class="image-preview" />
              <div v-else class="image-placeholder">图片加载中</div>
              <el-button
                link
                type="danger"
                size="small"
                :disabled="imageUploading"
                @click="removeImage(index)"
              >
                移除
              </el-button>
            </div>
            <div class="image-actions">
              <input
                ref="imageInput"
                type="file"
                accept="image/jpeg,image/png,image/webp"
                hidden
                @change="onImageChange"
              />
              <el-button
                size="small"
                :loading="imageUploading"
                :disabled="activityForm.locationImageKeys.length >= ACTIVITY_IMAGE_MAX_COUNT"
                @click="pickImage"
              >
                上传图片
              </el-button>
              <span class="muted small">JPG/PNG/WebP，单张不超过 5MB，最多 10 张</span>
            </div>
          </div>
          <p v-if="imageError" class="dialog-error">{{ imageError }}</p>
        </el-form-item>
        <el-form-item label="活动说明">
          <el-input
            v-model="activityForm.description"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
            placeholder="可选"
          />
        </el-form-item>
        <el-form-item
          v-if="activityDialog.mode === 'edit' && activityDialog.activity"
          label="当前状态"
        >
          <span class="muted small">
            {{ activityStatusText(activityDialog.activity.status) }}；仅报名中与报名截止可编辑。
          </span>
        </el-form-item>
      </el-form>
      <p v-if="activityDialog.error" class="dialog-error">{{ activityDialog.error }}</p>
      <template #footer>
        <el-button @click="activityDialog.visible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="activityDialog.submitting"
          :disabled="imageUploading"
          @click="submitActivity"
        >
          {{ activityDialog.mode === 'create' ? '创建活动' : '保存修改' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="cancelDialog.visible" title="取消活动" width="480px">
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        :title="`确认取消「${cancelDialog.activity?.title ?? '该活动'}」？取消后已报名记录保留但不再有效，且不可恢复。`"
      />
      <el-form label-width="96px">
        <el-form-item label="取消原因">
          <el-input
            v-model="cancelReason"
            type="textarea"
            :rows="3"
            maxlength="255"
            show-word-limit
            placeholder="取消原因（将记入审计）"
          />
        </el-form-item>
      </el-form>
      <p v-if="cancelDialog.error" class="dialog-error">{{ cancelDialog.error }}</p>
      <template #footer>
        <el-button @click="cancelDialog.visible = false">返回</el-button>
        <el-button type="danger" :loading="cancelDialog.submitting" @click="submitCancel">
          确认取消
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="rosterDrawer.visible" title="活动名单" size="560px">
      <div v-if="rosterDrawer.activity" class="roster-head muted small">
        {{ rosterDrawer.activity.title }} · 有效报名 {{ rosterDrawer.activity.signupCount }}
        <template v-if="rosterDrawer.activity.capacity === null"> / 不限</template>
        <template v-else> / {{ rosterDrawer.activity.capacity }}</template>
      </div>
      <el-alert
        v-if="rosterDrawer.error"
        class="page-error"
        type="error"
        :title="rosterDrawer.error"
        :closable="false"
        show-icon
      >
        <el-button size="small" @click="loadRoster">重试</el-button>
      </el-alert>
      <el-table
        v-loading="rosterDrawer.loading"
        :data="rosterDrawer.rows"
        empty-text="暂无报名"
        stripe
      >
        <el-table-column label="姓名" prop="playerName" min-width="110" />
        <el-table-column label="号码" width="70">
          <template #default="{ row }">{{ row.playerNo ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="携带家属" width="90">
          <template #default="{ row }">{{ row.withFamily ? '是' : '否' }}</template>
        </el-table-column>
        <el-table-column label="家属备注" prop="familyNote" min-width="120">
          <template #default="{ row }">{{ row.familyNote || '—' }}</template>
        </el-table-column>
        <el-table-column label="报名时间" min-width="160">
          <template #default="{ row }">{{ formatDateTime(row.signedUpAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :disabled="rosterDrawer.busy"
              @click="removeSignup(row)"
            >
              移除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <el-pagination
          v-model:current-page="rosterDrawer.page"
          v-model:page-size="rosterDrawer.pageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="rosterDrawer.total"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onRosterPageChange"
          @size-change="onRosterSizeChange"
        />
      </div>
    </el-drawer>
  </section>
</template>

<style scoped>
.page-head {
  margin-bottom: 16px;
}

.toolbar {
  flex-wrap: wrap;
  margin-bottom: 16px;
}

.type-filter {
  width: 130px;
}

.status-filter {
  width: 140px;
}

.date-filter {
  width: 280px;
}

.cost-input {
  width: 180px;
}

.image-field {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
}

.image-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.image-preview {
  width: 160px;
  height: 96px;
  border-radius: 6px;
  background: #f5f7fa;
}

.image-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 160px;
  height: 96px;
  border: 1px dashed var(--el-border-color);
  border-radius: 6px;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
}

.image-actions {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.roster-head {
  margin-bottom: 12px;
}
</style>
