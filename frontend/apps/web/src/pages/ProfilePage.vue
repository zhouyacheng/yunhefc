<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { Player } from '@yunhefc/shared';
import { positionText } from '@yunhefc/shared';
import { Button, CellGroup, Field, Popup, Tag, showConfirmDialog } from 'vant';
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import MyRegistrations from '../components/MyRegistrations.vue';
import NotificationList from '../components/NotificationList.vue';
import ProfileEditPopup from '../components/ProfileEditPopup.vue';
import { avatarPlaceholder, isDisplayableAvatarUrl } from '../features/profile/avatar';
import { isPending } from '../features/auth/user-status';
import { validatePhone, validateSmsCode } from '../features/auth/validation';
import { useSmsCode } from '../features/auth/useSmsCode';
import { isWechatBrowser, requestWechatAuthorize } from '../features/auth/wechat';
import SkillStars from '../components/SkillStars.vue';
import { useSessionStore } from '../stores/session';

const ROLE_TEXT: Readonly<Record<string, string>> = {
  ADMIN: '管理员',
  PLAYER: '正式球员',
  GUEST: '外挂球员',
};

const session = useSessionStore();
const router = useRouter();

const editing = ref(false);
const avatarBroken = ref(false);
const savedHint = ref('');

const wechatBrowser = isWechatBrowser(navigator.userAgent);
const wechatLoading = ref(false);
const wechatError = ref('');

const canBindWechat = computed(
  () => wechatBrowser && session.user !== null && !session.user.wechatBound,
);

const pending = computed(() => isPending(session.user?.status ?? 1));
const roleText = computed(() => {
  const roles = session.user?.roles ?? [];
  return roles.length ? roles.map((role) => ROLE_TEXT[role] ?? role).join(' / ') : '—';
});
const avatarSrc = computed(() => session.player?.avatarUrl ?? null);

const canBindPhone = computed(
  () => session.user !== null && session.user.phoneMasked === null,
);
const showPhoneBind = ref(false);
const phone = ref('');
const smsCode = ref('');
const phoneFieldError = ref('');
const smsFieldError = ref('');
const phoneError = ref('');
const binding = ref(false);
const {
  countdown,
  sending: codeSending,
  hint: phoneHint,
  send: sendSms,
  stop: stopSmsCountdown,
} = useSmsCode();

function openPhoneBind(): void {
  phone.value = '';
  smsCode.value = '';
  phoneFieldError.value = '';
  smsFieldError.value = '';
  phoneHint.value = '';
  phoneError.value = '';
  showPhoneBind.value = true;
}

async function sendBindCode(): Promise<void> {
  phoneError.value = '';
  const result = await sendSms(phone.value);
  if (result.phoneError) {
    phoneFieldError.value = result.phoneError;
  } else if (!result.ok && result.errorMessage) {
    phoneError.value = result.errorMessage;
  }
}

async function submitBindPhone(): Promise<void> {
  phoneFieldError.value = validatePhone(phone.value) ?? '';
  smsFieldError.value = validateSmsCode(smsCode.value) ?? '';
  if (phoneFieldError.value || smsFieldError.value) {
    return;
  }
  binding.value = true;
  phoneError.value = '';
  try {
    await session.bindPhone({ phone: phone.value.trim(), code: smsCode.value.trim() });
    stopSmsCountdown();
    showPhoneBind.value = false;
    savedHint.value = '手机号已绑定';
  } catch (error) {
    phoneError.value = isApiError(error) ? error.message : '绑定失败，请稍后重试';
  } finally {
    binding.value = false;
  }
}

function openEdit(): void {
  if (pending.value) {
    return;
  }
  savedHint.value = '';
  editing.value = true;
}

function onSaved(player: Player): void {
  session.player = player;
  savedHint.value = '资料已保存';
}

async function logout(): Promise<void> {
  try {
    await showConfirmDialog({
      title: '退出登录',
      message: '确定退出当前账号吗？',
      confirmButtonText: '退出',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }
  await session.logout();
  await router.replace('/login');
}

/** C06：微信内且未绑定时发起 AUTH-07，redirect 回个人中心。 */
async function bindWechat(): Promise<void> {
  wechatError.value = '';
  wechatLoading.value = true;
  try {
    const authorizeUrl = await requestWechatAuthorize('/profile');
    window.location.assign(authorizeUrl);
  } catch (error) {
    wechatError.value = isApiError(error) ? error.message : '无法发起微信绑定，请稍后重试';
    wechatLoading.value = false;
  }
}

watch(avatarSrc, () => {
  avatarBroken.value = false;
});

onMounted(() => {
  // 进入个人中心即刷新 /me，保证 wechatBound、角色与未读数为最新。
  void session.refresh().catch(() => undefined);
});
</script>

<template>
  <section class="page profile-page">
    <header class="page-head">
      <p class="eyebrow">PROFILE</p>
      <h1>我的</h1>
      <p class="muted">个人资料、通知与报名记录</p>
    </header>

    <p v-if="pending" class="notice profile-pending" role="status">账号待审核</p>

    <div class="profile-grid">
      <div class="profile-col">
        <section v-if="session.user && session.player" class="card profile-identity">
          <div class="profile-avatar">
            <img
              v-if="isDisplayableAvatarUrl(avatarSrc) && !avatarBroken"
              :src="avatarSrc"
              :alt="session.player.name"
              @error="avatarBroken = true"
            />
            <span v-else class="profile-avatar__placeholder">
              {{ avatarPlaceholder(session.player.name) }}
            </span>
          </div>

          <div class="profile-identity__info">
            <div class="profile-identity__name-row">
              <h2>{{ session.player.name }}</h2>
              <Tag class="status-tag status-tag--admin">{{ roleText }}</Tag>
            </div>
            <p class="muted small">{{ session.user.phoneMasked ?? '未绑定手机号' }}</p>
            <div class="profile-identity__meta">
              <span>号码 {{ session.player.jersey ?? '—' }}</span>
              <span>位置 {{ positionText(session.player.position) }}</span>
              <SkillStars :skill="session.player.skill" />
            </div>
          </div>

          <Button
            v-if="!pending"
            class="profile-edit-trigger"
            size="small"
            plain
            type="primary"
            @click="openEdit"
          >
            编辑资料
          </Button>
        </section>

        <p v-if="savedHint" class="hint profile-saved">{{ savedHint }}</p>

        <MyRegistrations class="profile-block" :pending="pending" />
      </div>

      <div class="profile-col">
        <NotificationList class="profile-block" />

        <section v-if="session.user" class="card profile-security profile-block">
          <div class="card-head">
            <h2>账号与安全</h2>
          </div>

          <div class="security-row">
            <span>手机号</span>
            <span class="muted">{{ session.user.phoneMasked ?? '未绑定手机号' }}</span>
          </div>
          <Button
            v-if="canBindPhone"
            class="phone-bind-trigger"
            size="small"
            type="primary"
            plain
            @click="openPhoneBind"
          >
            绑定手机号
          </Button>
          <div class="security-row">
            <span>微信绑定</span>
            <span class="muted">{{ session.user.wechatBound ? '已绑定' : '未绑定' }}</span>
          </div>
          <Button
            v-if="canBindWechat"
            class="wechat-bind"
            size="small"
            type="primary"
            plain
            :loading="wechatLoading"
            @click="bindWechat"
          >
            绑定微信
          </Button>
          <p v-if="wechatError" class="notice notice--danger wechat-bind__error">
            {{ wechatError }}
          </p>
          <RouterLink
            v-if="session.user.phoneMasked"
            class="security-row security-row--link"
            :to="{ name: 'login', query: { mode: 'reset' } }"
          >
            <span>找回密码</span>
            <span class="muted">短信验证码重置 →</span>
          </RouterLink>

          <Button type="danger" plain block @click="logout">退出登录</Button>
        </section>
      </div>
    </div>

    <ProfileEditPopup
      v-if="session.player"
      v-model:show="editing"
      :player="session.player"
      @saved="onSaved"
    />

    <Popup v-model:show="showPhoneBind" position="bottom" round>
      <form class="phone-bind" @submit.prevent="submitBindPhone">
        <h3 class="phone-bind__title">绑定手机号</h3>
        <CellGroup inset>
          <Field
            v-model="phone"
            name="phone"
            type="tel"
            label="手机号"
            placeholder="请输入手机号"
            autocomplete="tel"
            maxlength="11"
            :error-message="phoneFieldError"
          />
          <Field
            v-model="smsCode"
            name="code"
            type="digit"
            label="验证码"
            placeholder="6 位数字验证码"
            autocomplete="one-time-code"
            maxlength="6"
            :error-message="smsFieldError"
          >
            <template #button>
              <Button
                size="small"
                type="primary"
                plain
                :disabled="countdown > 0 || codeSending || binding"
                :loading="codeSending"
                @click="sendBindCode"
              >
                {{ countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
              </Button>
            </template>
          </Field>
        </CellGroup>
        <p v-if="phoneHint" class="hint">{{ phoneHint }}</p>
        <p v-if="phoneError" class="notice notice--danger">{{ phoneError }}</p>
        <Button type="primary" block native-type="submit" :loading="binding">保存</Button>
        <Button block plain @click="showPhoneBind = false">取消</Button>
      </form>
    </Popup>
  </section>
</template>

<style scoped>
.phone-bind {
  padding: 24px;
}

.phone-bind__title {
  margin: 0 0 16px;
}

.profile-pending {
  margin: 0 0 16px;
}

.profile-grid {
  display: grid;
  grid-template-columns: 0.9fr 1.1fr;
  gap: 16px;
  align-items: start;
}

.profile-col {
  display: flex;
  flex-direction: column;
  gap: 16px;
  min-width: 0;
}

.profile-identity {
  display: flex;
  gap: 16px;
  align-items: center;
}

.profile-avatar {
  flex: none;
  width: 72px;
  height: 72px;
  overflow: hidden;
  background: var(--yh-navy);
  border-radius: 50%;
}

.profile-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.profile-avatar__placeholder {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--yh-gold);
  font-size: 26px;
  font-weight: 800;
}

.profile-identity__info {
  flex: 1;
  min-width: 0;
}

.profile-identity__name-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.profile-identity__name-row h2 {
  margin: 0;
  font-size: 24px;
  letter-spacing: -0.02em;
}

.profile-identity__info > .muted {
  margin: 4px 0;
}

.profile-identity__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 12px;
  color: var(--yh-muted);
  font-size: 13px;
}

.profile-edit-trigger {
  flex: none;
}

.profile-block {
  display: block;
  margin-top: 0;
}

.security-row {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  min-height: 44px;
  padding: 10px 0;
  font-size: 14px;
  border-bottom: 1px dashed var(--yh-line);
}

.security-row--link {
  color: var(--yh-green);
}

.profile-security :deep(.van-button) {
  margin-top: 14px;
}

@media (max-width: 1000px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }

  .profile-col {
    display: contents;
  }

  .profile-identity {
    order: 1;
  }

  .notifications {
    order: 2;
  }

  .my-registrations {
    order: 3;
  }

  .profile-security {
    order: 4;
  }

  .profile-saved {
    order: 5;
  }
}

@media (max-width: 480px) {
  .profile-identity {
    flex-wrap: wrap;
  }

  .profile-edit-trigger {
    width: 100%;
  }
}
</style>
