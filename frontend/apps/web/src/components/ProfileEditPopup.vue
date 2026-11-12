<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { Player, PlayerPublic } from '@yunhefc/shared';
import { POSITION_TEXT } from '@yunhefc/shared';
import { Button, CellGroup, Field, Popup } from 'vant';
import { onUnmounted, reactive, ref, watch } from 'vue';

import { api } from '../api/client';
import {
  AVATAR_UPLOAD_ERROR,
  avatarPlaceholder,
  compressAvatar,
  isDisplayableAvatarUrl,
  validateAvatarFile,
} from '../features/profile/avatar';

interface TeamPlayersView {
  items: PlayerPublic[];
  total: number;
}

interface AvatarUploadView {
  objectKey: string;
  signedUrl: string;
}

interface PlayerSelfUpdate {
  name: string;
  region: string;
  position: number;
  secondaryPositions: number[];
  avatarUrl?: string;
}

const props = defineProps<{ player: Player }>();
const show = defineModel<boolean>('show', { required: true });
const emit = defineEmits<{ saved: [player: Player] }>();

const MAX_SECONDARY_POSITIONS = 3;

const form = reactive({
  name: '',
  region: '',
  position: 0,
  secondaryPositions: [] as number[],
});

/** 副位置不能与主位置重复。 */
function withoutPrimary(positions: readonly number[], primary: number): number[] {
  return positions.filter((position) => position !== primary);
}

const fileInput = ref<HTMLInputElement | null>(null);
const extraLoading = ref(false);
const extraWarning = ref('');
const extraError = ref('');
const uploading = ref(false);
const fileError = ref('');
const uploadError = ref('');
const saving = ref(false);
const saveError = ref('');
const avatarObjectKey = ref<string | null>(null);
const avatarPreview = ref('');
const previewBroken = ref(false);
let localPreviewUrl: string | null = null;
/** 关闭/重开弹窗后，迟到的压缩或上传结果不得写回当前表单。 */
let avatarUploadSeq = 0;

function revokeLocalPreview(): void {
  if (localPreviewUrl) {
    URL.revokeObjectURL(localPreviewUrl);
    localPreviewUrl = null;
  }
}

function resetState(): void {
  avatarUploadSeq += 1;
  form.name = props.player.name;
  form.region = props.player.region ?? '';
  form.position = props.player.position;
  form.secondaryPositions = withoutPrimary(
    props.player.secondaryPositions ?? [],
    props.player.position,
  );
  avatarObjectKey.value = null;
  avatarPreview.value = isDisplayableAvatarUrl(props.player.avatarUrl)
    ? props.player.avatarUrl
    : '';
  previewBroken.value = false;
  revokeLocalPreview();
  extraLoading.value = false;
  extraWarning.value = '';
  extraError.value = '';
  uploading.value = false;
  fileError.value = '';
  uploadError.value = '';
  saving.value = false;
  saveError.value = '';
}

/** MeView.player 缺 region/secondaryPositions，打开时用 PLR-01 补齐本人资料。 */
async function loadExtra(): Promise<void> {
  extraLoading.value = true;
  extraWarning.value = '';
  extraError.value = '';
  try {
    const view = await api.get<TeamPlayersView>('/team/players', { query: { all: true } });
    const self = view.items.find((item) => item.id === props.player.id);
    if (!self) {
      extraWarning.value = '未能加载完整资料，请重新填写地区与副位置';
      form.region = '';
      form.secondaryPositions = [];
      return;
    }
    form.region = self.region ?? '';
    form.secondaryPositions = withoutPrimary(self.secondaryPositions ?? [], form.position).slice(
      0,
      MAX_SECONDARY_POSITIONS,
    );
  } catch (caught) {
    extraError.value = isApiError(caught) ? caught.message : '资料加载失败，请重试';
    form.region = '';
    form.secondaryPositions = [];
  } finally {
    extraLoading.value = false;
  }
}

function onPositionChange(): void {
  form.secondaryPositions = withoutPrimary(form.secondaryPositions, form.position);
}

function toggleSecondary(position: number): void {
  if (position === form.position) {
    return;
  }
  const index = form.secondaryPositions.indexOf(position);
  if (index >= 0) {
    form.secondaryPositions.splice(index, 1);
  } else if (form.secondaryPositions.length < MAX_SECONDARY_POSITIONS) {
    form.secondaryPositions.push(position);
  }
}

function pickFile(): void {
  if (!uploading.value) {
    fileInput.value?.click();
  }
}

async function onFileChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }
  fileError.value = '';
  uploadError.value = '';
  const invalid = validateAvatarFile(file);
  if (invalid) {
    fileError.value = invalid;
    return;
  }

  const uploadSeq = ++avatarUploadSeq;
  uploading.value = true;
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
    const uploaded = await api.postForm<AvatarUploadView>('/files/avatar', body);
    if (uploadSeq !== avatarUploadSeq) {
      return;
    }
    avatarObjectKey.value = uploaded.objectKey;
    avatarPreview.value = uploaded.signedUrl;
    previewBroken.value = false;
  } catch {
    if (uploadSeq !== avatarUploadSeq) {
      return;
    }
    uploadError.value = AVATAR_UPLOAD_ERROR;
  } finally {
    if (uploadSeq === avatarUploadSeq) {
      uploading.value = false;
    }
  }
}

async function save(): Promise<void> {
  if (saving.value || uploading.value) {
    return;
  }
  saveError.value = '';

  const name = form.name.trim();
  if (!name) {
    saveError.value = '请输入姓名';
    return;
  }
  if (name.length > 32) {
    saveError.value = '姓名最多 32 个字符';
    return;
  }
  if (form.region.trim().length > 64) {
    saveError.value = '地区最多 64 个字符';
    return;
  }

  saving.value = true;
  try {
    const payload: PlayerSelfUpdate = {
      name,
      // 空字符串表示清空地区；省略/null 会被后端视为未提供。
      region: form.region.trim(),
      position: form.position,
      secondaryPositions: [...form.secondaryPositions].sort((a, b) => a - b),
    };
    if (avatarObjectKey.value) {
      payload.avatarUrl = avatarObjectKey.value;
    }
    const saved = await api.patch<Player>('/me/player', payload);
    emit('saved', saved);
    show.value = false;
  } catch (caught) {
    saveError.value = isApiError(caught) ? caught.message : '保存失败，请稍后重试';
  } finally {
    saving.value = false;
  }
}

watch(show, (visible) => {
  if (!visible) {
    revokeLocalPreview();
    return;
  }
  resetState();
  void loadExtra();
});

onUnmounted(revokeLocalPreview);
</script>

<template>
  <Popup v-model:show="show" position="bottom" :style="{ height: '100%' }">
    <div class="profile-edit">
      <header class="profile-edit__head">
        <h2>编辑资料</h2>
        <button type="button" class="profile-edit__close" @click="show = false">关闭</button>
      </header>

      <div class="profile-edit__body">
        <div class="avatar-field">
          <button
            type="button"
            class="avatar-picker"
            :disabled="uploading"
            aria-label="选择头像"
            @click="pickFile"
          >
            <img
              v-if="avatarPreview && !previewBroken"
              :src="avatarPreview"
              alt="头像预览"
              @error="previewBroken = true"
            />
            <span v-else class="avatar-picker__placeholder">
              {{ avatarPlaceholder(form.name || player.name) }}
            </span>
          </button>
          <input
            ref="fileInput"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            class="visually-hidden"
            @change="onFileChange"
          />
          <div class="avatar-field__meta">
            <p class="muted small">JPG/PNG/WebP，不超过 2MB</p>
            <p v-if="uploading" class="hint">头像上传中…</p>
            <p v-if="fileError" class="action-error">{{ fileError }}</p>
            <p v-if="uploadError" class="action-error">{{ uploadError }}</p>
          </div>
        </div>

        <CellGroup inset>
          <Field
            v-model="form.name"
            name="name"
            label="姓名"
            placeholder="请输入姓名"
            maxlength="32"
          />
          <Field
            v-model="form.region"
            name="region"
            label="地区"
            placeholder="如：上海"
            maxlength="64"
          />
        </CellGroup>

        <p v-if="extraLoading" class="hint">正在补齐地区与副位置…</p>
        <p v-if="extraWarning" class="hint">{{ extraWarning }}</p>
        <p v-if="extraError" class="action-error">
          {{ extraError }}
          <button type="button" class="inline-retry" @click="loadExtra">重试</button>
        </p>

        <label class="edit-field">
          <span>主位置</span>
          <select v-model.number="form.position" aria-label="主位置" @change="onPositionChange">
            <option v-for="(text, code) in POSITION_TEXT" :key="code" :value="Number(code)">
              {{ text }}
            </option>
          </select>
        </label>

        <div class="edit-field edit-field--column">
          <span>副位置（最多 3 个）</span>
          <div class="position-options">
            <label v-for="(text, code) in POSITION_TEXT" :key="code" class="position-option">
              <input
                type="checkbox"
                :value="Number(code)"
                :checked="form.secondaryPositions.includes(Number(code))"
                :disabled="Number(code) === form.position"
                @change="toggleSecondary(Number(code))"
              />
              {{ text }}
            </label>
          </div>
        </div>

        <p v-if="saveError" class="action-error">{{ saveError }}</p>

        <Button
          type="primary"
          block
          :loading="saving"
          :disabled="saving || uploading || extraLoading"
          @click="save"
        >
          保存
        </Button>
      </div>
    </div>
  </Popup>
</template>

<style scoped>
.profile-edit {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--yh-bg);
}

.profile-edit__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  background: var(--yh-surface);
  border-bottom: 1px solid var(--yh-line);
}

.profile-edit__head h2 {
  margin: 0;
  font-size: 18px;
}

.profile-edit__close {
  padding: 0;
  color: var(--yh-muted);
  font: inherit;
  font-size: 14px;
  background: none;
  border: 0;
  cursor: pointer;
}

.profile-edit__body {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 14px;
  padding: 18px 18px 28px;
  overflow-y: auto;
}

.avatar-field {
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-picker {
  flex: none;
  width: 72px;
  height: 72px;
  padding: 0;
  overflow: hidden;
  background: var(--yh-navy);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.avatar-picker img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-picker__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--yh-gold);
  font-size: 26px;
  font-weight: 800;
}

.avatar-field__meta {
  min-width: 0;
}

.avatar-field__meta p {
  margin: 2px 0;
}

.visually-hidden {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0 0 0 0);
  white-space: nowrap;
}

.edit-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 14px;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 12px;
}

.edit-field > span {
  font-size: 14px;
}

.edit-field select {
  padding: 6px 10px;
  font: inherit;
  font-size: 14px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 8px;
}

.edit-field--column {
  flex-direction: column;
  align-items: stretch;
}

.position-options {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}

.position-option {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
}

.inline-retry {
  padding: 2px 8px;
  color: var(--yh-green);
  font: inherit;
  font-size: 12px;
  background: none;
  border: 1px solid var(--yh-green);
  border-radius: 6px;
  cursor: pointer;
}
</style>
