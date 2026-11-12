import { ERROR_CODES } from '@yunhefc/api-client';
import { describe, expect, it } from 'vitest';

import {
  authErrorMessage,
  isInviteUsable,
  queryString,
  registerInviteToken,
  validateName,
  validatePassword,
  validatePhone,
  validatePosition,
  validateSmsCode,
} from './validation';

describe('phone validation', () => {
  it('accepts 11-digit mainland mobile numbers', () => {
    expect(validatePhone('13910000021')).toBeUndefined();
  });

  it('rejects malformed numbers', () => {
    for (const value of ['', '1391000002', '23910000021', '139100000211']) {
      expect(validatePhone(value)).toBe('请输入正确的手机号');
    }
  });
});

describe('sms code validation', () => {
  it('accepts exactly six digits', () => {
    expect(validateSmsCode('123456')).toBeUndefined();
    expect(validateSmsCode(' 123456 ')).toBeUndefined();
  });

  it('rejects other shapes', () => {
    for (const value of ['', '12345', '1234567', 'abcdef']) {
      expect(validateSmsCode(value)).toBe('请输入 6 位数字验证码');
    }
  });
});

describe('password validation', () => {
  it('accepts 8–64 characters with letters and digits', () => {
    expect(validatePassword('Passw0rd!')).toBeUndefined();
    expect(validatePassword('a'.repeat(63) + '1')).toBeUndefined();
  });

  it('rejects out-of-range lengths', () => {
    expect(validatePassword('a1')).toBe('密码需为 8–64 位');
    expect(validatePassword('a'.repeat(64) + '1')).toBe('密码需为 8–64 位');
  });

  it('requires both letters and digits', () => {
    expect(validatePassword('passwordonly')).toBe('密码需同时包含字母和数字');
    expect(validatePassword('1234567890')).toBe('密码需同时包含字母和数字');
  });
});

describe('name and position validation', () => {
  it('requires a non-blank name within 32 characters', () => {
    expect(validateName('  张三  ')).toBeUndefined();
    expect(validateName('   ')).toBe('请输入姓名');
    expect(validateName('名'.repeat(33))).toBe('姓名不能超过 32 个字符');
  });

  it('requires one of the four position codes', () => {
    for (const value of [0, 1, 2, 3]) {
      expect(validatePosition(value)).toBeUndefined();
    }
    for (const value of [undefined, null, 4, -1]) {
      expect(validatePosition(value)).toBe('请选择主位置');
    }
  });
});

describe('invite token propagation', () => {
  it('reads the first non-empty query value', () => {
    expect(queryString(['B3-INVITE-TEAM', 'other'])).toBe('B3-INVITE-TEAM');
    expect(queryString(['', 'B3-INVITE-TEAM'])).toBe('B3-INVITE-TEAM');
    expect(queryString('B3-INVITE-TEAM')).toBe('B3-INVITE-TEAM');
    expect(queryString('')).toBeUndefined();
    expect(queryString([])).toBeUndefined();
    expect(queryString(undefined)).toBeUndefined();
  });

  it('keeps the token for usable invites, including before INV-01 resolves', () => {
    expect(registerInviteToken('token', undefined)).toBe('token');
    expect(registerInviteToken('token', { status: 0 })).toBe('token');
    expect(registerInviteToken('token', { status: 0, exhausted: false })).toBe('token');
  });

  it('drops the token when the invite is invalid or unavailable', () => {
    expect(registerInviteToken(undefined, { status: 0 })).toBeUndefined();
    expect(registerInviteToken('token', { status: 1 })).toBeUndefined();
    expect(registerInviteToken('token', { status: 2 })).toBeUndefined();
    expect(registerInviteToken('token', { status: 0, exhausted: true })).toBeUndefined();
    expect(registerInviteToken('token', { status: 0 }, true)).toBeUndefined();
  });

  it('only treats active, non-exhausted invites as usable', () => {
    expect(isInviteUsable({ status: 0 })).toBe(true);
    expect(isInviteUsable({ status: 0, exhausted: false })).toBe(true);
    expect(isInviteUsable({ status: 1 })).toBe(false);
    expect(isInviteUsable({ status: 2 })).toBe(false);
    expect(isInviteUsable({ status: 0, exhausted: true })).toBe(false);
    expect(isInviteUsable(undefined)).toBe(false);
  });
});

describe('auth error messages', () => {
  it('maps the six auth error codes to readable copy', () => {
    expect(authErrorMessage(ERROR_CODES.VALIDATION_FAILED, '验证码无效或已过期')).toBe(
      '验证码无效或已过期',
    );
    expect(authErrorMessage(ERROR_CODES.VALIDATION_FAILED, '参数/字段校验失败')).toBe(
      '提交内容有误，请检查后重试',
    );
    expect(authErrorMessage(ERROR_CODES.INVALID_CREDENTIALS, '手机号或密码错误')).toBe(
      '手机号或密码错误',
    );
    expect(authErrorMessage(ERROR_CODES.PHONE_EXISTS)).toBe('该手机号已注册，请直接登录');
    expect(authErrorMessage(ERROR_CODES.INVITE_UNAVAILABLE)).toBe('邀请不可用，可继续自助注册');
    expect(authErrorMessage(ERROR_CODES.RATE_LIMITED, '短信发送过于频繁')).toBe('短信发送过于频繁');
    expect(authErrorMessage(ERROR_CODES.ACCOUNT_PENDING)).toBe('账号待审核，审核通过后才能操作');
  });

  it('falls back to the server message or a generic copy', () => {
    expect(authErrorMessage(ERROR_CODES.INTERNAL_ERROR, '服务端错误')).toBe('服务端错误');
    expect(authErrorMessage(ERROR_CODES.INTERNAL_ERROR, '')).toBe('操作失败，请稍后重试');
  });
});
