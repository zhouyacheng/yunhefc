<script setup lang="ts">
import { describeError } from '@yunhefc/api-client';
import { CONTENT_DELETE_ANY, CONTENT_MANAGE_ANY, hasPermission } from '@yunhefc/permissions';
import type { ActivitySummary, AlbumSummary, ContentPost, MatchSummary } from '@yunhefc/shared';
import {
  CONTENT_ALBUM_TITLE_MAX_LENGTH,
  CONTENT_POST_MAX_LENGTH,
  CONTENT_REPORT_POST,
  avatarPlaceholder,
  contentAlbumTitleError,
  contentOwnerText,
  contentPostTextError,
  formatDateTime,
  formatDayStartTime,
  isDisplayableAvatarUrl,
} from '@yunhefc/shared';
import { Button, Empty, Loading, Pagination, Popup, showToast } from 'vant';
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import ContentAccessGate from '../components/ContentAccessGate.vue';
import {
  createAlbum,
  createPost,
  deleteAlbum,
  deletePost,
  listActivityOptions,
  listAlbums,
  listMatchOptions,
  listPosts,
  setPostLike,
} from '../features/content/api';
import { confirmAction } from '../features/common/confirm';
import {
  CONTENT_LOCATE_MAX_PAGES,
  CONTENT_LOCATE_PAGE_SIZE,
  CONTENT_PAGE_SIZE_OPTIONS,
  canDeleteOwned,
  toPositiveId,
} from '../features/content/presentation';
import { confirmAndReport } from '../features/content/report';
import { useContentAccess } from '../features/content/useContentAccess';
import { useSessionStore } from '../stores/session';

type TabKey = 'timeline' | 'albums';

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const { state: access, ensureLoaded } = useContentAccess();

function tabFromQuery(): TabKey {
  return route.query.tab === 'albums' || route.query.matchId || route.query.activityId
    ? 'albums'
    : 'timeline';
}

const activeTab = ref<TabKey>(tabFromQuery());
const matchId = ref<number | null>(toPositiveId(route.query.matchId));
const activityId = ref<number | null>(toPositiveId(route.query.activityId));
const focusPostId = ref<number | null>(toPositiveId(route.query.post));

const canDeleteAny = computed(() => hasPermission(session.permissions, CONTENT_DELETE_ANY));
const canManageAlbums = computed(() => hasPermission(session.permissions, CONTENT_MANAGE_ANY));

/** 视图/范围/通知定位一旦变化都写回 URL，刷新与前进后退保持一致。 */
function syncQuery(): void {
  const query: Record<string, string> = {};
  if (activeTab.value === 'albums') {
    query.tab = 'albums';
  }
  if (matchId.value !== null) {
    query.matchId = String(matchId.value);
  }
  if (activityId.value !== null) {
    query.activityId = String(activityId.value);
  }
  if (activeTab.value === 'timeline' && focusPostId.value !== null) {
    query.post = String(focusPostId.value);
  }
  void router.replace({ path: '/posts', query });
}

/** 响应浏览器前进/后退：URL 变化后回填视图与范围并重取数据。 */
function applyRouteState(): void {
  const nextTab = tabFromQuery();
  const nextMatch = toPositiveId(route.query.matchId);
  const nextActivity = toPositiveId(route.query.activityId);
  const nextPost = toPositiveId(route.query.post);
  const tabChanged = nextTab !== activeTab.value;
  const scopeChanged = nextMatch !== matchId.value || nextActivity !== activityId.value;
  const postChanged = nextPost !== focusPostId.value;
  if (!tabChanged && !scopeChanged && !postChanged) {
    return;
  }
  activeTab.value = nextTab;
  matchId.value = nextMatch;
  activityId.value = nextActivity;
  focusPostId.value = nextPost;
  if (access.value === 'ok') {
    void loadCurrentTab(postChanged && nextPost !== null);
  }
}

watch(
  () => [route.query.tab, route.query.matchId, route.query.activityId, route.query.post],
  () => applyRouteState(),
);

// ---------- 时间线 ----------

const posts = ref<ContentPost[]>([]);
const postPage = ref(1);
const postPageSize = ref<number>(CONTENT_PAGE_SIZE_OPTIONS[0]);
const postTotal = ref(0);
const postsLoading = ref(false);
const postsError = ref('');
const highlightId = ref<number | null>(null);
let timelineSeq = 0;

const composerText = ref('');
const composerAlbumId = ref('');
const publishing = ref(false);
const composerError = ref('');
const albumChoices = ref<AlbumSummary[]>([]);

const composerLength = computed(() => composerText.value.trim().length);
const composerInvalid = computed(() => contentPostTextError(composerText.value));
const canPublish = computed(() => composerLength.value > 0 && !publishing.value);

const likeBusy = ref<number | null>(null);
const reportBusy = ref<number | null>(null);
const deletingPostId = ref<number | null>(null);

async function loadTimeline(): Promise<void> {
  const seq = ++timelineSeq;
  postsLoading.value = true;
  postsError.value = '';
  try {
    const result = await listPosts({ page: postPage.value, pageSize: postPageSize.value });
    if (seq !== timelineSeq) {
      return;
    }
    posts.value = result.items;
    postTotal.value = result.total;
    postPage.value = result.page;
  } catch (caught) {
    if (seq !== timelineSeq) {
      return;
    }
    posts.value = [];
    postTotal.value = 0;
    postsError.value = describeError(caught, '动态加载失败，请稍后重试');
  } finally {
    if (seq === timelineSeq) {
      postsLoading.value = false;
    }
  }
}

async function loadAlbumChoices(): Promise<void> {
  try {
    const result = await listAlbums({ page: 1, pageSize: 100 });
    albumChoices.value = result.items;
  } catch {
    albumChoices.value = [];
  }
}

async function publishPost(): Promise<void> {
  if (publishing.value) {
    return;
  }
  const textError = contentPostTextError(composerText.value);
  if (textError) {
    composerError.value = textError;
    return;
  }
  publishing.value = true;
  composerError.value = '';
  try {
    const post = await createPost({
      content: composerText.value.trim(),
      albumId: composerAlbumId.value ? Number(composerAlbumId.value) : null,
    });
    if (postPage.value === 1) {
      posts.value = [post, ...posts.value].slice(0, postPageSize.value);
    }
    postTotal.value += 1;
    composerText.value = '';
    composerAlbumId.value = '';
    showToast('已发布');
  } catch (caught) {
    composerError.value = describeError(caught, '发布失败，请稍后重试');
  } finally {
    publishing.value = false;
  }
}

async function toggleLike(post: ContentPost): Promise<void> {
  if (likeBusy.value !== null) {
    return;
  }
  likeBusy.value = post.id;
  try {
    const result = await setPostLike(post.id, !post.likedByMe);
    post.likedByMe = result.liked;
    post.likeCount = result.likeCount;
  } catch (caught) {
    showToast(describeError(caught, '操作失败，请稍后重试'));
  } finally {
    likeBusy.value = null;
  }
}

async function reportPost(post: ContentPost): Promise<void> {
  if (reportBusy.value !== null) {
    return;
  }
  reportBusy.value = post.id;
  try {
    await confirmAndReport(CONTENT_REPORT_POST, post.id);
  } finally {
    reportBusy.value = null;
  }
}

function canRemovePost(post: ContentPost): boolean {
  return canDeleteOwned(post.author.id, session.user?.id, canDeleteAny.value);
}

async function removePost(post: ContentPost): Promise<void> {
  if (deletingPostId.value !== null) {
    return;
  }
  if (!(await confirmAction('删除动态', '删除后不可恢复，确定删除该动态吗？', '删除'))) {
    return;
  }
  deletingPostId.value = post.id;
  try {
    await deletePost(post.id);
    posts.value = posts.value.filter((item) => item.id !== post.id);
    postTotal.value = Math.max(0, postTotal.value - 1);
    showToast('动态已删除');
  } catch (caught) {
    showToast(describeError(caught, '删除失败，请稍后重试'));
  } finally {
    deletingPostId.value = null;
  }
}

function onPostPageChange(next: number): void {
  postPage.value = next;
  void loadTimeline();
}

function onPostPageSizeChange(event: Event): void {
  postPageSize.value = Number((event.target as HTMLSelectElement).value);
  postPage.value = 1;
  void loadTimeline();
}

async function focusPost(id: number): Promise<void> {
  highlightId.value = id;
  await nextTick();
  document
    .querySelector(`[data-post-id="${id}"]`)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' });
  window.setTimeout(() => {
    if (highlightId.value === id) {
      highlightId.value = null;
    }
  }, 4000);
}

/** 通知落点：在最近 5 页（每页 100）内定位动态，避免额外新增接口。 */
async function locatePost(id: number): Promise<void> {
  if (posts.value.some((post) => post.id === id)) {
    await focusPost(id);
    return;
  }
  try {
    for (let page = 1; page <= CONTENT_LOCATE_MAX_PAGES; page += 1) {
      const result = await listPosts({ page, pageSize: CONTENT_LOCATE_PAGE_SIZE });
      if (result.items.some((post) => post.id === id)) {
        posts.value = result.items;
        postPage.value = page;
        postPageSize.value = CONTENT_LOCATE_PAGE_SIZE;
        postTotal.value = result.total;
        await focusPost(id);
        return;
      }
      if (page >= result.totalPages) {
        return;
      }
    }
  } catch {
    // 定位失败不影响正常时间线展示。
  }
}

/** 签名 URL 失败时显示占位，避免破图。 */
const brokenCovers = ref<Record<string, true>>({});

function showCover(url: string | null): boolean {
  return !!url && !brokenCovers.value[url];
}

function markCoverBroken(url: string | null): void {
  if (url) {
    brokenCovers.value[url] = true;
  }
}

// ---------- 相册 ----------

const albums = ref<AlbumSummary[]>([]);
const albumPage = ref(1);
const albumPageSize = ref<number>(CONTENT_PAGE_SIZE_OPTIONS[0]);
const albumTotal = ref(0);
const albumsLoading = ref(false);
const albumsError = ref('');
let albumsSeq = 0;

const scopeText = computed(() => {
  if (matchId.value === null && activityId.value === null) {
    return '';
  }
  const owner = albums.value[0]?.ownerName;
  if (owner) {
    return owner;
  }
  return matchId.value !== null ? `比赛 #${matchId.value}` : `活动 #${activityId.value}`;
});

async function loadAlbums(): Promise<void> {
  const seq = ++albumsSeq;
  albumsLoading.value = true;
  albumsError.value = '';
  try {
    const result = await listAlbums({
      matchId: matchId.value ?? undefined,
      activityId: activityId.value ?? undefined,
      page: albumPage.value,
      pageSize: albumPageSize.value,
    });
    if (seq !== albumsSeq) {
      return;
    }
    albums.value = result.items;
    albumTotal.value = result.total;
    albumPage.value = result.page;
  } catch (caught) {
    if (seq !== albumsSeq) {
      return;
    }
    albums.value = [];
    albumTotal.value = 0;
    albumsError.value = describeError(caught, '相册加载失败，请稍后重试');
  } finally {
    if (seq === albumsSeq) {
      albumsLoading.value = false;
    }
  }
}

function clearScope(): void {
  matchId.value = null;
  activityId.value = null;
  albumPage.value = 1;
  syncQuery();
  void loadAlbums();
}

function openAlbum(album: AlbumSummary): void {
  void router.push(`/posts/albums/${album.id}`);
}

async function removeAlbum(album: AlbumSummary): Promise<void> {
  if (!canManageAlbums.value) {
    return;
  }
  if (!(await confirmAction('删除相册', `删除「${album.title}」及其中照片？`, '删除'))) {
    return;
  }
  try {
    await deleteAlbum(album.id);
    showToast('相册已删除');
    await loadAlbums();
    await loadAlbumChoices();
  } catch (caught) {
    showToast(describeError(caught, '删除失败，请稍后重试'));
  }
}

function onAlbumPageChange(next: number): void {
  albumPage.value = next;
  void loadAlbums();
}

function onAlbumPageSizeChange(event: Event): void {
  albumPageSize.value = Number((event.target as HTMLSelectElement).value);
  albumPage.value = 1;
  void loadAlbums();
}

// ---------- 新建相册 ----------

const createOpen = ref(false);
const newAlbum = reactive({ title: '', matchId: '', activityId: '' });
const createError = ref('');
const creating = ref(false);
const matchOptions = ref<MatchSummary[]>([]);
const activityOptions = ref<ActivitySummary[]>([]);
const optionsLoading = ref(false);
const optionsLoaded = ref(false);
const optionsError = ref('');

function openCreate(): void {
  newAlbum.title = '';
  newAlbum.matchId = matchId.value !== null ? String(matchId.value) : '';
  newAlbum.activityId = activityId.value !== null ? String(activityId.value) : '';
  createError.value = '';
  createOpen.value = true;
  void loadOptions();
}

async function loadOptions(): Promise<void> {
  if (optionsLoaded.value || optionsLoading.value) {
    return;
  }
  optionsLoading.value = true;
  optionsError.value = '';
  try {
    const [matches, activities] = await Promise.all([listMatchOptions(), listActivityOptions()]);
    matchOptions.value = matches.items;
    activityOptions.value = activities.items;
    optionsLoaded.value = true;
  } catch (caught) {
    optionsError.value = describeError(caught, '关联对象加载失败，可先创建通用相册');
  } finally {
    optionsLoading.value = false;
  }
}

function onMatchChange(): void {
  if (newAlbum.matchId) {
    newAlbum.activityId = '';
  }
}

function onActivityChange(): void {
  if (newAlbum.activityId) {
    newAlbum.matchId = '';
  }
}

async function submitCreate(): Promise<void> {
  if (creating.value) {
    return;
  }
  const titleError = contentAlbumTitleError(newAlbum.title);
  if (titleError) {
    createError.value = titleError;
    return;
  }
  creating.value = true;
  createError.value = '';
  try {
    const album = await createAlbum({
      title: newAlbum.title.trim(),
      matchId: newAlbum.matchId ? Number(newAlbum.matchId) : null,
      activityId: newAlbum.activityId ? Number(newAlbum.activityId) : null,
    });
    createOpen.value = false;
    showToast('相册已创建');
    await router.push(`/posts/albums/${album.id}`);
  } catch (caught) {
    createError.value = describeError(caught, '创建失败，请稍后重试');
  } finally {
    creating.value = false;
  }
}

// ---------- 启动 ----------

async function loadCurrentTab(locateFocus = false): Promise<void> {
  if (activeTab.value === 'timeline') {
    await loadTimeline();
    if (locateFocus && focusPostId.value !== null) {
      await locatePost(focusPostId.value);
    }
    await loadAlbumChoices();
  } else {
    await loadAlbums();
  }
}

/** 切换双视图时按需加载目标视图，避免只切 UI 不取数。 */
function selectTab(next: TabKey): void {
  if (activeTab.value === next) {
    return;
  }
  activeTab.value = next;
  syncQuery();
  void loadCurrentTab();
}

async function boot(): Promise<void> {
  await ensureLoaded();
  if (access.value === 'ok') {
    await loadCurrentTab(true);
  }
}

onMounted(boot);
</script>

<template>
  <section class="page content-page">
    <header class="page-head">
      <p class="eyebrow">CONTENT</p>
      <h1>动态与相册</h1>
      <p class="muted">仅正式球员与管理员可见，不对外公开</p>
    </header>

    <ContentAccessGate
      :state="access"
      noun="动态与相册"
      test-id-prefix="content"
      @retry="boot"
    />

    <template v-if="access === 'ok'">
      <nav class="segmented content-tabs" role="tablist" aria-label="内容视图">
        <button
          type="button"
          role="tab"
          :class="{ 'segmented__item--active': activeTab === 'timeline' }"
          :aria-selected="activeTab === 'timeline'"
          @click="selectTab('timeline')"
        >
          时间线
        </button>
        <button
          type="button"
          role="tab"
          :class="{ 'segmented__item--active': activeTab === 'albums' }"
          :aria-selected="activeTab === 'albums'"
          @click="selectTab('albums')"
        >
          相册
        </button>
      </nav>

      <div v-if="activeTab === 'timeline'">
        <section class="card content-composer">
          <textarea
            v-model="composerText"
            class="content-composer__input"
            rows="3"
            placeholder="说点什么，或者选一个相册分享照片…"
            aria-label="动态内容"
          />
          <div class="content-composer__row">
            <label class="content-composer__album">
              <span>引用相册</span>
              <select v-model="composerAlbumId" aria-label="引用相册">
                <option value="">不引用相册（纯文字）</option>
                <option v-for="album in albumChoices" :key="album.id" :value="String(album.id)">
                  {{ album.title }}
                </option>
              </select>
            </label>
            <span class="muted small">{{ composerLength }}/{{ CONTENT_POST_MAX_LENGTH }}</span>
            <Button
              type="primary"
              size="small"
              :loading="publishing"
              :disabled="!canPublish"
              data-testid="post-publish"
              @click="publishPost"
            >
              发布
            </Button>
          </div>
          <p v-if="composerError" class="action-error" data-testid="post-composer-error">
            {{ composerError }}
          </p>
          <p v-else-if="composerText.trim() && composerInvalid" class="action-error">
            {{ composerInvalid }}
          </p>
        </section>

        <div v-if="postsLoading" class="state"><Loading size="24">加载中…</Loading></div>

        <div v-else-if="postsError" class="state">
          <p class="muted">{{ postsError }}</p>
          <Button size="small" type="primary" @click="loadTimeline">重试</Button>
        </div>

        <Empty v-else-if="!posts.length" description="暂无动态" />

        <template v-else>
          <article
            v-for="post in posts"
            :key="post.id"
            class="card content-post"
            :class="{ 'content-post--focus': highlightId === post.id }"
            :data-post-id="post.id"
            data-testid="content-post"
          >
            <header class="content-post__head">
              <span class="content-avatar">
                <img
                  v-if="isDisplayableAvatarUrl(post.author.avatarUrl)"
                  :src="post.author.avatarUrl ?? ''"
                  alt=""
                />
                <template v-else>{{ avatarPlaceholder(post.author.name) }}</template>
              </span>
              <div class="content-post__meta">
                <strong>{{ post.author.name }}</strong>
                <p class="muted small">{{ formatDateTime(post.createdAt) }}</p>
              </div>
            </header>

            <p class="content-post__text">{{ post.content }}</p>

            <RouterLink
              v-if="post.album"
              class="content-post__album"
              :to="`/posts/albums/${post.album.id}`"
            >
              <span class="content-post__cover">
                <img
                  v-if="showCover(post.album.coverThumbnailUrl)"
                  :src="post.album.coverThumbnailUrl ?? ''"
                  alt=""
                  @error="markCoverBroken(post.album.coverThumbnailUrl)"
                />
                <span v-else class="muted small">
                  {{ post.album.coverThumbnailUrl ? '图片加载失败' : '暂无封面' }}
                </span>
              </span>
              <span class="content-post__album-main">
                <b>{{ post.album.title }}</b>
                <small>{{ contentOwnerText(post.album) }} · {{ post.album.photoCount }} 张</small>
              </span>
            </RouterLink>

            <footer class="content-post__actions">
              <button
                type="button"
                class="content-action"
                :class="{ 'content-action--active': post.likedByMe }"
                :disabled="likeBusy === post.id"
                :aria-pressed="post.likedByMe"
                data-testid="post-like"
                @click="toggleLike(post)"
              >
                ♥ {{ post.likeCount }}
              </button>
              <button
                type="button"
                class="content-action"
                :disabled="reportBusy === post.id"
                data-testid="post-report"
                @click="reportPost(post)"
              >
                举报
              </button>
              <button
                v-if="canRemovePost(post)"
                type="button"
                class="content-action content-action--danger"
                :disabled="deletingPostId === post.id"
                data-testid="post-delete"
                @click="removePost(post)"
              >
                删除
              </button>
            </footer>
          </article>

          <footer class="page-footer">
            <label class="page-size">
              每页
              <select
                :value="postPageSize"
                aria-label="动态每页条数"
                @change="onPostPageSizeChange"
              >
                <option v-for="size in CONTENT_PAGE_SIZE_OPTIONS" :key="size" :value="size">
                  {{ size }}
                </option>
              </select>
              条
            </label>
            <Pagination
              :model-value="postPage"
              mode="simple"
              :total-items="postTotal"
              :items-per-page="postPageSize"
              @change="onPostPageChange"
            />
          </footer>
        </template>
      </div>

      <div v-else>
        <div class="content-albums-head">
          <div>
            <h2>相册</h2>
            <p v-if="scopeText" class="muted small" data-testid="album-scope">
              仅显示「{{ scopeText }}」的相册
            </p>
          </div>
          <div class="content-albums-head__actions">
            <Button
              v-if="scopeText"
              size="small"
              plain
              data-testid="album-scope-clear"
              @click="clearScope"
            >
              查看全部
            </Button>
            <Button
              size="small"
              type="primary"
              plain
              data-testid="album-create"
              @click="openCreate"
            >
              新建相册
            </Button>
          </div>
        </div>

        <div v-if="albumsLoading" class="state"><Loading size="24">加载中…</Loading></div>

        <div v-else-if="albumsError" class="state">
          <p class="muted">{{ albumsError }}</p>
          <Button size="small" type="primary" @click="loadAlbums">重试</Button>
        </div>

        <Empty v-else-if="!albums.length" description="暂无相册" />

        <template v-else>
          <div class="content-album-grid">
            <article
              v-for="album in albums"
              :key="album.id"
              class="card content-album"
              data-testid="album-card"
            >
              <button
                type="button"
                class="content-album__main"
                :aria-label="`查看相册 ${album.title}`"
                @click="openAlbum(album)"
              >
                <span class="content-album__cover">
                  <img
                    v-if="showCover(album.coverThumbnailUrl)"
                    :src="album.coverThumbnailUrl ?? ''"
                    alt=""
                    @error="markCoverBroken(album.coverThumbnailUrl)"
                  />
                  <span v-else class="muted small">
                    {{ album.coverThumbnailUrl ? '图片加载失败' : '暂无封面' }}
                  </span>
                </span>
                <span class="content-album__body">
                  <b>{{ album.title }}</b>
                  <small>{{ contentOwnerText(album) }} · {{ album.photoCount }} 张</small>
                  <small class="muted">{{ formatDateTime(album.createdAt) }}</small>
                </span>
              </button>
              <Button
                v-if="canManageAlbums"
                size="mini"
                plain
                type="danger"
                @click.stop="removeAlbum(album)"
              >
                删除
              </Button>
            </article>
          </div>

          <footer class="page-footer">
            <label class="page-size">
              每页
              <select
                :value="albumPageSize"
                aria-label="相册每页条数"
                @change="onAlbumPageSizeChange"
              >
                <option v-for="size in CONTENT_PAGE_SIZE_OPTIONS" :key="size" :value="size">
                  {{ size }}
                </option>
              </select>
              条
            </label>
            <Pagination
              :model-value="albumPage"
              mode="simple"
              :total-items="albumTotal"
              :items-per-page="albumPageSize"
              @change="onAlbumPageChange"
            />
          </footer>
        </template>
      </div>
    </template>

    <Popup v-model:show="createOpen" round position="bottom">
      <div class="content-popup">
        <h3>新建相册</h3>
        <label class="content-field">
          <span>相册名称</span>
          <input
            v-model="newAlbum.title"
            type="text"
            :maxlength="CONTENT_ALBUM_TITLE_MAX_LENGTH"
            placeholder="1–64 字"
            aria-label="相册名称"
          />
        </label>
        <label class="content-field">
          <span>关联比赛（可选）</span>
          <select
            v-model="newAlbum.matchId"
            aria-label="关联比赛"
            :disabled="optionsLoading"
            @change="onMatchChange"
          >
            <option value="">不关联比赛</option>
            <option v-for="match in matchOptions" :key="match.id" :value="String(match.id)">
              {{ match.title }} · {{ formatDayStartTime(match.startAt) }}
            </option>
          </select>
        </label>
        <label class="content-field">
          <span>关联活动（可选）</span>
          <select
            v-model="newAlbum.activityId"
            aria-label="关联活动"
            :disabled="optionsLoading"
            @change="onActivityChange"
          >
            <option value="">不关联活动</option>
            <option
              v-for="activity in activityOptions"
              :key="activity.id"
              :value="String(activity.id)"
            >
              {{ activity.title }} · {{ formatDayStartTime(activity.startAt) }}
            </option>
          </select>
        </label>
        <p class="muted small">比赛与活动只能选一个；都不选即球队通用相册。</p>
        <p v-if="optionsError" class="hint">{{ optionsError }}</p>
        <p v-if="createError" class="action-error">{{ createError }}</p>
        <div class="content-popup__actions">
          <Button plain @click="createOpen = false">取消</Button>
          <Button
            type="primary"
            :loading="creating"
            data-testid="album-create-submit"
            @click="submitCreate"
          >
            创建
          </Button>
        </div>
      </div>
    </Popup>
  </section>
</template>

<style scoped>
.content-tabs {
  margin-bottom: 16px;
}

.content-composer {
  margin-bottom: 16px;
}

.content-composer__input {
  width: 100%;
  min-height: 88px;
  padding: 12px 14px;
  font: inherit;
  color: var(--yh-ink);
  resize: vertical;
  background: var(--yh-surface-soft);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius-sm);
}

.content-composer__row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-top: 12px;
}

.content-composer__album {
  display: inline-flex;
  gap: 8px;
  align-items: center;
  font-size: 13px;
  color: var(--yh-muted);
}

.content-composer__album select,
.content-field select,
.content-field input {
  min-height: 40px;
  padding: 6px 10px;
  color: var(--yh-ink);
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.content-composer__row > .muted {
  margin-right: auto;
}

.content-post {
  margin-bottom: 14px;
}

.content-post--focus {
  outline: 2px solid var(--yh-green-bright);
  outline-offset: 2px;
}

.content-post__head {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
}

.content-avatar {
  display: grid;
  flex: 0 0 40px;
  place-items: center;
  width: 40px;
  height: 40px;
  overflow: hidden;
  font-weight: 700;
  color: var(--yh-green-ink);
  background: var(--yh-green-soft);
  border-radius: 50%;
}

.content-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content-post__meta p {
  margin: 2px 0 0;
}

.content-post__text {
  margin: 0 0 12px;
  line-height: 1.65;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}

.content-post__album {
  display: flex;
  gap: 10px;
  align-items: center;
  padding: 10px;
  margin-bottom: 12px;
  color: inherit;
  text-decoration: none;
  background: var(--yh-surface-soft);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius-sm);
}

.content-post__cover {
  display: grid;
  flex: 0 0 64px;
  place-items: center;
  width: 64px;
  height: 48px;
  overflow: hidden;
  background: var(--yh-slate-soft);
  border-radius: 8px;
}

.content-post__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content-post__album-main {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.content-post__album-main small {
  color: var(--yh-muted);
}

.content-post__actions {
  display: flex;
  gap: 8px;
  align-items: center;
  padding-top: 10px;
  border-top: 1px dashed var(--yh-line);
}

.content-action {
  min-height: 34px;
  padding: 0 10px;
  font: inherit;
  font-size: 13px;
  color: var(--yh-muted);
  background: none;
  border: 0;
  border-radius: 8px;
  cursor: pointer;
}

.content-action:disabled {
  opacity: 0.6;
  cursor: default;
}

.content-action--active {
  color: var(--yh-green);
  font-weight: 700;
  background: var(--yh-green-soft);
}

.content-action--danger {
  margin-left: auto;
  color: var(--yh-red-ink);
}

.content-albums-head {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 16px;
}

.content-albums-head h2 {
  margin: 0;
  font-size: 18px;
}

.content-albums-head p {
  margin: 4px 0 0;
}

.content-albums-head__actions {
  display: flex;
  gap: 8px;
}

.content-album-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.content-album {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.content-album__main {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  background: none;
  border: 0;
  cursor: pointer;
}

.content-album__cover {
  display: grid;
  flex: 0 0 88px;
  place-items: center;
  width: 88px;
  height: 66px;
  overflow: hidden;
  background: var(--yh-slate-soft);
  border-radius: 10px;
}

.content-album__cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.content-album__body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
  font-size: 13px;
}

.content-album__body small {
  color: var(--yh-muted);
}

.content-popup {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px 20px 28px;
}

.content-popup h3 {
  margin: 0 0 4px;
}

.content-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--yh-muted);
}

.content-field input,
.content-field select {
  width: 100%;
}

.content-popup__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 1000px) {
  .content-album-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .content-album-grid {
    grid-template-columns: 1fr;
  }

  .content-composer__album {
    width: 100%;
  }

  .content-composer__album select {
    flex: 1;
  }
}
</style>
