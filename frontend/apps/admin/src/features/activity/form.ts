import type { ActivityType } from '@yunhefc/shared';
import {
  ACTIVITY_STATUS_TEXT,
  ACTIVITY_TYPE_TEXT,
  activityStatusText,
  activityTypeText,
} from '@yunhefc/shared';

export {
  ACTIVITY_STATUS_TEXT,
  ACTIVITY_TYPE_TEXT,
  activityStatusText,
  activityTypeText,
};

/** FIL-03：JPG/PNG/WebP，单张 ≤5 MiB，每活动最多 10 张（02 §6.14）。 */
export const ACTIVITY_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
export const ACTIVITY_IMAGE_MAX_COUNT = 10;
export const ACTIVITY_IMAGE_TYPE_ERROR = '仅支持 JPG/PNG/WebP';
export const ACTIVITY_IMAGE_SIZE_ERROR = '图片不能超过 5MB';
export const ACTIVITY_IMAGE_COUNT_ERROR = '场地环境图片最多 10 张';
export const ACTIVITY_IMAGE_UPLOAD_ERROR = '上传失败，请稍后重试';

const ACCEPTED_IMAGE_TYPES = new Set(['image/jpeg', 'image/png', 'image/webp']);

export const ACTIVITY_TYPE_OPTIONS = Object.entries(ACTIVITY_TYPE_TEXT).map(([value, text]) => ({
  value: Number(value) as ActivityType,
  text,
}));

export const ACTIVITY_STATUS_OPTIONS = Object.entries(ACTIVITY_STATUS_TEXT).map(
  ([value, text]) => ({
    value: String(value),
    text,
  }),
);

export interface ActivityFormValues {
  title: string;
  startAt: Date | null;
  endAt: Date | null;
  signupDeadline: Date | null;
  capacity: number | null;
  familyAllowed: boolean;
  costPerPerson: string;
  location: string;
  locationImageKeys: string[];
  description: string;
}

export function activityStatusTone(
  status: number,
): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (status === 0) {
    return 'primary';
  }
  if (status === 1) {
    return 'warning';
  }
  if (status === 2) {
    return 'success';
  }
  return status === 3 ? 'danger' : 'info';
}

/** 返回表单校验错误文案；通过返回 null。 */
export function activityFormError(form: ActivityFormValues): string | null {
  const title = form.title.trim();
  if (!title) {
    return '请输入活动名称';
  }
  if (title.length > 128) {
    return '活动名称不能超过 128 字';
  }
  if (!form.startAt) {
    return '请选择活动时间';
  }
  if (!form.signupDeadline) {
    return '请选择报名截止';
  }
  if (form.signupDeadline.getTime() > form.startAt.getTime()) {
    return '报名截止不能晚于活动时间';
  }
  if (form.endAt && form.endAt.getTime() <= form.startAt.getTime()) {
    return '结束时间必须晚于活动时间';
  }
  const capacity = form.capacity ?? null;
  if (capacity !== null && (!Number.isInteger(capacity) || capacity < 1 || capacity > 65535)) {
    return '总人数上限必须为 1–65535 的整数';
  }
  const cost = form.costPerPerson.trim();
  if (cost && !/^\d+(\.\d{1,2})?$/.test(cost)) {
    return '人均消费必须为非负数，最多两位小数';
  }
  if (cost && Number(cost) > 99_999_999.99) {
    return '人均消费不能超过 99,999,999.99';
  }
  if (form.locationImageKeys.length > ACTIVITY_IMAGE_MAX_COUNT) {
    return ACTIVITY_IMAGE_COUNT_ERROR;
  }
  const location = form.location.trim();
  if (!location) {
    return '请输入活动地址';
  }
  if (location.length > 128) {
    return '活动地址不能超过 128 字';
  }
  if (form.description.trim().length > 1000) {
    return '活动说明不能超过 1000 字';
  }
  return null;
}

/** 场地环境图片本地校验；返回错误文案或 null。 */
export function validateActivityImage(file: { type: string; size: number }): string | null {
  if (!ACCEPTED_IMAGE_TYPES.has(file.type)) {
    return ACTIVITY_IMAGE_TYPE_ERROR;
  }
  if (file.size > ACTIVITY_IMAGE_MAX_BYTES) {
    return ACTIVITY_IMAGE_SIZE_ERROR;
  }
  return null;
}
