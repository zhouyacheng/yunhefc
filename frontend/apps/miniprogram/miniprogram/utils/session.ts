import type { AuthUser, BindPhonePayload, MiniLoginResult, PlayerView } from '../types/api';
import {
  bindPhoneWithTicket,
  fetchMe,
  logoutRequest,
  miniLogin,
  sendBindSmsCode,
} from './api-client';
import { hasPermission as hasGrantedPermission } from './permissions';
import { resetAuth, setCsrf, setUnauthorizedHandler } from './request';

let currentUser: AuthUser | null = null;
let currentPlayer: PlayerView | null = null;
let currentPermissions: string[] = [];
let bindTicket: string | null = null;
let sessionPromise: Promise<AuthUser | null> | null = null;
/** 用户显式退出后禁止静默续登，直到再次主动登录；clearSession 会复位。 */
let silentLoginSuppressed = false;

export function getUser(): AuthUser | null {
  return currentUser;
}

export function getPlayer(): PlayerView | null {
  return currentPlayer;
}

export function getPermissions(): string[] {
  return currentPermissions;
}

export function hasPermission(code: string): boolean {
  return hasGrantedPermission(currentPermissions, code);
}

export function setPlayer(player: PlayerView | null): void {
  currentPlayer = player;
}

/** 启动探测与等待共用：先 restore，未登录再静默 wx.login；并发只执行一次，永不 reject。 */
export function sessionReady(): Promise<AuthUser | null> {
  if (silentLoginSuppressed) {
    return Promise.resolve(null);
  }
  if (!sessionPromise) {
    sessionPromise = restore()
      .then(async (user) => {
        if (user) {
          return user;
        }
        const result = await login().catch(() => null);
        return result && !result.needBindPhone ? result.user : null;
      })
      .catch(() => null);
  }
  return sessionPromise;
}

export function getBindTicket(): string | null {
  return bindTicket;
}

export function clearSession(): void {
  currentUser = null;
  currentPlayer = null;
  currentPermissions = [];
  bindTicket = null;
  sessionPromise = null;
  silentLoginSuppressed = false;
  resetAuth();
}

// 已登录请求遇到 401：清空用户缓存并允许下次 sessionReady 重新探测。
setUnauthorizedHandler(() => {
  if (currentUser) {
    clearSession();
  }
});

export async function logout(): Promise<void> {
  // 显式退出后不再静默续登；请求失败且会话仍在时恢复，避免网络抖动把用户登出。
  silentLoginSuppressed = true;
  try {
    await logoutRequest();
  } catch (error) {
    if (getUser()) {
      silentLoginSuppressed = false;
    }
    throw error;
  }
  clearSession();
  silentLoginSuppressed = true;
}

/** 静默探测登录态；未登录返回 null，不抛出。 */
export async function restore(): Promise<AuthUser | null> {
  try {
    const data = await fetchMe();
    currentUser = data.user;
    currentPlayer = data.player ?? null;
    currentPermissions = data.permissions ?? [];
    return data.user;
  } catch {
    currentUser = null;
    currentPlayer = null;
    currentPermissions = [];
    return null;
  }
}

export async function login(): Promise<MiniLoginResult> {
  silentLoginSuppressed = false;
  const code = await wxLogin();
  const result = await miniLogin(code);
  if (result.needBindPhone) {
    bindTicket = result.bindTicket;
    currentUser = null;
    currentPlayer = null;
    return result;
  }
  setCsrf(result.csrfToken);
  currentUser = result.user;
  currentPlayer = result.player ?? null;
  currentPermissions = result.permissions ?? [];
  bindTicket = null;
  sessionPromise = Promise.resolve(result.user);
  return result;
}

export async function bindPhone(payload: BindPhonePayload): Promise<AuthUser> {
  if (!bindTicket) {
    throw new Error('登录已过期，请重新登录');
  }
  const result = await bindPhoneWithTicket(bindTicket, payload);
  setCsrf(result.csrfToken);
  currentUser = result.user;
  currentPlayer = result.player ?? null;
  currentPermissions = result.permissions ?? [];
  bindTicket = null;
  sessionPromise = Promise.resolve(result.user);
  return result.user;
}

export async function sendSmsCode(phone: string): Promise<void> {
  await sendBindSmsCode(phone);
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
