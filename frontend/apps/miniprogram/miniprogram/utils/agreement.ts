// 协议同意记录：登录与绑定手机号前必须主动勾选《用户服务协议》和《隐私政策》。
// 仅存本机，不上传；同意版本与协议页更新日期联动，改协议实质内容时旧同意自动失效。

import { LEGAL_VERSION } from './legal';

const CONSENT_KEY = 'yhfc.agreement.consent';

export const AGREEMENT_VERSION = LEGAL_VERSION;

export const AGREEMENT_REQUIRED_MESSAGE = '请先阅读并同意《用户服务协议》和《隐私政策》';

interface ConsentRecord {
  version: string;
  agreedAt: string;
}

/** 读取本机同意记录；版本不符、格式异常或存储不可用时按未同意处理。 */
export function hasAgreedToAgreements(): boolean {
  try {
    const stored = wx.getStorageSync(CONSENT_KEY) as ConsentRecord | string | undefined;
    if (!stored) {
      return false;
    }
    const record = typeof stored === 'string' ? (JSON.parse(stored) as ConsentRecord) : stored;
    return record?.version === AGREEMENT_VERSION;
  } catch {
    return false;
  }
}

/** 记录当前版本的同意时间；存储失败不阻断登录，下次进入会重新征得同意。 */
export function recordAgreementConsent(): void {
  const record: ConsentRecord = {
    version: AGREEMENT_VERSION,
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

/** 同意门禁：已同意返回 true；未同意时调用 onMissing 提示并返回 false。 */
export function ensureAgreement(agreed: boolean, onMissing: () => void): boolean {
  if (agreed) {
    return true;
  }
  onMissing();
  return false;
}
