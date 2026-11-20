// 首页 CTA adapter：只把报名决策映射为 Hero 按钮字段，不判断状态、名额、截止或待审核。
import { REGISTRATION_STATUS, type SignupDecision } from './registration';

export type HomeCtaAction = 'login' | 'register' | 'view-detail' | 'none';

export interface HomeCtaResult {
  label: string;
  action: HomeCtaAction;
  disabled: boolean;
  /** 补充说明，可为空；禁用原因已由 label 表达。 */
  reason: string;
}

/** 把报名决策映射成首页按钮；决策文案随决策走，只有首页专属入口文案在这里。 */
export function homeCta(decision: SignupDecision): HomeCtaResult {
  switch (decision.kind) {
    case 'anonymous':
      return { label: decision.label, action: 'login', disabled: false, reason: '' };
    case 'registered':
      return {
        label: '查看详情',
        action: 'view-detail',
        disabled: false,
        reason:
          decision.registrationStatus === REGISTRATION_STATUS.WAITLISTED ? '候补中' : '已报名',
      };
    case 'open':
    case 'waitlist':
      return {
        label: decision.label,
        action: 'register',
        disabled: false,
        reason: decision.reason,
      };
    case 'pending':
    case 'blocked':
      return {
        label: decision.label || decision.reason,
        action: 'none',
        disabled: true,
        reason: '',
      };
  }
}
