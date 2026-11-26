<script setup lang="ts">
import { ERROR_CODES, describeError, isApiError } from '@yunhefc/api-client';
import {
  CONTENT_CREATE_OWN,
  CONTENT_DELETE_ANY,
  CONTENT_MANAGE_ANY,
  hasPermission,
} from '@yunhefc/permissions';
import type { AlbumDetail, AlbumPhoto } from '@yunhefc/shared';
import {
  CONTENT_ALBUM_FULL_ERROR,
  CONTENT_ALBUM_MAX_PHOTOS,
  CONTENT_ALBUM_TITLE_MAX_LENGTH,
  CONTENT_PHOTO_MAX_EDGE,
  CONTENT_PHOTO_SIZE_ERROR,
  CONTENT_REPORT_PHOTO,
  compressContentPhoto,
  contentAlbumTitleError,
  contentOwnerText,
  contentPhotoSizeError,
  contentPhotoTypeError,
  formatDateTime,
} from '@yunhefc/shared';
import {
  ActionSheet,
  Button,
  Empty,
  Loading,
  Pagination,
  Popup,
  showImagePreview,
  showToast,
} from 'vant';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { confirmAction } from '../features/common/confirm';
import {
  albumDetail,
  albumPhotos,
  deleteAlbum,
  deleteAlbumPhoto,
  reportContent,
  updateAlbum,
  uploadAlbumPhoto,
} from '../features/content/api';
import {
  CONTENT_PHOTO_PAGE_SIZE_OPTIONS,
  canDeletePhoto as photoDeleteAllowed,
} from '../features/content/presentation';
import { useContentAccess } from '../features/content/useContentAccess';
import { toBrowserPath } from '../router';
import { useSessionStore } from '../stores/session';

interface UploadTask {
  key: number;
  name: string;
  percent: number;
  status: 'uploading' | 'done' | 'error';
  message: string;
}

interface PhotoAction {
  name: string;
  color?: string;
  callback: () => void;
}

const route = useRoute();
const router = useRouter();
const session = useSessionStore();
const { state: access, ensureLoaded } = useContentAccess();

const albumId = computed(() => {
  const parsed = Number(route.params.id);
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
});
const loginTarget = computed(() => ({
  name: 'login',
  query: { redirect: toBrowserPath(route.fullPath) },
}));

const album = ref<AlbumDetail | null>(null);
const photos = ref<AlbumPhoto[]>([]);
const page = ref(1);
const pageSize = ref<number>(CONTENT_PHOTO_PAGE_SIZE_OPTIONS[0]);
const total = ref(0);
const detailLoading = ref(false);
const detailError = ref('');
const photosLoading = ref(false);
const photosError = ref('');
let photosSeq = 0;

const canDeleteAny = computed(() => hasPermission(session.permissions, CONTENT_DELETE_ANY));
const canManage = computed(() => hasPermission(session.permissions, CONTENT_MANAGE_ANY));
const canUpload = computed(() => hasPermission(session.permissions, CONTENT_CREATE_OWN));

const brokenPhotos = ref<Record<number, true>>({});
const fileInput = ref<HTMLInputElement | null>(null);
const uploadTasks = ref<UploadTask[]>([]);
const uploading = ref(false);
const uploadMessage = ref('');
let uploadSeq = 0;

const albumFull = computed(() => (album.value?.photoCount ?? 0) >= CONTENT_ALBUM_MAX_PHOTOS);

const sheetOpen = ref(false);
const activePhoto = ref<AlbumPhoto | null>(null);

const sheetActions = computed<PhotoAction[]>(() => {
  const photo = activePhoto.value;
  if (!photo) {
    return [];
  }
  const actions: PhotoAction[] = [{ name: '举报', callback: () => void reportPhoto(photo) }];
  if (canManage.value) {
    actions.push({ name: '设为封面', callback: () => void setCover(photo) });
  }
  if (photoDeleteAllowed(photo, session.user?.id, canDeleteAny.value)) {
    actions.push({ name: '删除', color: '#dc2626', callback: () => void removePhoto(photo) });
  }
  return actions;
});

async function loadDetail(): Promise<void> {
  if (!albumId.value) {
    await router.replace('/404');
    return;
  }
  detailLoading.value = true;
  detailError.value = '';
  try {
    album.value = await albumDetail(albumId.value);
  } catch (caught) {
    if (isApiError(caught) && caught.code === ERROR_CODES.NOT_FOUND) {
      await router.replace('/404');
      return;
    }
    detailError.value = describeError(caught, '相册加载失败，请稍后重试');
  } finally {
    detailLoading.value = false;
  }
}

async function loadPhotos(): Promise<void> {
  if (!albumId.value) {
    return;
  }
  const seq = ++photosSeq;
  photosLoading.value = true;
  photosError.value = '';
  try {
    const result = await albumPhotos(albumId.value, page.value, pageSize.value);
    if (seq !== photosSeq) {
      return;
    }
    photos.value = result.items;
    total.value = result.total;
    page.value = result.page;
  } catch (caught) {
    if (seq !== photosSeq) {
      return;
    }
    photos.value = [];
    total.value = 0;
    photosError.value = describeError(caught, '照片加载失败，请稍后重试');
  } finally {
    if (seq === photosSeq) {
      photosLoading.value = false;
    }
  }
}

function onPageChange(next: number): void {
  page.value = next;
  void loadPhotos();
}

function onPageSizeChange(event: Event): void {
  pageSize.value = Number((event.target as HTMLSelectElement).value);
  page.value = 1;
  void loadPhotos();
}

function markBroken(photoId: number): void {
  brokenPhotos.value[photoId] = true;
}

function previewPhoto(index: number): void {
  showImagePreview({
    images: photos.value.map((photo) => photo.originalUrl),
    startPosition: index,
    closeable: true,
  });
}

function openSheet(photo: AlbumPhoto): void {
  activePhoto.value = photo;
  sheetOpen.value = true;
}

function uploadErrorMessage(caught: unknown): string {
  if (isApiError(caught) && caught.code === ERROR_CODES.CONTENT_ALBUM_FULL) {
    return CONTENT_ALBUM_FULL_ERROR;
  }
  if (caught instanceof Error && caught.message === CONTENT_PHOTO_SIZE_ERROR) {
    return CONTENT_PHOTO_SIZE_ERROR;
  }
  return describeError(caught, '上传失败，请稍后重试');
}

function triggerUpload(): void {
  if (uploading.value) {
    return;
  }
  if (albumFull.value) {
    uploadMessage.value = CONTENT_ALBUM_FULL_ERROR;
    return;
  }
  uploadMessage.value = '';
  fileInput.value?.click();
}

async function onFilesChange(event: Event): Promise<void> {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files ?? []);
  input.value = '';
  if (!files.length || uploading.value) {
    return;
  }
  uploading.value = true;
  uploadMessage.value = '';
  let uploaded = 0;
  for (const file of files) {
    const task: UploadTask = {
      key: ++uploadSeq,
      name: file.name,
      percent: 0,
      status: 'uploading',
      message: '',
    };
    uploadTasks.value.push(task);
    const typeError = contentPhotoTypeError(file.type);
    if (typeError) {
      task.status = 'error';
      task.message = typeError;
      continue;
    }
    if ((album.value?.photoCount ?? 0) >= CONTENT_ALBUM_MAX_PHOTOS) {
      task.status = 'error';
      task.message = CONTENT_ALBUM_FULL_ERROR;
      uploadMessage.value = CONTENT_ALBUM_FULL_ERROR;
      break;
    }
    try {
      const compressed = await compressContentPhoto(file);
      const sizeError = contentPhotoSizeError(compressed.size);
      if (sizeError) {
        throw new Error(sizeError);
      }
      const result = await uploadAlbumPhoto(albumId.value!, compressed, (percent) => {
        task.percent = percent;
      });
      task.status = 'done';
      task.percent = 100;
      uploaded += 1;
      if (album.value) {
        album.value.photoCount = result.photoCount;
      }
    } catch (caught) {
      task.status = 'error';
      task.message = uploadErrorMessage(caught);
    }
  }
  uploading.value = false;
  if (uploaded > 0) {
    await Promise.all([loadDetail(), loadPhotos()]);
  }
}

async function setCover(photo: AlbumPhoto): Promise<void> {
  try {
    const updated = await updateAlbum(albumId.value!, { coverPhotoId: photo.id });
    album.value = updated;
    showToast('封面已更新');
  } catch (caught) {
    showToast(describeError(caught, '设置封面失败，请稍后重试'));
  }
}

async function removePhoto(photo: AlbumPhoto): Promise<void> {
  const confirmed = await confirmAction('删除照片', '删除后不可恢复，确定删除这张照片吗？', '删除');
  if (!confirmed) {
    return;
  }
  try {
    const result = await deleteAlbumPhoto(albumId.value!, photo.id);
    photos.value = photos.value.filter((item) => item.id !== photo.id);
    total.value = Math.max(0, total.value - 1);
    if (album.value) {
      album.value.photoCount = Math.max(0, album.value.photoCount - 1);
    }
    if (result.coverReset) {
      await loadDetail();
    }
    if (!photos.value.length && page.value > 1) {
      page.value -= 1;
      await loadPhotos();
    }
    showToast('照片已删除');
  } catch (caught) {
    showToast(describeError(caught, '删除失败，请稍后重试'));
  }
}

async function reportPhoto(photo: AlbumPhoto): Promise<void> {
  const confirmed = await confirmAction('举报照片', '确定举报该照片并通知管理员？', '举报');
  if (!confirmed) {
    return;
  }
  try {
    await reportContent(CONTENT_REPORT_PHOTO, photo.id);
    showToast('已通知管理员');
  } catch (caught) {
    showToast(describeError(caught, '举报失败，请稍后重试'));
  }
}

// ---------- 相册管理 ----------

const editOpen = ref(false);
const editTitle = ref('');
const editError = ref('');
const savingTitle = ref(false);
const deletingAlbum = ref(false);

function openEdit(): void {
  if (!album.value) {
    return;
  }
  editTitle.value = album.value.title;
  editError.value = '';
  editOpen.value = true;
}

async function submitEdit(): Promise<void> {
  if (savingTitle.value) {
    return;
  }
  const error = contentAlbumTitleError(editTitle.value);
  if (error) {
    editError.value = error;
    return;
  }
  savingTitle.value = true;
  editError.value = '';
  try {
    album.value = await updateAlbum(albumId.value!, { title: editTitle.value.trim() });
    editOpen.value = false;
    showToast('相册名称已更新');
  } catch (caught) {
    editError.value = describeError(caught, '保存失败，请稍后重试');
  } finally {
    savingTitle.value = false;
  }
}

async function removeAlbum(): Promise<void> {
  if (deletingAlbum.value || !album.value) {
    return;
  }
  const confirmed = await confirmAction(
    '删除相册',
    `删除「${album.value.title}」及其中照片？`,
    '删除',
  );
  if (!confirmed) {
    return;
  }
  deletingAlbum.value = true;
  try {
    await deleteAlbum(albumId.value!);
    showToast('相册已删除');
    await router.replace('/posts?tab=albums');
  } catch (caught) {
    showToast(describeError(caught, '删除失败，请稍后重试'));
  } finally {
    deletingAlbum.value = false;
  }
}

/** 同一路由组件在相册 ID 变化（通知/前进后退）时重取数据，避免残留上一相册内容。 */
watch(albumId, () => {
  if (access.value !== 'ok') {
    return;
  }
  album.value = null;
  photos.value = [];
  total.value = 0;
  page.value = 1;
  void loadDetail();
  void loadPhotos();
});

onMounted(async () => {
  await ensureLoaded();
  if (access.value !== 'ok') {
    return;
  }
  await Promise.all([loadDetail(), loadPhotos()]);
});
</script>

<template>
  <section class="page content-page">
    <header class="page-head">
      <p class="eyebrow">ALBUM</p>
      <h1>{{ album?.title ?? '相册' }}</h1>
      <p v-if="album" class="muted">
        {{ contentOwnerText(album) }} · {{ album.photoCount }} 张 · 创建于
        {{ formatDateTime(album.createdAt) }}
      </p>
      <div v-if="album" class="album-head__actions">
        <Button size="small" plain @click="router.push('/posts?tab=albums')">返回相册</Button>
        <template v-if="canManage">
          <Button size="small" plain data-testid="album-edit" @click="openEdit">编辑名称</Button>
          <Button
            size="small"
            plain
            type="danger"
            :loading="deletingAlbum"
            data-testid="album-delete"
            @click="removeAlbum"
          >
            删除相册
          </Button>
        </template>
      </div>
    </header>

    <div v-if="access === 'loading'" class="state"><Loading size="24">加载中…</Loading></div>

    <section v-else-if="access === 'guest'" class="state card" data-testid="album-guest">
      <Empty description="登录后查看相册" />
      <p class="muted small">动态与相册仅对球队成员开放。</p>
      <RouterLink :to="loginTarget">
        <Button type="primary" size="small">去登录</Button>
      </RouterLink>
    </section>

    <section v-else-if="access === 'pending'" class="state card" data-testid="album-pending">
      <Empty description="账号待审核，暂不能查看相册" />
      <p class="muted small">通过审核后即可查看队内相册。</p>
    </section>

    <section v-else-if="access === 'blocked'" class="state card" data-testid="album-blocked">
      <Empty description="动态与相册仅正式球员与管理员可见" />
    </section>

    <div v-else-if="detailLoading" class="state"><Loading size="24">加载中…</Loading></div>

    <div v-else-if="detailError" class="state">
      <p class="muted">{{ detailError }}</p>
      <Button size="small" type="primary" @click="loadDetail">重试</Button>
    </div>

    <template v-else-if="album">
      <section class="card album-upload">
        <div class="card-head">
          <h2>照片</h2>
          <Button
            v-if="canUpload"
            type="primary"
            size="small"
            :disabled="uploading"
            :loading="uploading"
            data-testid="album-upload"
            @click="triggerUpload"
          >
            {{ albumFull ? '相册已满' : '上传照片' }}
          </Button>
        </div>
        <input
          ref="fileInput"
          class="album-upload__input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          multiple
          data-testid="album-photo-input"
          @change="onFilesChange"
        />
        <p class="muted small">
          支持 JPG/PNG/WebP；客户端压缩长边 {{ CONTENT_PHOTO_MAX_EDGE }}px，单张 ≤5 MB，单相册最多
          {{ CONTENT_ALBUM_MAX_PHOTOS }} 张。
        </p>
        <p v-if="uploadMessage" class="action-error" data-testid="album-upload-message">
          {{ uploadMessage }}
        </p>
        <ul v-if="uploadTasks.length" class="upload-tasks">
          <li
            v-for="task in uploadTasks"
            :key="task.key"
            class="upload-task"
            :class="`upload-task--${task.status}`"
          >
            <span class="upload-task__name">{{ task.name }}</span>
            <span class="upload-task__state">
              <template v-if="task.status === 'uploading'">{{ task.percent }}%</template>
              <template v-else-if="task.status === 'done'">已上传</template>
              <template v-else>{{ task.message }}</template>
            </span>
          </li>
        </ul>
      </section>

      <div v-if="photosLoading" class="state"><Loading size="24">加载中…</Loading></div>

      <div v-else-if="photosError" class="state">
        <p class="muted">{{ photosError }}</p>
        <Button size="small" type="primary" @click="loadPhotos">重试</Button>
      </div>

      <Empty v-else-if="!photos.length" description="相册还没有照片，点上方上传" />

      <template v-else>
        <div class="photo-grid">
          <figure
            v-for="(photo, index) in photos"
            :key="photo.id"
            class="photo-tile"
            data-testid="album-photo"
            :data-photo-id="photo.id"
          >
            <button
              type="button"
              class="photo-tile__open"
              :aria-label="`查看照片 ${photo.id}`"
              @click="previewPhoto(index)"
            >
              <img
                v-if="!brokenPhotos[photo.id]"
                :src="photo.thumbnailUrl"
                alt=""
                @error="markBroken(photo.id)"
              />
              <span v-else class="photo-tile__broken">图片加载失败</span>
            </button>
            <button
              type="button"
              class="photo-tile__menu"
              aria-label="照片操作"
              data-testid="album-photo-menu"
              @click="openSheet(photo)"
            >
              ⋯
            </button>
            <figcaption class="photo-tile__meta">
              {{ photo.uploaderName }} · {{ formatDateTime(photo.createdAt) }}
            </figcaption>
          </figure>
        </div>

        <footer class="page-footer">
          <label class="page-size">
            每页
            <select :value="pageSize" aria-label="照片每页条数" @change="onPageSizeChange">
              <option v-for="size in CONTENT_PHOTO_PAGE_SIZE_OPTIONS" :key="size" :value="size">
                {{ size }}
              </option>
            </select>
            条
          </label>
          <Pagination
            :model-value="page"
            mode="simple"
            :total-items="total"
            :items-per-page="pageSize"
            @change="onPageChange"
          />
        </footer>
      </template>
    </template>

    <Popup v-model:show="editOpen" round position="bottom">
      <div class="album-popup">
        <h3>编辑相册</h3>
        <label class="album-field">
          <span>相册名称</span>
          <input
            v-model="editTitle"
            type="text"
            :maxlength="CONTENT_ALBUM_TITLE_MAX_LENGTH"
            aria-label="相册名称"
          />
        </label>
        <p v-if="editError" class="action-error">{{ editError }}</p>
        <div class="album-popup__actions">
          <Button plain @click="editOpen = false">取消</Button>
          <Button
            type="primary"
            :loading="savingTitle"
            data-testid="album-edit-submit"
            @click="submitEdit"
          >
            保存
          </Button>
        </div>
      </div>
    </Popup>

    <ActionSheet
      v-model:show="sheetOpen"
      :actions="sheetActions"
      cancel-text="取消"
      close-on-click-action
    />
  </section>
</template>

<style scoped>
.album-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.album-upload {
  margin-bottom: 16px;
}

.album-upload__input {
  display: none;
}

.upload-tasks {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 0;
  margin: 12px 0 0;
  list-style: none;
}

.upload-task {
  display: flex;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  font-size: 12px;
  background: var(--yh-surface-soft);
  border-radius: 8px;
}

.upload-task--error {
  color: var(--yh-red-ink);
  background: var(--yh-red-soft);
}

.upload-task--done {
  color: var(--yh-green-ink);
  background: var(--yh-green-soft);
}

.upload-task__name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.upload-task__state {
  flex: 0 0 auto;
}

.photo-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.photo-tile {
  position: relative;
  margin: 0;
  overflow: hidden;
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: var(--yh-radius-sm);
}

.photo-tile__open {
  display: grid;
  place-items: center;
  width: 100%;
  aspect-ratio: 4 / 3;
  padding: 0;
  overflow: hidden;
  background: var(--yh-slate-soft);
  border: 0;
  cursor: zoom-in;
}

.photo-tile__open img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.photo-tile__broken {
  font-size: 12px;
  color: var(--yh-muted);
}

.photo-tile__menu {
  position: absolute;
  top: 6px;
  right: 6px;
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  font-size: 16px;
  color: var(--yh-ink);
  background: rgba(255, 255, 255, 0.88);
  border: 0;
  border-radius: 50%;
  cursor: pointer;
}

.photo-tile__meta {
  padding: 8px 10px;
  overflow: hidden;
  font-size: 11px;
  color: var(--yh-muted);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.album-popup {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding: 20px 20px 28px;
}

.album-popup h3 {
  margin: 0 0 4px;
}

.album-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 13px;
  color: var(--yh-muted);
}

.album-field input {
  min-height: 40px;
  padding: 6px 10px;
  color: var(--yh-ink);
  background: var(--yh-surface);
  border: 1px solid var(--yh-line);
  border-radius: 9px;
}

.album-popup__actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 8px;
}

@media (max-width: 1000px) {
  .photo-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .photo-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
</style>
