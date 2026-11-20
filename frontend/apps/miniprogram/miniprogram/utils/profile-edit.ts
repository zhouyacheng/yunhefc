import { POSITION_TEXT } from './presentation';

export const MAX_SECONDARY = 3;
export const MAX_AVATAR_BYTES = 2 * 1024 * 1024;

export const POSITION_CODES = Object.keys(POSITION_TEXT).map(Number);
export const POSITION_LABELS = POSITION_CODES.map((code) => POSITION_TEXT[code]);

const AVATAR_EXTENSIONS = new Set(['jpg', 'jpeg', 'png', 'webp']);

export interface SecondaryOption {
  code: number;
  label: string;
  checked: boolean;
  disabled: boolean;
}

export function withoutPrimary(positions: number[], primary: number): number[] {
  return positions.filter((position) => position !== primary);
}

export function buildOptions(primary: number, selected: number[]): SecondaryOption[] {
  return POSITION_CODES.map((code) => ({
    code,
    label: POSITION_TEXT[code],
    checked: selected.includes(code),
    disabled: code === primary,
  }));
}

/** 头像格式与大小校验；无法识别扩展名时交给服务端最终校验。 */
export function validateAvatar(size: number, filePath: string): string {
  if (size > MAX_AVATAR_BYTES) {
    return '头像不能超过 2MB';
  }
  const matched = /\.([a-z0-9]+)(?:\?|$)/i.exec(filePath);
  if (matched && !AVATAR_EXTENSIONS.has(matched[1].toLowerCase())) {
    return '头像仅支持 JPG/PNG/WebP';
  }
  return '';
}

export function validateProfile(name: string, region: string): string {
  const trimmed = name.trim();
  if (!trimmed) {
    return '请输入姓名';
  }
  if (trimmed.length > 32) {
    return '姓名最多 32 个字符';
  }
  if (region.trim().length > 64) {
    return '地区最多 64 个字符';
  }
  return '';
}
