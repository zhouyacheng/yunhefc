import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { clearSessionHint, hasSessionHint, markSessionHint } from './session-hint';

const values = new Map<string, string>();

const fakeStorage = {
  getItem: (key: string) => values.get(key) ?? null,
  setItem: (key: string, value: string) => {
    values.set(key, value);
  },
  removeItem: (key: string) => {
    values.delete(key);
  },
  clear: () => {
    values.clear();
  },
  key: () => null,
  length: 0,
} as Storage;

describe('session hint', () => {
  beforeEach(() => {
    values.clear();
    vi.stubGlobal('localStorage', fakeStorage);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('marks and clears the local session hint', () => {
    expect(hasSessionHint()).toBe(false);

    markSessionHint();
    expect(hasSessionHint()).toBe(true);
    expect(values.get('yunhefc.c.sessionHint')).toBe('1');

    clearSessionHint();
    expect(hasSessionHint()).toBe(false);
  });
});
