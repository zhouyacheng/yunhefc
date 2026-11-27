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
import { formatDayStartTime } from '../../utils/datetime';
import { CONTENT_DELETE_ANY, CONTENT_MANAGE_ANY, hasPermission } from '../../utils/permissions';
import { getPermissions, getUser, sessionReady } from '../../utils/session';

type TabKey = 'timeline' | 'albums';

interface PickerOption {
  id: number;
  label: string;
}

const LIST_PAGE_SIZE = 10;
/** 通知定位动态：与 C14 同口径，最近 5 页（每页 100）内查找。 */
const LOCATE_PAGE_SIZE = 100;
const LOCATE_MAX_PAGES = 5;
const FOCUS_MS = 4000;

let requestSeq = 0;
let timelineSeq = 0;
let albumSeq = 0;

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
    posts: [] as ContentPostRow[],
    postPage: 1,
    /** 通知定位改用 100/页后保持同一分页口径（与 C14 一致）。 */
    postPageSize: LIST_PAGE_SIZE,
    postTotalPages: 0,
    postTotal: 0,
    postsLoading: false,
    postsMoreLoading: false,
    postsError: '',
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
    albums: [] as AlbumCardView[],
    albumPage: 1,
    albumTotalPages: 0,
    albumsLoading: false,
    albumsMoreLoading: false,
    albumsError: '',
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
    void this.boot(false, focusPostId > 0);
  },

  onShow() {
    // 登录状态与相册内容可能在其它页变化；首帧由 onLoad 负责，避免重复请求。
    if (this.booted) {
      void this.boot(true, false);
    }
  },

  onPullDownRefresh() {
    const job =
      this.data.access === 'ok'
        ? this.data.activeTab === 'timeline'
          ? this.loadTimeline(true)
          : this.loadAlbums(true)
        : this.boot(true, false);
    void job.then(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.access !== 'ok') {
      return;
    }
    if (this.data.activeTab === 'timeline') {
      void this.loadTimeline(false);
    } else {
      void this.loadAlbums(false);
    }
  },

  onUnload() {
    requestSeq += 1;
    timelineSeq += 1;
    albumSeq += 1;
    if (this.focusTimer) {
      clearTimeout(this.focusTimer);
      this.focusTimer = null;
    }
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
    if (this.data.activeTab === 'timeline') {
      await Promise.all([this.loadTimeline(true), this.loadAlbumChoices()]);
      if (locateFocus && this.data.focusPostId) {
        await this.locatePost(this.data.focusPostId);
      }
    } else {
      await this.loadAlbums(true);
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
    this.setData({ activeTab: tab, postsError: '', albumsError: '' });
    if (tab === 'timeline') {
      void this.loadTimeline(true);
      void this.loadAlbumChoices();
    } else {
      void this.loadAlbums(true);
    }
  },

  // ---------- 时间线 ----------

  async loadTimeline(reset: boolean): Promise<void> {
    if (
      !reset &&
      (this.data.postsLoading ||
        this.data.postsMoreLoading ||
        this.data.postPage >= this.data.postTotalPages)
    ) {
      return;
    }
    const seq = ++timelineSeq;
    const page = reset ? 1 : this.data.postPage + 1;
    this.setData(
      reset
        ? { postsLoading: this.data.posts.length === 0, postsMoreLoading: false, postsError: '' }
        : { postsMoreLoading: true, postsError: '' },
    );
    try {
      const result = await listPosts({ page, pageSize: this.data.postPageSize });
      if (seq !== timelineSeq) {
        return;
      }
      const rows = result.items.map((post) => buildContentPostRow(post, this.viewer()));
      this.setData({
        posts: reset ? rows : this.data.posts.concat(rows),
        postPage: result.page,
        postTotalPages: result.totalPages,
        postTotal: result.total,
        postsLoading: false,
        postsMoreLoading: false,
        postsError: '',
      });
    } catch (error) {
      if (seq !== timelineSeq) {
        return;
      }
      const message = errorMessage(error, '动态加载失败，请稍后重试');
      this.setData({ postsLoading: false, postsMoreLoading: false, postsError: message });
      if (this.data.posts.length > 0) {
        wx.showToast({ title: message, icon: 'none' });
      }
    }
  },

  retryTimeline() {
    void this.loadTimeline(true);
  },

  /** 通知定位：列表里没有时按最近 5 页查找并高亮滚动。 */
  async locatePost(id: number): Promise<void> {
    if (this.data.posts.some((post) => post.id === id)) {
      this.focusPost(id);
      return;
    }
    try {
      for (let page = 1; page <= LOCATE_MAX_PAGES; page += 1) {
        const result = await listPosts({ page, pageSize: LOCATE_PAGE_SIZE });
        const rows = result.items.map((post) => buildContentPostRow(post, this.viewer()));
        if (rows.some((post) => post.id === id)) {
          this.setData({
            posts: rows,
            postPage: result.page,
            postPageSize: LOCATE_PAGE_SIZE,
            postTotalPages: result.totalPages,
            postTotal: result.total,
          });
          this.focusPost(id);
          return;
        }
        if (page >= result.totalPages) {
          return;
        }
      }
    } catch {
      // 定位失败不影响正常时间线展示。
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
      this.setData({
        posts:
          this.data.postPage === 1
            ? [row, ...this.data.posts].slice(0, this.data.postPageSize)
            : this.data.posts,
        postTotal: this.data.postTotal + 1,
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
    if (this.data.likeBusyId) {
      return;
    }
    const index = this.data.posts.findIndex((post) => post.id === id);
    if (index < 0) {
      return;
    }
    this.setData({ likeBusyId: id });
    try {
      const result = await setPostLike(id, !this.data.posts[index].likedByMe);
      const posts = this.data.posts.slice();
      posts[index] = applyPostLike(posts[index], result);
      this.setData({ posts });
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
      this.setData({
        posts: this.data.posts.filter((post) => post.id !== id),
        postTotal: Math.max(0, this.data.postTotal - 1),
      });
      wx.showToast({ title: '动态已删除', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '删除失败，请稍后重试'), icon: 'none' });
    } finally {
      this.setData({ deleteBusyId: 0 });
    }
  },

  onAvatarError(event: WechatMiniprogram.CustomEvent) {
    const index = Number(event.currentTarget.dataset.index);
    if (!Number.isInteger(index) || index < 0 || index >= this.data.posts.length) {
      return;
    }
    this.setData({ [`posts[${index}].avatarUrl`]: '' });
  },

  onCoverError(event: WechatMiniprogram.CustomEvent) {
    const url = String(event.currentTarget.dataset.url ?? '');
    if (!url) {
      return;
    }
    this.setData({ coverBroken: { ...this.data.coverBroken, [url]: true } });
  },

  // ---------- 相册 ----------

  async loadAlbums(reset: boolean): Promise<void> {
    if (
      !reset &&
      (this.data.albumsLoading ||
        this.data.albumsMoreLoading ||
        this.data.albumPage >= this.data.albumTotalPages)
    ) {
      return;
    }
    const seq = ++albumSeq;
    const page = reset ? 1 : this.data.albumPage + 1;
    this.setData(
      reset
        ? {
            albumsLoading: this.data.albums.length === 0,
            albumsMoreLoading: false,
            albumsError: '',
          }
        : { albumsMoreLoading: true, albumsError: '' },
    );
    try {
      const result = await listAlbums({
        matchId: this.data.matchId || undefined,
        activityId: this.data.activityId || undefined,
        page,
        pageSize: LIST_PAGE_SIZE,
      });
      if (seq !== albumSeq) {
        return;
      }
      const rows = result.items.map(buildAlbumCardView);
      const scoped = this.data.matchId > 0 || this.data.activityId > 0;
      this.setData({
        albums: reset ? rows : this.data.albums.concat(rows),
        albumPage: result.page,
        albumTotalPages: result.totalPages,
        albumsLoading: false,
        albumsMoreLoading: false,
        albumsError: '',
        scopeText: scoped
          ? (rows[0]?.ownerText ?? scopeFallback(this.data.matchId, this.data.activityId))
          : '',
      });
    } catch (error) {
      if (seq !== albumSeq) {
        return;
      }
      const message = errorMessage(error, '相册加载失败，请稍后重试');
      this.setData({ albumsLoading: false, albumsMoreLoading: false, albumsError: message });
      if (this.data.albums.length > 0) {
        wx.showToast({ title: message, icon: 'none' });
      }
    }
  },

  retryAlbums() {
    void this.loadAlbums(true);
  },

  clearScope() {
    this.setData({ matchId: 0, activityId: 0, scopeText: '' });
    void this.loadAlbums(true);
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
      await Promise.all([this.loadAlbums(true), this.loadAlbumChoices()]);
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
