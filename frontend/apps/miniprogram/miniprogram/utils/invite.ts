// 比赛邀请：创建表单、落地页状态与信息行。
// 与 Web `features/invite/invite-state.ts`、`features/match-detail/invite.ts` 同口径；
// 小程序没有打包链路、不引共享包，按本地移植处理，改动需两处同步（由 tests/invite.test.ts 锁定）。
import type { InviteLanding, MatchDetail } from '../types/api';
import { formatDateWithYear, formatDateTimeWithYear, formatTimeRange } from './datetime';
import { seasonMatchText } from './presentation';

/** invite_link.type：0-入队；1-比赛。 */
export const INVITE_TYPE = { TEAM: 0, MATCH: 1 } as const;
/** invite_link.status：0-有效；1-已撤销；2-已过期。 */
export const INVITE_STATUS = { ACTIVE: 0, REVOKED: 1, EXPIRED: 2 } as const;

export const DEFAULT_INVITE_DAYS = 7;

const MIN_INVITE_DAYS = 1;
const MAX_INVITE_DAYS = 30;

export interface InviteForm {
  expiresInDays: number | string | null;
  maxUses: number | string | null;
}

export interface InvitePayload {
  expiresInDays: number;
  maxUses?: number;
}

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

/** 有效有效期（1–30 天）；越界或非整数返回 null，空值由调用方按默认 7 处理。 */
function validDays(parsed: ParsedNumber): number | null {
  return parsed.value !== null && parsed.value >= MIN_INVITE_DAYS && parsed.value <= MAX_INVITE_DAYS
    ? parsed.value
    : null;
}

/** 有效使用次数（≥1）；空值表示不限。 */
function validMaxUses(parsed: ParsedNumber): number | null {
  return parsed.value !== null && parsed.value >= 1 ? parsed.value : null;
}

/** INV-03 表单校验：有效期 1–30 天（空默认 7），次数留空不限。 */
export function inviteFormError(form: InviteForm): string | null {
  const days = parseNumber(form.expiresInDays);
  if (!days.empty && validDays(days) === null) {
    return '有效期必须为 1–30 天';
  }
  const maxUses = parseNumber(form.maxUses);
  if (!maxUses.empty && validMaxUses(maxUses) === null) {
    return '使用次数上限必须大于 0';
  }
  return null;
}

/** 提交体：空有效期取默认 7，空次数不发送。 */
export function invitePayload(form: InviteForm): InvitePayload {
  const days = validDays(parseNumber(form.expiresInDays));
  const uses = validMaxUses(parseNumber(form.maxUses));
  const payload: InvitePayload = { expiresInDays: days ?? DEFAULT_INVITE_DAYS };
  if (uses !== null) {
    payload.maxUses = uses;
  }
  return payload;
}

export function isInviteUsable(invite: InviteLanding): boolean {
  return invite.status === INVITE_STATUS.ACTIVE && !invite.exhausted;
}

/** 比赛 ID 可能在 `matchId` 或嵌套的 `match.id`；两处读取统一走这里。 */
export function matchIdOf(invite: InviteLanding): number | null {
  return invite.matchId ?? invite.match?.id ?? null;
}

function queryValue(query: Record<string, string | undefined>, key: string): string {
  return String(query[key] ?? '').trim();
}

/** 登录/绑定回跳参数只接受 `inviteToken`，不提供通用 redirect。 */
export function inviteTokenFromQuery(query: Record<string, string | undefined>): string {
  return queryValue(query, 'inviteToken');
}

/** 邀请落地页路由参数为 `token`；与回跳参数分开命名，避免混用。 */
export function inviteLandingTokenFromQuery(query: Record<string, string | undefined>): string {
  return queryValue(query, 'token');
}

export function inviteLandingPath(token: string): string {
  return `/pages/invite/index?token=${encodeURIComponent(token)}`;
}

export function inviteLoginPath(token: string): string {
  return token
    ? `/pages/login/index?inviteToken=${encodeURIComponent(token)}`
    : '/pages/login/index';
}

export function inviteBindPath(token: string): string {
  return token
    ? `/pages/phone-bind/index?inviteToken=${encodeURIComponent(token)}`
    : '/pages/phone-bind/index';
}

/** 登录/绑定成功后的统一落点：邀请回跳优先，否则回首页。 */
export function navigateAfterAuth(inviteToken: string): void {
  if (inviteToken) {
    void wx.redirectTo({ url: inviteLandingPath(inviteToken) });
    return;
  }
  void wx.switchTab({ url: '/pages/home/index' });
}

/** 失效原因按后端 status 枚举输出中文。 */
export function inviteInvalidText(invite: InviteLanding | null): string {
  if (invite?.status === INVITE_STATUS.EXPIRED) {
    return '该邀请已过期';
  }
  if (invite?.status === INVITE_STATUS.REVOKED) {
    return '该邀请已被撤销';
  }
  if (invite?.exhausted) {
    return '该邀请的使用次数已用完';
  }
  return '该邀请已失效';
}

/** maxUses 为 null 表示不限次数，不能显示「剩余 0 次」。 */
export function remainingUsesText(
  invite: Pick<InviteLanding, 'maxUses' | 'usedCount' | 'remainingUses'>,
): string {
  if (invite.maxUses === null) {
    return '不限';
  }
  const remaining = invite.remainingUses ?? Math.max(0, invite.maxUses - invite.usedCount);
  return `${remaining} 次`;
}

export type MatchInviteHint = 'open' | 'full' | 'closed';

/** 比赛邀请提示：先判报名截止，再判名额已满。 */
export function matchInviteHint(
  detail: Pick<MatchDetail, 'confirmedCount' | 'capacity' | 'signupDeadline'> | null,
  now: number,
): MatchInviteHint | null {
  if (!detail) {
    return null;
  }
  const deadline = Date.parse(detail.signupDeadline);
  if (Number.isFinite(deadline) && deadline <= now) {
    return 'closed';
  }
  if (detail.capacity > 0 && detail.confirmedCount >= detail.capacity) {
    return 'full';
  }
  return 'open';
}

export interface InviteFactRow {
  icon: string;
  label: string;
  value: string;
}

export interface InviteMatchView {
  title: string;
  dateText: string;
  timeRangeText: string;
  facts: InviteFactRow[];
  hintText: string;
}

function fact(icon: string, label: string, value: string): InviteFactRow {
  return { icon, label, value };
}

/** 落地页信息行与 C07 比赛邀请一致；MATCH-02 缺失时保留摘要，其余显示占位。 */
export function buildInviteMatchView(
  invite: InviteLanding,
  detail: MatchDetail | null,
  now: number = Date.now(),
): InviteMatchView {
  const startAt = invite.match?.startAt ?? detail?.startAt;
  const endAt = invite.match?.endAt ?? detail?.endAt;
  const hint = matchInviteHint(detail, now);
  return {
    title: invite.match?.title || detail?.title || '比赛',
    dateText: formatDateWithYear(startAt),
    timeRangeText: formatTimeRange(startAt, endAt) || '—',
    facts: [
      fact('⏰', '报名截止', detail ? formatDateTimeWithYear(detail.signupDeadline) : '—'),
      fact('🔒', '报名锁定', detail ? formatDateTimeWithYear(detail.cancelLock) : '—'),
      fact('⚽', '赛季场次', detail ? seasonMatchText(detail) || '—' : '—'),
      fact('📍', '场地', invite.match?.venue || detail?.venue || '待定'),
      fact('🎫', '名额', detail ? `${detail.confirmedCount} / ${detail.capacity}` : '—'),
      fact('🤝', '邀请人', invite.inviter?.name ?? '未知'),
      fact('⏳', '有效期至', formatDateTimeWithYear(invite.expiresAt)),
      fact('🔁', '剩余次数', remainingUsesText(invite)),
    ],
    hintText:
      hint === 'closed'
        ? '报名已截止'
        : hint === 'full'
          ? '名额已满，接受后可进入比赛查看候补'
          : '',
  };
}
