<script setup lang="ts">
import type { InviteCreated } from '@yunhefc/shared';
import { formatDateTime } from '@yunhefc/shared';
import { Button, Popup } from 'vant';
import { ref, watch } from 'vue';

import { DEFAULT_INVITE_DAYS, inviteFormError, invitePayload } from './invite';
import type { MatchInvitePayload } from './invite';

const props = defineProps<{
  show: boolean;
  submitting: boolean;
  result: InviteCreated | null;
  error: string;
}>();

const emit = defineEmits<{
  'update:show': [value: boolean];
  submit: [payload: MatchInvitePayload];
  copy: [url: string];
}>();

const days = ref(String(DEFAULT_INVITE_DAYS));
const maxUses = ref('');
const formError = ref('');
const copied = ref(false);

watch(
  () => props.show,
  (show) => {
    if (!show) {
      return;
    }
    if (!props.result) {
      days.value = String(DEFAULT_INVITE_DAYS);
      maxUses.value = '';
    }
    formError.value = '';
    copied.value = false;
  },
);

watch(
  () => props.result,
  () => {
    copied.value = false;
  },
);

watch(
  () => props.error,
  (message) => {
    if (message) {
      copied.value = false;
    }
  },
);

function close(): void {
  if (props.submitting) {
    return;
  }
  emit('update:show', false);
}

function submit(): void {
  if (props.submitting) {
    return;
  }
  const message = inviteFormError({ expiresInDays: days.value, maxUses: maxUses.value });
  if (message) {
    formError.value = message;
    return;
  }
  formError.value = '';
  emit('submit', invitePayload({ expiresInDays: days.value, maxUses: maxUses.value }));
}

function copy(): void {
  if (!props.result) {
    return;
  }
  copied.value = true;
  emit('copy', props.result.inviteUrl);
}

function selectAll(event: Event): void {
  const input = event.target as HTMLInputElement;
  input.select();
}
</script>

<template>
  <Popup
    :show="show"
    position="bottom"
    round
    :close-on-click-overlay="!submitting"
    :style="{ maxHeight: '90%' }"
    @update:show="emit('update:show', $event)"
  >
    <div class="invite-dialog">
      <div class="card-head">
        <h2>邀请队友</h2>
        <button type="button" class="invite-close" data-testid="invite-close" @click="close">
          关闭
        </button>
      </div>

      <template v-if="result">
        <p class="invite-notice">链接仅显示一次，请立即复制并发送给受邀人。</p>
        <input
          class="invite-link"
          data-testid="invite-link"
          :value="result.inviteUrl"
          readonly
          aria-label="邀请链接"
          @focus="selectAll"
        />
        <p class="muted small">有效期至 {{ formatDateTime(result.expiresAt) }}</p>
        <p v-if="error" class="invite-error" data-testid="invite-error">{{ error }}</p>
        <Button type="primary" block data-testid="invite-copy" @click="copy">
          {{ copied ? '已复制' : '复制链接' }}
        </Button>
      </template>

      <template v-else>
        <label class="invite-field">
          <span>有效期（天）</span>
          <input
            v-model="days"
            data-testid="invite-days"
            type="number"
            min="1"
            max="30"
            inputmode="numeric"
            aria-label="有效期天数"
          />
        </label>
        <label class="invite-field">
          <span>使用次数</span>
          <input
            v-model="maxUses"
            data-testid="invite-max-uses"
            type="number"
            min="1"
            inputmode="numeric"
            placeholder="留空表示不限"
            aria-label="使用次数上限"
          />
        </label>
        <p class="muted small">默认 7 天有效；次数留空表示不限。链接创建后只展示一次。</p>
        <p v-if="formError" class="invite-error" data-testid="invite-form-error">{{ formError }}</p>
        <p v-if="error" class="invite-error" data-testid="invite-error">{{ error }}</p>
        <Button
          type="primary"
          block
          data-testid="invite-submit"
          :loading="submitting"
          :disabled="submitting"
          @click="submit"
        >
          创建邀请
        </Button>
      </template>
    </div>
  </Popup>
</template>

<style scoped>
.invite-dialog {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 18px 18px 28px;
}

.invite-close {
  padding: 4px 8px;
  color: var(--yh-muted);
  font: inherit;
  font-size: 13px;
  background: none;
  border: 0;
  cursor: pointer;
}

.invite-notice {
  margin: 0;
  padding: 9px 10px;
  color: #92400e;
  font-size: 13px;
  background: #fef3c7;
  border-radius: 10px;
}

.invite-link {
  width: 100%;
  padding: 10px;
  color: var(--yh-ink);
  font: inherit;
  font-size: 13px;
  background: #f8fafc;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
  word-break: break-all;
}

.invite-field {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 14px;
}

.invite-field > span {
  flex: 0 0 96px;
  color: var(--yh-muted);
}

.invite-field input {
  flex: 1;
  padding: 9px 10px;
  color: var(--yh-ink);
  font: inherit;
  background: #ffffff;
  border: 1px solid var(--yh-line);
  border-radius: 10px;
}

.invite-error {
  margin: 0;
  color: #b91c1c;
  font-size: 13px;
}
</style>
