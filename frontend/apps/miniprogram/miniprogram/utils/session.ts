import type { AuthUser, BindPhonePayload, MiniLoginResult, PlayerView } from '../types/api';
import {
  bindPhoneWithTicket,
  fetchMe,
  logoutRequest,
  miniLogin,
  passwordLogin as passwordLoginRequest,
  sendBindSmsCode,
  type SessionResult,
} from './api-client';
import { hasAgreedToAgreements } from './agreement';
import { hasPermission as hasGrantedPermission } from './permissions';
import { resetAuth, setCsrf, setUnauthorizedHandler } from './request';

let currentUser: AuthUser | null = null;
let currentPlayer: PlayerView | null = null;
let currentPermissions: string[] = [];
let bindTicket: string | null = null;
let sessionPromise: Promise<AuthUser | null> | null = null;
/** 用户显式退出后禁止静默续登，直到再次主动登录；clearSession 会复位。 */
let silentLoginSuppressed = false;

/** 登录/绑定成功后的共享会话写入；只有成功建立会话才解除静默登录抑制。 */
function applySession(result: SessionResult): AuthUser {
  silentLoginSuppressed = false;
  setCsrf(result.csrfToken);
  currentUser = result.user;
  currentPlayer = result.player ?? null;
  currentPermissions = result.permissions ?? [];
  bindTicket = null;
  sessionPromise = Promise.resolve(result.user);
  return result.user;
}

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

/** 未同意当前协议版本时清除本地凭据并返回 false，避免拉取个人信息。 */
function agreementGate(): boolean {
  if (hasAgreedToAgreements()) {
    return true;
  }
  clearSession();
  return false;
}

/** 启动探测与等待共用：先校验同意，再 restore，未登录再静默 wx.login；并发只执行一次，永不 reject。 */
export function sessionReady(): Promise<AuthUser | null> {
  if (silentLoginSuppressed) {
    return Promise.resolve(null);
  }
  if (!sessionPromise) {
    sessionPromise = (async () => {
      if (!agreementGate()) {
        return null;
      }
      const user = await restore();
      if (user) {
        return user;
      }
      const result = await loginWithWechat().catch(() => null);
      return result && !result.needBindPhone ? result.user : null;
    })().catch(() => null);
  }
  return sessionPromise;
}

/** 刷新已有会话；未同意协议时不访问 /me。 */
export async function restoreIfAgreed(): Promise<AuthUser | null> {
  if (!agreementGate()) {
    return null;
  }
  return restore();
}

export function getBindTicket(): string | null {
  return bindTicket;
}

/** 改走密码登录时放弃当前微信绑定票据。 */
export function clearBindTicket(): void {
  bindTicket = null;
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

/** 手机号+密码登录（AUTH-04）：CSRF 由请求层按需获取；会话 Cookie 由请求层持久化。 */
export async function loginWithPassword(phone: string, password: string): Promise<AuthUser> {
  const result = await passwordLoginRequest(phone, password);
  return applySession(result);
}

/** 微信登录（AUTH-20）：可能返回 needBindPhone，此时只保存临时票据不建立会话。 */
export async function loginWithWechat(): Promise<MiniLoginResult> {
  const code = await wxLogin();
  const result = await miniLogin(code);
  if (result.needBindPhone || !result.user) {
    bindTicket = result.bindTicket;
    currentUser = null;
    currentPlayer = null;
    return result;
  }
  applySession({ ...result, user: result.user, csrfToken: result.csrfToken ?? '' });
  return result;
}

export async function bindPhone(payload: BindPhonePayload): Promise<AuthUser> {
  if (!bindTicket) {
    throw new Error('登录已过期，请重新登录');
  }
  const result = await bindPhoneWithTicket(bindTicket, payload);
  return applySession(result);
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
