// 受代次守卫的加载：迟到响应丢弃、loading/error/finally 收进一处。
// 页面只提供 fetcher、成功映射与空态回退；重载或卸载通过 invalidateAll 使在途请求过期。
import { errorMessage } from './api-errors';

export interface GuardedLoadSpec<T> {
  /** loader 标识：loading/error 分别写入 `${key}Loading`、`${key}Error`。 */
  key: string;
  /** 请求。 */
  fetch: () => Promise<T>;
  /** 成功映射：返回要写入 data 的字段；仅在响应仍为最新时调用。 */
  map: (value: T) => Record<string, unknown>;
  /** 失败时额外写入的字段（空态回退）；error 字段由 module 写入。 */
  fallback?: Record<string, unknown>;
  /** 请求前与 loading/error 一起写入的字段（如阵容主客）。 */
  begin?: Record<string, unknown>;
  /** 默认错误文案。 */
  errorText: string;
  /** 成功写入后、仍为最新时执行（如重算窗口状态）。 */
  after?: () => void;
}

export interface GuardedLoads {
  /** 运行一次；同一 key 的旧运行随后返回的响应被丢弃。 */
  run<T>(spec: GuardedLoadSpec<T>): Promise<void>;
  /**
   * 使所有在途运行过期，不触发请求，也不写 loading。
   * 重载时会重新跑 loader；卸载时页面不应再 setData，调用方按需自行复位 loading。
   */
  invalidateAll(): void;
}

/** 按页面共享代次；每个 key 独立计数，互不影响。 */
export function createGuardedLoads(
  setData: (patch: Record<string, unknown>) => void,
): GuardedLoads {
  const generations = new Map<string, number>();

  function isCurrent(key: string, generation: number): boolean {
    return generations.get(key) === generation;
  }

  async function run<T>(spec: GuardedLoadSpec<T>): Promise<void> {
    const { key } = spec;
    const generation = (generations.get(key) ?? 0) + 1;
    const loadingField = `${key}Loading`;
    const errorField = `${key}Error`;
    generations.set(key, generation);
    setData({ ...spec.begin, [loadingField]: true, [errorField]: '' });
    try {
      const value = await spec.fetch();
      if (!isCurrent(key, generation)) {
        return;
      }
      setData(spec.map(value));
      spec.after?.();
    } catch (error) {
      if (!isCurrent(key, generation)) {
        return;
      }
      setData({ ...spec.fallback, [errorField]: errorMessage(error, spec.errorText) });
    } finally {
      if (isCurrent(key, generation)) {
        setData({ [loadingField]: false });
      }
    }
  }

  function invalidateAll(): void {
    for (const [key, generation] of generations) {
      generations.set(key, generation + 1);
    }
  }

  return { run, invalidateAll };
}
