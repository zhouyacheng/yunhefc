<script setup lang="ts">
import type { QueryParams } from '@yunhefc/api-client';
import { ERROR_CODES, isApiError, isObjectForbidden, newIdempotencyKey } from '@yunhefc/api-client';
import { ROLE_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { Page, PermissionView, Role, UserSummary } from '@yunhefc/shared';
import {
  ADMIN_ROLE_CODE,
  PLAYER_STATUS_TEXT,
  PLAYER_TYPE,
  groupPermissions,
  playerStatusText,
  playerStatusTone,
  playerTypeText,
  roleCodeError,
  roleNameError,
} from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

interface ItemsResponse<T> {
  items: T[];
}

/** ROL-07 响应：`roles` 为角色 code。 */
interface UserRolesResult {
  userId: number;
  roles: string[];
}

const GUEST_ROLE_CODE = 'GUEST';
const PLAYER_ROLE_CODE = 'PLAYER';
const SWITCH_TYPE_HINT = '外挂转正请到 B07 球员页操作';
const USER_STATUS_OPTIONS = Object.entries(PLAYER_STATUS_TEXT).map(([value, label]) => ({
  value,
  label,
}));

const session = useSessionStore();
const canManage = computed(() => hasPermission(session.permissions, ROLE_MANAGE_ANY));

const roles = ref<Role[]>([]);
const permissions = ref<PermissionView[]>([]);
const selectedRoleId = ref<number | null>(null);
const checkedPermissions = ref<string[]>([]);
const loading = ref(false);
const error = ref('');
const saving = ref(false);
let requestSeq = 0;

const userRows = ref<UserSummary[]>([]);
const userTotal = ref(0);
const userPage = ref(1);
const userPageSize = ref(10);
const keyword = ref('');
const statusFilter = ref<string | undefined>(undefined);
const usersLoading = ref(false);
const usersError = ref('');
const selectedUserId = ref<number | null>(null);
const checkedRoleIds = ref<number[]>([]);
const savingUserRoles = ref(false);
let userRequestSeq = 0;

const selectedUser = computed(
  () => userRows.value.find((user) => user.id === selectedUserId.value) ?? null,
);

const selectedRole = computed(
  () => roles.value.find((role) => role.id === selectedRoleId.value) ?? null,
);
const groups = computed(() => groupPermissions(permissions.value));
const allPermissionCodes = computed(() => permissions.value.map((permission) => permission.code));

const createDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  code: '',
  name: '',
});

const renameDialog = reactive({
  visible: false,
  submitting: false,
  error: '',
  role: null as Role | null,
  name: '',
});

function syncChecked(): void {
  checkedPermissions.value = [...(selectedRole.value?.permissions ?? [])];
}

function selectRole(role: Role): void {
  selectedRoleId.value = role.id;
  syncChecked();
}

function replaceRole(updated: Role): void {
  const index = roles.value.findIndex((role) => role.id === updated.id);
  if (index >= 0) {
    roles.value[index] = updated;
  } else {
    roles.value.push(updated);
  }
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const [roleResult, permissionResult] = await Promise.all([
      api.get<ItemsResponse<Role>>('/roles'),
      api.get<ItemsResponse<PermissionView>>('/permissions'),
    ]);
    if (seq !== requestSeq) {
      return;
    }
    roles.value = roleResult.items;
    permissions.value = permissionResult.items;
    if (!roles.value.some((role) => role.id === selectedRoleId.value)) {
      selectedRoleId.value = roles.value[0]?.id ?? null;
    }
    syncChecked();
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

/** 40401 刷新角色列表；40301 提示无权限；其余展示后端消息。 */
function reportWriteError(caught: unknown, inline?: (message: string) => void): void {
  if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
    ElMessage.warning('角色不存在，已刷新列表');
    void load();
    return;
  }
  if (isObjectForbidden(caught)) {
    const message = '无权限执行此操作';
    if (inline) {
      inline(message);
    } else {
      ElMessage.error(message);
    }
    return;
  }
  const message = isApiError(caught) ? caught.message : '操作失败，请稍后重试';
  if (inline) {
    inline(message);
  } else {
    ElMessage.error(message);
  }
}

// ---------------------------------------------------------------- permissions

async function savePermissions(): Promise<void> {
  const role = selectedRole.value;
  if (!canManage.value || !role || saving.value) {
    return;
  }
  // 06 §21：管理员始终拥有全部权限，保存时强制全量。
  const codes = role.code === ADMIN_ROLE_CODE ? allPermissionCodes.value : checkedPermissions.value;
  if (codes.length === 0) {
    ElMessage.warning('至少选择一个权限');
    return;
  }
  saving.value = true;
  try {
    const updated = await api.put<Role>(
      `/roles/${role.id}/permissions`,
      { permissions: codes },
      { idempotencyKey: newIdempotencyKey() },
    );
    replaceRole(updated);
    if (selectedRoleId.value === role.id) {
      checkedPermissions.value = [...updated.permissions];
    }
    ElMessage.success('权限已保存');
  } catch (caught) {
    reportWriteError(caught);
  } finally {
    saving.value = false;
  }
}

// ---------------------------------------------------------------- create

function openCreate(): void {
  if (!canManage.value) {
    return;
  }
  createDialog.code = '';
  createDialog.name = '';
  createDialog.error = '';
  createDialog.submitting = false;
  createDialog.visible = true;
}

async function submitCreate(): Promise<void> {
  if (!canManage.value || createDialog.submitting) {
    return;
  }
  const code = createDialog.code.trim();
  const name = createDialog.name.trim();
  createDialog.error = roleCodeError(code) ?? roleNameError(name) ?? '';
  if (createDialog.error) {
    return;
  }
  if (roles.value.some((role) => role.code === code)) {
    createDialog.error = '角色编码已存在';
    return;
  }
  createDialog.submitting = true;
  try {
    const created = await api.post<Role>('/roles', { code, name, permissions: [] });
    replaceRole(created);
    selectedRoleId.value = created.id;
    syncChecked();
    createDialog.visible = false;
    ElMessage.success('角色已创建');
  } catch (caught) {
    reportWriteError(caught, (message) => {
      createDialog.error = message;
    });
  } finally {
    createDialog.submitting = false;
  }
}

// ---------------------------------------------------------------- rename

function openRename(): void {
  const role = selectedRole.value;
  if (!canManage.value || !role) {
    return;
  }
  renameDialog.role = role;
  renameDialog.name = role.name;
  renameDialog.error = '';
  renameDialog.submitting = false;
  renameDialog.visible = true;
}

async function submitRename(): Promise<void> {
  const role = renameDialog.role;
  if (!canManage.value || !role || renameDialog.submitting) {
    return;
  }
  const name = renameDialog.name.trim();
  const nameError = roleNameError(name);
  if (nameError) {
    renameDialog.error = nameError;
    return;
  }
  renameDialog.submitting = true;
  renameDialog.error = '';
  try {
    const updated = await api.patch<Role>(`/roles/${role.id}`, { name });
    replaceRole(updated);
    renameDialog.visible = false;
    ElMessage.success('角色已重命名');
  } catch (caught) {
    reportWriteError(caught, (message) => {
      renameDialog.error = message;
    });
  } finally {
    renameDialog.submitting = false;
  }
}

// ---------------------------------------------------------------- delete

async function deleteRole(): Promise<void> {
  const role = selectedRole.value;
  if (!canManage.value || !role || role.isDefault) {
    return;
  }
  const confirmed = await ElMessageBox.confirm(`确认删除角色「${role.name}」？`, '删除角色', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
  })
    .then(() => true)
    .catch(() => false);
  if (!confirmed) {
    return;
  }
  try {
    await api.delete<null>(`/roles/${role.id}`);
    roles.value = roles.value.filter((item) => item.id !== role.id);
    selectedRoleId.value = roles.value[0]?.id ?? null;
    syncChecked();
    ElMessage.success('角色已删除');
  } catch (caught) {
    reportWriteError(caught);
  }
}

// ---------------------------------------------------------------- user roles

function roleNames(codes: readonly string[]): string {
  const names = codes.map((code) => roles.value.find((role) => role.code === code)?.name ?? code);
  return names.length ? names.join(' / ') : '—';
}

function roleIdsFor(codes: readonly string[]): number[] {
  return roles.value.filter((role) => codes.includes(role.code)).map((role) => role.id);
}

async function loadUsers(): Promise<void> {
  if (!canManage.value) {
    return;
  }
  const seq = ++userRequestSeq;
  usersLoading.value = true;
  usersError.value = '';
  try {
    const query: QueryParams = { page: userPage.value, pageSize: userPageSize.value };
    const trimmed = keyword.value.trim();
    if (trimmed) {
      query.keyword = trimmed;
    }
    if (statusFilter.value !== undefined && statusFilter.value !== '') {
      query.status = Number(statusFilter.value);
    }
    const result = await api.get<Page<UserSummary>>('/users', { query });
    if (seq !== userRequestSeq) {
      return;
    }
    userRows.value = result.items;
    userTotal.value = result.total;
    if (
      selectedUserId.value !== null &&
      !result.items.some((user) => user.id === selectedUserId.value)
    ) {
      selectedUserId.value = null;
    }
  } catch (caught) {
    if (seq !== userRequestSeq) {
      return;
    }
    usersError.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    if (seq === userRequestSeq) {
      usersLoading.value = false;
    }
  }
}

function searchUsers(): void {
  userPage.value = 1;
  void loadUsers();
}

function onUserPageChange(): void {
  void loadUsers();
}

function onUserPageSizeChange(): void {
  userPage.value = 1;
  void loadUsers();
}

function selectUser(user: UserSummary): void {
  selectedUserId.value = user.id;
  checkedRoleIds.value = roleIdsFor(user.roles);
}

/**
 * 类型一致守卫：外挂只能保留 GUEST + 自定义角色，正式只能保留 PLAYER + 自定义/ADMIN；
 * 勾到另一类型的角色视为尝试转正/转外挂，引导去 B07。
 */
function roleTypeSwitchError(user: UserSummary | null, code: string | undefined): string | null {
  if (!user || !code || user.type == null) {
    return null;
  }
  if (user.type === PLAYER_TYPE.GUEST && (code === PLAYER_ROLE_CODE || code === ADMIN_ROLE_CODE)) {
    return SWITCH_TYPE_HINT;
  }
  if (user.type === PLAYER_TYPE.PLAYER && code === GUEST_ROLE_CODE) {
    return SWITCH_TYPE_HINT;
  }
  return null;
}

function onRoleToggle(values: Array<string | number | boolean>): void {
  const next = values.map((value) => Number(value));
  const added = next.find((id) => !checkedRoleIds.value.includes(id));
  checkedRoleIds.value = next;
  if (added === undefined) {
    return;
  }
  const code = roles.value.find((role) => role.id === added)?.code;
  const message = roleTypeSwitchError(selectedUser.value, code);
  if (message) {
    checkedRoleIds.value = next.filter((id) => id !== added);
    ElMessage.warning(message);
  }
}

function validateUserRoles(): string | null {
  const user = selectedUser.value;
  if (!user) {
    return '请选择用户';
  }
  if (checkedRoleIds.value.length === 0) {
    return '至少选择一个角色';
  }
  const codes = checkedRoleIds.value
    .map((id) => roles.value.find((role) => role.id === id)?.code)
    .filter((code): code is string => Boolean(code));
  for (const code of codes) {
    const message = roleTypeSwitchError(user, code);
    if (message) {
      return message;
    }
  }
  if (codes.includes(GUEST_ROLE_CODE) && codes.includes(PLAYER_ROLE_CODE)) {
    return '外挂与正式球员角色互斥';
  }
  if (codes.includes(ADMIN_ROLE_CODE) && !codes.includes(PLAYER_ROLE_CODE)) {
    return '管理员必须叠加正式球员角色';
  }
  return null;
}

function reportUserRoleError(caught: unknown): void {
  if (isApiError(caught) && caught.code === ERROR_CODES.LAST_ADMIN_PROTECTED) {
    ElMessage.error('不能移除最后一个管理员');
    return;
  }
  if (isObjectForbidden(caught)) {
    ElMessage.error('无权限执行此操作');
    return;
  }
  if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
    ElMessage.warning('用户不存在，已刷新列表');
    void loadUsers();
    return;
  }
  ElMessage.error(isApiError(caught) ? caught.message : '操作失败，请稍后重试');
}

async function saveUserRoles(): Promise<void> {
  const user = selectedUser.value;
  if (!canManage.value || !user || savingUserRoles.value) {
    return;
  }
  const message = validateUserRoles();
  if (message) {
    ElMessage.warning(message);
    return;
  }
  savingUserRoles.value = true;
  try {
    const result = await api.put<UserRolesResult>(
      `/users/${user.id}/roles`,
      { roles: [...checkedRoleIds.value] },
      { idempotencyKey: newIdempotencyKey() },
    );
    const row = userRows.value.find((item) => item.id === user.id);
    if (row) {
      row.roles = [...result.roles];
    }
    // 保存期间可能已切换到其他用户：只更新仍处于选中状态的编辑器。
    if (selectedUserId.value === user.id) {
      checkedRoleIds.value = roleIdsFor(result.roles);
    }
    ElMessage.success('用户角色已保存');
  } catch (caught) {
    reportUserRoleError(caught);
  } finally {
    savingUserRoles.value = false;
  }
}

onMounted(() => {
  void load();
  if (canManage.value) {
    void loadUsers();
  }
});
</script>

<template>
  <section>
    <div class="page-head">
      <div>
        <h2>角色权限</h2>
        <p class="muted">默认角色可改名、不可删除；管理员角色始终拥有全部权限。</p>
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

    <div v-else v-loading="loading" class="roles-layout">
      <aside class="role-panel">
        <div class="role-panel-head">
          <h3>角色</h3>
          <el-button v-if="canManage" size="small" @click="openCreate">新建角色</el-button>
        </div>
        <el-empty v-if="!roles.length && !loading" description="暂无角色" />
        <ul v-else class="role-list">
          <li v-for="role in roles" :key="role.id">
            <button
              type="button"
              class="role-item"
              :class="{ active: role.id === selectedRoleId }"
              @click="selectRole(role)"
            >
              <span class="role-name">{{ role.name }}</span>
              <span class="role-code">{{ role.code }}</span>
              <el-tag size="small" :type="role.isDefault ? 'info' : 'success'" effect="plain">
                {{ role.isDefault ? '默认' : '自定义' }}
              </el-tag>
            </button>
          </li>
        </ul>
      </aside>

      <section class="permission-panel">
        <template v-if="selectedRole">
          <div class="permission-head">
            <h3>{{ selectedRole.name }} 的权限</h3>
            <div v-if="canManage" class="permission-actions">
              <el-button size="small" @click="openRename">重命名</el-button>
              <el-button
                v-if="!selectedRole.isDefault"
                size="small"
                type="danger"
                plain
                @click="deleteRole"
              >
                删除
              </el-button>
              <el-button size="small" type="primary" :loading="saving" @click="savePermissions">
                保存权限
              </el-button>
            </div>
            <span v-else class="muted">只读</span>
          </div>

          <p v-if="selectedRole.code === ADMIN_ROLE_CODE" class="admin-hint muted">
            管理员角色始终拥有全部权限，保存时自动全选。
          </p>

          <el-empty v-if="!groups.length" description="暂无权限数据" />

          <div v-else class="permission-groups">
            <div v-for="group in groups" :key="group.label" class="permission-group">
              <h4>{{ group.label }}</h4>
              <el-checkbox-group v-model="checkedPermissions" :disabled="!canManage">
                <el-checkbox
                  v-for="permission in group.items"
                  :key="permission.code"
                  :value="permission.code"
                  class="permission-item"
                >
                  <span class="permission-name">{{ permission.name }}</span>
                  <code>{{ permission.code }}</code>
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </div>
        </template>
        <el-empty v-else description="请选择角色" />
      </section>
    </div>

    <section v-if="canManage" class="user-roles-panel">
      <div class="user-roles-head">
        <h3>用户角色分配</h3>
        <p class="muted">至少一个角色；外挂与正式球员互斥；管理员必须叠加正式球员。</p>
      </div>

      <el-alert
        v-if="usersError"
        class="page-error"
        type="error"
        :title="usersError"
        :closable="false"
        show-icon
      >
        <el-button size="small" @click="loadUsers">重试</el-button>
      </el-alert>

      <el-form class="user-filters" :inline="true" @submit.prevent="searchUsers">
        <el-form-item label="关键词">
          <el-input
            v-model="keyword"
            class="filter-input"
            maxlength="64"
            placeholder="搜索姓名或手机号"
            clearable
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="statusFilter" class="filter-select" placeholder="全部" clearable>
            <el-option
              v-for="option in USER_STATUS_OPTIONS"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="usersLoading" @click="searchUsers">搜索</el-button>
        </el-form-item>
      </el-form>

      <el-table v-loading="usersLoading" :data="userRows" empty-text="没有符合条件的用户" stripe>
        <el-table-column prop="name" label="姓名" min-width="120" />
        <el-table-column prop="phoneMasked" label="手机号" width="130" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="playerStatusTone(row.status)" effect="light">
              {{ playerStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="当前角色" min-width="150" class-name="user-roles-cell">
          <template #default="{ row }">{{ roleNames(row.roles) }}</template>
        </el-table-column>
        <el-table-column label="球员类型" width="100">
          <template #default="{ row }">{{ playerTypeText(row.type) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!roles.length"
              @click="selectUser(row)"
            >
              分配角色
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <el-pagination
          v-model:current-page="userPage"
          v-model:page-size="userPageSize"
          background
          layout="total, sizes, prev, pager, next"
          :total="userTotal"
          :page-sizes="[10, 20, 50, 100]"
          @current-change="onUserPageChange"
          @size-change="onUserPageSizeChange"
        />
      </div>

      <div v-if="selectedUser" class="user-role-editor">
        <div class="user-role-editor-head">
          <h4>为「{{ selectedUser.name }}」分配角色</h4>
          <el-button type="primary" size="small" :loading="savingUserRoles" @click="saveUserRoles">
            保存用户角色
          </el-button>
        </div>
        <el-checkbox-group :model-value="checkedRoleIds" @change="onRoleToggle">
          <el-checkbox v-for="role in roles" :key="role.id" :value="role.id" class="user-role-item">
            {{ role.name }}
            <code>{{ role.code }}</code>
          </el-checkbox>
        </el-checkbox-group>
      </div>
      <p v-else class="muted user-role-empty">请选择用户</p>
    </section>

    <el-dialog
      v-model="createDialog.visible"
      title="新建角色"
      width="480px"
      :show-close="!createDialog.submitting"
      :close-on-click-modal="!createDialog.submitting"
      :close-on-press-escape="!createDialog.submitting"
    >
      <el-form label-width="88px" @submit.prevent="submitCreate">
        <el-form-item label="角色编码">
          <el-input v-model="createDialog.code" maxlength="32" placeholder="例如 COACH" />
        </el-form-item>
        <el-form-item label="角色名称">
          <el-input v-model="createDialog.name" maxlength="64" placeholder="例如 教练" />
        </el-form-item>
      </el-form>
      <p v-if="createDialog.error" class="dialog-error">{{ createDialog.error }}</p>
      <template #footer>
        <el-button :disabled="createDialog.submitting" @click="createDialog.visible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="createDialog.submitting" @click="submitCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="renameDialog.visible"
      title="重命名角色"
      width="480px"
      :show-close="!renameDialog.submitting"
      :close-on-click-modal="!renameDialog.submitting"
      :close-on-press-escape="!renameDialog.submitting"
    >
      <el-form label-width="88px" @submit.prevent="submitRename">
        <el-form-item label="角色名称">
          <el-input v-model="renameDialog.name" maxlength="64" placeholder="请输入角色名称" />
        </el-form-item>
      </el-form>
      <p v-if="renameDialog.error" class="dialog-error">{{ renameDialog.error }}</p>
      <template #footer>
        <el-button :disabled="renameDialog.submitting" @click="renameDialog.visible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="renameDialog.submitting" @click="submitRename">
          保存
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

.muted {
  color: var(--yh-muted);
}

.roles-layout {
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  align-items: start;
  min-height: 240px;
}

.role-panel,
.permission-panel {
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.role-panel-head,
.permission-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.role-panel-head h3,
.permission-head h3 {
  margin: 0;
  font-size: 16px;
}

.permission-actions {
  display: flex;
  gap: 8px;
}

.role-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.role-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  grid-template-areas:
    'name tag'
    'code tag';
  gap: 2px 8px;
  width: 100%;
  padding: 10px 12px;
  color: var(--yh-ink);
  text-align: left;
  cursor: pointer;
  background: transparent;
  border: 1px solid var(--yh-line);
  border-radius: 8px;
}

.role-item.active {
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary);
}

.role-name {
  grid-area: name;
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-code {
  grid-area: code;
  overflow: hidden;
  color: var(--yh-muted);
  font-size: 12px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role-item .el-tag {
  grid-area: tag;
  align-self: center;
}

.admin-hint {
  margin: 0 0 12px;
  font-size: 12px;
}

.permission-groups {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 16px;
}

.permission-group {
  padding: 12px;
  border: 1px solid var(--yh-line);
  border-radius: 8px;
}

.permission-group h4 {
  margin: 0 0 8px;
  font-size: 14px;
}

.permission-groups :deep(.el-checkbox-group) {
  display: flex;
  flex-direction: column;
}

.permission-groups :deep(.el-checkbox) {
  display: flex;
  align-items: flex-start;
  height: auto;
  margin-right: 0;
  margin-bottom: 8px;
  white-space: normal;
}

.permission-groups :deep(.el-checkbox__label) {
  white-space: normal;
  line-height: 1.4;
}

.permission-item code {
  display: block;
  color: var(--yh-muted);
  font-size: 11px;
}

.dialog-error {
  margin: 0;
  color: var(--el-color-danger);
  font-size: 13px;
}

.user-roles-panel {
  margin-top: 16px;
  padding: 16px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.user-roles-head h3 {
  margin: 0 0 4px;
  font-size: 16px;
}

.user-roles-head p {
  margin: 0 0 12px;
  font-size: 13px;
}

.user-filters {
  margin-bottom: 4px;
}

.filter-input {
  width: 220px;
}

.filter-select {
  width: 140px;
}

.table-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.user-role-editor {
  margin-top: 16px;
  padding: 12px;
  border: 1px solid var(--yh-line);
  border-radius: 8px;
}

.user-role-editor-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.user-role-editor-head h4 {
  margin: 0;
  font-size: 14px;
}

.user-role-editor :deep(.el-checkbox) {
  margin-right: 16px;
  margin-bottom: 8px;
}

.user-role-item code {
  margin-left: 4px;
  color: var(--yh-muted);
  font-size: 11px;
}

.user-role-empty {
  margin: 16px 0 0;
  font-size: 13px;
}
</style>
