import { PHONE_PATTERN } from './auth';

/** 04 §2.4 球员类型。 */
export const PLAYER_TYPE = {
  GUEST: 0,
  PLAYER: 1,
} as const;

/** `app_user.status`：0-PENDING；1-ACTIVE；2-DISABLED。 */
export const USER_STATUS = {
  PENDING: 0,
  ACTIVE: 1,
  DISABLED: 2,
} as const;

export const PLAYER_TYPE_TEXT: Readonly<Record<number, string>> = {
  0: '外挂',
  1: '正式',
};

export const PLAYER_STATUS_TEXT: Readonly<Record<number, string>> = {
  0: '待审核',
  1: '正常',
  2: '已注销',
};

export function playerTypeText(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : (PLAYER_TYPE_TEXT[code] ?? '—');
}

export function playerStatusText(code: number | null | undefined): string {
  return code === null || code === undefined ? '—' : (PLAYER_STATUS_TEXT[code] ?? '—');
}

export type PlayerStatusTone = 'success' | 'warning' | 'danger' | 'info';

export function playerStatusTone(status: number | null | undefined): PlayerStatusTone {
  if (status === USER_STATUS.ACTIVE) {
    return 'success';
  }
  if (status === USER_STATUS.PENDING) {
    return 'warning';
  }
  if (status === USER_STATUS.DISABLED) {
    return 'danger';
  }
  return 'info';
}

/** PLR-05 sort 白名单；未知字段回退默认号码升序。 */
export const PLAYER_SORTABLE_FIELDS = ['jersey', 'skill', 'position', 'region'] as const;

export type PlayerSortOrder = 'ascending' | 'descending' | null | undefined;

export function playerSort(field: string | null | undefined, order: PlayerSortOrder): string {
  const allowed = (PLAYER_SORTABLE_FIELDS as readonly string[]).includes(field ?? '');
  if (!allowed || !order) {
    return 'jersey:asc';
  }
  return `${field}:${order === 'descending' ? 'desc' : 'asc'}`;
}

export function playerApprovable(player: { status: number | null }): boolean {
  return player.status === USER_STATUS.PENDING;
}

export function playerConvertible(player: { status: number | null; type: number }): boolean {
  return player.status === USER_STATUS.ACTIVE && player.type === PLAYER_TYPE.GUEST;
}

export function playerDisableable(player: { status: number | null }): boolean {
  return player.status === USER_STATUS.ACTIVE;
}

export interface PlayerProfileInput {
  name: string;
  jersey: number | null;
  region: string;
  position: number;
  secondaryPositions: readonly number[];
  skill: number;
}

/** PLR-06 前端校验，与后端 `PlayerUpdateRequest` 的范围保持一致。 */
export function playerProfileError(input: PlayerProfileInput): string | null {
  const name = input.name.trim();
  if (!name) {
    return '请输入姓名';
  }
  if (name.length > 32) {
    return '姓名最多 32 个字符';
  }
  if (
    input.jersey !== null &&
    (!Number.isInteger(input.jersey) || input.jersey < 0 || input.jersey > 65535)
  ) {
    return '号码必须为 0–65535 的整数';
  }
  if (input.region.trim().length > 64) {
    return '地区最多 64 个字符';
  }
  if (![0, 1, 2, 3].includes(input.position)) {
    return '请选择主位置';
  }
  if (input.secondaryPositions.length > 3) {
    return '副位置最多 3 个';
  }
  if (input.secondaryPositions.some((code) => ![0, 1, 2, 3].includes(code))) {
    return '副位置必须为 0–3';
  }
  if (new Set(input.secondaryPositions).size !== input.secondaryPositions.length) {
    return '副位置不能重复';
  }
  if (input.secondaryPositions.includes(input.position)) {
    return '副位置不能包含主位置';
  }
  if (!Number.isInteger(input.skill) || input.skill < 1 || input.skill > 5) {
    return '星级必须为 1–5';
  }
  return null;
}

/** AUTH-11 沿用后端 `^1\d{10}$`。 */
export function phoneError(phone: string): string | null {
  return PHONE_PATTERN.test(phone.trim()) ? null : '手机号格式不正确';
}
