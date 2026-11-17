const RELOAD_KEY = 'yunhefc:chunk-reload-at';
const RELOAD_COOLDOWN_MS = 10_000;

/** 发布后旧入口引用的哈希分包已不存在时，路由懒加载会抛出这些错误。 */
export function isStaleChunkError(error: unknown): boolean {
  const message = error instanceof Error ? error.message : String(error ?? '');
  return /Failed to fetch dynamically imported module|error loading dynamically imported module|Importing a module script failed/i.test(
    message,
  );
}

/** 整页刷新换到新入口；10 秒内只刷新一次，避免网络故障时循环刷新。 */
export function reloadForStaleChunk(): void {
  const now = Date.now();
  const last = Number(sessionStorage.getItem(RELOAD_KEY) ?? 0);
  if (now - last < RELOAD_COOLDOWN_MS) {
    return;
  }
  sessionStorage.setItem(RELOAD_KEY, String(now));
  window.location.reload();
}

/** 兜底 Vite 模块预加载失败与路由懒加载失败：用户跨发布停留的页面自动刷新一次。 */
export function installChunkReloadGuard(router: {
  onError(handler: (error: unknown) => void): unknown;
}): void {
  window.addEventListener('vite:preloadError', (event) => {
    event.preventDefault();
    reloadForStaleChunk();
  });
  router.onError((error) => {
    if (isStaleChunkError(error)) {
      reloadForStaleChunk();
    }
  });
}
