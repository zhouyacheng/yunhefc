<script setup lang="ts">
import { isApiError } from '@yunhefc/api-client';
import type { Player } from '@yunhefc/shared';
import { positionText, skillAriaLabel, skillStarText } from '@yunhefc/shared';
import { Button, Tag, showConfirmDialog } from 'vant';
import { computed, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';

import MyRegistrations from '../components/MyRegistrations.vue';
import NotificationList from '../components/NotificationList.vue';
import ProfileEditPopup from '../components/ProfileEditPopup.vue';
import { avatarPlaceholder, isDisplayableAvatarUrl } from '../features/profile/avatar';
import { isPending } from '../features/auth/user-status';
import { isWechatBrowser, requestWechatAuthorize } from '../features/auth/wechat';
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
            <p class="muted small">{{ session.user.phoneMasked }}</p>
            <div class="profile-identity__meta">
              <span>号码 {{ session.player.jersey ?? '—' }}</span>
              <span>位置 {{ positionText(session.player.position) }}</span>
              <span :aria-label="skillAriaLabel(session.player.skill)">
                {{ skillStarText(session.player.skill) }}
              </span>
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
            <span class="muted">{{ session.user.phoneMasked }}</span>
          </div>
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
  </section>
</template>

<style scoped>
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
