import { ERROR_CODES } from '@yunhefc/api-client';
import { PHONE_PATTERN } from '@yunhefc/shared';

export { PHONE_PATTERN };
export const SMS_CODE_PATTERN = /^\d{6}$/;
export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 64;

export interface InviteAvailability {
  status: number;
  exhausted?: boolean;
}

export function validatePhone(value: string): string | undefined {
  return PHONE_PATTERN.test(value.trim()) ? undefined : '请输入正确的手机号';
}

export function validateSmsCode(value: string): string | undefined {
  return SMS_CODE_PATTERN.test(value.trim()) ? undefined : '请输入 6 位数字验证码';
}

export function validatePassword(value: string): string | undefined {
  if (value.length < PASSWORD_MIN_LENGTH || value.length > PASSWORD_MAX_LENGTH) {
    return `密码需为 ${PASSWORD_MIN_LENGTH}–${PASSWORD_MAX_LENGTH} 位`;
  }
  if (!/[A-Za-z]/.test(value) || !/\d/.test(value)) {
    return '密码需同时包含字母和数字';
  }
  return undefined;
}

export function validateName(value: string): string | undefined {
  const name = value.trim();
  if (!name) {
    return '请输入姓名';
  }
  return name.length > 32 ? '姓名不能超过 32 个字符' : undefined;
}

export function validatePosition(value: number | null | undefined): string | undefined {
  return value === 0 || value === 1 || value === 2 || value === 3 ? undefined : '请选择主位置';
}

/** query 参数可能是数组或空串，统一取首个非空字符串。 */
export function queryString(value: unknown): string | undefined {
  const values = Array.isArray(value) ? value : [value];
  for (const item of values) {
    if (typeof item === 'string' && item.trim()) {
      return item;
    }
  }
  return undefined;
}

export function isInviteUsable(invite: InviteAvailability | null | undefined): boolean {
  return invite !== null && invite !== undefined && invite.status === 0 && !invite.exhausted;
}

/** 仅把可用邀请随注册提交；失效或加载失败时降级为自助注册。 */
export function registerInviteToken(
  token: string | undefined,
  invite: InviteAvailability | null | undefined,
  unavailable = false,
): string | undefined {
  if (!token || unavailable || (invite && !isInviteUsable(invite))) {
    return undefined;
  }
  return token;
}

/** 6 类认证错误码统一转成可读文案；40102 不区分账号不存在与密码错误。 */
export function authErrorMessage(code: number, message?: string): string {
  switch (code) {
    case ERROR_CODES.VALIDATION_FAILED:
      return message && message !== '参数/字段校验失败' ? message : '提交内容有误，请检查后重试';
    case ERROR_CODES.INVALID_CREDENTIALS:
      return '手机号或密码错误';
    case ERROR_CODES.ACCOUNT_LOCKED:
    case ERROR_CODES.ACCOUNT_DISABLED:
      return message || '账号不可用，请联系管理员';
    case ERROR_CODES.ACCOUNT_PENDING:
      return '账号待审核，审核通过后才能操作';
    case ERROR_CODES.PHONE_EXISTS:
      return '该手机号已注册，请直接登录';
    case ERROR_CODES.INVITE_UNAVAILABLE:
      return '邀请不可用，可继续自助注册';
    case ERROR_CODES.RATE_LIMITED:
      return message && message !== '触发限流' ? message : '操作过于频繁，请稍后再试';
    default:
      return message || '操作失败，请稍后重试';
  }
}
