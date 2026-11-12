import type { MatchDetail, Registration } from '@yunhefc/shared';
import { REGISTRATION_STATUS } from '@yunhefc/shared';

import { isPending } from '../auth/user-status';

export type HomeCtaAction = 'login' | 'register' | 'view-detail' | 'none';

export type HomeCtaQueue = Pick<
  MatchDetail,
  | 'capacity'
  | 'guestCapacity'
  | 'waitlistCapacity'
  | 'confirmedCount'
  | 'guestCount'
  | 'waitlistCount'
>;

export interface HomeCtaInput {
  authenticated: boolean;
  /** app_user.status：0-待审核；1-正常；2-已注销。 */
  userStatus: number | null;
  /** player.type：0-外挂；1-正式。 */
  playerType: number | null;
  matchStatus: number;
  /** epoch 毫秒，由调用方传入以保持纯函数。 */
  now: number;
  signupDeadline: string;
  queue: HomeCtaQueue;
  myRegistration?: Registration | null;
}

export interface HomeCtaResult {
  label: string;
  action: HomeCtaAction;
  disabled: boolean;
  /** 补充说明，可为空；禁用原因已由 label 表达。 */
  reason: string;
}

const MATCH_STATUS_CTA_TEXT: Readonly<Record<number, string>> = {
  1: '报名已截止',
  2: '报名已锁定',
  3: '比赛已开始',
  4: '比赛已结束',
  5: '比赛已延期',
  6: '比赛已取消',
};

function disabled(label: string): HomeCtaResult {
  return { label, action: 'none', disabled: true, reason: '' };
}

/** 首页报名 CTA 状态机；纯函数，不读时钟、不修改入参。 */
export function homeCta(input: HomeCtaInput): HomeCtaResult {
  if (!input.authenticated) {
    return { label: '登录后报名', action: 'login', disabled: false, reason: '' };
  }

  if (input.userStatus !== null && isPending(input.userStatus)) {
    return disabled('账号待审核，通过后可报名');
  }

  const status = input.myRegistration?.status;
  if (status === REGISTRATION_STATUS.CONFIRMED || status === REGISTRATION_STATUS.WAITLISTED) {
    return {
      label: '查看详情',
      action: 'view-detail',
      disabled: false,
      reason: status === REGISTRATION_STATUS.CONFIRMED ? '已报名' : '候补中',
    };
  }

  if (input.matchStatus !== 0) {
    return disabled(MATCH_STATUS_CTA_TEXT[input.matchStatus] ?? '当前不可报名');
  }

  if (input.now >= Date.parse(input.signupDeadline)) {
    return disabled('报名已截止');
  }

  const { queue } = input;
  if (input.playerType === 1) {
    if (queue.confirmedCount >= queue.capacity) {
      if (queue.waitlistCount < queue.waitlistCapacity) {
        return {
          label: '加入候补',
          action: 'register',
          disabled: false,
          reason: '正式名额已满，报名将进入候补',
        };
      }
      return disabled('名额与候补已满');
    }
    return { label: '报名', action: 'register', disabled: false, reason: '' };
  }

  if (queue.guestCount >= queue.guestCapacity) {
    return disabled('外挂名额已满');
  }
  return { label: '报名', action: 'register', disabled: false, reason: '' };
}
