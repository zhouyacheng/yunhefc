/** 客户端会话提示：登录/注册成功写入，退出、401 与 clearSession 清除。 */
const SESSION_HINT_KEY = 'yunhefc.c.sessionHint';

function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage;
  } catch {
    return null;
  }
}

export function hasSessionHint(): boolean {
  try {
    return storage()?.getItem(SESSION_HINT_KEY) === '1';
  } catch {
    return false;
  }
}

export function markSessionHint(): void {
  try {
    storage()?.setItem(SESSION_HINT_KEY, '1');
  } catch {
    // 存储不可用时仅影响刷新后的访客/登录态展示。
  }
}

export function clearSessionHint(): void {
  try {
    storage()?.removeItem(SESSION_HINT_KEY);
  } catch {
    // 同上。
  }
}
