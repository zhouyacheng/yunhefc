package com.yunhefc.auth.wechat;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * dev/test 微信授权模拟页：模拟微信确认页后跳回 AUTH-08，便于端到端联调。
 * 仅 mock 模式且非 prod 注册，不计入 05 契约。
 */
@RestController
@Profile("!prod")
@ConditionalOnProperty(prefix = "yunhefc.wechat", name = "mode", havingValue = "mock", matchIfMissing = true)
public class MockWechatAuthorizeController {

    @GetMapping(value = "/api/v1/c/auth/wechat/mock-authorize", produces = MediaType.TEXT_HTML_VALUE)
    public String authorize(@RequestParam String state) {
        String callback = "/api/v1/c/auth/wechat/callback?code=mock-code&state="
                + URLEncoder.encode(state, StandardCharsets.UTF_8);
        return """
                <!doctype html>
                <html lang="zh-CN">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>微信授权（开发环境）</title>
                  <style>
                    body { margin: 0; padding: 32px 20px; font-family: system-ui, sans-serif; color: #0f172a; }
                    .card { max-width: 480px; margin: 0 auto; padding: 24px; border: 1px solid #e2e8f0; border-radius: 16px; }
                    .confirm { display: inline-block; margin-top: 16px; padding: 10px 24px; border-radius: 999px;
                               background: #0f7a4d; color: #fff; text-decoration: none; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <h1>微信授权（开发环境模拟）</h1>
                    <p class="muted">此页面仅用于本地与测试环境，模拟微信静默授权。</p>
                    <a class="confirm" href="%s">确认授权</a>
                  </div>
                </body>
                </html>
                """.formatted(HtmlUtils.htmlEscape(callback));
    }
}
