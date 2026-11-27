import { afterAll, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  loginWithPassword: vi.fn(),
  loginWithWechat: vi.fn(),
  refreshUnreadBadge: vi.fn(),
  hasAgreedToAgreements: vi.fn(),
  recordAgreementConsent: vi.fn(),
  navigateAfterAuth: vi.fn(),
}));

vi.mock('../miniprogram/utils/agreement', () => ({
  AGREEMENT_REQUIRED_MESSAGE: '请先阅读并同意《用户服务协议》和《隐私政策》',
  ensureAgreement: (agreed: boolean, onMissing: () => void) => {
    if (agreed) {
      return true;
    }
    onMissing();
    return false;
  },
  hasAgreedToAgreements: mocks.hasAgreedToAgreements,
  recordAgreementConsent: mocks.recordAgreementConsent,
}));

vi.mock('../miniprogram/utils/session', () => ({
  loginWithPassword: mocks.loginWithPassword,
  loginWithWechat: mocks.loginWithWechat,
}));

vi.mock('../miniprogram/utils/notifications', () => ({
  refreshUnreadBadge: mocks.refreshUnreadBadge,
}));

vi.mock('../miniprogram/utils/invite', () => ({
  inviteBindPath: vi.fn(() => '/pages/phone-bind/index'),
  inviteTokenFromQuery: vi.fn(() => ''),
  navigateAfterAuth: mocks.navigateAfterAuth,
}));

vi.mock('../miniprogram/utils/api-errors', () => ({
  errorMessage: (_error: unknown, fallback: string) => fallback,
}));

interface LoginPage {
  data: Record<string, unknown> & { agreed: boolean; error: string; phone: string; password: string };
  setData(patch: Record<string, unknown>): void;
  onLoad(query: Record<string, string | undefined>): void;
  submit(): Promise<void>;
  wechatLogin(): Promise<void>;
}

let pageConfig!: Record<string, unknown> & { data: Record<string, unknown> };

beforeAll(async () => {
  vi.stubGlobal('Page', (config: typeof pageConfig) => {
    pageConfig = config;
  });
  await import('../miniprogram/pages/login/index');
});

afterAll(() => {
  vi.unstubAllGlobals();
});

beforeEach(() => {
  vi.clearAllMocks();
  mocks.hasAgreedToAgreements.mockReturnValue(false);
  mocks.loginWithPassword.mockResolvedValue({});
  mocks.loginWithWechat.mockResolvedValue({ needBindPhone: false, user: {} });
});

function pageInstance(): LoginPage {
  const page = Object.create(pageConfig) as LoginPage;
  page.data = { ...pageConfig.data } as LoginPage['data'];
  page.setData = (patch) => Object.assign(page.data, patch);
  return page;
}

describe('MP01 登录页协议同意', () => {
  it('未勾选时手机号密码登录被拦截，不记录同意', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.phone = '13800138000';
    page.data.password = 'abc12345';

    await page.submit();

    expect(mocks.loginWithPassword).not.toHaveBeenCalled();
    expect(mocks.recordAgreementConsent).not.toHaveBeenCalled();
    expect(page.data.error).toContain('请先阅读并同意');
  });

  it('未勾选时微信登录被拦截', async () => {
    const page = pageInstance();
    page.onLoad({});

    await page.wechatLogin();

    expect(mocks.loginWithWechat).not.toHaveBeenCalled();
    expect(mocks.recordAgreementConsent).not.toHaveBeenCalled();
  });

  it('勾选后记录同意并继续登录', async () => {
    const page = pageInstance();
    page.onLoad({});
    page.data.agreed = true;
    page.data.phone = '13800138000';
    page.data.password = 'abc12345';

    await page.submit();

    expect(mocks.recordAgreementConsent).toHaveBeenCalledOnce();
    expect(mocks.loginWithPassword).toHaveBeenCalledWith('13800138000', 'abc12345');
  });

  it('已同意当前版本的用户进入页面时默认勾选', () => {
    mocks.hasAgreedToAgreements.mockReturnValue(true);
    const page = pageInstance();
    page.onLoad({});
    expect(page.data.agreed).toBe(true);
  });
});
