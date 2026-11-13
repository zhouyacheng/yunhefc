<script setup lang="ts">
import { Button, Popup } from 'vant';
import { computed, ref, watch } from 'vue';

import {
  cancelReasonError,
  isoToShanghaiLocal,
  postponeFormError,
  shanghaiLocalToIso,
} from './status-options';

export type StatusDialogMode = 'postpone' | 'cancel';

export type StatusDialogPayload = { startAt: string; endAt: string } | { reason: string };

const props = defineProps<{
  show: boolean;
  mode: StatusDialogMode;
  initialStartAt: string;
  initialEndAt: string;
  submitting: boolean;
  error: string;
}>();

const emit = defineEmits<{
  'update:show': [value: boolean];
  submit: [payload: StatusDialogPayload];
}>();

const startAt = ref('');
const endAt = ref('');
const reason = ref('');
const formError = ref('');

const title = computed(() => (props.mode === 'postpone' ? '延期比赛' : '取消比赛'));
const submitLabel = computed(() => (props.mode === 'postpone' ? '确认延期' : '确认取消'));

watch(
  () => [props.show, props.mode, props.initialStartAt, props.initialEndAt] as const,
  ([show]) => {
    if (!show) {
      return;
    }
    startAt.value = isoToShanghaiLocal(props.initialStartAt);
    endAt.value = isoToShanghaiLocal(props.initialEndAt);
    reason.value = '';
    formError.value = '';
  },
  { immediate: true },
);

function close(): void {
  emit('update:show', false);
}

function submit(): void {
  if (props.submitting) {
    return;
  }
  if (props.mode === 'postpone') {
    const message = postponeFormError(startAt.value, endAt.value);
    if (message) {
      formError.value = message;
      return;
    }
    const start = shanghaiLocalToIso(startAt.value);
    const end = shanghaiLocalToIso(endAt.value);
    if (!start || !end) {
      formError.value = '请选择开始与结束时间';
      return;
    }
    formError.value = '';
    emit('submit', { startAt: start, endAt: end });
    return;
  }

  const message = cancelReasonError(reason.value);
  if (message) {
    formError.value = message;
    return;
  }
  formError.value = '';
  emit('submit', { reason: reason.value.trim() });
}
</script>

<template>
  <Popup
    :show="show"
    position="bottom"
    round
    :style="{ maxHeight: '90%' }"
    @update:show="emit('update:show', $event)"
  >
    <div class="status-dialog">
      <div class="card-head">
        <h2>{{ title }}</h2>
        <button type="button" class="status-close" data-testid="status-dialog-close" @click="close">
          关闭
        </button>
      </div>

      <template v-if="mode === 'postpone'">
        <label class="status-field">
          <span>开始时间</span>
          <input
            v-model="startAt"
            data-testid="status-start-at"
            type="datetime-local"
            aria-label="延期开始时间"
          />
        </label>
        <label class="status-field">
          <span>结束时间</span>
          <input
            v-model="endAt"
            data-testid="status-end-at"
            type="datetime-local"
            aria-label="延期结束时间"
          />
        </label>
        <p class="muted small">
          开赛需晚于当天 10:00，开始/结束相差 2–4 小时。截止与锁定时间将按新日期重算。
        </p>
      </template>

      <template v-else>
        <label class="status-field status-field--stack">
          <span>取消原因</span>
          <textarea
            v-model="reason"
            data-testid="status-reason"
            maxlength="200"
            rows="3"
            placeholder="请填写取消原因（最多 200 字）"
            aria-label="取消原因"
          />
        </label>
        <p class="muted small">{{ reason.trim().length }} / 200</p>
      </template>

      <p v-if="formError" class="status-error" data-testid="status-dialog-error">
        {{ formError }}
      </p>
      <p v-if="error" class="status-error" data-testid="status-server-error">{{ error }}</p>

      <Button
        type="primary"
        block
        data-testid="status-submit"
        :loading="submitting"
        :disabled="submitting"
        @click="submit"
      >
        {{ submitLabel }}
      </Button>
    </div>
  </Popup>
</template>

<style scoped>
.status-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 18px 28px;
}

.status-close {
  padding: 4px 8px;
  color: var(--yh-muted);
  font: inherit;
  font-size: 13px;
  background: none;
  border: 0;
  cursor: pointer;
}

.status-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
}

.status-field--stack {
  flex-direction: column;
  align-items: stretch;
  gap: 6px;
}

.status-field > span {
  flex: 0 0 96px;
  color: var(--yh-muted);
}

.status-field--stack > span {
  flex: none;
}

.status-field input,
.status-field textarea {
  flex: 1;
  padding: 9px 10px;
  color: var(--yh-ink);
  font: inherit;
  background: #ffffff;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.status-field textarea {
  resize: vertical;
}

.status-error {
  margin: 0;
  color: #b91c1c;
  font-size: 13px;
}
</style>
