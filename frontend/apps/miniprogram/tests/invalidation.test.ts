import { beforeEach, describe, expect, it } from 'vitest';

import { invalidate, subscribe } from '../miniprogram/utils/invalidation';

const registrations = subscribe('my-registrations');

beforeEach(() => {
  registrations.consume();
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

  it('同一资源的多个订阅共享一次失效', () => {
    const secondRegistrations = subscribe('my-registrations');
    invalidate('my-registrations');

    expect(registrations.consume()).toBe(true);
    expect(secondRegistrations.consume()).toBe(false);
  });
});
