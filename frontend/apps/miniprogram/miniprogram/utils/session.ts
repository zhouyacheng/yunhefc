import type { AuthUser, MiniLoginResult } from '../types/api';
import { ensureCsrf, request, resetAuth, setCsrf } from './request';

let currentUser: AuthUser | null = null;
let bindTicket: string | null = null;

export function getUser(): AuthUser | null {
  return currentUser;
}

export function getBindTicket(): string | null {
  return bindTicket;
}

export function clearSession(): void {
  currentUser = null;
  bindTicket = null;
  resetAuth();
}

/** 静默探测登录态；未登录返回 null，不抛出。 */
export async function restore(): Promise<AuthUser | null> {
  try {
    const data = await request<{ user: AuthUser }>('GET', '/me');
    currentUser = data.user;
    return data.user;
  } catch {
    currentUser = null;
    return null;
  }
}

export async function login(): Promise<MiniLoginResult> {
  const code = await wxLogin();
  const result = await request<MiniLoginResult>('POST', '/auth/mini/login', { code });
  if (result.needBindPhone) {
    bindTicket = result.bindTicket;
    currentUser = null;
    return result;
  }
  setCsrf(result.csrfToken);
  currentUser = result.user;
  bindTicket = null;
  return result;
}

export interface BindPhonePayload {
  phone: string;
  code: string;
  name: string;
  position: number;
}

export async function bindPhone(payload: BindPhonePayload): Promise<AuthUser> {
  if (!bindTicket) {
    throw new Error('登录已过期，请重新登录');
  }
  const result = await request<{ user: AuthUser; csrfToken: string }>(
    'POST',
    '/auth/mini/bind-phone',
    { bindTicket, ...payload },
  );
  setCsrf(result.csrfToken);
  currentUser = result.user;
  bindTicket = null;
  return result.user;
}

export async function sendSmsCode(phone: string): Promise<void> {
  await ensureCsrf();
  await request<{ expiresIn: number }>('POST', '/auth/sms-codes', { phone, purpose: 'BIND' });
}

function wxLogin(): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    wx.login({
      success: (res) => {
        if (res.code) {
          resolve(res.code);
        } else {
          reject(new Error('微信登录失败，请重试'));
        }
      },
      fail: (error) => reject(new Error(error.errMsg || '微信登录失败，请重试')),
    });
  });
}
