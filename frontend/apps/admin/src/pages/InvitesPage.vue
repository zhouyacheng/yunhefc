<script setup lang="ts">
import { ERROR_CODES, isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { INVITE_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { Invite, InviteCreated, InviteType, MatchSummary, Page } from '@yunhefc/shared';
import {
  formatDateTime,
  inviteMatchTitle,
  inviteQuotaText,
  inviteStatusText,
  inviteStatusTone,
  inviteTypeText,
  matchStatusText,
} from '@yunhefc/shared';
import type { InputInstance } from 'element-plus';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave } from 'vue-router';

import { api, publicApi } from '../api/client';
import { useSessionStore } from '../stores/session';

/** 比赛选择器只保留仍可报名的状态：报名中/报名截止/已锁定。 */
const INVITABLE_MATCH_STATUSES = new Set([0, 1, 2]);

const session = useSessionStore();
const canManage = computed(() => hasPermission(session.permissions, INVITE_MANAGE_ANY));

const rows = ref<Invite[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(10);
const loading = ref(false);
const error = ref('');
let requestSeq = 0;

// 列表停留期间也按当前时间重算过期状态，无需等下一次加载。
const now = ref(Date.now());
let clockTimer: ReturnType<typeof setInterval> | null = null;

const createDialog = reactive({
  visible: false,
  submitting: false,
  /** 上一次提交结果未知（网络中断/5xx）：必须先重试原请求或明确放弃。 */
  retryable: false,
  error: '',
  type: 0 as InviteType,
  matchId: null as number | null,
  expiresInDays: 7,
  maxUses: null as number | null,
});
const createDialogLocked = computed(() => createDialog.submitting || createDialog.retryable);

interface CreateInvitePayload {
  type: InviteType;
  matchId?: number | null;
  expiresInDays: number;
  maxUses: number | null;
}

/** 网络超时后重试沿用同一幂等键与请求体，命中服务端缓存即可取回 inviteUrl。 */
let createSubmitKey: string | null = null;
let createSubmitPayload: CreateInvitePayload | null = null;

const matchOptions = ref<MatchSummary[]>([]);
const matchesLoading = ref(false);
const matchesError = ref('');
let matchesLoaded = false;

const createdDialog = reactive({
  visible: false,
  inviteUrl: '',
  expiresAt: '',
  copied: false,
  copyFailed: false,
});
const urlInput = ref<InputInstance | null>(null);

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<Page<Invite>>('/invites', {
      query: { page: page.value, pageSize: pageSize.value },
    });
    if (seq !== requestSeq) {
      return;
    }
    rows.value = result.items;
    total.value = result.total;
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

function onPageChange(): void {
  void load();
}

function onSizeChange(): void {
  page.value = 1;
  void load();
}

function openCreate(): void {
  if (!canManage.value) {
    return;
  }
  createDialog.visible = true;
  createDialog.submitting = false;
  createDialog.retryable = false;
  createDialog.error = '';
  createDialog.type = 0;
  createDialog.matchId = null;
  createDialog.expiresInDays = 7;
  createDialog.maxUses = null;
  createSubmitKey = newIdempotencyKey();
  createSubmitPayload = null;
}

async function loadMatches(force = false): Promise<void> {
  if (matchesLoading.value || (matchesLoaded && !force)) {
    return;
  }
  matchesLoading.value = true;
  matchesError.value = '';
  try {
    const result = await publicApi.get<Page<MatchSummary>>('/matches', {
      query: { scope: 'upcoming', pageSize: 100, sort: 'startAt:asc' },
    });
    matchOptions.value = result.items.filter((match) => INVITABLE_MATCH_STATUSES.has(match.status));
    matchesLoaded = true;
  } catch (caught) {
    matchesError.value = isApiError(caught) ? caught.message : '比赛列表加载失败';
  } finally {
    matchesLoading.value = false;
  }
}

watch(
  () => createDialog.type,
  (type) => {
    if (type === 1) {
      void loadMatches();
    }
  },
);

async function submitCreate(): Promise<void> {
  if (!canManage.value || createDialog.submitting) {
    return;
  }
  createDialog.error = '';
  let payload: CreateInvitePayload;
  if (createDialog.retryable && createSubmitPayload !== null) {
    // 结果未知时冻结表单，只允许原样重试，命中服务端缓存即可取回链接。
    payload = createSubmitPayload;
  } else {
    if (createDialog.type === 1 && createDialog.matchId === null) {
      createDialog.error = '请选择比赛';
      return;
    }
    const days = Number(createDialog.expiresInDays);
    if (!Number.isInteger(days) || days < 1 || days > 30) {
      createDialog.error = '有效期必须为 1–30 天';
      return;
    }
    const maxUses = createDialog.maxUses === null ? null : Number(createDialog.maxUses);
    if (maxUses !== null && (!Number.isInteger(maxUses) || maxUses < 1)) {
      createDialog.error = '使用次数上限必须大于 0';
      return;
    }
    payload = {
      type: createDialog.type,
      matchId: createDialog.type === 1 ? createDialog.matchId : undefined,
      expiresInDays: days,
      maxUses,
    };
  }
  createDialog.submitting = true;
  createSubmitPayload = payload;
  createSubmitKey ??= newIdempotencyKey();
  try {
    const created = await api.post<InviteCreated>('/invites', payload, {
      idempotencyKey: createSubmitKey,
    });
    createSubmitKey = null;
    createSubmitPayload = null;
    createDialog.retryable = false;
    createDialog.visible = false;
    createdDialog.inviteUrl = created.inviteUrl;
    createdDialog.expiresAt = created.expiresAt;
    createdDialog.copied = false;
    createdDialog.copyFailed = false;
    createdDialog.visible = true;
    void load();
  } catch (caught) {
    if (isUncertainCreateFailure(caught)) {
      createDialog.retryable = true;
      createDialog.error = '创建结果未知，请点击「重试创建」取回链接；修改表单前请先重试或取消。';
    } else {
      // 服务端已明确拒绝（4xx），不会留下邀请，换新键后允许修改重提。
      createDialog.retryable = false;
      createSubmitKey = null;
      createSubmitPayload = null;
      createDialog.error = isApiError(caught) ? caught.message : '创建失败，请稍后重试';
    }
  } finally {
    createDialog.submitting = false;
  }
}

/** 网络中断、超时、5xx 与占位进行中都可能“已落库但响应丢失”。 */
function isUncertainCreateFailure(caught: unknown): boolean {
  if (!isApiError(caught)) {
    return true;
  }
  return (
    caught.status === 0 ||
    caught.status >= 500 ||
    caught.code === ERROR_CODES.IDEMPOTENCY_IN_PROGRESS
  );
}

async function cancelCreate(): Promise<void> {
  if (createDialog.submitting) {
    return;
  }
  if (createDialog.retryable) {
    const discard = await ElMessageBox.confirm(
      '上一次创建结果未知，关闭后将无法取回邀请链接。确定放弃？',
      '放弃创建',
      { type: 'warning', confirmButtonText: '放弃', cancelButtonText: '继续重试' },
    )
      .then(() => true)
      .catch(() => false);
    if (!discard) {
      return;
    }
  }
  createDialog.visible = false;
  createDialog.retryable = false;
  createSubmitKey = null;
  createSubmitPayload = null;
}

async function copyInviteUrl(): Promise<void> {
  createdDialog.copied = false;
  createdDialog.copyFailed = false;
  try {
    await navigator.clipboard.writeText(createdDialog.inviteUrl);
    createdDialog.copied = true;
  } catch {
    // 非安全上下文或权限被拒时退化为手动复制。
    createdDialog.copyFailed = true;
    urlInput.value?.select();
  }
}

async function revokeInvite(invite: Invite): Promise<void> {
  if (!canManage.value) {
    return;
  }
  const confirmed = await ElMessageBox.confirm('撤销后邀请链接立即失效，确认撤销？', '撤销邀请', {
    type: 'warning',
    confirmButtonText: '撤销',
    cancelButtonText: '取消',
  })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await api.post<{ status: number }>(`/invites/${invite.id}/revoke`, undefined, {
      idempotencyKey: newIdempotencyKey(),
    });
    ElMessage.success('邀请已撤销');
    void load();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      ElMessage.warning('邀请不存在，已刷新列表');
      void load();
      return;
    }
    ElMessage.error(isApiError(caught) ? caught.message : '撤销失败，请稍后重试');
  }
}

function handleBeforeUnload(event: BeforeUnloadEvent): void {
  event.preventDefault();
  event.returnValue = '';
}

// 创建请求在途时离开页面会丢失唯一一次展示的 inviteUrl，先让用户确认。
onBeforeRouteLeave(() => {
  if (!createDialog.submitting) {
    return true;
  }
  return window.confirm('邀请正在创建，离开后将无法取回邀请链接。确定离开？');
});

watch(
  () => createDialog.submitting,
  (submitting) => {
    if (submitting) {
      window.addEventListener('beforeunload', handleBeforeUnload);
    } else {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    }
  },
);

onMounted(() => {
  void load();
  clockTimer = setInterval(() => {
    now.value = Date.now();
  }, 60_000);
});

onUnmounted(() => {
  if (clockTimer !== null) {
    clearInterval(clockTimer);
  }
  window.removeEventListener('beforeunload', handleBeforeUnload);
});
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>邀请管理</h2>
        <p class="muted">创建入队或比赛邀请；链接仅在创建成功时展示一次，列表不提供复制。</p>
      </div>
      <el-button v-if="canManage" type="primary" @click="openCreate">新建邀请</el-button>
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

    <template v-else>
      <el-table v-loading="loading" :data="rows" empty-text="暂无邀请" stripe>
        <el-table-column label="类型" width="110">
          <template #default="{ row }">{{ inviteTypeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="关联比赛" min-width="180">
          <template #default="{ row }">{{ inviteMatchTitle(row) }}</template>
        </el-table-column>
        <el-table-column label="邀请人" width="130">
          <template #default="{ row }">{{ row.inviter?.name ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="有效期" width="170">
          <template #default="{ row }">{{ formatDateTime(row.expiresAt) }}</template>
        </el-table-column>
        <el-table-column label="已用/上限" width="110">
          <template #default="{ row }">{{ inviteQuotaText(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="inviteStatusTone(row, now)" effect="light">
              {{ inviteStatusText(row, now) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="100">
          <template #default="{ row }">
            <el-button
              link
              type="danger"
              size="small"
              :disabled="row.status === 1"
              @click="revokeInvite(row)"
            >
              撤销
            </el-button>
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
    </template>

    <el-dialog
      v-model="createDialog.visible"
      title="新建邀请"
      width="520px"
      :show-close="!createDialogLocked"
      :close-on-click-modal="!createDialogLocked"
      :close-on-press-escape="!createDialogLocked"
    >
      <el-form label-width="96px" @submit.prevent="submitCreate">
        <el-form-item label="邀请类型">
          <el-radio-group v-model="createDialog.type" :disabled="createDialog.retryable">
            <el-radio :value="0">入队邀请</el-radio>
            <el-radio :value="1">比赛邀请</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="createDialog.type === 1" label="关联比赛">
          <el-select
            v-model="createDialog.matchId"
            class="full-width"
            filterable
            placeholder="请选择比赛"
            :loading="matchesLoading"
            :disabled="createDialog.retryable"
            no-data-text="暂无可邀请的比赛"
          >
            <el-option
              v-for="match in matchOptions"
              :key="match.id"
              :label="`${match.title} · ${matchStatusText(match.status)}`"
              :value="match.id"
            />
          </el-select>
          <p v-if="matchesError" class="dialog-error">
            {{ matchesError }}
            <el-button link type="primary" size="small" @click="loadMatches(true)">重试</el-button>
          </p>
        </el-form-item>
        <el-form-item label="有效期">
          <el-input-number
            v-model="createDialog.expiresInDays"
            :min="1"
            :max="30"
            :precision="0"
            :disabled="createDialog.retryable"
            controls-position="right"
          />
          <span class="form-suffix">天</span>
        </el-form-item>
        <el-form-item label="次数上限">
          <el-input-number
            v-model="createDialog.maxUses"
            :min="1"
            :precision="0"
            :value-on-clear="null"
            :disabled="createDialog.retryable"
            controls-position="right"
            placeholder="留空不限"
          />
          <span class="form-suffix">留空为不限</span>
        </el-form-item>
      </el-form>
      <p v-if="createDialog.error" class="dialog-error">{{ createDialog.error }}</p>
      <template #footer>
        <el-button :disabled="createDialog.submitting" @click="cancelCreate">取消</el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitCreate">
          {{ createDialog.retryable ? '重试创建' : '创建邀请' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="createdDialog.visible"
      title="邀请已创建"
      width="560px"
      :show-close="false"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
    >
      <p class="muted">链接仅展示一次，请立即复制并发送给受邀人。</p>
      <div class="invite-url">
        <el-input ref="urlInput" :model-value="createdDialog.inviteUrl" readonly />
        <el-button type="primary" @click="copyInviteUrl">
          {{ createdDialog.copied ? '已复制' : '复制链接' }}
        </el-button>
      </div>
      <p v-if="createdDialog.copyFailed" class="dialog-error">复制失败，请手动选中上方链接复制。</p>
      <p class="muted">有效期至 {{ formatDateTime(createdDialog.expiresAt) }}</p>
      <template #footer>
        <el-button type="primary" @click="createdDialog.visible = false">关闭</el-button>
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

.muted {
  color: var(--yh-muted);
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.full-width {
  width: 100%;
}

.form-suffix {
  margin-left: 8px;
  color: var(--yh-muted);
  font-size: 13px;
}

.invite-url {
  display: flex;
  gap: 8px;
  align-items: center;
}

.dialog-error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}
</style>
