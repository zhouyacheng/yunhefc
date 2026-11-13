import type { CancellationResult, Registration } from '@yunhefc/shared';
import { REGISTRATION_STATUS } from '@yunhefc/shared';

export interface RegistrationQueue {
  capacity: number;
  guestCapacity: number;
  waitlistCapacity: number;
  confirmedCount: number;
  guestCount: number;
  waitlistCount: number;
}

export type SignupAction = 'login' | 'signup' | 'none';

export interface SignupStateInput {
  authenticated: boolean;
  pending: boolean;
  /** player.type：0-外挂；1-正式；null 按外挂处理。 */
  playerType: number | null;
  matchStatus: number;
  /** epoch 毫秒，由调用方注入以保持纯函数。 */
  now: number;
  signupDeadline: string;
  queue: RegistrationQueue;
  myRegistration?: Pick<Registration, 'status'> | null;
  canSignup: boolean;
}

export interface SignupState {
  visible: boolean;
  label: string;
  action: SignupAction;
  disabled: boolean;
  /** 补充说明；禁用原因已由 label 表达时为空格。 */
  reason: string;
}

export interface CancelStateInput {
  authenticated: boolean;
  pending: boolean;
  matchStatus: number;
  cancelLock: string;
  myRegistration?: Pick<Registration, 'status'> | null;
  now: number;
}

export interface CancelState {
  visible: boolean;
  label: string;
  disabled: boolean;
  reason: string;
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

function hidden(): SignupState {
  return { visible: false, label: '', action: 'none', disabled: true, reason: '' };
}

function disabled(label: string): SignupState {
  return { visible: true, label, action: 'none', disabled: true, reason: '' };
}

function isActive(status: number | undefined): boolean {
  return status === REGISTRATION_STATUS.CONFIRMED || status === REGISTRATION_STATUS.WAITLISTED;
}

/** 本人报名卡 / 首页共用的报名状态机；纯函数，不读时钟。 */
export function signupState(input: SignupStateInput): SignupState {
  if (!input.authenticated) {
    return { visible: true, label: '登录后报名', action: 'login', disabled: false, reason: '' };
  }
  if (input.pending) {
    return {
      visible: false,
      label: '',
      action: 'none',
      disabled: true,
      reason: '账号待审核，通过后可报名',
    };
  }
  if (isActive(input.myRegistration?.status)) {
    return hidden();
  }
  if (input.matchStatus !== 0) {
    return disabled(MATCH_STATUS_SIGNUP_TEXT[input.matchStatus] ?? '当前不可报名');
  }
  const deadline = Date.parse(input.signupDeadline);
  if (Number.isNaN(deadline)) {
    return disabled('报名截止时间无效');
  }
  if (input.now >= deadline) {
    return disabled('报名已截止');
  }
  if (!input.canSignup) {
    return disabled('没有报名权限');
  }

  const { queue } = input;
  if (input.playerType === 1) {
    if (queue.confirmedCount >= queue.capacity) {
      if (queue.waitlistCount < queue.waitlistCapacity) {
        return {
          visible: true,
          label: '加入候补',
          action: 'signup',
          disabled: false,
          reason: '正式名额已满，报名将进入候补',
        };
      }
      return disabled('名额与候补已满');
    }
    return { visible: true, label: '报名', action: 'signup', disabled: false, reason: '' };
  }
  if (queue.guestCount >= queue.guestCapacity) {
    return disabled('外挂名额已满');
  }
  return { visible: true, label: '报名', action: 'signup', disabled: false, reason: '' };
}

/** 本人取消入口；只有报名中/报名截止且锁定前可自行取消。 */
export function cancelState(input: CancelStateInput): CancelState {
  const status = input.myRegistration?.status;
  if (!input.authenticated || input.pending || !isActive(status)) {
    return { visible: false, label: '', disabled: true, reason: '' };
  }
  const label = status === REGISTRATION_STATUS.WAITLISTED ? '取消候补' : '取消报名';
  if (input.matchStatus !== 0 && input.matchStatus !== 1) {
    return {
      visible: true,
      label,
      disabled: true,
      reason: MATCH_STATUS_CANCEL_TEXT[input.matchStatus] ?? '当前比赛状态不可取消',
    };
  }
  const lock = Date.parse(input.cancelLock);
  if (Number.isNaN(lock)) {
    return { visible: true, label, disabled: true, reason: '取消截止时间无效' };
  }
  if (input.now >= lock) {
    return { visible: true, label, disabled: true, reason: '已过取消截止，请联系管理员' };
  }
  return { visible: true, label, disabled: false, reason: '' };
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
