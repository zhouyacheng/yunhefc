// 协议与隐私静态页共用结构、版本日期与页面数据。

export interface LegalSection {
  heading: string;
  paragraphs?: string[];
  items?: string[];
}

/** 协议版本；修改协议实质内容时只改这里，页面日期与用户同意版本同步失效。 */
export const LEGAL_VERSION = '2026-11-27';
export const LEGAL_UPDATED_AT = LEGAL_VERSION;
export const LEGAL_EFFECTIVE_AT = LEGAL_VERSION;

/** MP15/MP16 共用的页面数据，避免两页重复拼装。 */
export function legalPageData(title: string, sections: LegalSection[]) {
  return {
    title,
    updatedAt: LEGAL_UPDATED_AT,
    effectiveAt: LEGAL_EFFECTIVE_AT,
    sections,
  };
}
