// 协议同意记录：登录与绑定手机号前必须主动勾选《用户服务协议》和《隐私政策》。
// 仅存本机，不上传；同意版本与协议页更新日期联动，改协议实质内容时旧同意自动失效。

import { LEGAL_VERSION } from './legal';

const CONSENT_KEY = 'yhfc.agreement.consent';

export const AGREEMENT_REQUIRED_MESSAGE = '请先阅读并同意《用户服务协议》和《隐私政策》';

/** 协议勾选组件的 change 事件载荷。 */
export interface AgreementChangeEvent {
  detail: { checked: boolean };
}

/** 页面侧协议状态所需的最小形状。 */
export interface AgreementPageLike {
  data: { agreed: boolean };
  setData(patch: { agreed?: boolean; error?: string }): void;
}

interface ConsentRecord {
  version: string;
  agreedAt: string;
}

/** 读取本机同意记录；版本不符或存储不可用时按未同意处理。 */
export function hasAgreedToAgreements(): boolean {
  try {
    const record = wx.getStorageSync(CONSENT_KEY) as ConsentRecord | undefined;
    return record?.version === LEGAL_VERSION;
  } catch {
    return false;
  }
}

/** 记录当前版本的同意时间；存储失败不阻断登录，下次进入会重新征得同意。 */
export function recordAgreementConsent(): void {
  const record: ConsentRecord = {
    version: LEGAL_VERSION,
    agreedAt: new Date().toISOString(),
  };
  try {
    wx.setStorageSync(CONSENT_KEY, record);
  } catch {
    // 忽略本地存储异常。
  }
}

export function openTerms(): void {
  void wx.navigateTo({ url: '/pages/terms/index' });
}

export function openPrivacy(): void {
  void wx.navigateTo({ url: '/pages/privacy/index' });
}

/** 协议勾选变化：写入页面状态并清除旧错误。 */
export function applyAgreementChange(page: AgreementPageLike, event: AgreementChangeEvent): void {
  page.setData({ agreed: event.detail.checked, error: '' });
}

/** 页面共用门禁：未同意时写入提示并返回 false。 */
export function requireAgreement(page: AgreementPageLike): boolean {
  if (page.data.agreed) {
    return true;
  }
  page.setData({ error: AGREEMENT_REQUIRED_MESSAGE });
  return false;
}
