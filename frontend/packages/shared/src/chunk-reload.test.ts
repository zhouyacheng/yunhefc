import { afterEach, describe, expect, it, vi } from 'vitest';

import { installChunkReloadGuard, isStaleChunkError } from './chunk-reload';

describe('isStaleChunkError', () => {
  it.each([
    'Failed to fetch dynamically imported module: https://www.yunhefc.com/admin/assets/RegistrationsPage-abc.js',
    'error loading dynamically imported module',
    'Importing a module script failed.',
  ])('识别发布后分包缺失：%s', (message) => {
    expect(isStaleChunkError(new TypeError(message))).toBe(true);
  });

  it.each([null, '', 'Network Error', 'Request failed with status code 500'])(
    '忽略其他错误：%s',
    (message) => {
      expect(isStaleChunkError(message)).toBe(false);
    },
  );
});

describe('installChunkReloadGuard', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('分包加载失败时整页刷新，冷却期内不重复刷新', () => {
    const listeners = new Map<string, (event: Event) => void>();
    const reload = vi.fn();
    const storage = new Map<string, string>();
    vi.stubGlobal('window', {
      addEventListener: (type: string, handler: (event: Event) => void) => {
        listeners.set(type, handler);
      },
      location: { reload },
    });
    vi.stubGlobal('sessionStorage', {
      getItem: (key: string) => storage.get(key) ?? null,
      setItem: (key: string, value: string) => {
        storage.set(key, value);
      },
    });

    const handlers: ((error: unknown) => void)[] = [];
    installChunkReloadGuard({ onError: (handler) => handlers.push(handler) });

    listeners.get('vite:preloadError')?.({ preventDefault: vi.fn() } as unknown as Event);
    expect(reload).toHaveBeenCalledTimes(1);

    handlers[0]?.(new TypeError('Failed to fetch dynamically imported module'));
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
