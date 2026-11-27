import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  bindPhone: vi.fn(),
  clearBindTicket: vi.fn(),
  getBindTicket: vi.fn(() => 't1'),
  sendSmsCode: vi.fn(),
  refreshUnreadBadge: vi.fn(),
  hasAgreedToAgreements: vi.fn(),
  recordAgreementConsent: vi.fn(),
  navigateAfterAuth: vi.fn(),
}));

vi.mock('../miniprogram/utils/agreement', async () => {
  const actual = await vi.importActual<typeof import('../miniprogram/utils/agreement')>(
    '../miniprogram/utils/agreement',
  );
  return {
    ...actual,
    hasAgreedToAgreements: mocks.hasAgreedToAgreements,
    recordAgreementConsent: mocks.recordAgreementConsent,
  };
});

vi.mock('../miniprogram/utils/session', () => ({
  bindPhone: mocks.bindPhone,
  clearBindTicket: mocks.clearBindTicket,
  getBindTicket: mocks.getBindTicket,
  sendSmsCode: mocks.sendSmsCode,
}));

vi.mock('../miniprogram/utils/notifications', () => ({
  refreshUnreadBadge: mocks.refreshUnreadBadge,
}));

vi.mock('../miniprogram/utils/invite', () => ({
  inviteLoginPath: vi.fn(() => '/pages/login/index'),
  inviteTokenFromQuery: vi.fn(() => ''),
  navigateAfterAuth: mocks.navigateAfterAuth,
}));

vi.mock('../miniprogram/utils/request', () => ({
  ApiError: class ApiError extends Error {
    code: number;

    constructor(code: number, message: string) {
      super(message);
      this.code = code;
    }
  },
}));

vi.mock('../miniprogram/utils/api-errors', () => ({
  errorMessage: (_error: unknown, fallback: string) => fallback,
}));

interface PhoneBindPage {
  data: Record<string, unknown> & {
    agreed: boolean;
    error: string;
    phone: string;
    code: string;
    name: string;
    positionIndex: number;
    countdown: number;
  };
  setData(patch: Record<string, unknown>): void;
  onLoad(query: Record<string, string | undefined>): void;
  onUnload(): void;
  sendCode(): Promise<void>;
  submit(): Promise<void>;
  onAgreementChange(event: { detail: { checked: boolean } }): void;
}

let pageConfig!: Record<string, unknown> & { data: Record<string, unknown> };

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/phone-bind/index');
});

afterAll(() => {
  vi.unstubAllGlobals();
});

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  mocks.hasAgreedToAgreements.mockReturnValue(false);
  mocks.sendSmsCode.mockResolvedValue(undefined);
  mocks.bindPhone.mockResolvedValue({});
});

afterEach(() => {
  vi.useRealTimers();
});

function pageInstance(): PhoneBindPage {
  const page = Object.create(pageConfig) as PhoneBindPage;
  page.data = { ...pageConfig.data } as PhoneBindPage['data'];
  page.setData = (patch) => Object.assign(page.data, patch);
  return page;
}

describe('MP02 绑定手机号协议同意', () => {
  it('未勾选时发送验证码被拦截，不记录同意', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.phone = '13800138000';

    await page.sendCode();

    expect(mocks.sendSmsCode).not.toHaveBeenCalled();
    expect(mocks.recordAgreementConsent).not.toHaveBeenCalled();
    expect(page.data.error).toContain('请先阅读并同意');
  });

  it('未勾选时提交绑定被拦截', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.phone = '13800138000';
    page.data.code = '123456';

    await page.submit();

    expect(mocks.bindPhone).not.toHaveBeenCalled();
    expect(mocks.recordAgreementConsent).not.toHaveBeenCalled();
    expect(page.data.error).toContain('请先阅读并同意');
  });

  it('勾选后发送验证码并记录同意', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.agreed = true;
    page.data.phone = '13800138000';

    await page.sendCode();

    expect(mocks.sendSmsCode).toHaveBeenCalledWith('13800138000');
    expect(mocks.recordAgreementConsent).toHaveBeenCalledOnce();
    expect(page.data.countdown).toBe(60);
    page.onUnload();
  });

  it('勾选后提交绑定并记录同意', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.agreed = true;
    page.data.phone = '13800138000';
    page.data.code = '123456';
    page.data.name = '张三';
    page.data.positionIndex = 2;

    await page.submit();

    expect(mocks.bindPhone).toHaveBeenCalledWith({
      phone: '13800138000',
      code: '123456',
      name: '张三',
      position: 2,
    });
    expect(mocks.recordAgreementConsent).toHaveBeenCalledOnce();
  });

  it('协议勾选变化写入 agreed 并清除错误', () => {
    const page = pageInstance();
    page.data.error = '旧错误';

    page.onAgreementChange({ detail: { checked: true } });

    expect(page.data.agreed).toBe(true);
    expect(page.data.error).toBe('');
  });
});
