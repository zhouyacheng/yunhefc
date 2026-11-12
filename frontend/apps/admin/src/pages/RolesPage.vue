<script setup lang="ts">
import { ERROR_CODES, isApiError, isObjectForbidden, newIdempotencyKey } from '@yunhefc/api-client';
import { ROLE_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { PermissionView, Role } from '@yunhefc/shared';
import { ADMIN_ROLE_CODE, groupPermissions, roleCodeError, roleNameError } from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

interface ItemsResponse<T> {
  items: T[];
}

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

onMounted(load);
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
</style>
