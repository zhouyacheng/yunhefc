<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import { LINEUP_EDIT_ANY, hasPermission } from '@yunhefc/permissions';
import type {
  FormationTemplate,
  FormationTemplateCreateRequest,
  FormationTemplateListResponse,
  FormationTemplateRenameRequest,
  FormationTemplateResponse,
} from '@yunhefc/shared';
import { positionText } from '@yunhefc/shared';
import { ElMessage, ElMessageBox } from 'element-plus';
import { computed, onMounted, reactive, ref } from 'vue';

import { api } from '../api/client';
import { useSessionStore } from '../stores/session';

const session = useSessionStore();
const templates = ref<FormationTemplate[]>([]);
const loading = ref(false);
const error = ref('');
const canEdit = computed(() => hasPermission(session.permissions, LINEUP_EDIT_ANY));
const presets = computed(() => templates.value.filter((template) => template.isPreset));
const customs = computed(() => templates.value.filter((template) => !template.isPreset));

const copyDialog = reactive({
  visible: false,
  baseId: 0,
  baseName: '',
  name: '',
  saving: false,
  error: '',
});
const renameDialog = reactive({
  visible: false,
  id: 0,
  name: '',
  saving: false,
  error: '',
});

function slotSummary(template: FormationTemplate): string {
  return [0, 1, 2, 3]
    .map((role) => ({ role, count: template.slots.filter((slot) => slot.role === role).length }))
    .filter((item) => item.count > 0)
    .map((item) => `${positionText(item.role)} ${item.count}`)
    .join(' · ');
}

async function load(): Promise<void> {
  loading.value = true;
  error.value = '';
  try {
    const result = await api.get<FormationTemplateListResponse>('/formation-templates', {
      query: { includePreset: true },
    });
    templates.value = result.items;
  } catch (caught) {
    templates.value = [];
    error.value = isApiError(caught) ? caught.message : '阵型模板加载失败，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function openCopy(base: FormationTemplate): void {
  copyDialog.baseId = base.id;
  copyDialog.baseName = base.name;
  copyDialog.name = `${base.name} 变体`;
  copyDialog.error = '';
  copyDialog.visible = true;
}

async function submitCopy(): Promise<void> {
  if (copyDialog.saving) {
    return;
  }
  const name = copyDialog.name.trim();
  if (!name) {
    copyDialog.error = '请输入模板名称';
    return;
  }
  copyDialog.saving = true;
  copyDialog.error = '';
  try {
    const request: FormationTemplateCreateRequest = {
      baseTemplateId: copyDialog.baseId,
      name,
    };
    await api.post<FormationTemplateResponse>('/formation-templates', request);
    copyDialog.visible = false;
    ElMessage.success('阵型模板已创建');
    await load();
  } catch (caught) {
    copyDialog.error = isApiError(caught) ? caught.message : '创建模板失败，请稍后重试';
  } finally {
    copyDialog.saving = false;
  }
}

function openRename(template: FormationTemplate): void {
  renameDialog.id = template.id;
  renameDialog.name = template.name;
  renameDialog.error = '';
  renameDialog.visible = true;
}

async function submitRename(): Promise<void> {
  if (renameDialog.saving) {
    return;
  }
  const name = renameDialog.name.trim();
  if (!name) {
    renameDialog.error = '请输入模板名称';
    return;
  }
  renameDialog.saving = true;
  renameDialog.error = '';
  try {
    const request: FormationTemplateRenameRequest = { name };
    await api.patch<FormationTemplateResponse>(
      `/formation-templates/${renameDialog.id}`,
      request,
    );
    renameDialog.visible = false;
    ElMessage.success('阵型模板已重命名');
    await load();
  } catch (caught) {
    renameDialog.error = isApiError(caught) ? caught.message : '重命名失败，请稍后重试';
  } finally {
    renameDialog.saving = false;
  }
}

async function removeTemplate(template: FormationTemplate): Promise<void> {
  try {
    await ElMessageBox.confirm(
      `删除自定义模板“${template.name}”？历史阵容仍保留该模板引用。`,
      '删除阵型模板',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }
  try {
    await api.delete(`/formation-templates/${template.id}`);
    ElMessage.success('阵型模板已删除');
    await load();
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      ElMessage.warning('模板已被删除，列表已刷新');
      await load();
      return;
    }
    ElMessage.error(isApiError(caught) ? caught.message : '删除失败，请稍后重试');
  }
}

onMounted(() => {
  if (canEdit.value) {
    void load();
  }
});
</script>

<template>
  <section class="lineup-page">
    <div class="page-head">
      <div>
        <h2>阵容</h2>
        <p class="muted">阵型模板管理；预置阵型只读，自定义模板由预置复制后命名。</p>
      </div>
      <el-button :disabled="!canEdit || loading" @click="load">刷新</el-button>
    </div>

    <el-alert
      v-if="!canEdit"
      type="info"
      :closable="false"
      show-icon
      title="当前账号没有阵型模板编辑权限，只能查看本页说明。"
    />
    <el-alert v-else-if="error" type="error" :title="error" :closable="false" show-icon>
      <el-button size="small" @click="load">重试</el-button>
    </el-alert>

    <div v-if="canEdit" v-loading="loading" class="template-body">
      <section class="template-section">
        <header class="section-head">
          <h3>预置阵型</h3>
          <span class="muted small">{{ presets.length }} 套 · 只读</span>
        </header>
        <div v-if="presets.length" class="template-grid">
          <article
            v-for="template in presets"
            :key="template.id"
            class="template-card template-card--preset"
          >
            <header class="card-head">
              <strong>{{ template.name }}</strong>
              <el-tag size="small" type="info" effect="plain">预置</el-tag>
            </header>
            <div class="slot-preview" aria-hidden="true">
              <span
                v-for="slot in template.slots"
                :key="slot.slotKey"
                class="slot-dot"
                :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
              />
            </div>
            <p class="slot-summary">{{ slotSummary(template) }}</p>
            <div class="template-actions">
              <el-button type="primary" plain size="small" @click="openCopy(template)">
                复制并新建
              </el-button>
              <el-tooltip content="预置阵型不可重命名或删除，请复制为自定义模板后修改">
                <span class="preset-locked">
                  <el-button size="small" disabled>重命名</el-button>
                  <el-button size="small" disabled>删除</el-button>
                </span>
              </el-tooltip>
            </div>
            <p class="muted small preset-note">预置阵型只读，可复制后修改。</p>
          </article>
        </div>
        <el-empty v-else-if="!loading && !error" description="暂无预置阵型" />
      </section>

      <section class="template-section">
        <header class="section-head">
          <h3>自定义模板</h3>
          <span class="muted small">{{ customs.length }} 个</span>
        </header>
        <div v-if="customs.length" class="template-grid">
          <article
            v-for="template in customs"
            :key="template.id"
            class="template-card template-card--custom"
          >
            <header class="card-head">
              <strong>{{ template.name }}</strong>
              <el-tag size="small" type="success" effect="plain">自定义</el-tag>
            </header>
            <div class="slot-preview" aria-hidden="true">
              <span
                v-for="slot in template.slots"
                :key="slot.slotKey"
                class="slot-dot"
                :style="{ left: `${slot.x}%`, top: `${slot.y}%` }"
              />
            </div>
            <p class="slot-summary">{{ slotSummary(template) }}</p>
            <div class="template-actions">
              <el-button size="small" @click="openRename(template)">重命名</el-button>
              <el-button size="small" type="danger" plain @click="removeTemplate(template)">
                删除
              </el-button>
            </div>
          </article>
        </div>
        <el-empty
          v-else-if="!loading && !error"
          description="暂无自定义模板，可从预置阵型复制新建"
        />
      </section>
    </div>

    <el-dialog v-model="copyDialog.visible" title="复制预置阵型" width="420px">
      <p class="muted small">来源：{{ copyDialog.baseName }}</p>
      <el-input
        v-model="copyDialog.name"
        maxlength="64"
        show-word-limit
        placeholder="模板名称"
        @keyup.enter="submitCopy"
      />
      <p v-if="copyDialog.error" class="dialog-error">{{ copyDialog.error }}</p>
      <template #footer>
        <el-button @click="copyDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="copyDialog.saving" @click="submitCopy">
          创建模板
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="renameDialog.visible" title="重命名自定义模板" width="420px">
      <el-input
        v-model="renameDialog.name"
        maxlength="64"
        show-word-limit
        placeholder="新的模板名称"
        @keyup.enter="submitRename"
      />
      <p v-if="renameDialog.error" class="dialog-error">{{ renameDialog.error }}</p>
      <template #footer>
        <el-button @click="renameDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="renameDialog.saving" @click="submitRename">
          保存名称
        </el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.lineup-page {
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

.template-body {
  display: flex;
  flex-direction: column;
  gap: 20px;
  min-height: 160px;
}

.template-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.section-head h3 {
  margin: 0;
  font-size: 15px;
}

.template-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(230px, 1fr));
  gap: 12px;
}

.template-card {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.template-card--custom {
  border-color: #b7dccb;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.card-head strong {
  font-size: 15px;
}

.slot-preview {
  position: relative;
  height: 110px;
  overflow: hidden;
  background:
    linear-gradient(0deg, rgba(255, 255, 255, 0.08) 1px, transparent 1px) 0 0 / 100% 25%,
    linear-gradient(90deg, #166534, #15803d);
  border-radius: 9px;
}

.slot-preview::after {
  position: absolute;
  right: 8%;
  left: 8%;
  height: 1px;
  content: '';
  background: rgba(255, 255, 255, 0.4);
  top: 50%;
}

.slot-dot {
  position: absolute;
  z-index: 1;
  width: 10px;
  height: 10px;
  background: #f8fafc;
  border: 2px solid #0f172a;
  border-radius: 50%;
  transform: translate(-50%, -50%);
}

.slot-summary {
  margin: 0;
  color: var(--yh-muted);
  font-size: 12px;
}

.template-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.template-actions .el-button + .el-button {
  margin-left: 0;
}

.preset-locked {
  display: inline-flex;
  gap: 8px;
}

.preset-note {
  margin: 0;
}

.muted {
  color: var(--yh-muted);
}

.small {
  font-size: 12px;
}

.dialog-error {
  margin: 8px 0 0;
  color: var(--el-color-danger);
  font-size: 12px;
}

@media (max-width: 720px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
