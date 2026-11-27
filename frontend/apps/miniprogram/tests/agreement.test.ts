import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  AGREEMENT_VERSION,
  ensureAgreement,
  hasAgreedToAgreements,
  openPrivacy,
  openTerms,
  recordAgreementConsent,
} from '../miniprogram/utils/agreement';

const CONSENT_KEY = 'yhfc.agreement.consent';

interface StorageStub {
  store: Map<string, unknown>;
  navigateTo: ReturnType<typeof vi.fn>;
}

function stubStorage(options: { initial?: unknown; throwOnRead?: boolean; throwOnWrite?: boolean } = {}): StorageStub {
  const store = new Map<string, unknown>();
  if (options.initial !== undefined) {
    store.set(CONSENT_KEY, options.initial);
  }
  const navigateTo = vi.fn();
  vi.stubGlobal('wx', {
    getStorageSync: (key: string) => {
      if (options.throwOnRead) {
        throw new Error('storage unavailable');
      }
      return store.get(key) ?? '';
    },
    setStorageSync: (key: string, value: unknown) => {
      if (options.throwOnWrite) {
        throw new Error('storage full');
      }
      store.set(key, value);
    },
    navigateTo,
  });
  return { store, navigateTo };
}

afterEach(() => {
  vi.unstubAllGlobals();
});

describe('协议同意记录', () => {
  it('没有记录时视为未同意', () => {
    stubStorage();
    expect(hasAgreedToAgreements()).toBe(false);
  });

  it('记录当前版本后可读取，并保存同意时间', () => {
    const { store } = stubStorage();
    recordAgreementConsent();
    expect(hasAgreedToAgreements()).toBe(true);
    const record = store.get(CONSENT_KEY) as { version: string; agreedAt: string };
    expect(record.version).toBe(AGREEMENT_VERSION);
    expect(Number.isNaN(Date.parse(record.agreedAt))).toBe(false);
  });

  it('旧版本同意记录失效', () => {
    stubStorage({ initial: { version: '2026-01-01', agreedAt: '2026-01-01T00:00:00.000Z' } });
    expect(hasAgreedToAgreements()).toBe(false);
  });

  it('字符串记录也可解析', () => {
    stubStorage({
      initial: JSON.stringify({ version: AGREEMENT_VERSION, agreedAt: '2026-11-27T00:00:00.000Z' }),
    });
    expect(hasAgreedToAgreements()).toBe(true);
  });

  it('非法记录或存储异常时按未同意处理', () => {
    stubStorage({ initial: 'not-json' });
    expect(hasAgreedToAgreements()).toBe(false);

    stubStorage({ throwOnRead: true });
    expect(hasAgreedToAgreements()).toBe(false);

    stubStorage({ throwOnWrite: true });
    expect(() => recordAgreementConsent()).not.toThrow();
  });

  it('同意门禁只负责判定，未同意时交给调用方提示', () => {
    const onMissing = vi.fn();
    expect(ensureAgreement(true, onMissing)).toBe(true);
    expect(onMissing).not.toHaveBeenCalled();

    expect(ensureAgreement(false, onMissing)).toBe(false);
    expect(onMissing).toHaveBeenCalledOnce();
  });

  it('打开协议页使用固定路由', () => {
    const { navigateTo } = stubStorage();
    openTerms();
    openPrivacy();
    expect(navigateTo).toHaveBeenNthCalledWith(1, { url: '/pages/terms/index' });
    expect(navigateTo).toHaveBeenNthCalledWith(2, { url: '/pages/privacy/index' });
  });
});
