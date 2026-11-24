// 跨页失效：写入方用 invalidate 声明哪份命名数据已改动，订阅方在刷新时机 consume，消费一次即复位。
// 资源名是接口的一部分：新增资源先在此登记，避免隐藏布尔让页面之间隐式耦合。
export type InvalidationResource = 'activities' | 'my-registrations';

const invalidated = new Set<InvalidationResource>();

/** 写入方：声明命名资源已失效。 */
export function invalidate(resource: InvalidationResource): void {
  invalidated.add(resource);
}

export interface InvalidationSubscription {
  /** 订阅方在刷新时机调用；该资源被标记过则返回 true 并复位。 */
  consume(): boolean;
}

/**
 * 订阅方：显式声明本页消费的命名资源。
 * 每个资源约定只由一个页面消费；同一资源的多个订阅共享一次失效，先消费者复位。
 */
export function subscribe(resource: InvalidationResource): InvalidationSubscription {
  return {
    consume: () => invalidated.delete(resource),
  };
}
