import type { AlbumSummary, ActivitySummary, MatchSummary } from '../../types/api';
import {
  createAlbum,
  createPost,
  deleteAlbum,
  deletePost,
  listActivities,
  listAlbums,
  listMatches,
  listPosts,
  reportContent,
  setPostLike,
} from '../../utils/api-client';
import { errorMessage } from '../../utils/api-errors';
import { confirmDialog } from '../../utils/confirm';
import {
  applyPostLike,
  buildAlbumCardView,
  buildContentPostRow,
  contentAccessState,
  contentAlbumTitleError,
  contentIdFromQuery,
  contentPostTextError,
  CONTENT_ALBUM_TITLE_MAX_LENGTH,
  CONTENT_POST_MAX_LENGTH,
  CONTENT_REPORT_POST,
  type AlbumCardView,
  type ContentAccessState,
  type ContentPostRow,
  type ContentViewer,
} from '../../utils/content';
import { createContentFeed, type ContentFeed } from '../../utils/content-feed';
import { formatDayStartTime } from '../../utils/datetime';
import { emptyPagedFeedState } from '../../utils/paged-feed';
import { CONTENT_DELETE_ANY, CONTENT_MANAGE_ANY, hasPermission } from '../../utils/permissions';
import { getPermissions, getUser, sessionReady } from '../../utils/session';

type TabKey = 'timeline' | 'albums';

interface PickerOption {
  id: number;
  label: string;
}

const LIST_PAGE_SIZE = 10;
const FOCUS_MS = 4000;

let requestSeq = 0;

function scopeFallback(matchId: number, activityId: number): string {
  if (matchId) {
    return `比赛 #${matchId}`;
  }
  if (activityId) {
    return `活动 #${activityId}`;
  }
  return '';
}

/** picker 第 0 项是「不关联」，命中时返回选项下标 +1。 */
function pickerIndexFor(options: PickerOption[], id: number): number {
  const index = options.findIndex((option) => option.id === id);
  return index < 0 ? 0 : index + 1;
}

function optionLabels(noneText: string, options: PickerOption[]): string[] {
  return [noneText, ...options.map((option) => option.label)];
}

Page({
  data: {
    access: 'loading' as 'loading' | ContentAccessState,
    activeTab: 'timeline' as TabKey,
    matchId: 0,
    activityId: 0,
    scopeText: '',
    userId: 0,
    canDeleteAny: false,
    canManageAlbums: false,
    // 时间线
    timeline: emptyPagedFeedState<ContentPostRow>('content', LIST_PAGE_SIZE),
    composerText: '',
    composerLength: 0,
    composerError: '',
    publishing: false,
    canPublish: false,
    albumChoices: [] as { id: number; title: string }[],
    albumChoiceLabels: ['不引用相册'],
    albumChoiceIndex: 0,
    composerAlbumId: 0,
    likeBusyId: 0,
    reportBusyId: 0,
    deleteBusyId: 0,
    focusPostId: 0,
    coverBroken: {} as Record<string, true>,
    // 相册
    albums: emptyPagedFeedState<AlbumCardView>('content', LIST_PAGE_SIZE),
    albumBusyId: 0,
    // 新建相册
    createOpen: false,
    newAlbumTitle: '',
    createError: '',
    creating: false,
    optionsLoaded: false,
    optionsLoading: false,
    matchOptions: [] as PickerOption[],
    activityOptions: [] as PickerOption[],
    matchOptionLabels: ['不关联比赛'],
    activityOptionLabels: ['不关联活动'],
    newMatchIndex: 0,
    newActivityIndex: 0,
    albumTitleMax: CONTENT_ALBUM_TITLE_MAX_LENGTH,
    postMaxLength: CONTENT_POST_MAX_LENGTH,
  },

  booted: false,
  timelineFeed: null as ContentFeed<ContentPostRow> | null,
  albumFeed: null as ContentFeed<AlbumCardView> | null,
  focusTimer: null as ReturnType<typeof setTimeout> | null,

  onLoad(options: Record<string, string | undefined>) {
    // 内容不对外公开，不提供右上角转发入口（02 MP14）。
    wx.hideShareMenu();
    const matchId = contentIdFromQuery(options.matchId) ?? 0;
    const activityId = contentIdFromQuery(options.activityId) ?? 0;
    const focusPostId = contentIdFromQuery(options.post) ?? 0;
    const activeTab: TabKey =
      options.tab === 'albums' || matchId || activityId ? 'albums' : 'timeline';
    this.setData({
      activeTab,
      matchId,
      activityId,
      focusPostId,
      scopeText: scopeFallback(matchId, activityId),
    });
    this.createFeeds();
    void this.boot(false, focusPostId > 0);
  },

  onShow() {
    // 登录状态与相册内容可能在其它页变化；首帧由 onLoad 负责，避免重复请求。
    if (this.booted) {
      void this.boot(true, false);
    }
  },

  onPullDownRefresh() {
    const feed = this.data.activeTab === 'timeline' ? this.timelineFeed : this.albumFeed;
    const job = this.data.access === 'ok' && feed ? feed.load() : this.boot(true, false);
    void job.then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.access !== 'ok') {
      return;
    }
    const feed = this.data.activeTab === 'timeline' ? this.timelineFeed : this.albumFeed;
    void feed?.loadMore();
  },

  onUnload() {
    requestSeq += 1;
    this.timelineFeed?.dispose();
    this.albumFeed?.dispose();
    this.timelineFeed = null;
    this.albumFeed = null;
    if (this.focusTimer) {
      clearTimeout(this.focusTimer);
      this.focusTimer = null;
    }
  },

  createFeeds() {
    this.timelineFeed = createContentFeed<ContentPostRow>({
      pageSize: LIST_PAGE_SIZE,
      errorText: '动态加载失败，请稍后重试',
      fetchPage: async ({ page, pageSize }) => {
        const result = await listPosts({ page, pageSize });
        return {
          ...result,
          items: result.items.map((post) => buildContentPostRow(post, this.viewer())),
        };
      },
      onChange: (state) => this.setData({ timeline: state }),
    });

    this.albumFeed = createContentFeed<AlbumCardView>({
      pageSize: LIST_PAGE_SIZE,
      errorText: '相册加载失败，请稍后重试',
      fetchPage: async ({ page, pageSize }) => {
        const result = await listAlbums({
          matchId: this.data.matchId || undefined,
          activityId: this.data.activityId || undefined,
          page,
          pageSize,
        });
        return { ...result, items: result.items.map(buildAlbumCardView) };
      },
      onChange: (state) => {
        const scoped = this.data.matchId > 0 || this.data.activityId > 0;
        this.setData({
          albums: state,
          scopeText: scoped
            ? (state.items[0]?.ownerText ?? scopeFallback(this.data.matchId, this.data.activityId))
            : '',
        });
      },
    });
  },

  async boot(silent: boolean, locateFocus: boolean): Promise<void> {
    const seq = ++requestSeq;
    if (!silent) {
      this.setData({ access: 'loading' });
    }
    await sessionReady();
    if (seq !== requestSeq) {
      return;
    }
    const user = getUser();
    const permissions = getPermissions();
    const access = contentAccessState(user, permissions);
    this.setData({
      access,
      userId: user?.id ?? 0,
      canDeleteAny: hasPermission(permissions, CONTENT_DELETE_ANY),
      canManageAlbums: hasPermission(permissions, CONTENT_MANAGE_ANY),
    });
    this.booted = true;
    if (access !== 'ok') {
      return;
    }
    if (!this.timelineFeed) {
      this.albumFeed?.dispose();
      this.createFeeds();
    }
    if (this.data.activeTab === 'timeline') {
      await Promise.all([this.timelineFeed?.load() ?? Promise.resolve(), this.loadAlbumChoices()]);
      if (locateFocus && this.data.focusPostId) {
        await this.locatePost(this.data.focusPostId);
      }
    } else {
      await (this.albumFeed?.load() ?? Promise.resolve());
    }
  },

  retryAccess() {
    this.booted = false;
    void this.boot(false, false);
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },

  viewer(): ContentViewer {
    return { userId: this.data.userId || null, canDeleteAny: this.data.canDeleteAny };
  },

  selectTab(event: WechatMiniprogram.BaseEvent) {
    const tab = event.currentTarget.dataset.tab as TabKey;
    if (tab !== 'timeline' && tab !== 'albums') {
      return;
    }
    if (tab === this.data.activeTab) {
      return;
    }
    this.setData({ activeTab: tab });
    // 已加载的 Tab 直接展示缓存；onShow/下拉刷新才重载。
    if (tab === 'timeline') {
      if (this.timelineFeed && !this.timelineFeed.state.loaded) {
        void this.timelineFeed.load();
      }
      void this.loadAlbumChoices();
    } else if (this.albumFeed && !this.albumFeed.state.loaded) {
      void this.albumFeed.load();
    }
  },

  // ---------- 时间线 ----------

  retryTimeline() {
    void this.timelineFeed?.load();
  },

  /** 通知定位：feed 静默跨页查找；命中后高亮滚动。 */
  async locatePost(id: number): Promise<void> {
    const feed = this.timelineFeed;
    if (!feed) {
      return;
    }
    if (feed.state.items.some((post) => post.id === id)) {
      this.focusPost(id);
      return;
    }
    const found = await feed.locate((post) => post.id === id);
    if (found) {
      this.focusPost(id);
    }
  },

  focusPost(id: number): void {
    this.setData({ focusPostId: id });
    wx.nextTick(() => {
      wx.pageScrollTo({ selector: `#post-${id}`, offsetTop: -40, duration: 300 });
    });
    if (this.focusTimer) {
      clearTimeout(this.focusTimer);
    }
    this.focusTimer = setTimeout(() => {
      if (this.data.focusPostId === id) {
        this.setData({ focusPostId: 0 });
      }
    }, FOCUS_MS);
  },

  onComposerInput(event: WechatMiniprogram.CustomEvent) {
    const value = String(event.detail.value || '');
    this.setData({
      composerText: value,
      composerLength: value.trim().length,
      composerError: '',
      canPublish: value.trim().length > 0 && !this.data.publishing,
    });
  },

  onAlbumChoiceChange(event: WechatMiniprogram.CustomEvent) {
    const index = Number(event.detail.value) || 0;
    const choice = index > 0 ? this.data.albumChoices[index - 1] : null;
    this.setData({ albumChoiceIndex: index, composerAlbumId: choice?.id ?? 0 });
  },

  async loadAlbumChoices(): Promise<void> {
    try {
      const result = await listAlbums({ page: 1, pageSize: 100 });
      const choices = result.items.map((album: AlbumSummary) => ({
        id: album.id,
        title: album.title,
      }));
      const index = this.data.composerAlbumId
        ? choices.findIndex((choice) => choice.id === this.data.composerAlbumId) + 1
        : 0;
      this.setData({
        albumChoices: choices,
        albumChoiceLabels: ['不引用相册', ...choices.map((choice) => choice.title)],
        albumChoiceIndex: Math.max(0, index),
        composerAlbumId: index > 0 ? this.data.composerAlbumId : 0,
      });
    } catch {
      // 引用相册加载失败不阻断发布；保持纯文字。
    }
  },

  async publish(): Promise<void> {
    if (this.data.publishing) {
      return;
    }
    const textError = contentPostTextError(this.data.composerText);
    if (textError) {
      this.setData({ composerError: textError });
      return;
    }
    this.setData({ publishing: true, canPublish: false, composerError: '' });
    try {
      const post = await createPost({
        content: this.data.composerText.trim(),
        albumId: this.data.composerAlbumId || null,
      });
      const row = buildContentPostRow(post, this.viewer());
      const feed = this.timelineFeed;
      if (feed) {
        const page = feed.state.page;
        feed.update(
          (items) => (page === 1 ? [row, ...items].slice(0, feed.state.pageSize) : items),
          { totalDelta: 1 },
        );
      }
      this.setData({
        composerText: '',
        composerLength: 0,
        composerAlbumId: 0,
        albumChoiceIndex: 0,
        publishing: false,
      });
      wx.showToast({ title: '已发布', icon: 'none' });
    } catch (error) {
      const message = errorMessage(error, '发布失败，请稍后重试');
      this.setData({ composerError: message, publishing: false });
      this.setData({ canPublish: this.data.composerLength > 0 });
    }
  },

  async toggleLike(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const feed = this.timelineFeed;
    if (this.data.likeBusyId || !feed) {
      return;
    }
    const current = feed.state.items.find((post) => post.id === id);
    if (!current) {
      return;
    }
    this.setData({ likeBusyId: id });
    try {
      const result = await setPostLike(id, !current.likedByMe);
      feed.update((items) =>
        items.map((item) => (item.id === id ? applyPostLike(item, result) : item)),
      );
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '操作失败，请稍后重试'), icon: 'none' });
    } finally {
      this.setData({ likeBusyId: 0 });
    }
  },

  async reportPost(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (this.data.reportBusyId) {
      return;
    }
    if (!(await confirmDialog('举报动态', '确定举报该动态并通知管理员？'))) {
      return;
    }
    this.setData({ reportBusyId: id });
    try {
      await reportContent(CONTENT_REPORT_POST, id);
      wx.showToast({ title: '已通知管理员', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '举报失败，请稍后重试'), icon: 'none' });
    } finally {
      this.setData({ reportBusyId: 0 });
    }
  },

  async removePost(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (this.data.deleteBusyId) {
      return;
    }
    if (!(await confirmDialog('删除动态', '删除后不可恢复，确定删除该动态吗？'))) {
      return;
    }
    this.setData({ deleteBusyId: id });
    try {
      await deletePost(id);
      this.timelineFeed?.update((items) => items.filter((post) => post.id !== id), {
        totalDelta: -1,
      });
      wx.showToast({ title: '动态已删除', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '删除失败，请稍后重试'), icon: 'none' });
    } finally {
      this.setData({ deleteBusyId: 0 });
    }
  },

  onAvatarError(event: WechatMiniprogram.CustomEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const feed = this.timelineFeed;
    if (!feed || !feed.state.items.some((item) => item.id === id)) {
      return;
    }
    feed.update((items) =>
      items.map((item) => (item.id === id ? { ...item, avatarUrl: '' } : item)),
    );
  },

  onCoverError(event: WechatMiniprogram.CustomEvent) {
    const url = String(event.currentTarget.dataset.url ?? '');
    if (!url) {
      return;
    }
    this.setData({ coverBroken: { ...this.data.coverBroken, [url]: true } });
  },

  // ---------- 相册 ----------

  retryAlbums() {
    void this.albumFeed?.load();
  },

  clearScope() {
    this.setData({ matchId: 0, activityId: 0, scopeText: '' });
    void this.albumFeed?.load();
  },

  openAlbum(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    if (!id) {
      return;
    }
    void wx.navigateTo({ url: `/pages/album-detail/index?id=${id}` });
  },

  async removeAlbum(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const title = String(event.currentTarget.dataset.title ?? '');
    if (!this.data.canManageAlbums || this.data.albumBusyId) {
      return;
    }
    if (!(await confirmDialog('删除相册', `删除「${title}」及其中照片？`))) {
      return;
    }
    this.setData({ albumBusyId: id });
    try {
      await deleteAlbum(id);
      wx.showToast({ title: '相册已删除', icon: 'none' });
      await Promise.all([this.albumFeed?.load() ?? Promise.resolve(), this.loadAlbumChoices()]);
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '删除失败，请稍后重试'), icon: 'none' });
    } finally {
      this.setData({ albumBusyId: 0 });
    }
  },

  // ---------- 新建相册 ----------

  openCreate() {
    if (this.data.createOpen) {
      return;
    }
    this.setData({
      createOpen: true,
      newAlbumTitle: '',
      createError: '',
      newMatchIndex: 0,
      newActivityIndex: 0,
    });
    if (this.data.optionsLoaded) {
      this.prefillCreateScope();
    } else {
      void this.ensureOptions();
    }
  },

  closeCreate() {
    if (this.data.creating) {
      return;
    }
    this.setData({ createOpen: false });
  },

  /** 从比赛/活动详情进入相册页时，新建默认预选当前归属。 */
  prefillCreateScope() {
    const matchIndex = this.data.matchId
      ? pickerIndexFor(this.data.matchOptions, this.data.matchId)
      : 0;
    const activityIndex =
      matchIndex > 0
        ? 0
        : this.data.activityId
          ? pickerIndexFor(this.data.activityOptions, this.data.activityId)
          : 0;
    this.setData({ newMatchIndex: matchIndex, newActivityIndex: activityIndex });
  },

  async ensureOptions(): Promise<void> {
    if (this.data.optionsLoaded || this.data.optionsLoading) {
      return;
    }
    this.setData({ optionsLoading: true, createError: '' });
    try {
      const [matches, activities] = await Promise.all([
        listMatches({ pageSize: 100 }),
        listActivities({ pageSize: 100, scope: 'all' }),
      ]);
      const matchOptions = matches.items.map((match: MatchSummary) => ({
        id: match.id,
        label: `${match.title} · ${formatDayStartTime(match.startAt)}`,
      }));
      const activityOptions = activities.items.map((activity: ActivitySummary) => ({
        id: activity.id,
        label: `${activity.title} · ${formatDayStartTime(activity.startAt)}`,
      }));
      this.setData({
        matchOptions,
        activityOptions,
        matchOptionLabels: optionLabels('不关联比赛', matchOptions),
        activityOptionLabels: optionLabels('不关联活动', activityOptions),
        optionsLoaded: true,
        optionsLoading: false,
      });
      this.prefillCreateScope();
    } catch {
      this.setData({
        optionsLoading: false,
        createError: '关联对象加载失败，可先创建通用相册',
      });
    }
  },

  onTitleInput(event: WechatMiniprogram.CustomEvent) {
    this.setData({ newAlbumTitle: String(event.detail || ''), createError: '' });
  },

  onMatchPick(event: WechatMiniprogram.CustomEvent) {
    const index = Number(event.detail.value) || 0;
    this.setData({
      newMatchIndex: index,
      newActivityIndex: index > 0 ? 0 : this.data.newActivityIndex,
    });
  },

  onActivityPick(event: WechatMiniprogram.CustomEvent) {
    const index = Number(event.detail.value) || 0;
    this.setData({
      newActivityIndex: index,
      newMatchIndex: index > 0 ? 0 : this.data.newMatchIndex,
    });
  },

  async submitCreate(): Promise<void> {
    if (this.data.creating) {
      return;
    }
    const titleError = contentAlbumTitleError(this.data.newAlbumTitle);
    if (titleError) {
      this.setData({ createError: titleError });
      return;
    }
    const match =
      this.data.newMatchIndex > 0 ? this.data.matchOptions[this.data.newMatchIndex - 1] : null;
    const activity =
      this.data.newActivityIndex > 0
        ? this.data.activityOptions[this.data.newActivityIndex - 1]
        : null;
    this.setData({ creating: true, createError: '' });
    try {
      const album = await createAlbum({
        title: this.data.newAlbumTitle.trim(),
        matchId: match?.id ?? null,
        activityId: activity?.id ?? null,
      });
      this.setData({ createOpen: false, creating: false });
      wx.showToast({ title: '相册已创建', icon: 'none' });
      void wx.navigateTo({ url: `/pages/album-detail/index?id=${album.id}` });
    } catch (error) {
      this.setData({
        creating: false,
        createError: errorMessage(error, '创建失败，请稍后重试'),
      });
    }
  },
});
