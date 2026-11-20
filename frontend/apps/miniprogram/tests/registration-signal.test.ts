import { describe, expect, it } from 'vitest';

import {
  consumeMyRegistrationsDirty,
  markMyRegistrationsDirty,
} from '../miniprogram/utils/registration-signal';

describe('registration-signal', () => {
  it('初始状态无需重载', () => {
    expect(consumeMyRegistrationsDirty()).toBe(false);
  });

  it('标记后消费一次即复位', () => {
    markMyRegistrationsDirty();
    expect(consumeMyRegistrationsDirty()).toBe(true);
    expect(consumeMyRegistrationsDirty()).toBe(false);
  });
});
