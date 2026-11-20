import { beforeEach, describe, expect, it } from 'vitest';

import { invalidate, subscribe } from '../miniprogram/utils/invalidation';

const registrations = subscribe('my-registrations');
const notifications = subscribe('my-notifications');

beforeEach(() => {
  registrations.consume();
  notifications.consume();
});

describe('invalidation', () => {
  it('未标记时消费无需重载', () => {
    expect(registrations.consume()).toBe(false);
  });

  it('标记后消费一次即复位', () => {
    invalidate('my-registrations');
    expect(registrations.consume()).toBe(true);
    expect(registrations.consume()).toBe(false);
  });

  it('重复标记仍只消费一次', () => {
    invalidate('my-registrations');
    invalidate('my-registrations');
    expect(registrations.consume()).toBe(true);
    expect(registrations.consume()).toBe(false);
  });

  it('消费一个资源不影响其他资源', () => {
    invalidate('my-registrations');
    invalidate('my-notifications');

    expect(notifications.consume()).toBe(true);
    expect(registrations.consume()).toBe(true);
    expect(registrations.consume()).toBe(false);
  });

  it('未标记的资源保持未失效', () => {
    invalidate('my-registrations');

    expect(notifications.consume()).toBe(false);
    expect(registrations.consume()).toBe(true);
  });

  it('同一资源的多个订阅共享一次失效', () => {
    const secondRegistrations = subscribe('my-registrations');
    invalidate('my-registrations');

    expect(registrations.consume()).toBe(true);
    expect(secondRegistrations.consume()).toBe(false);
  });
});
