<script setup lang="ts">
import { ERROR_CODES, isApiError } from '@yunhefc/api-client';
import type { MatchDetail } from '@yunhefc/shared';
import { formatDateTime } from '@yunhefc/shared';
import { Button, Loading, Tag } from 'vant';
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { api } from '../api/client';
import {
  type InviteLandingState,
  inviteInvalidText,
  inviteLandingState,
  inviteTypeText,
  matchInviteHint,
  remainingUsesText,
} from '../features/invite/invite-state';
import type { InviteAcceptResult, InviteLandingView } from '../features/invite/types';
import { toBrowserPath } from '../router';
import { hasSessionHint } from '../stores/session-hint';
import { useSessionStore } from '../stores/session';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();

const token = computed(() => String(route.params.token ?? ''));
const invite = ref<InviteLandingView | null>(null);
const matchDetail = ref<MatchDetail | null>(null);
const loading = ref(true);
const error = ref('');
const actionError = ref('');
const accepting = ref(false);
const accepted = ref(false);
const invalidated = ref(false);
let requestSeq = 0;

const state = computed<InviteLandingState>(() =>
  invalidated.value
    ? 'invalid'
    : inviteLandingState({
        invite: invite.value,
        member: accepted.value || session.player?.type === 1,
      }),
);
const invalidText = computed(() => inviteInvalidText(invite.value));
const matchTitle = computed(() => invite.value?.match?.title ?? matchDetail.value?.title ?? '比赛');
const matchStartAt = computed(
  () => invite.value?.match?.startAt ?? matchDetail.value?.startAt ?? undefined,
);
const matchVenue = computed(() => invite.value?.match?.venue ?? matchDetail.value?.venue ?? '待定');
const matchHintText = computed(() => {
  const hint = matchInviteHint(matchDetail.value, Date.now());
  if (hint === 'closed') {
    return '报名已截止';
  }
  if (hint === 'full') {
    return '名额已满，接受后可进入比赛查看候补';
  }
  return '';
});

async function loadMatch(matchId: number, seq: number): Promise<void> {
  try {
    const detail = await api.get<MatchDetail>(`/matches/${matchId}`, {
      redirectOnUnauthorized: false,
    });
    if (seq === requestSeq) {
      matchDetail.value = detail;
    }
  } catch {
    // 名额只是补充信息；失败时保留 INV-01 的时间与地点。
  }
}

async function load(): Promise<void> {
  const seq = ++requestSeq;
  loading.value = true;
  error.value = '';
  actionError.value = '';
  accepted.value = false;
  invalidated.value = false;
  invite.value = null;
  matchDetail.value = null;

  try {
    if (hasSessionHint() && !session.loaded) {
      await session.ensureLoaded().catch(() => undefined);
    }
    const result = await api.get<InviteLandingView>(`/invites/${encodeURIComponent(token.value)}`, {
      redirectOnUnauthorized: false,
    });
    if (seq !== requestSeq) {
      return;
    }
    invite.value = result;
    const matchId = result.matchId ?? result.match?.id ?? null;
    if (result.type === 1 && matchId !== null) {
      void loadMatch(matchId, seq);
    }
  } catch (caught) {
    if (seq !== requestSeq) {
      return;
    }
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      await router.replace('/404');
      return;
    }
    error.value = isApiError(caught) ? caught.message : '加载失败，请稍后重试';
  } finally {
    if (seq === requestSeq) {
      loading.value = false;
    }
  }
}

async function accept(): Promise<void> {
  const view = invite.value;
  if (!view || accepting.value) {
    return;
  }
  accepting.value = true;
  actionError.value = '';
  try {
    const result = await api.post<InviteAcceptResult>(
      `/invites/${encodeURIComponent(token.value)}/accept`,
    );
    accepted.value = true;
    const target = result.matchId ?? view.matchId;
    if (target !== null) {
      await router.push(`/matches/${target}`);
      return;
    }
    await session.refresh().catch(() => undefined);
  } catch (caught) {
    if (isApiError(caught)) {
      if (caught.code === ERROR_CODES.NOT_FOUND) {
        await router.replace('/404');
        return;
      }
      if (caught.code === ERROR_CODES.INVITE_UNAVAILABLE) {
        invalidated.value = true;
        return;
      }
      if (caught.code === ERROR_CODES.ACCOUNT_PENDING) {
        actionError.value = '账号待审核，暂无法接受邀请';
        return;
      }
    }
    actionError.value = isApiError(caught) ? caught.message : '接受邀请失败，请稍后重试';
  } finally {
    accepting.value = false;
  }
}

function goLogin(): void {
  void router.push({ name: 'login', query: { redirect: toBrowserPath(route.fullPath) } });
}

function goRegister(): void {
  void router.push({
    name: 'login',
    query: {
      mode: 'register',
      redirect: toBrowserPath(route.fullPath),
      inviteToken: token.value,
    },
  });
}

function goHome(): void {
  void router.push('/');
}

watch(token, () => void load(), { immediate: true });
</script>

<template>
  <section class="page">
    <header class="page-head">
      <h1>邀请</h1>
      <p class="muted">通过邀请加入云河FC</p>
    </header>

    <div v-if="loading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="error" class="state">
      <p class="muted">{{ error }}</p>
      <Button size="small" type="primary" @click="load">重试</Button>
    </div>

    <template v-else-if="invite">
      <div v-if="state === 'invalid'" class="card" data-testid="invite-invalid">
        <div class="card-head">
          <h2>邀请已失效</h2>
        </div>
        <p class="muted">{{ invalidText }}</p>
        <p class="muted">请联系邀请人重新获取邀请链接。</p>
        <Button type="primary" block style="margin-top: 12px" @click="goHome"> 返回首页 </Button>
      </div>

      <div v-else-if="state === 'member'" class="card" data-testid="invite-member">
        <div class="card-head">
          <h2>你已是球队成员，无需重复加入</h2>
          <Tag type="primary">{{ inviteTypeText(invite.type) }}</Tag>
        </div>
        <p class="muted">{{ invite.team?.name ?? '云河FC' }}</p>
        <Button type="primary" block style="margin-top: 12px" @click="goHome"> 返回首页 </Button>
      </div>

      <div v-else-if="state === 'team'" class="card" data-testid="invite-team">
        <div class="card-head">
          <h2>{{ invite.team?.name ?? '未知球队' }}</h2>
          <Tag type="primary">{{ inviteTypeText(invite.type) }}</Tag>
        </div>
        <p class="muted">邀请人 {{ invite.inviter?.name ?? '未知' }}</p>
        <p class="muted">有效期至 {{ formatDateTime(invite.expiresAt) }}</p>
        <p class="muted">剩余次数：{{ remainingUsesText(invite) }}</p>
        <p v-if="actionError" class="action-error">{{ actionError }}</p>

        <Button
          v-if="session.isAuthenticated"
          type="primary"
          block
          :loading="accepting"
          style="margin-top: 12px"
          @click="accept"
        >
          加入球队
        </Button>
        <template v-else>
          <p class="muted">登录或注册后即可加入球队</p>
          <Button type="primary" block @click="goLogin">登录</Button>
          <Button block style="margin-top: 12px" @click="goRegister">注册</Button>
        </template>
      </div>

      <div v-else class="card" data-testid="invite-match">
        <div class="card-head">
          <h2>{{ matchTitle }}</h2>
          <Tag type="primary">{{ inviteTypeText(invite.type) }}</Tag>
        </div>
        <p class="muted">时间：{{ formatDateTime(matchStartAt) }}</p>
        <p class="muted">地点：{{ matchVenue }}</p>
        <p class="muted">
          名额：{{ matchDetail ? `${matchDetail.confirmedCount} / ${matchDetail.capacity}` : '—' }}
        </p>
        <p v-if="matchHintText" class="muted">{{ matchHintText }}</p>
        <p class="muted">邀请人 {{ invite.inviter?.name ?? '未知' }}</p>
        <p class="muted">有效期至 {{ formatDateTime(invite.expiresAt) }}</p>
        <p class="muted">剩余次数：{{ remainingUsesText(invite) }}</p>
        <p v-if="actionError" class="action-error">{{ actionError }}</p>

        <template v-if="accepted">
          <p class="muted">邀请已接受，比赛信息暂不可用。</p>
          <Button type="primary" block style="margin-top: 12px" @click="goHome"> 返回首页 </Button>
        </template>
        <Button
          v-else-if="session.isAuthenticated"
          type="primary"
          block
          :loading="accepting"
          style="margin-top: 12px"
          @click="accept"
        >
          去比赛
        </Button>
        <template v-else>
          <p class="muted">登录或注册后即可前往比赛</p>
          <Button type="primary" block @click="goLogin">登录</Button>
          <Button block style="margin-top: 12px" @click="goRegister">注册</Button>
        </template>
      </div>
    </template>
  </section>
</template>
