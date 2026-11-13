<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { ERROR_CODES, describeError, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { PLAYER_EDIT_ANY, USER_APPROVE_ANY, hasPermission } from '@yunhefc/permissions';
import type { Page, PlayerAdmin } from '@yunhefc/shared';
import {
  AVATAR_UPLOAD_ERROR,
  PLAYER_STATUS_TEXT,
  PLAYER_TYPE_TEXT,
  POSITION_TEXT,
  avatarPlaceholder,
  compressAvatar,
  isDisplayableAvatarUrl,
  phoneError,
  playerApprovable,
  playerConvertible,
  playerDisableable,
  playerProfileError,
  playerSort,
  playerStatusText,
  playerStatusTone,
  playerTypeText,
  positionText,
  validateAvatarFile,
} from '@yunhefc/shared';
import type { TableInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

interface PlayerFilters {
  name: string;
  region: string;
  type: string | undefined;
  admin: string | undefined;
  status: string | undefined;
  position: string | undefined;
  skill: string | undefined;
}

interface AvatarUploadView {
  objectKey: string;
  signedUrl: string;
}

interface PlayerProfilePayload {
  name: string;
  jersey?: number;
  region: string;
  position: number;
  secondaryPositions: number[];
  skill: number;
  avatarUrl?: string;
}

const POSITION_CODES = [0, 1, 2, 3];
const SKILL_CODES = [1, 2, 3, 4, 5];
const TYPE_OPTIONS = Object.entries(PLAYER_TYPE_TEXT).map(([value, label]) => ({
  value,
  label,
}));
const STATUS_OPTIONS = Object.entries(PLAYER_STATUS_TEXT).map(([value, label]) => ({
  value,
  label,
}));

const session = useSessionStore();
const canEdit = computed(() => hasPermission(session.permissions, PLAYER_EDIT_ANY));
const canApprove = computed(() => hasPermission(session.permissions, USER_APPROVE_ANY));

const rows = ref<PlayerAdmin[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const sort = ref('jersey:asc');
const loading = ref(false);
const error = ref('');
const failedAvatars = reactive(new Set<number>());
const tableRef = ref<TableInstance>();
const filters = reactive<PlayerFilters>(emptyFilters());
const actionBusy = ref<string | null>(null);
let requestSeq = 0;

const editDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  player: null as PlayerAdmin | null,
});
/** 弹窗代际：关闭/切换球员后，旧请求的响应不得写回新弹窗。 */
let editDialogSeq = 0;
let phoneDialogSeq = 0;
let disableDialogSeq = 0;
const editForm = reactive({
  name: '',
  jersey: null as number | null,
  region: '',
  position: 0,
  secondaryPositions: [] as number[],
  skill: 3,
});
const avatarInput = ref<HTMLInputElement | null>(null);
const avatarUploading = ref(false);
const avatarFileError = ref('');
const avatarUploadError = ref('');
const avatarObjectKey = ref<string | null>(null);
const avatarPreview = ref('');
const previewBroken = ref(false);
let localPreviewUrl: string | null = null;
/** 弹窗关闭/切换球员后，迟到的压缩或上传结果不得写回当前表单。 */
let avatarUploadSeq = 0;

const phoneDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  player: null as PlayerAdmin | null,
});
const phoneForm = reactive({ phone: '' });

const disableDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  player: null as PlayerAdmin | null,
});
const disableForm = reactive({ reason: '' });

function emptyFilters(): PlayerFilters {
  return {
    name: '',
    region: '',
    type: undefined,
    admin: undefined,
    status: undefined,
    position: undefined,
    skill: undefined,
  };
}

function avatarSrc(row: PlayerAdmin): string | null {
  return isDisplayableAvatarUrl(row.avatarUrl) ? row.avatarUrl : null;
}

function markAvatarFailed(id: number): void {
  failedAvatars.add(id);
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const query: QueryParams = {
      sort: sort.value,
      page: page.value,
      pageSize: pageSize.value,
    };
    if (filters.name.trim()) {
      query.name = filters.name.trim();
    }
    if (filters.region.trim()) {
      query.region = filters.region.trim();
    }
    if (filters.type !== undefined && filters.type !== '') {
      query.type = Number(filters.type);
    }
    if (filters.admin !== undefined && filters.admin !== '') {
      query.admin = filters.admin === 'true';
    }
    if (filters.status !== undefined && filters.status !== '') {
      query.status = Number(filters.status);
    }
    if (filters.position !== undefined && filters.position !== '') {
      query.position = Number(filters.position);
    }
    if (filters.skill !== undefined && filters.skill !== '') {
      query.skill = Number(filters.skill);
    }
    const result = await api.get<Page<PlayerAdmin>>('/players', { query });
    if (seq !== requestSeq) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
    failedAvatars.clear();
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

function search(): void {
  page.value = 1;
  void load();
}

/** 审核/转正/注销会改变账号状态；在状态筛选下回到第 1 页，避免请求越界页。 */
async function reloadAfterStatusChange(): Promise<void> {
  if (filters.status !== undefined && filters.status !== '') {
    page.value = 1;
  }
  await load();
}

function resetFilters(): void {
  Object.assign(filters, emptyFilters());
  sort.value = 'jersey:asc';
  page.value = 1;
  tableRef.value?.clearSort();
  void load();
}

function onSortChange({
  prop,
  order,
}: {
  prop: string | null;
  order: 'ascending' | 'descending' | null;
}): void {
  sort.value = playerSort(prop, order);
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

// ---------------------------------------------------------------- avatar

function revokeLocalPreview(): void {
  if (localPreviewUrl) {
    URL.revokeObjectURL(localPreviewUrl);
    localPreviewUrl = null;
  }
}

function resetAvatarState(player: PlayerAdmin | null): void {
  avatarUploadSeq += 1;
  revokeLocalPreview();
  avatarObjectKey.value = null;
  avatarPreview.value = player && isDisplayableAvatarUrl(player.avatarUrl) ? player.avatarUrl : '';
  previewBroken.value = false;
  avatarUploading.value = false;
  avatarFileError.value = '';
  avatarUploadError.value = '';
}

function pickAvatar(): void {
  if (!avatarUploading.value) {
    avatarInput.value?.click();
  }
}

async function onAvatarChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  const player = editDialog.player;
  if (!file || !player || player.userId == null) {
    return;
  }
  avatarFileError.value = '';
  avatarUploadError.value = '';
  const invalid = validateAvatarFile(file);
  if (invalid) {
    avatarFileError.value = invalid;
    return;
  }

  const uploadSeq = ++avatarUploadSeq;
  avatarUploading.value = true;
  avatarObjectKey.value = null;
  try {
    const compressed = await compressAvatar(file);
    if (uploadSeq !== avatarUploadSeq) {
      return;
    }
    revokeLocalPreview();
    localPreviewUrl = URL.createObjectURL(compressed);
    avatarPreview.value = localPreviewUrl;
    previewBroken.value = false;

    const body = new FormData();
    body.append('file', compressed);
    const uploaded = await api.postForm<AvatarUploadView>(`/players/${player.id}/avatar`, body);
    if (uploadSeq !== avatarUploadSeq) {
      return;
    }
    avatarObjectKey.value = uploaded.objectKey;
  } catch {
    if (uploadSeq !== avatarUploadSeq) {
      return;
    }
    avatarUploadError.value = AVATAR_UPLOAD_ERROR;
    revokeLocalPreview();
    avatarPreview.value = isDisplayableAvatarUrl(player.avatarUrl) ? player.avatarUrl : '';
    previewBroken.value = false;
  } finally {
    if (uploadSeq === avatarUploadSeq) {
      avatarUploading.value = false;
    }
  }
}

// ---------------------------------------------------------------- edit

function withoutPrimary(positions: readonly number[], primary: number): number[] {
  return positions.filter((position) => position !== primary);
}

function openEdit(row: PlayerAdmin): void {
  if (!canEdit.value) {
    return;
  }
  editDialogSeq += 1;
  editDialog.player = row;
  editDialog.error = '';
  editDialog.submitting = false;
  editForm.name = row.name;
  editForm.jersey = row.jersey ?? null;
  editForm.region = row.region ?? '';
  editForm.position = row.position;
  editForm.secondaryPositions = withoutPrimary(row.secondaryPositions ?? [], row.position).slice(
    0,
    3,
  );
  editForm.skill = row.skill;
  resetAvatarState(row);
  editDialog.visible = true;
}

function onPositionChange(): void {
  editForm.secondaryPositions = withoutPrimary(editForm.secondaryPositions, editForm.position);
}

async function submitEdit(): Promise<void> {
  if (editDialog.submitting || avatarUploading.value) {
    return;
  }
  const player = editDialog.player;
  if (!player) {
    return;
  }
  if (player.jersey != null && editForm.jersey === null) {
    editDialog.error = '号码不能清空；留空不会修改原号码';
    return;
  }
  const validationError = playerProfileError(editForm);
  if (validationError) {
    editDialog.error = validationError;
    return;
  }
  const seq = editDialogSeq;
  editDialog.submitting = true;
  editDialog.error = '';
  try {
    const payload: PlayerProfilePayload = {
      name: editForm.name.trim(),
      region: editForm.region.trim(),
      position: editForm.position,
      secondaryPositions: [...editForm.secondaryPositions].sort((a, b) => a - b),
      skill: editForm.skill,
    };
    if (editForm.jersey !== null) {
      payload.jersey = editForm.jersey;
    }
    if (avatarObjectKey.value) {
      payload.avatarUrl = avatarObjectKey.value;
    }
    await api.patch<PlayerAdmin>(`/players/${player.id}`, payload);
    if (seq !== editDialogSeq) {
      void load();
      return;
    }
    editDialog.visible = false;
    ElMessage.success('球员已更新');
    await load();
  } catch (caught) {
    if (seq !== editDialogSeq) {
      return;
    }
    editDialog.error =
      isApiError(caught) && caught.code === ERROR_CODES.DUPLICATE_JERSEY
        ? '正式球员号码冲突，请修改号码'
        : describeError(caught, '保存失败，请稍后重试');
  } finally {
    if (seq === editDialogSeq) {
      editDialog.submitting = false;
    }
  }
}

// ---------------------------------------------------------------- lifecycle

async function confirmAction(message: string, confirmText: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(message, '二次确认', {
      type: 'warning',
      confirmButtonText: confirmText,
      cancelButtonText: '取消',
    });
    return true;
  } catch {
    return false;
  }
}

async function approve(row: PlayerAdmin): Promise<void> {
  if (!canApprove.value || !playerApprovable(row) || actionBusy.value !== null) {
    return;
  }
  const confirmed = await confirmAction(
    `确认通过「${row.name}」的审核？通过后账号可报名与签到，角色保持外挂。`,
    '通过审核',
  );
  if (!confirmed) {
    return;
  }
  actionBusy.value = `approve:${row.id}`;
  try {
    await api.post<PlayerAdmin>(`/players/${row.id}/approve`, undefined, {
      idempotencyKey: newIdempotencyKey(),
    });
    ElMessage.success('已通过审核');
    await reloadAfterStatusChange();
  } catch (caught) {
    ElMessage.error(describeError(caught, '审核失败，请稍后重试'));
  } finally {
    actionBusy.value = null;
  }
}

async function convert(row: PlayerAdmin): Promise<void> {
  if (!canEdit.value || !playerConvertible(row) || actionBusy.value !== null) {
    return;
  }
  const confirmed = await confirmAction(
    `确认将外挂「${row.name}」转为正式球员？将移除外挂角色并授予正式球员；号码被占用时需先修改号码。`,
    '确认转正',
  );
  if (!confirmed) {
    return;
  }
  actionBusy.value = `convert:${row.id}`;
  try {
    await api.post<PlayerAdmin>(
      `/players/${row.id}/convert`,
      {},
      {
        idempotencyKey: newIdempotencyKey(),
      },
    );
    ElMessage.success('已转为正式球员');
    await reloadAfterStatusChange();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.DUPLICATE_JERSEY) {
      ElMessage.error('号码已被正式球员占用，请先在编辑中修改号码');
    } else {
      ElMessage.error(describeError(caught, '转正失败，请稍后重试'));
    }
  } finally {
    actionBusy.value = null;
  }
}

// ---------------------------------------------------------------- phone / wechat

function openPhone(row: PlayerAdmin): void {
  if (!canEdit.value || row.userId == null) {
    return;
  }
  phoneDialogSeq += 1;
  phoneDialog.player = row;
  phoneForm.phone = '';
  phoneDialog.error = '';
  phoneDialog.submitting = false;
  phoneDialog.visible = true;
}

async function submitPhone(): Promise<void> {
  if (phoneDialog.submitting) {
    return;
  }
  const player = phoneDialog.player;
  if (player?.userId == null) {
    return;
  }
  const phone = phoneForm.phone.trim();
  const invalid = phoneError(phone);
  if (invalid) {
    phoneDialog.error = invalid;
    return;
  }
  const seq = phoneDialogSeq;
  phoneDialog.submitting = true;
  phoneDialog.error = '';
  try {
    await api.patch(`/users/${player.userId}/phone`, { phone });
    if (seq !== phoneDialogSeq) {
      void load();
      return;
    }
    phoneDialog.visible = false;
    ElMessage.success('手机号已换绑');
    await load();
  } catch (caught) {
    if (seq !== phoneDialogSeq) {
      return;
    }
    phoneDialog.error =
      isApiError(caught) && caught.code === ERROR_CODES.PHONE_EXISTS
        ? '手机号已注册'
        : describeError(caught, '换绑失败，请稍后重试');
  } finally {
    if (seq === phoneDialogSeq) {
      phoneDialog.submitting = false;
    }
  }
}

async function unbindWechat(row: PlayerAdmin): Promise<void> {
  if (!canEdit.value || !row.wechatBound || row.userId == null || actionBusy.value !== null) {
    return;
  }
  const confirmed = await confirmAction(
    `确认解绑「${row.name}」的微信？解绑后需重新授权才能绑定，登录态不受影响。`,
    '解绑微信',
  );
  if (!confirmed) {
    return;
  }
  actionBusy.value = `wechat:${row.id}`;
  try {
    await api.delete(`/users/${row.userId}/wechat-binding`);
    ElMessage.success('微信已解绑');
    await load();
  } catch (caught) {
    ElMessage.error(describeError(caught, '解绑失败，请稍后重试'));
  } finally {
    actionBusy.value = null;
  }
}

// ---------------------------------------------------------------- disable

function openDisable(row: PlayerAdmin): void {
  if (!canEdit.value || !playerDisableable(row)) {
    return;
  }
  disableDialogSeq += 1;
  disableDialog.player = row;
  disableForm.reason = '';
  disableDialog.error = '';
  disableDialog.submitting = false;
  disableDialog.visible = true;
}

async function submitDisable(): Promise<void> {
  if (disableDialog.submitting) {
    return;
  }
  const player = disableDialog.player;
  if (!player) {
    return;
  }
  const reason = disableForm.reason.trim();
  if (!reason) {
    disableDialog.error = '请输入注销原因';
    return;
  }
  if (reason.length > 200) {
    disableDialog.error = '注销原因不能超过 200 字';
    return;
  }
  const seq = disableDialogSeq;
  const confirmed = await confirmAction(
    `确认注销「${player.name}」？注销不可恢复，将自动取消未开赛报名并递补。`,
    '确认注销',
  );
  if (!confirmed || seq !== disableDialogSeq) {
    return;
  }
  disableDialog.submitting = true;
  disableDialog.error = '';
  try {
    await api.post<PlayerAdmin>(
      `/players/${player.id}/disable`,
      { reason },
      { idempotencyKey: newIdempotencyKey() },
    );
    if (seq !== disableDialogSeq) {
      void load();
      return;
    }
    disableDialog.visible = false;
    ElMessage.success('球员已注销');
    await reloadAfterStatusChange();
  } catch (caught) {
    if (seq !== disableDialogSeq) {
      return;
    }
    disableDialog.error =
      isApiError(caught) && caught.code === ERROR_CODES.LAST_ADMIN_PROTECTED
        ? '不能注销最后一个管理员'
        : describeError(caught, '注销失败，请稍后重试');
  } finally {
    if (seq === disableDialogSeq) {
      disableDialog.submitting = false;
    }
  }
}

onMounted(load);
onUnmounted(() => {
  avatarUploadSeq += 1;
  revokeLocalPreview();
});
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>球员</h2>
        <p class="muted">审核、外挂转正、注销；历史数据保留。头像为空时显示姓名首字。</p>
      </div>
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

    <el-form class="player-filters" :inline="true" @submit.prevent="search">
      <el-form-item label="姓名">
        <el-input
          v-model="filters.name"
          class="filter-input"
          maxlength="32"
          placeholder="搜索球员姓名"
          clearable
        />
      </el-form-item>
      <el-form-item label="地区">
        <el-input
          v-model="filters.region"
          class="filter-input"
          maxlength="64"
          placeholder="搜索地区"
          clearable
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="filters.type" class="filter-select" placeholder="全部" clearable>
          <el-option
            v-for="option in TYPE_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="管理员">
        <el-select v-model="filters.admin" class="filter-select" placeholder="全部" clearable>
          <el-option label="是" value="true" />
          <el-option label="否" value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="filters.status" class="filter-select" placeholder="全部" clearable>
          <el-option
            v-for="option in STATUS_OPTIONS"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="位置">
        <el-select v-model="filters.position" class="filter-select" placeholder="全部" clearable>
          <el-option
            v-for="code in POSITION_CODES"
            :key="code"
            :label="POSITION_TEXT[code]"
            :value="String(code)"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="星级">
        <el-select v-model="filters.skill" class="filter-select" placeholder="全部" clearable>
          <el-option
            v-for="code in SKILL_CODES"
            :key="code"
            :label="`${code} 星`"
            :value="String(code)"
          />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :loading="loading" @click="search">搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </el-form-item>
    </el-form>

    <el-table
      ref="tableRef"
      v-loading="loading"
      :data="rows"
      empty-text="没有符合条件的球员"
      stripe
      @sort-change="onSortChange"
    >
      <el-table-column prop="jersey" label="号码" width="88" sortable="custom">
        <template #default="{ row }">{{ row.jersey ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="头像" width="72" align="center">
        <template #default="{ row }">
          <img
            v-if="avatarSrc(row) && !failedAvatars.has(row.id)"
            class="player-avatar"
            :src="avatarSrc(row) ?? ''"
            alt=""
            @error="markAvatarFailed(row.id)"
          />
          <span v-else class="player-avatar player-avatar--placeholder">
            {{ avatarPlaceholder(row.name) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="姓名" prop="name" min-width="120" />
      <el-table-column prop="region" label="地区" width="110" sortable="custom">
        <template #default="{ row }">{{ row.region || '—' }}</template>
      </el-table-column>
      <el-table-column label="类型" width="90">
        <template #default="{ row }">{{ playerTypeText(row.type) }}</template>
      </el-table-column>
      <el-table-column label="管理员" width="90">
        <template #default="{ row }">{{ row.admin ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="playerStatusTone(row.status)" effect="light">
            {{ playerStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="position" label="位置" width="90" sortable="custom">
        <template #default="{ row }">{{ positionText(row.position) }}</template>
      </el-table-column>
      <el-table-column prop="skill" label="星级" width="92" sortable="custom">
        <template #default="{ row }">{{ row.skill }}★</template>
      </el-table-column>
      <el-table-column label="微信" width="92">
        <template #default="{ row }">{{ row.wechatBound ? '已绑定' : '未绑定' }}</template>
      </el-table-column>
      <el-table-column v-if="canEdit || canApprove" label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <div class="row-actions">
            <el-button
              v-if="canEdit"
              link
              type="primary"
              size="small"
              :disabled="actionBusy !== null"
              @click="openEdit(row)"
            >
              编辑
            </el-button>
            <el-button
              v-if="canEdit && row.userId != null"
              link
              type="primary"
              size="small"
              :disabled="actionBusy !== null"
              @click="openPhone(row)"
            >
              换绑
            </el-button>
            <el-button
              v-if="canEdit && row.wechatBound"
              link
              type="primary"
              size="small"
              :disabled="actionBusy !== null"
              @click="unbindWechat(row)"
            >
              解绑微信
            </el-button>
            <el-button
              v-if="canApprove && playerApprovable(row)"
              link
              type="success"
              size="small"
              :disabled="actionBusy !== null"
              @click="approve(row)"
            >
              通过
            </el-button>
            <el-button
              v-if="canEdit && playerConvertible(row)"
              link
              type="warning"
              size="small"
              :disabled="actionBusy !== null"
              @click="convert(row)"
            >
              转正
            </el-button>
            <el-button
              v-if="canEdit && playerDisableable(row)"
              link
              type="danger"
              size="small"
              :disabled="actionBusy !== null"
              @click="openDisable(row)"
            >
              注销
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

    <el-dialog
      v-model="editDialog.visible"
      title="编辑球员"
      width="620px"
      :show-close="!editDialog.submitting"
      :close-on-click-modal="!editDialog.submitting"
      :close-on-press-escape="!editDialog.submitting"
      @closed="resetAvatarState(null)"
    >
      <div v-if="editDialog.player?.userId != null" class="avatar-editor">
        <button
          type="button"
          class="avatar-picker"
          :disabled="avatarUploading || editDialog.submitting"
          aria-label="选择头像"
          @click="pickAvatar"
        >
          <img
            v-if="avatarPreview && !previewBroken"
            :src="avatarPreview"
            alt="头像预览"
            @error="previewBroken = true"
          />
          <span v-else class="player-avatar player-avatar--large">
            {{ avatarPlaceholder(editForm.name || editDialog.player?.name) }}
          </span>
        </button>
        <input
          ref="avatarInput"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          class="visually-hidden"
          @change="onAvatarChange"
        />
        <div class="avatar-editor__meta">
          <el-button
            size="small"
            :disabled="avatarUploading || editDialog.submitting"
            @click="pickAvatar"
          >
            选择头像
          </el-button>
          <p class="muted small">JPG/PNG/WebP，不超过 2MB，自动压缩</p>
          <p v-if="avatarUploading" class="muted small">头像上传中…</p>
          <p v-if="avatarFileError" class="dialog-error">{{ avatarFileError }}</p>
          <p v-if="avatarUploadError" class="dialog-error">{{ avatarUploadError }}</p>
        </div>
      </div>
      <p v-else class="muted small">无账号球员不可上传头像。</p>

      <el-form label-width="88px">
        <el-form-item label="姓名">
          <el-input v-model="editForm.name" maxlength="32" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="号码">
          <el-input-number
            v-model="editForm.jersey"
            :min="0"
            :max="65535"
            :precision="0"
            controls-position="right"
          />
          <span class="muted small">无号码球员可留空；已有号码不可清空</span>
        </el-form-item>
        <el-form-item label="地区">
          <el-input v-model="editForm.region" maxlength="64" placeholder="如：上海" />
        </el-form-item>
        <el-form-item label="主位置">
          <el-select v-model="editForm.position" @change="onPositionChange">
            <el-option
              v-for="code in POSITION_CODES"
              :key="code"
              :label="POSITION_TEXT[code]"
              :value="code"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="副位置">
          <el-checkbox-group v-model="editForm.secondaryPositions">
            <el-checkbox
              v-for="code in POSITION_CODES"
              :key="code"
              :value="code"
              :disabled="code === editForm.position"
            >
              {{ POSITION_TEXT[code] }}
            </el-checkbox>
          </el-checkbox-group>
          <span class="muted small">最多 3 个，不含主位置</span>
        </el-form-item>
        <el-form-item label="星级">
          <el-select v-model="editForm.skill">
            <el-option
              v-for="code in SKILL_CODES"
              :key="code"
              :label="`${code} 星`"
              :value="code"
            />
          </el-select>
        </el-form-item>
      </el-form>
      <p v-if="editDialog.error" class="dialog-error">{{ editDialog.error }}</p>
      <template #footer>
        <el-button :disabled="editDialog.submitting" @click="editDialog.visible = false">
          取消
        </el-button>
        <el-button
          type="primary"
          :loading="editDialog.submitting"
          :disabled="avatarUploading"
          @click="submitEdit"
        >
          保存修改
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="phoneDialog.visible"
      title="换绑手机号"
      width="440px"
      :show-close="!phoneDialog.submitting"
      :close-on-click-modal="!phoneDialog.submitting"
      :close-on-press-escape="!phoneDialog.submitting"
    >
      <p class="muted small">
        当前账号：{{ phoneDialog.player?.name }}（{{ phoneDialog.player?.phoneMasked ?? '—' }}）
      </p>
      <el-form label-width="88px">
        <el-form-item label="新手机号">
          <el-input v-model="phoneForm.phone" maxlength="11" placeholder="请输入新手机号" />
        </el-form-item>
      </el-form>
      <p v-if="phoneDialog.error" class="dialog-error">{{ phoneDialog.error }}</p>
      <template #footer>
        <el-button :disabled="phoneDialog.submitting" @click="phoneDialog.visible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="phoneDialog.submitting" @click="submitPhone">
          确认换绑
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="disableDialog.visible"
      title="注销球员"
      width="480px"
      :show-close="!disableDialog.submitting"
      :close-on-click-modal="!disableDialog.submitting"
      :close-on-press-escape="!disableDialog.submitting"
    >
      <el-alert
        type="warning"
        :closable="false"
        show-icon
        title="注销不可恢复；未开赛报名将自动取消并递补，最后一名管理员不可注销。"
      />
      <el-form label-width="88px">
        <el-form-item label="注销原因">
          <el-input
            v-model="disableForm.reason"
            type="textarea"
            :rows="3"
            maxlength="200"
            show-word-limit
            placeholder="注销原因（将记入审计）"
          />
        </el-form-item>
      </el-form>
      <p v-if="disableDialog.error" class="dialog-error">{{ disableDialog.error }}</p>
      <template #footer>
        <el-button :disabled="disableDialog.submitting" @click="disableDialog.visible = false">
          取消
        </el-button>
        <el-button type="danger" :loading="disableDialog.submitting" @click="submitDisable">
          确认注销
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

.player-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 8px;
  margin-bottom: 8px;
}

.filter-input {
  width: 168px;
}

.filter-select {
  width: 128px;
}

.player-avatar {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  object-fit: cover;
  color: #ffffff;
  font-size: 14px;
  font-weight: 700;
  background: var(--yh-navy);
  border-radius: 50%;
}

.player-avatar--large {
  width: 72px;
  height: 72px;
  font-size: 26px;
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

.avatar-editor {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.avatar-picker {
  flex: none;
  width: 72px;
  height: 72px;
  padding: 0;
  overflow: hidden;
  background: none;
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-picker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-editor__meta p {
  margin: 4px 0 0;
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

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}
</style>
