import type { InviteLanding, MatchDetail } from '../../types/api';
import { INVITE_UNAVAILABLE, NOT_FOUND, errorMessage } from '../../utils/api-errors';
import { acceptInvite, fetchInvite, fetchMatchDetail } from '../../utils/api-client';
import {
  INVITE_TYPE,
  buildInviteMatchView,
  inviteInvalidText,
  inviteLandingPath,
  inviteLandingTokenFromQuery,
  inviteLoginPath,
  isInviteUsable,
  matchIdOf,
  type InviteMatchView,
} from '../../utils/invite';
import { ApiError } from '../../utils/request';
import { getUser, sessionReady } from '../../utils/session';
import { isPending } from '../../utils/permissions';

Page({
  data: {
    token: '',
    loading: true,
    error: '',
    notFound: false,
    invite: null as InviteLanding | null,
    matchView: null as InviteMatchView | null,
    isMatchInvite: false,
    usable: false,
    invalidText: '',
    authenticated: false,
    pending: false,
    accepting: false,
    accepted: false,
    actionError: '',
  },

  onLoad(query: Record<string, string | undefined>) {
    const token = inviteLandingTokenFromQuery(query);
    if (!token) {
      this.setData({ loading: false, notFound: true });
      return;
    }
    this.setData({ token });
    void this.load();
  },

  async load(): Promise<void> {
    this.setData({ loading: true, error: '', notFound: false, accepted: false, actionError: '' });
    await sessionReady();
    const user = getUser();
    const authenticated = user !== null;
    const pending = isPending(user?.status);
    try {
      const invite = await fetchInvite(this.data.token);
      const matchId = matchIdOf(invite);
      let detail: MatchDetail | null = null;
      if (invite.type === INVITE_TYPE.MATCH && matchId !== null) {
        // 报名截止/锁定、赛季场次与名额只是补充信息；失败时保留 INV-01 摘要。
        detail = await fetchMatchDetail(matchId).catch(() => null);
      }
      const isMatchInvite = invite.type === INVITE_TYPE.MATCH;
      this.setData({
        loading: false,
        invite,
        isMatchInvite,
        matchView: isMatchInvite ? buildInviteMatchView(invite, detail) : null,
        usable: isInviteUsable(invite),
        invalidText: inviteInvalidText(invite),
        authenticated,
        pending,
      });
    } catch (error) {
      if (error instanceof ApiError && error.code === NOT_FOUND) {
        this.setData({ loading: false, notFound: true });
        return;
      }
      this.setData({ loading: false, error: errorMessage(error, '加载失败，请稍后重试') });
    }
  },

  retry() {
    void this.load();
  },

  /** 接受失败后重新读取 INV-01，按最新状态给出过期/撤销/用尽原因。 */
  async refreshInvalidReason(): Promise<void> {
    const latest = await fetchInvite(this.data.token).catch(() => null);
    this.setData({
      usable: false,
      invite: latest ?? this.data.invite,
      invalidText: inviteInvalidText(latest ?? this.data.invite),
    });
  },

  goLogin() {
    void wx.redirectTo({ url: inviteLoginPath(this.data.token) });
  },

  goHome() {
    void wx.switchTab({ url: '/pages/home/index' });
  },

  /** 比赛邀请不自动报名；接受后进入 MP04，由报名卡决定报名或候补。 */
  async accept(): Promise<void> {
    if (this.data.accepting) {
      return;
    }
    const invite = this.data.invite;
    if (!invite) {
      return;
    }
    this.setData({ accepting: true, actionError: '' });
    try {
      const result = await acceptInvite(this.data.token);
      const matchId = result.matchId ?? matchIdOf(invite);
      if (matchId === null) {
        this.setData({
          accepted: true,
          actionError: '邀请已接受，但比赛信息暂不可用',
        });
        return;
      }
      void wx.navigateTo({ url: `/pages/match-detail/index?id=${matchId}` });
    } catch (error) {
      if (error instanceof ApiError && error.code === INVITE_UNAVAILABLE) {
        await this.refreshInvalidReason();
        return;
      }
      if (error instanceof ApiError && error.code === NOT_FOUND) {
        this.setData({ notFound: true });
        return;
      }
      this.setData({ actionError: errorMessage(error, '接受邀请失败，请稍后重试') });
    } finally {
      this.setData({ accepting: false });
    }
  },

  onShareAppMessage(): WechatMiniprogram.Page.ICustomShareContent {
    return {
      title: `${this.data.matchView?.title ?? '比赛'} · 比赛邀请`,
      path: inviteLandingPath(this.data.token),
    };
  },
});
