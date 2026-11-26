/** M17 内容接口封装（05 §3.18 CNT-01~13）；页面不直接拼请求。 */
import type {
  ActivitySummary,
  AlbumDetail,
  AlbumPhoto,
  AlbumSummary,
  ContentPost,
  ContentReportTargetType,
  MatchSummary,
  Page,
} from '@yunhefc/shared';
import { newIdempotencyKey } from '@yunhefc/api-client';

import { api } from '../../api/client';

export interface AlbumListQuery {
  matchId?: number;
  activityId?: number;
  page?: number;
  pageSize?: number;
}

export interface PostListQuery {
  albumId?: number;
  page?: number;
  pageSize?: number;
}

export interface AlbumUpdateBody {
  title?: string;
  coverPhotoId?: number | null;
  matchId?: number | null;
  activityId?: number | null;
}

export interface CreateAlbumBody {
  title: string;
  matchId?: number | null;
  activityId?: number | null;
}

export interface CreatePostBody {
  content: string;
  albumId?: number | null;
}

/** CNT-01。 */
export function listAlbums(query: AlbumListQuery): Promise<Page<AlbumSummary>> {
  return api.get<Page<AlbumSummary>>('/albums', { query: { ...query } });
}

/** CNT-02。 */
export function albumDetail(id: number): Promise<AlbumDetail> {
  return api.get<AlbumDetail>(`/albums/${id}`);
}

/** CNT-03；pageSize 只接受 30/60/100。 */
export function albumPhotos(
  albumId: number,
  page: number,
  pageSize: number,
): Promise<Page<AlbumPhoto>> {
  return api.get<Page<AlbumPhoto>>(`/albums/${albumId}/photos`, { query: { page, pageSize } });
}

/** CNT-04。 */
export function createAlbum(body: CreateAlbumBody): Promise<AlbumDetail> {
  return api.post<AlbumDetail>('/albums', body, { idempotencyKey: newIdempotencyKey() });
}

/** CNT-05；只发需要修改的字段。 */
export function updateAlbum(id: number, body: AlbumUpdateBody): Promise<AlbumDetail> {
  return api.patch<AlbumDetail>(`/albums/${id}`, body, {
    idempotencyKey: newIdempotencyKey(),
  });
}

/** CNT-06。 */
export function deleteAlbum(id: number): Promise<{ deleted: boolean; deletedPhotos: number }> {
  return api.delete<{ deleted: boolean; deletedPhotos: number }>(`/albums/${id}`, undefined, {
    idempotencyKey: newIdempotencyKey(),
  });
}

/** CNT-07：multipart 单文件；上传按钮防重，不使用幂等键。 */
export function uploadAlbumPhoto(
  albumId: number,
  file: File,
  onUploadProgress?: (percent: number) => void,
): Promise<{ photo: AlbumPhoto; photoCount: number }> {
  const form = new FormData();
  form.append('file', file);
  return api.postForm(`/albums/${albumId}/photos`, form, { onUploadProgress });
}

/** CNT-08。 */
export function deleteAlbumPhoto(
  albumId: number,
  photoId: number,
): Promise<{ deleted: boolean; coverReset: boolean }> {
  return api.delete<{ deleted: boolean; coverReset: boolean }>(
    `/albums/${albumId}/photos/${photoId}`,
    undefined,
    { idempotencyKey: newIdempotencyKey() },
  );
}

/** CNT-09。 */
export function listPosts(query: PostListQuery): Promise<Page<ContentPost>> {
  return api.get<Page<ContentPost>>('/posts', { query: { ...query } });
}

/** CNT-10。 */
export function createPost(body: CreatePostBody): Promise<ContentPost> {
  return api.post<ContentPost>('/posts', body, { idempotencyKey: newIdempotencyKey() });
}

/** CNT-11。 */
export function deletePost(id: number): Promise<{ deleted: boolean }> {
  return api.delete<{ deleted: boolean }>(`/posts/${id}`, undefined, {
    idempotencyKey: newIdempotencyKey(),
  });
}

/** CNT-12：目标状态幂等，不使用幂等键。 */
export function setPostLike(
  id: number,
  liked: boolean,
): Promise<{ liked: boolean; likeCount: number }> {
  return api.put<{ liked: boolean; likeCount: number }>(`/posts/${id}/like`, { liked });
}

/** CNT-13。 */
export function reportContent(
  targetType: ContentReportTargetType,
  targetId: number,
  reason?: string,
): Promise<{ reported: boolean }> {
  return api.post<{ reported: boolean }>(
    '/content-reports',
    { targetType, targetId, reason: reason ?? null },
    { idempotencyKey: newIdempotencyKey() },
  );
}

/** C03/C12 相册入口建默认相册时用于回填标题。 */
export function listMatchOptions(): Promise<Page<MatchSummary>> {
  return api.get<Page<MatchSummary>>('/matches', {
    query: { pageSize: 100, sort: 'startAt:desc' },
  });
}

export function listActivityOptions(): Promise<Page<ActivitySummary>> {
  return api.get<Page<ActivitySummary>>('/activities', {
    query: { pageSize: 100, scope: 'all' },
  });
}
