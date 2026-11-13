export interface MatchInviteForm {
  expiresInDays: number | string | null;
  maxUses: number | string | null;
}

export interface MatchInvitePayload {
  expiresInDays: number;
  maxUses?: number;
}

export const DEFAULT_INVITE_DAYS = 7;

interface ParsedNumber {
  empty: boolean;
  value: number | null;
}

function parseNumber(value: number | string | null | undefined): ParsedNumber {
  if (value === null || value === undefined) {
    return { empty: true, value: null };
  }
  if (typeof value === 'string' && value.trim() === '') {
    return { empty: true, value: null };
  }
  const parsed = typeof value === 'number' ? value : Number(value.trim());
  return { empty: false, value: Number.isInteger(parsed) ? parsed : null };
}

/** INV-03 表单校验：有效期 1–30 天（空默认 7），次数留空不限。 */
export function inviteFormError(form: MatchInviteForm): string | null {
  const days = parseNumber(form.expiresInDays);
  if (!days.empty && (days.value === null || days.value < 1 || days.value > 30)) {
    return '有效期必须为 1–30 天';
  }
  const maxUses = parseNumber(form.maxUses);
  if (!maxUses.empty && (maxUses.value === null || maxUses.value < 1)) {
    return '使用次数上限必须大于 0';
  }
  return null;
}

/** 提交体：空有效期取默认 7，空次数不发送。 */
export function invitePayload(form: MatchInviteForm): MatchInvitePayload {
  const days = parseNumber(form.expiresInDays);
  const maxUses = parseNumber(form.maxUses);
  const payload: MatchInvitePayload = {
    expiresInDays:
      !days.empty && days.value !== null && days.value >= 1 && days.value <= 30
        ? days.value
        : DEFAULT_INVITE_DAYS,
  };
  if (!maxUses.empty && maxUses.value !== null && maxUses.value >= 1) {
    payload.maxUses = maxUses.value;
  }
  return payload;
}
