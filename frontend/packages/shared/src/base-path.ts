/** 浏览器地址 → 应用内路由路径（去掉 BASE_URL 前缀）。 */
export function toRoutePath(browserPath: string, appBase: string): string {
  if (appBase === '/') {
    return browserPath;
  }
  const prefix = appBase.replace(/\/$/, '');
  if (browserPath === prefix) {
    return '/';
  }
  return browserPath.startsWith(`${prefix}/`) ? browserPath.slice(prefix.length) : browserPath;
}

/** 应用内路由路径 → 浏览器地址（带上 BASE_URL 前缀）。 */
export function toBrowserPath(routePath: string, appBase: string): string {
  if (appBase === '/') {
    return routePath;
  }
  return `${appBase.replace(/\/$/, '')}${routePath.startsWith('/') ? '' : '/'}${routePath}`;
}
