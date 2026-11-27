import type { AlbumDetail, AlbumPhoto } from '../../types/api';
import {
  deleteAlbum,
  deleteAlbumPhoto,
  fetchAlbum,
  listAlbumPhotos,
  reportContent,
  updateAlbum,
  uploadAlbumPhoto,
} from '../../utils/api-client';
import { CONTENT_ALBUM_FULL, errorMessage, NOT_FOUND } from '../../utils/api-errors';
import { confirmDialog } from '../../utils/confirm';
import {
  buildContentPhotoRow,
  contentAccessState,
  contentAlbumTitleError,
  contentIdFromQuery,
  contentOwnerText,
  CONTENT_ALBUM_FULL_ERROR,
  CONTENT_ALBUM_MAX_PHOTOS,
  CONTENT_ALBUM_TITLE_MAX_LENGTH,
  CONTENT_PHOTO_MAX_EDGE,
  CONTENT_REPORT_PHOTO,
  type ContentAccessState,
  type ContentPhotoRow,
} from '../../utils/content';
import { contentPhotoSizeError, prepareContentPhoto } from '../../utils/content-photo';
import { formatDateTime } from '../../utils/datetime';
import {
  CONTENT_CREATE_OWN,
  CONTENT_DELETE_ANY,
  CONTENT_MANAGE_ANY,
  hasPermission,
} from '../../utils/permissions';
import { ApiError } from '../../utils/request';
import { getPermissions, getUser, sessionReady } from '../../utils/session';

interface UploadTask {
  key: number;
  name: string;
  status: 'uploading' | 'done' | 'error';
  message: string;
}

/** CNT-03 分页白名单 30/60/100；上拉加载用固定 30。 */
const PHOTO_PAGE_SIZE = 30;
/** 单次 chooseMedia 上限。 */
const PHOTO_UPLOAD_LIMIT = 9;

let requestSeq = 0;
let photosSeq = 0;

function isNotFound(error: unknown): boolean {
  return error instanceof ApiError && (error.code === NOT_FOUND || error.statusCode === 404);
}

function isAlbumFull(error: unknown): boolean {
  return error instanceof ApiError && error.code === CONTENT_ALBUM_FULL;
}

Page({
  data: {
    id: 0,
    access: 'loading' as 'loading' | ContentAccessState,
    notFound: false,
    album: null as AlbumDetail | null,
    albumOwnerText: '',
    albumTimeText: '',
    albumFull: false,
    detailLoading: true,
    detailError: '',
    photos: [] as ContentPhotoRow[],
    photoPage: 1,
    photoTotalPages: 0,
    photoTotal: 0,
    photosLoading: false,
    photosMoreLoading: false,
    photosError: '',
    brokenPhotos: {} as Record<string, true>,
    canManage: false,
    canUpload: false,
    canDeleteAny: false,
    userId: 0,
    uploading: false,
    uploadTasks: [] as UploadTask[],
    uploadMessage: '',
    editOpen: false,
    editTitle: '',
    editError: '',
    savingTitle: false,
    deletingAlbum: false,
    albumTitleMax: CONTENT_ALBUM_TITLE_MAX_LENGTH,
    photoMaxEdge: CONTENT_PHOTO_MAX_EDGE,
    albumMaxPhotos: CONTENT_ALBUM_MAX_PHOTOS,
  },

  booted: false,
  taskSeq: 0,

  onLoad(options: Record<string, string | undefined>) {
    // 内容不对外公开，不提供右上角转发入口（02 MP14）。
    wx.hideShareMenu();
    const id = contentIdFromQuery(options.id) ?? 0;
    this.setData({ id });
    if (!id) {
      this.setData({ access: 'ok', notFound: true, detailLoading: false });
      return;
    }
    void this.boot(false);
  },

  onShow() {
    if (this.booted) {
      void this.boot(true);
    }
  },

  onPullDownRefresh() {
    if (this.data.access !== 'ok' || !this.data.id) {
      wx.stopPullDownRefresh();
      return;
    }
    void Promise.all([this.loadDetail(), this.loadPhotos(true)]).then(() =>
      wx.stopPullDownRefresh(),
    );
  },

  onReachBottom() {
    if (this.data.access === 'ok') {
      void this.loadPhotos(false);
    }
  },

  onUnload() {
    requestSeq += 1;
    photosSeq += 1;
  },

  async boot(silent: boolean): Promise<void> {
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
      canManage: hasPermission(permissions, CONTENT_MANAGE_ANY),
      canUpload: hasPermission(permissions, CONTENT_CREATE_OWN),
      canDeleteAny: hasPermission(permissions, CONTENT_DELETE_ANY),
    });
    this.booted = true;
    if (access !== 'ok' || !this.data.id) {
      return;
    }
    await Promise.all([this.loadDetail(), this.loadPhotos(true)]);
  },

  retryAccess() {
    this.booted = false;
    void this.boot(false);
  },

  goLogin() {
    void wx.navigateTo({ url: '/pages/login/index' });
  },

  applyAlbum(album: AlbumDetail): void {
    this.setData({
      album,
      albumOwnerText: contentOwnerText(album),
      albumTimeText: formatDateTime(album.createdAt),
      albumFull: album.photoCount >= CONTENT_ALBUM_MAX_PHOTOS,
      detailLoading: false,
      detailError: '',
      notFound: false,
    });
  },

  async loadDetail(): Promise<void> {
    if (!this.data.id) {
      return;
    }
    if (!this.data.album) {
      this.setData({ detailLoading: true, detailError: '' });
    }
    try {
      const album = await fetchAlbum(this.data.id);
      this.applyAlbum(album);
    } catch (error) {
      if (isNotFound(error)) {
        this.setData({ notFound: true, detailLoading: false, album: null });
        return;
      }
      const message = errorMessage(error, '相册加载失败，请稍后重试');
      this.setData({ detailLoading: false, detailError: message });
      if (this.data.album) {
        wx.showToast({ title: message, icon: 'none' });
      }
    }
  },

  retryDetail() {
    void this.loadDetail();
  },

  async loadPhotos(reset: boolean): Promise<void> {
    if (
      !reset &&
      (this.data.photosLoading ||
        this.data.photosMoreLoading ||
        this.data.photoPage >= this.data.photoTotalPages)
    ) {
      return;
    }
    const seq = ++photosSeq;
    const page = reset ? 1 : this.data.photoPage + 1;
    this.setData(
      reset
        ? {
            photosLoading: this.data.photos.length === 0,
            photosMoreLoading: false,
            photosError: '',
          }
        : { photosMoreLoading: true, photosError: '' },
    );
    try {
      const result = await listAlbumPhotos(this.data.id, page, PHOTO_PAGE_SIZE);
      if (seq !== photosSeq) {
        return;
      }
      const viewer = {
        userId: this.data.userId || null,
        canDeleteAny: this.data.canDeleteAny,
      };
      const rows = result.items.map((photo) => buildContentPhotoRow(photo, viewer));
      this.setData({
        photos: reset ? rows : this.data.photos.concat(rows),
        photoPage: result.page,
        photoTotalPages: result.totalPages,
        photoTotal: result.total,
        photosLoading: false,
        photosMoreLoading: false,
        photosError: '',
      });
    } catch (error) {
      if (seq !== photosSeq) {
        return;
      }
      const message = errorMessage(error, '照片加载失败，请稍后重试');
      this.setData({ photosLoading: false, photosMoreLoading: false, photosError: message });
      if (this.data.photos.length > 0) {
        wx.showToast({ title: message, icon: 'none' });
      }
    }
  },

  retryPhotos() {
    void this.loadPhotos(true);
  },

  onPhotoError(event: WechatMiniprogram.CustomEvent) {
    const url = String(event.currentTarget.dataset.url ?? '');
    if (!url) {
      return;
    }
    this.setData({ brokenPhotos: { ...this.data.brokenPhotos, [url]: true } });
  },

  previewPhoto(event: WechatMiniprogram.BaseEvent) {
    const index = Number(event.currentTarget.dataset.index);
    const urls = this.data.photos.map((photo) => photo.originalUrl).filter(Boolean);
    if (!urls.length) {
      return;
    }
    const current = urls[Math.min(Math.max(index, 0), urls.length - 1)];
    wx.previewImage({ urls, current });
  },

  // ---------- 上传 ----------

  choosePhotos() {
    if (this.data.uploading) {
      return;
    }
    if (this.data.albumFull) {
      wx.showToast({ title: CONTENT_ALBUM_FULL_ERROR, icon: 'none' });
      return;
    }
    this.setData({ uploadMessage: '' });
    wx.chooseMedia({
      count: PHOTO_UPLOAD_LIMIT,
      mediaType: ['image'],
      sizeType: ['compressed'],
      sourceType: ['album', 'camera'],
      success: (result) => {
        if (result.tempFiles.length) {
          void this.uploadFiles(result.tempFiles);
        }
      },
      fail: (error) => {
        if (error.errMsg && error.errMsg.includes('cancel')) {
          return;
        }
        this.setData({ uploadMessage: '选择图片失败，请重试' });
      },
    });
  },

  renderTasks(tasks: UploadTask[]): void {
    this.setData({ uploadTasks: tasks.slice() });
  },

  /** 逐张压缩、校验并上传；第 201 张与超限图片以任务行提示。 */
  async uploadFiles(files: WechatMiniprogram.MediaFile[]): Promise<void> {
    const album = this.data.album;
    if (!album) {
      return;
    }
    this.setData({ uploading: true, uploadMessage: '', uploadTasks: [] });
    const tasks: UploadTask[] = [];
    let uploaded = 0;
    let remaining = CONTENT_ALBUM_MAX_PHOTOS - album.photoCount;
    for (const [index, file] of files.entries()) {
      const task: UploadTask = {
        key: ++this.taskSeq,
        name: `照片 ${index + 1}`,
        status: 'uploading',
        message: '',
      };
      tasks.push(task);
      this.renderTasks(tasks);
      if (remaining <= 0) {
        task.status = 'error';
        task.message = CONTENT_ALBUM_FULL_ERROR;
        this.renderTasks(tasks);
        continue;
      }
      try {
        const prepared = await prepareContentPhoto({
          tempFilePath: file.tempFilePath,
          size: file.size,
          width: file.width,
          height: file.height,
        });
        const sizeError = contentPhotoSizeError(prepared.size);
        if (sizeError) {
          task.status = 'error';
          task.message = sizeError;
          this.renderTasks(tasks);
          continue;
        }
        const result = await uploadAlbumPhoto(this.data.id, prepared.path);
        task.status = 'done';
        uploaded += 1;
        remaining = CONTENT_ALBUM_MAX_PHOTOS - result.photoCount;
        this.renderTasks(tasks);
        this.setData({
          'album.photoCount': result.photoCount,
          albumFull: result.photoCount >= CONTENT_ALBUM_MAX_PHOTOS,
        });
      } catch (error) {
        task.status = 'error';
        task.message = isAlbumFull(error)
          ? CONTENT_ALBUM_FULL_ERROR
          : errorMessage(error, '上传失败，请稍后重试');
        if (isAlbumFull(error)) {
          remaining = 0;
        }
        this.renderTasks(tasks);
      }
    }
    this.setData({ uploading: false });
    if (uploaded > 0) {
      wx.showToast({ title: `已上传 ${uploaded} 张`, icon: 'none' });
      await Promise.all([this.loadDetail(), this.loadPhotos(true)]);
    }
  },

  // ---------- 照片操作 ----------

  openPhotoActions(event: WechatMiniprogram.BaseEvent) {
    const id = Number(event.currentTarget.dataset.id);
    const photo = this.data.photos.find((item) => item.id === id);
    if (!photo) {
      return;
    }
    const actions: string[] = ['举报'];
    if (this.data.canManage) {
      actions.push('设为封面');
    }
    if (photo.canDelete) {
      actions.push('删除');
    }
    wx.showActionSheet({
      itemList: actions,
      success: (result) => {
        const action = actions[result.tapIndex];
        if (action === '举报') {
          void this.reportPhoto(photo);
        } else if (action === '设为封面') {
          void this.setCover(photo);
        } else if (action === '删除') {
          void this.removePhoto(photo);
        }
      },
    });
  },

  async reportPhoto(photo: AlbumPhoto): Promise<void> {
    if (!(await confirmDialog('举报照片', '确定举报该照片并通知管理员？'))) {
      return;
    }
    try {
      await reportContent(CONTENT_REPORT_PHOTO, photo.id);
      wx.showToast({ title: '已通知管理员', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '举报失败，请稍后重试'), icon: 'none' });
    }
  },

  async setCover(photo: AlbumPhoto): Promise<void> {
    try {
      this.applyAlbum(await updateAlbum(this.data.id, { coverPhotoId: photo.id }));
      wx.showToast({ title: '封面已更新', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '设置封面失败，请稍后重试'), icon: 'none' });
    }
  },

  async removePhoto(photo: AlbumPhoto): Promise<void> {
    if (!(await confirmDialog('删除照片', '删除后不可恢复，确定删除这张照片吗？'))) {
      return;
    }
    try {
      const result = await deleteAlbumPhoto(this.data.id, photo.id);
      const photos = this.data.photos.filter((item) => item.id !== photo.id);
      const photoCount = Math.max(0, (this.data.album?.photoCount ?? 1) - 1);
      this.setData({
        photos,
        photoTotal: Math.max(0, this.data.photoTotal - 1),
        'album.photoCount': photoCount,
        albumFull: photoCount >= CONTENT_ALBUM_MAX_PHOTOS,
      });
      if (result.coverReset) {
        await this.loadDetail();
      }
      if (!photos.length && this.data.photoPage > 1) {
        await this.loadPhotos(true);
      }
      wx.showToast({ title: '照片已删除', icon: 'none' });
    } catch (error) {
      wx.showToast({ title: errorMessage(error, '删除失败，请稍后重试'), icon: 'none' });
    }
  },

  // ---------- 相册管理 ----------

  openEdit() {
    const album = this.data.album;
    if (!album || !this.data.canManage) {
      return;
    }
    this.setData({ editOpen: true, editTitle: album.title, editError: '' });
  },

  closeEdit() {
    if (this.data.savingTitle) {
      return;
    }
    this.setData({ editOpen: false });
  },

  onEditTitleInput(event: WechatMiniprogram.CustomEvent) {
    this.setData({ editTitle: String(event.detail || ''), editError: '' });
  },

  async submitEdit(): Promise<void> {
    if (this.data.savingTitle) {
      return;
    }
    const titleError = contentAlbumTitleError(this.data.editTitle);
    if (titleError) {
      this.setData({ editError: titleError });
      return;
    }
    this.setData({ savingTitle: true, editError: '' });
    try {
      this.applyAlbum(await updateAlbum(this.data.id, { title: this.data.editTitle.trim() }));
      this.setData({ editOpen: false, savingTitle: false });
      wx.showToast({ title: '相册名称已更新', icon: 'none' });
    } catch (error) {
      this.setData({
        savingTitle: false,
        editError: errorMessage(error, '保存失败，请稍后重试'),
      });
    }
  },

  async removeAlbum(): Promise<void> {
    const album = this.data.album;
    if (!album || !this.data.canManage || this.data.deletingAlbum) {
      return;
    }
    if (!(await confirmDialog('删除相册', `删除「${album.title}」及其中照片？`))) {
      return;
    }
    this.setData({ deletingAlbum: true });
    try {
      await deleteAlbum(this.data.id);
      wx.showToast({ title: '相册已删除', icon: 'none' });
      wx.navigateBack({
        fail: () => {
          void wx.redirectTo({ url: '/pages/posts/index?tab=albums' });
        },
      });
    } catch (error) {
      this.setData({ deletingAlbum: false });
      wx.showToast({ title: errorMessage(error, '删除失败，请稍后重试'), icon: 'none' });
    }
  },
});
