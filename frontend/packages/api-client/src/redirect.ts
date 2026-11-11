export interface RedirectOptions {
  /** 应用挂载前缀，admin 为 `/admin/`。 */
  basePath?: string;
  /** 登录页完整路径，默认 `${basePath}login`。 */
  loginPath?: string;
}

function normalizeBasePath(basePath = '/'): string {
  return basePath.endsWith('/') ? basePath : `${basePath}/`;
}

/**
 * 只接受站内白名单路径：必须落在应用 base 内、不是登录页、不是绝对/协议相对 URL。
 * 用于 AUTH 跳转与 05 的站内 redirect 白名单。
 */
export function safeRedirectPath(
  path: string | null | undefined,
  options: RedirectOptions = {},
): string | undefined {
  if (!path) {
    return undefined;
  }
  const basePath = normalizeBasePath(options.basePath);
  const loginPath = options.loginPath ?? `${basePath}login`;

  if (!path.startsWith('/') || path.startsWith('//')) {
    return undefined;
  }
  if (path.includes('\\') || [...path].some((char) => char.charCodeAt(0) < 0x20)) {
    return undefined;
  }
  if (basePath !== '/' && !path.startsWith(basePath)) {
    return undefined;
  }
  if (path === loginPath || path.startsWith(`${loginPath}?`) || path.startsWith(`${loginPath}/`)) {
    return undefined;
  }
  return path;
}

export function buildLoginUrl(
  loginPath: string,
  currentPath: string | null | undefined,
  options: RedirectOptions = {},
): string {
  const safe = safeRedirectPath(currentPath, { ...options, loginPath });
  return safe ? `${loginPath}?redirect=${encodeURIComponent(safe)}` : loginPath;
}
