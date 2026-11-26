package com.yunhefc.content.domain;

/**
 * 内容域固定约定；成长上限与分组规则随票 03/04 逐步补充。
 */
public final class ContentRules {

    /** 05 CNT-03：网格与列表的缩略图走签名 URL 的 x-oss-process，详情签原图。 */
    public static final String THUMBNAIL_PROCESS = "image/resize,m_lfit,w_480/quality,q_80";

    private ContentRules() {
    }
}
