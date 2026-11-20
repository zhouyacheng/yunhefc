// 报名资格的唯一实现：首页、比赛详情、我的报名只做字段映射，不各自判断状态、名额、截止与待审核。
// 小程序不引共享包，靠 Vitest 锁定口径。
import type { CancellationResult, Registration } from '../types/api';
import { REGISTRATION_CREATE_OWN, hasPermission } from './permissions';

export const REGISTRATION_STATUS = {
  CONFIRMED: 0,
  WAITLISTED: 1,
  CANCELLED: 2,
  REMOVED: 3,
} as const;

export interface RegistrationQueue {
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
  confirmedCount: number;
  guestCount: number;
  waitlistCount: number;
}

export type SignupAction = 'login' | 'signup' | 'none';

/**
 * anonymous-未登录；pending-待审核；registered-本人已报名/候补；
 * open-可报名；waitlist-名额已满但可候补；blocked-其它不可报名原因。
 */
export type SignupDecisionKind =
  'anonymous' | 'pending' | 'registered' | 'open' | 'waitlist' | 'blocked';

export interface SignupDecision {
  kind: SignupDecisionKind;
  action: SignupAction;
  /** 是否展示报名入口。 */
  visible: boolean;
  /** 按钮文案；无入口（已报名/待审核）时为空。 */
  label: string;
  disabled: boolean;
  /** 无入口时是不可报名原因，有入口时是补充提示（如候补）。 */
  reason: string;
  /** 本人已报名/候补时的报名状态，否则 null。 */
  registrationStatus: number | null;
}

export interface CancelDecision {
  visible: boolean;
  label: string;
  disabled: boolean;
  reason: string;
}

export interface RegistrationDecision {
  signup: SignupDecision;
  cancel: CancelDecision;
}

export interface RegistrationEligibilityInput {
  authenticated: boolean;
  /** app_user.status：0-待审核。 */
  pending: boolean;
  /** player.type：0-外挂；1-正式；null 按外挂处理。 */
  playerType: number | null;
  permissions: readonly string[] | null | undefined;
  matchStatus: number;
  /** epoch 毫秒，由调用方注入以保持纯函数。 */
  now: number;
  signupDeadline: string;
  cancelLock: string;
  queue: RegistrationQueue;
  myRegistration?: Pick<Registration, 'status'> | null;
}

const MATCH_STATUS_SIGNUP_TEXT: Readonly<Record<number, string>> = {
  1: '报名已截止',
  2: '报名已锁定',
  3: '比赛已开始',
  4: '比赛已结束',
  5: '比赛已延期',
  6: '比赛已取消',
};

const MATCH_STATUS_CANCEL_TEXT: Readonly<Record<number, string>> = {
  2: '报名已锁定，请联系管理员',
  3: '比赛已开始，无法取消',
  4: '比赛已结束，无法取消',
  5: '比赛已延期，无法取消',
  6: '比赛已取消',
};

function isActive(status: number | undefined): boolean {
  return status === REGISTRATION_STATUS.CONFIRMED || status === REGISTRATION_STATUS.WAITLISTED;
}

/** 报名/候补/取消共用同一张角色 × 状态 × 时间窗判定；纯函数，不读时钟。 */
export function registrationDecision(input: RegistrationEligibilityInput): RegistrationDecision {
  return {
    signup: signupDecisionOf(input),
    cancel: cancelDecisionOf(input),
  };
}

function signupDecisionOf(input: RegistrationEligibilityInput): SignupDecision {
  if (!input.authenticated) {
    return {
      kind: 'anonymous',
      action: 'login',
      visible: true,
      label: '登录后报名',
      disabled: false,
      reason: '',
      registrationStatus: null,
    };
  }
  if (input.pending) {
    return {
      kind: 'pending',
      action: 'none',
      visible: false,
      label: '',
      disabled: true,
      reason: '账号待审核，通过后可报名',
      registrationStatus: null,
    };
  }
  const status = input.myRegistration?.status;
  if (isActive(status)) {
    return {
      kind: 'registered',
      action: 'none',
      visible: false,
      label: '',
      disabled: true,
      reason: '',
      registrationStatus: status ?? null,
    };
  }
  const blocked = signupBlockedReason(input);
  if (blocked !== '') {
    return blockedDecision(blocked);
  }

  const { queue } = input;
  if (input.playerType === 1) {
    if (queue.confirmedCount >= queue.capacity) {
      if (queue.waitlistCount < queue.waitlistCapacity) {
        return {
          kind: 'waitlist',
          action: 'signup',
          visible: true,
          label: '加入候补',
          disabled: false,
          reason: '正式名额已满，报名将进入候补',
          registrationStatus: null,
        };
      }
      return blockedDecision('名额与候补已满');
    }
    return openDecision();
  }
  if (queue.guestCount >= queue.guestCapacity) {
    return blockedDecision('外挂名额已满');
  }
  return openDecision();
}

function signupBlockedReason(input: RegistrationEligibilityInput): string {
  if (input.matchStatus !== 0) {
    return MATCH_STATUS_SIGNUP_TEXT[input.matchStatus] ?? '当前不可报名';
  }
  const deadline = Date.parse(input.signupDeadline);
  if (Number.isNaN(deadline)) {
    return '报名截止时间无效';
  }
  if (input.now >= deadline) {
    return '报名已截止';
  }
  if (!hasPermission(input.permissions, REGISTRATION_CREATE_OWN)) {
    return '没有报名权限';
  }
  return '';
}

function openDecision(): SignupDecision {
  return {
    kind: 'open',
    action: 'signup',
    visible: true,
    label: '报名',
    disabled: false,
    reason: '',
    registrationStatus: null,
  };
}

function blockedDecision(reason: string): SignupDecision {
  return {
    kind: 'blocked',
    action: 'none',
    visible: true,
    label: reason,
    disabled: true,
    reason: '',
    registrationStatus: null,
  };
}

function cancelDecisionOf(input: RegistrationEligibilityInput): CancelDecision {
  const status = input.myRegistration?.status;
  if (!input.authenticated || input.pending || !isActive(status)) {
    return { visible: false, label: '', disabled: true, reason: '' };
  }
  const label = status === REGISTRATION_STATUS.WAITLISTED ? '取消候补' : '取消报名';
  const reason = cancelBlockedReason(input);
  return { visible: true, label, disabled: reason !== '', reason };
}

function cancelBlockedReason(input: RegistrationEligibilityInput): string {
  if (input.matchStatus !== 0 && input.matchStatus !== 1) {
    return MATCH_STATUS_CANCEL_TEXT[input.matchStatus] ?? '当前比赛状态不可取消';
  }
  const lock = Date.parse(input.cancelLock);
  if (Number.isNaN(lock)) {
    return '取消截止时间无效';
  }
  if (input.now >= lock) {
    return '已过取消截止，请联系管理员';
  }
  return '';
}

/** 取消后提示；优先展示 REG-02 返回的真实递补结果。 */
export function promotionText(
  result: Pick<CancellationResult, 'promoted'>,
  promotedName?: string | null,
  cancelledStatus?: number | null,
): string {
  if (!result.promoted) {
    return cancelledStatus === REGISTRATION_STATUS.WAITLISTED ? '已取消候补' : '已取消报名';
  }
  return promotedName
    ? `已取消报名，${promotedName} 已递补为正式报名`
    : '已取消报名，候补球员已递补为正式报名';
}
