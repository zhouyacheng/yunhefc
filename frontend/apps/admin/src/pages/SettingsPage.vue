<script setup lang="ts">
import { isApiError, newIdempotencyKey } from '@yunhefc/api-client';
import { SETTINGS_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { SettingsErrors, SettingsForm, SystemSettings } from '@yunhefc/shared';
import {
  SETTINGS_FIELD_RULES,
  SETTINGS_GROUPING_KEYS,
  SETTINGS_KEYS,
  SETTINGS_REGISTRATION_KEYS,
  settingsErrors,
  toSettingsPayload,
} from '@yunhefc/shared';
import { ElMessage } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

interface SettingsResponse {
  settings: SystemSettings;
}

const session = useSessionStore();
const canManage = computed(() => hasPermission(session.permissions, SETTINGS_MANAGE_ANY));

function emptyForm(): SettingsForm {
  return Object.fromEntries(SETTINGS_KEYS.map((key) => [key, null])) as SettingsForm;
}

const form = reactive<SettingsForm>(emptyForm());
const errors = ref<SettingsErrors>({});
const loading = ref(false);
const saving = ref(false);
const error = ref('');
let requestSeq = 0;

function applySettings(settings: SystemSettings): void {
  for (const key of SETTINGS_KEYS) {
    form[key] = settings[key];
  }
  errors.value = {};
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<SettingsResponse>('/settings');
    if (seq === requestSeq) {
      applySettings(result.settings);
    }
  } catch (caught) {
    if (seq === requestSeq) {
      error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
    }
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function save(): Promise<void> {
  if (!canManage.value || saving.value) {
    return;
  }
  const nextErrors = settingsErrors(form);
  errors.value = nextErrors;
  if (Object.keys(nextErrors).length > 0) {
    ElMessage.warning('请先修正标红的设置项');
    return;
  }

  saving.value = true;
  try {
    const result = await api.patch<SettingsResponse>(
      '/settings',
      { settings: toSettingsPayload(form) },
      { idempotencyKey: newIdempotencyKey() },
    );
    applySettings(result.settings);
    ElMessage.success('设置已保存');
  } catch (caught) {
    ElMessage.error(isApiError(caught) ? caught.message : '保存失败，请稍后重试');
  } finally {
    saving.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="settings-page">
    <div class="page-head">
      <div>
        <h2>系统设置</h2>
        <p class="muted">报名默认参数；新建比赛时自动带入，可在比赛里覆盖。</p>
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

    <el-form
      v-else
      v-loading="loading"
      class="settings-form"
      label-width="160px"
      @submit.prevent="save"
    >
      <el-card header="报名默认值" class="settings-card">
        <el-form-item
          v-for="key in SETTINGS_REGISTRATION_KEYS"
          :key="key"
          :label="SETTINGS_FIELD_RULES[key].label"
          :error="errors[key]"
        >
          <el-input-number
            v-model="form[key]"
            :min="SETTINGS_FIELD_RULES[key].min"
            :max="SETTINGS_FIELD_RULES[key].max"
            :precision="0"
            :step="1"
            :disabled="!canManage"
            controls-position="right"
          />
        </el-form-item>
      </el-card>

      <el-card header="分组参数" class="settings-card">
        <el-form-item
          v-for="key in SETTINGS_GROUPING_KEYS"
          :key="key"
          :label="SETTINGS_FIELD_RULES[key].label"
          :error="errors[key]"
        >
          <el-input-number
            v-model="form[key]"
            :min="SETTINGS_FIELD_RULES[key].min"
            :max="SETTINGS_FIELD_RULES[key].max"
            :precision="0"
            :step="1"
            :disabled="!canManage"
            controls-position="right"
          />
        </el-form-item>
        <p class="muted small">手动与自动权重合计须为 100；出场率、进球、助攻权重合计须大于 0。</p>
      </el-card>

      <p class="muted small boundary">
        修改只影响新建比赛与后续分组；已创建比赛参数不变，已保存分组分数不回算，权重标签展示当前有效配置。
      </p>

      <div class="settings-actions">
        <el-button type="primary" :disabled="!canManage || loading" :loading="saving" @click="save">
          保存设置
        </el-button>
        <span v-if="!canManage" class="muted small">只读：没有管理系统设置的权限。</span>
      </div>
    </el-form>
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

.settings-form {
  max-width: 640px;
}

.settings-card + .settings-card {
  margin-top: 16px;
}

.boundary {
  margin: 16px 0 0;
}

.settings-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 16px;
}

.muted {
  color: var(--yh-muted);
}

.small {
  font-size: 12px;
}
</style>
