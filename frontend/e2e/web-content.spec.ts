import { Buffer } from 'node:buffer';

import { expect, test, type Page, type Route } from '@playwright/test';

import { collectConsoleErrors } from './support';

const CONTENT_PERMS = [
  'content:view:any',
  'content:create:own',
  'content:delete:any',
  'content:manage:any',
];

const MATCH = {
  id: 1001,
  title: '第3场 vs 聚龙FC',
  type: 0,
  typeLabel: 'FRIENDLY',
  startAt: '2099-12-01T10:00:00+08:00',
  endAt: '2099-12-01T12:00:00+08:00',
  venue: '聚龙足球场',
  season: '2099',
  homeTeamName: '橙队',
  awayTeamName: '聚龙FC',
  ourSide: 0,
  ourSideLabel: 'HOME',
  status: 0,
  statusLabel: 'SIGNING_UP',
  totalMatchNo: 3,
  seasonMatchNo: 3,
  capacity: 30,
  guestCapacity: 4,
  waitlistCapacity: 4,
  confirmedCount: 10,
  guestCount: 0,
  waitlistCount: 0,
  format: '8v8',
  signupDeadline: '2099-11-30T20:00:00+08:00',
  cancelLock: '2099-12-01T01:00:00+08:00',
  version: 0,
  myRegistration: null,
};

const ACTIVITY = {
  id: 9001,
  title: '赛季收官聚餐',
  type: 0,
  typeLabel: 'DINNER',
  startAt: '2099-12-06T18:30:00+08:00',
  endAt: '2099-12-06T21:00:00+08:00',
  signupDeadline: '2099-12-04T20:00:00+08:00',
  capacity: 20,
  familyAllowed: true,
  costPerPerson: 120,
  location: '老地方火锅',
  locationImageUrls: [],
  description: '赛季结束一起聚个餐。',
  status: 0,
  statusLabel: 'SIGNUP_OPEN',
  signupCount: 12,
  signupOpen: true,
  version: 0,
  mySignup: null,
};

/** 可被浏览器解码并重新压缩的 2×2 PNG；用作 mock 图片避免外网请求。 */
const VALID_PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAAqADAAQAAAABAAAAAgAAAADtGLyqAAAAFElEQVQIHWP4z8DAAMIM////ZwAAHu8E/CH5t8cAAAAASUVORK5CYII=',
  'base64',
);
const PIXEL_DATA_URL = `data:image/png;base64,${VALID_PNG.toString('base64')}`;

function albumSummary(overrides: Record<string, unknown> = {}) {
  return {
    id: 7001,
    title: '第3场 照片',
    matchId: 1001,
    activityId: null,
    ownerName: MATCH.title,
    coverThumbnailUrl: PIXEL_DATA_URL,
    photoCount: 2,
    createdBy: 2,
    createdAt: '2026-11-20T10:00:00+08:00',
    ...overrides,
  };
}

function albumDetail(overrides: Record<string, unknown> = {}) {
  return {
    ...albumSummary(overrides),
    coverUrl: PIXEL_DATA_URL,
  };
}

function photo(id: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    thumbnailUrl: PIXEL_DATA_URL,
    originalUrl: PIXEL_DATA_URL,
    uploadedBy: 2,
    uploaderName: '张伟',
    createdAt: '2026-11-20T10:00:00+08:00',
    ...overrides,
  };
}

const BASE_POST = {
  id: 50,
  author: { id: 2, name: '张伟', avatarUrl: null },
  content: '今天训练很带劲',
  album: albumSummary(),
  likeCount: 0,
  likedByMe: false,
  createdAt: '2026-11-20T10:00:00+08:00',
};

function envelope(data: unknown): unknown {
  return { code: 0, message: 'success', data, requestId: 'e2e' };
}

async function fulfill(route: Route, data: unknown, status = 200): Promise<void> {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(envelope(data)),
  });
}

function pageOf<T>(items: T[], pageSize = 10): unknown {
  return { items, total: items.length, page: 1, pageSize, totalPages: items.length ? 1 : 0 };
}

function meWith(permissions: string[], status = 1) {
  return {
    user: {
      id: 2,
      name: '张伟',
      phoneMasked: '139****0002',
      status,
      statusLabel: status === 0 ? 'PENDING' : 'ACTIVE',
      roles: ['PLAYER'],
      createdAt: '2026-01-01T00:00:00+08:00',
      wechatBound: true,
    },
    player: {
      id: 2,
      name: '张伟',
      jersey: 7,
      type: 1,
      typeLabel: 'FORMAL',
      position: 2,
      positionLabel: 'MID',
      region: '上海',
      avatarUrl: null,
      skill: 3.5,
    },
    roles: ['PLAYER'],
    permissions,
    unreadCount: 0,
  };
}

async function mockSession(page: Page, permissions = CONTENT_PERMS, status = 1): Promise<void> {
  await page.addInitScript(() => {
    localStorage.setItem('yunhefc.c.sessionHint', '1');
  });
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    (route) => fulfill(route, meWith(permissions, status)),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/notifications/unread-count',
    (route) => fulfill(route, { count: 0 }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/auth/csrf',
    (route) => fulfill(route, { token: 'csrf-token' }),
  );
}

interface DemoPost {
  id: number;
  author: { id: number; name: string; avatarUrl: string | null };
  content: string;
  album: ReturnType<typeof albumSummary> | null;
  likeCount: number;
  likedByMe: boolean;
  createdAt: string;
}

interface ContentState {
  albums: ReturnType<typeof albumSummary>[];
  posts: DemoPost[];
}

/** 内容接口的可变 mock：上传/删除/点赞会同步更新后续 GET 的结果。 */
async function mockContent(
  page: Page,
  state: ContentState,
): Promise<{
  postBodies: unknown[];
  likeBodies: unknown[];
  reportBodies: unknown[];
  albumPatchBodies: unknown[];
  albumCreateBodies: unknown[];
  uploadCount: () => number;
}> {
  const postBodies: unknown[] = [];
  const likeBodies: unknown[] = [];
  const reportBodies: unknown[] = [];
  const albumPatchBodies: unknown[] = [];
  const albumCreateBodies: unknown[] = [];
  let uploads = 0;

  const photosByAlbum = new Map<number, ReturnType<typeof photo>[]>([
    [7001, [photo(9001), photo(9002)]],
    [7002, []],
  ]);

  await page.route(
    (url) => url.pathname === '/api/v1/c/posts',
    async (route) => {
      if (route.request().method() === 'GET') {
        await fulfill(route, pageOf(state.posts));
        return;
      }
      const body = JSON.parse(route.request().postData() ?? '{}') as {
        content: string;
        albumId: number | null;
      };
      postBodies.push(body);
      const created = {
        ...BASE_POST,
        id: 51,
        content: body.content,
        album: body.albumId ? albumSummary({ id: body.albumId }) : null,
        createdAt: '2026-11-21T10:00:00+08:00',
      };
      state.posts = [created, ...state.posts];
      await fulfill(route, created);
    },
  );

  await page.route(
    (url) => /^\/api\/v1\/c\/posts\/\d+$/.test(url.pathname),
    async (route) => {
      if (route.request().method() !== 'DELETE') {
        await route.fallback();
        return;
      }
      const id = Number(new URL(route.request().url()).pathname.split('/').pop());
      state.posts = state.posts.filter((post) => post.id !== id);
      await fulfill(route, { deleted: true });
    },
  );

  await page.route(
    (url) => /^\/api\/v1\/c\/posts\/\d+\/like$/.test(url.pathname),
    async (route) => {
      const id = Number(new URL(route.request().url()).pathname.split('/').at(-2));
      const body = JSON.parse(route.request().postData() ?? '{}') as { liked: boolean };
      likeBodies.push({ id, liked: body.liked });
      const post = state.posts.find((item) => item.id === id);
      if (post) {
        post.likedByMe = body.liked;
        post.likeCount = body.liked ? 1 : 0;
      }
      await fulfill(route, { liked: body.liked, likeCount: body.liked ? 1 : 0 });
    },
  );

  await page.route(
    (url) => url.pathname === '/api/v1/c/content-reports',
    async (route) => {
      reportBodies.push(JSON.parse(route.request().postData() ?? '{}'));
      await fulfill(route, { reported: true });
    },
  );

  await page.route(
    (url) => url.pathname === '/api/v1/c/albums',
    async (route) => {
      if (route.request().method() === 'GET') {
        const params = new URL(route.request().url()).searchParams;
        const matchId = params.get('matchId');
        const activityId = params.get('activityId');
        const items = state.albums.filter(
          (album) =>
            (!matchId || String(album.matchId) === matchId) &&
            (!activityId || String(album.activityId) === activityId),
        );
        await fulfill(route, pageOf(items));
        return;
      }
      const body = JSON.parse(route.request().postData() ?? '{}') as {
        title: string;
        matchId: number | null;
        activityId: number | null;
      };
      albumCreateBodies.push(body);
      const created = albumSummary({
        id: 7002,
        title: body.title,
        matchId: body.matchId,
        activityId: body.activityId,
        ownerName: body.matchId ? MATCH.title : body.activityId ? ACTIVITY.title : null,
        coverThumbnailUrl: null,
        photoCount: 0,
      });
      state.albums = [created, ...state.albums];
      photosByAlbum.set(7002, []);
      await fulfill(route, albumDetail(created));
    },
  );

  await page.route(
    (url) => /^\/api\/v1\/c\/albums\/\d+$/.test(url.pathname),
    async (route) => {
      const id = Number(new URL(route.request().url()).pathname.split('/').pop());
      const found = state.albums.find((album) => album.id === id);
      if (route.request().method() === 'PATCH') {
        const body = JSON.parse(route.request().postData() ?? '{}') as Record<string, unknown>;
        albumPatchBodies.push(body);
        if (found) {
          Object.assign(found, body);
        }
        await fulfill(route, albumDetail(found ?? albumSummary({ id })));
        return;
      }
      if (route.request().method() === 'DELETE') {
        state.albums = state.albums.filter((album) => album.id !== id);
        await fulfill(route, { deleted: true, deletedPhotos: 1 });
        return;
      }
      if (!found) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: JSON.stringify({ code: 40401, message: '相册不存在', data: null }),
        });
        return;
      }
      await fulfill(route, albumDetail(found));
    },
  );

  await page.route(
    (url) => /^\/api\/v1\/c\/albums\/\d+\/photos$/.test(url.pathname),
    async (route) => {
      const albumId = Number(new URL(route.request().url()).pathname.split('/').at(-2));
      if (route.request().method() === 'POST') {
        uploads += 1;
        const contentType = route.request().headers()['content-type'] ?? '';
        expect(contentType).toContain('multipart/form-data');
        const uploaded = photo(9003 + uploads);
        const list = photosByAlbum.get(albumId) ?? [];
        photosByAlbum.set(albumId, [...list, uploaded]);
        const album = state.albums.find((item) => item.id === albumId);
        if (album) {
          album.photoCount = (photosByAlbum.get(albumId) ?? []).length;
        }
        await fulfill(route, {
          photo: uploaded,
          photoCount: photosByAlbum.get(albumId)?.length ?? 1,
        });
        return;
      }
      await fulfill(route, pageOf(photosByAlbum.get(albumId) ?? [], 30));
    },
  );

  await page.route(
    (url) => /^\/api\/v1\/c\/albums\/\d+\/photos\/\d+$/.test(url.pathname),
    async (route) => {
      const parts = new URL(route.request().url()).pathname.split('/');
      const albumId = Number(parts.at(-3));
      const photoId = Number(parts.at(-1));
      const list = (photosByAlbum.get(albumId) ?? []).filter((item) => item.id !== photoId);
      photosByAlbum.set(albumId, list);
      const album = state.albums.find((item) => item.id === albumId);
      if (album) {
        album.photoCount = list.length;
      }
      await fulfill(route, { deleted: true, coverReset: photoId === 9001 });
    },
  );

  await page.route(
    (url) => url.pathname === '/api/v1/c/matches' && url.searchParams.get('pageSize') === '100',
    (route) => fulfill(route, pageOf([MATCH], 100)),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities',
    (route) => fulfill(route, pageOf([ACTIVITY], 100)),
  );

  return {
    postBodies,
    likeBodies,
    reportBodies,
    albumPatchBodies,
    albumCreateBodies,
    uploadCount: () => uploads,
  };
}

test('C14 时间线：发布、点赞、举报、删除本人内容', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  const mocks = await mockContent(page, state);

  await page.goto('/posts');

  await expect(page.getByRole('heading', { name: '动态与相册' })).toBeVisible();
  await expect(page.getByTestId('content-post')).toHaveCount(1);
  await expect(page.getByTestId('content-post')).toContainText('今天训练很带劲');
  await expect(page.getByTestId('content-post')).toContainText('第3场 照片');

  // 发布引用相册的动态。
  await page.getByLabel('动态内容').fill('周末合影');
  await page.getByLabel('引用相册').selectOption('7001');
  await page.getByTestId('post-publish').click();
  await expect(page.getByTestId('content-post')).toHaveCount(2);
  await expect(page.getByTestId('content-post').first()).toContainText('周末合影');
  expect(mocks.postBodies).toEqual([{ content: '周末合影', albumId: 7001 }]);

  // 超过 2000 字被拒绝，不发请求。
  await page.getByLabel('动态内容').fill('x'.repeat(2001));
  await page.getByTestId('post-publish').click();
  await expect(page.getByTestId('post-composer-error')).toContainText('2000');
  expect(mocks.postBodies).toHaveLength(1);

  // 点赞/取消即时更新计数。
  const post = page.locator('[data-post-id="50"]');
  await post.getByTestId('post-like').click();
  await expect(post.getByTestId('post-like')).toHaveText('♥ 1');
  await expect(post.getByTestId('post-like')).toHaveAttribute('aria-pressed', 'true');
  await post.getByTestId('post-like').click();
  await expect(post.getByTestId('post-like')).toHaveText('♥ 0');
  expect(mocks.likeBodies).toEqual([
    { id: 50, liked: true },
    { id: 50, liked: false },
  ]);

  // 举报动态：确认后提示并提交 targetType=0。
  await post.getByTestId('post-report').click();
  await page.locator('.van-dialog__confirm').click();
  await expect(page.getByText('已通知管理员')).toBeVisible();
  expect(mocks.reportBodies).toEqual([{ targetType: 0, targetId: 50, reason: null }]);

  // 删除本人动态。
  await post.getByTestId('post-delete').click();
  await page.locator('.van-dialog__confirm').click();
  await expect(page.locator('[data-post-id="50"]')).toHaveCount(0);

  // 双视图切换时按需加载目标数据（未带 tab 参数进入也能拿到相册）。
  await page.getByRole('tab', { name: '相册' }).click();
  await expect(page.getByTestId('album-card')).toHaveCount(1);
  await page.getByRole('tab', { name: '时间线' }).click();
  await expect(page.getByTestId('content-post')).toHaveCount(1);

  expect(errors).toEqual([]);
});

test('C14 相册：按比赛过滤、新建相册并自动进入详情', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  const mocks = await mockContent(page, state);

  await page.goto('/posts?tab=albums&matchId=1001');

  await expect(page.getByTestId('album-scope')).toContainText(MATCH.title);
  await expect(page.getByTestId('album-card')).toHaveCount(1);
  await expect(page.getByTestId('album-card')).toContainText('第3场 照片');

  await page.getByTestId('album-create').click();
  await page.getByLabel('相册名称').fill('新相册');
  await page.getByTestId('album-create-submit').click();

  await expect(page).toHaveURL(/\/posts\/albums\/7002$/);
  await expect(page.getByRole('heading', { name: '新相册' })).toBeVisible();
  expect(mocks.albumCreateBodies).toEqual([{ title: '新相册', matchId: 1001, activityId: null }]);

  // 管理员删除相册后回到相册列表。
  await page.getByTestId('album-delete').click();
  await page.locator('.van-dialog__confirm').click();
  await expect(page).toHaveURL(/\/posts\?tab=albums&matchId=1001$/);

  expect(errors).toEqual([]);
});

test('C14 相册详情：上传、设封面、删除照片与编辑名称', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  const mocks = await mockContent(page, state);

  await page.goto('/posts/albums/7001');

  await expect(page.getByRole('heading', { name: '第3场 照片' })).toBeVisible();
  await expect(page.getByTestId('album-photo')).toHaveCount(2);

  // 上传 PNG：客户端压缩为 JPEG 后 multipart 上传。
  await page.getByTestId('album-photo-input').setInputFiles({
    name: 'photo.png',
    mimeType: 'image/png',
    buffer: VALID_PNG,
  });
  await expect(page.getByTestId('album-photo')).toHaveCount(3);
  expect(mocks.uploadCount()).toBe(1);

  // 管理员设封面。
  await page.getByTestId('album-photo').first().getByTestId('album-photo-menu').click();
  await page.locator('.van-action-sheet__item').filter({ hasText: '设为封面' }).click();
  await expect(page.getByText('封面已更新')).toBeVisible();
  expect(mocks.albumPatchBodies).toEqual([{ coverPhotoId: 9001 }]);

  // 删除照片。
  await page.getByTestId('album-photo').first().getByTestId('album-photo-menu').click();
  await page.locator('.van-action-sheet__item').filter({ hasText: '删除' }).click();
  await page.locator('.van-dialog__confirm').click();
  await expect(page.getByTestId('album-photo')).toHaveCount(2);

  // 编辑相册名称。
  await page.getByTestId('album-edit').click();
  await page.getByLabel('相册名称').fill('赛后合影');
  await page.getByTestId('album-edit-submit').click();
  await expect(page.getByRole('heading', { name: '赛后合影' })).toBeVisible();
  expect(mocks.albumPatchBodies.at(-1)).toEqual({ title: '赛后合影' });

  expect(errors).toEqual([]);
});

test('C14 相册已满：第 201 张提示且不发上传请求', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = {
    albums: [albumSummary({ photoCount: 200 })],
    posts: [BASE_POST],
  };
  const mocks = await mockContent(page, state);

  await page.goto('/posts/albums/7001');

  await expect(page.getByTestId('album-photo')).toHaveCount(2);
  await page.getByTestId('album-upload').click();
  await expect(page.getByTestId('album-upload-message')).toContainText('相册已满');
  expect(mocks.uploadCount()).toBe(0);

  expect(errors).toEqual([]);
});

test('C14 通知：CONTENT_PHOTO 进入相册，CONTENT_POST 定位动态', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  await mockContent(page, state);

  const notifications = [
    {
      id: 1,
      type: 14,
      typeLabel: 'CONTENT_REPORTED',
      title: '相册被举报',
      body: '有成员举报了相册照片',
      refType: 'CONTENT_PHOTO',
      refId: 7001,
      readAt: null,
      createdAt: '2026-11-20T10:00:00+08:00',
    },
    {
      id: 2,
      type: 14,
      typeLabel: 'CONTENT_REPORTED',
      title: '动态被举报',
      body: '有成员举报了动态',
      refType: 'CONTENT_POST',
      refId: 50,
      readAt: null,
      createdAt: '2026-11-20T10:00:00+08:00',
    },
  ];
  await page.route(
    (url) => url.pathname === '/api/v1/c/notifications',
    (route) => fulfill(route, pageOf(notifications)),
  );
  await page.route(
    (url) => /^\/api\/v1\/c\/notifications\/\d+\/read$/.test(url.pathname),
    (route) => fulfill(route, { readAt: '2026-11-20T11:00:00+08:00' }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/me/registrations',
    (route) => fulfill(route, pageOf([])),
  );

  await page.goto('/profile');
  await expect(page.getByText('相册被举报')).toBeVisible();
  await expect(page.getByText('动态被举报')).toBeVisible();

  await page.getByText('相册被举报').click();
  await expect(page).toHaveURL(/\/posts\/albums\/7001$/);
  await expect(page.getByRole('heading', { name: '第3场 照片' })).toBeVisible();

  await page.goto('/profile');
  await page.getByText('动态被举报').click();
  await expect(page).toHaveURL(/\/posts\?post=50$/);
  await expect(page.locator('[data-post-id="50"]')).toBeVisible();

  expect(errors).toEqual([]);
});

test('C01 动态入口：正式球员可进入，待审核不展示', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  await mockContent(page, state);
  let current = meWith(CONTENT_PERMS, 1);
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    (route) => fulfill(route, current),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches' && url.searchParams.get('pageSize') === '4',
    (route) => fulfill(route, pageOf([MATCH], 4)),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/1001',
    (route) => fulfill(route, MATCH),
  );

  await page.goto('/');
  const entry = page.locator('.home-entry[href="/posts"]');
  await expect(entry).toContainText('动态');
  await entry.click();
  await expect(page).toHaveURL(/\/posts$/);
  await expect(page.getByTestId('content-post')).toHaveCount(1);

  // 待审核账号首页不展示动态入口。
  current = meWith(CONTENT_PERMS, 0);
  await page.goto('/');
  await expect(page.locator('.home-entry[href="/posts"]')).toHaveCount(0);

  expect(errors).toEqual([]);
});

test('C14 访客：显示登录引导且不请求内容接口', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  const paths: string[] = [];
  page.on('request', (request) => {
    const path = new URL(request.url()).pathname;
    if (path === '/api/v1/c/posts' || path.startsWith('/api/v1/c/albums')) {
      paths.push(path);
    }
  });

  await page.goto('/posts');

  await expect(page.getByTestId('content-guest')).toContainText('登录后查看动态');
  expect(paths).toEqual([]);
  expect(errors).toEqual([]);
});

test('C14 待审核与外挂：不可见提示', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page, CONTENT_PERMS, 0);
  let current = meWith(CONTENT_PERMS, 0);
  // 后注册的 /me 覆盖 mockSession 的默认响应。
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    (route) => fulfill(route, current),
  );

  await page.goto('/posts');
  await expect(page.getByTestId('content-pending')).toContainText('账号待审核');

  // 外挂账号（无 content:view:any）不可见。
  current = meWith(['activity:signup:own']);
  await page.goto('/posts');
  await expect(page.getByTestId('content-blocked')).toContainText('仅正式球员与管理员可见');

  expect(errors).toEqual([]);
});

test('C14 会话探测失败：显示可重试错误态', async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem('yunhefc.c.sessionHint', '1');
  });
  let failing = true;
  await page.route(
    (url) => url.pathname === '/api/v1/c/me',
    async (route) => {
      if (failing) {
        await route.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ code: 50001, message: '服务异常', data: null }),
        });
        return;
      }
      await fulfill(route, meWith(CONTENT_PERMS));
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/notifications/unread-count',
    (route) => fulfill(route, { count: 0 }),
  );
  const state: ContentState = { albums: [albumSummary()], posts: [BASE_POST] };
  await mockContent(page, state);

  await page.goto('/posts');
  await expect(page.getByTestId('content-error')).toContainText('加载失败');

  failing = false;
  await page.getByTestId('content-error').getByRole('button', { name: '重试' }).click();
  await expect(page.getByTestId('content-post')).toHaveCount(1);
});

test('C03 比赛详情：无相册时自动创建默认相册并进入', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = { albums: [], posts: [] };
  const mocks = await mockContent(page, state);

  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/1001',
    (route) => fulfill(route, MATCH),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/1001/registrations/me',
    (route) => fulfill(route, { registration: null, attendance: null }),
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/matches/1001/events',
    (route) => fulfill(route, { items: [], derivedScore: null }),
  );

  await page.goto('/matches/1001');
  await page.getByTestId('match-photos').click();

  await expect(page).toHaveURL(/\/posts\/albums\/7002$/);
  await expect(page.getByRole('heading', { name: '第3场 vs 聚龙FC 照片' })).toBeVisible();
  expect(mocks.albumCreateBodies).toEqual([{ title: '第3场 vs 聚龙FC 照片', matchId: 1001 }]);

  expect(errors).toEqual([]);
});

test('C12 活动详情：有相册时按活动过滤进入相册列表', async ({ page }) => {
  const errors = collectConsoleErrors(page);
  await mockSession(page);
  const state: ContentState = {
    albums: [
      albumSummary({ id: 7101, matchId: null, activityId: 9001, ownerName: ACTIVITY.title }),
    ],
    posts: [],
  };
  const albumQueries: URLSearchParams[] = [];
  await mockContent(page, state);
  await page.route(
    (url) => url.pathname === '/api/v1/c/albums',
    async (route) => {
      const params = new URL(route.request().url()).searchParams;
      albumQueries.push(params);
      const items = state.albums.filter(
        (album) =>
          !params.get('activityId') || String(album.activityId) === params.get('activityId'),
      );
      await fulfill(route, pageOf(items));
    },
  );
  await page.route(
    (url) => url.pathname === '/api/v1/c/activities/9001',
    (route) => fulfill(route, ACTIVITY),
  );

  await page.goto('/activities/9001');
  await page.getByTestId('activity-photos').click();

  await expect(page).toHaveURL(/\/posts\?tab=albums&activityId=9001$/);
  await expect(page.getByTestId('album-card')).toHaveCount(1);
  expect(albumQueries.at(-1)?.get('activityId')).toBe('9001');

  expect(errors).toEqual([]);
});
