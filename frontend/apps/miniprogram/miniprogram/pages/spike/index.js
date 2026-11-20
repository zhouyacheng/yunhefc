// 与 utils/config.ts 保持一致；spike 用 JS，避免同目录出现同名 .js/.ts 模块。
const BASE_URL = 'http://localhost:8080/api/v1/c';

// 账号由使用者自行输入，避免把测试凭据打进小程序包。

// 从响应头提取 YHFC_C。已过期（Max-Age=0）时返回空串表示清除。
function extractSessionCookie(header) {
  const raw = header['Set-Cookie'] || header['set-cookie'];
  if (!raw) return '';
  const list = Array.isArray(raw) ? raw : [raw];
  for (let i = 0; i < list.length; i += 1) {
    const item = String(list[i]);
    const matched = item.match(/YHFC_C=([^;]*)/);
    if (!matched) continue;
    if (/Max-Age=0/i.test(item) || /Expires=Thu, 01 Jan 1970/i.test(item)) return '';
    return 'YHFC_C=' + matched[1];
  }
  return '';
}

Page({
  data: {
    baseUrl: BASE_URL,
    phone: '',
    password: '',
    running: false,
    steps: [],
    verdict: '',
    verdictClass: '',
  },

  onPhoneInput(event) {
    this.setData({ phone: event.detail.value });
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value });
  },

  reset() {
    this.csrfToken = '';
    this.csrfHeaderName = 'X-XSRF-TOKEN';
    this.cookieHeader = '';
    this.setData({ steps: [], verdict: '', verdictClass: '' });
  },

  addStep(name, status, message, meta) {
    this.setData({
      steps: this.data.steps.concat([{ idx: this.data.steps.length, name, status, message, meta }]),
    });
  },

  // manualCookie=false：完全依赖微信网络栈自动处理 Set-Cookie。
  // manualCookie=true ：手动保存 Set-Cookie 并在后续请求回传 Cookie 头。
  request(method, path, data, header, manualCookie) {
    return new Promise((resolve) => {
      const finalHeader = Object.assign({ 'Content-Type': 'application/json' }, header || {});
      const sentCookie = manualCookie && this.cookieHeader;
      if (sentCookie) finalHeader.Cookie = this.cookieHeader;

      wx.request({
        url: BASE_URL + path,
        method,
        data,
        header: finalHeader,
        success: (res) => {
          if (manualCookie) {
            const next = extractSessionCookie(res.header || {});
            if (next) this.cookieHeader = next;
          }
          res.sentCookie = Boolean(sentCookie);
          resolve(res);
        },
        fail: (err) =>
          resolve({ statusCode: -1, data: { message: err.errMsg }, header: {}, sentCookie: false }),
      });
    });
  },

  cookieHint(res, manualCookie) {
    const header = res.header || {};
    const raw = header['Set-Cookie'] || header['set-cookie'];
    const seen = raw
      ? String(raw).indexOf('YHFC_C=') !== -1
        ? '收到 YHFC_C'
        : '收到其他 Cookie'
      : '无 Set-Cookie';
    if (manualCookie) {
      return '手动 Cookie：' + (res.sentCookie ? '已回传' : '未回传') + '；' + seen;
    }
    return '自动 Cookie：依赖微信网络栈；' + seen;
  },

  bodyOf(res) {
    return res.data || {};
  },

  codeOf(res) {
    const code = this.bodyOf(res).code;
    return code === undefined ? '-' : code;
  },

  messageOf(res) {
    const body = this.bodyOf(res);
    return body.message || body.msg || '';
  },

  async runFlow(manualCookie) {
    if (this.data.running) return;
    if (!this.data.phone || !this.data.password) {
      this.setData({ verdict: '请先填写测试手机号和密码', verdictClass: 'bad' });
      return;
    }
    this.reset();
    this.setData({ running: true });
    const modeName = manualCookie ? '手动 Cookie' : '自动 Cookie';

    const csrfRes = await this.request('GET', '/auth/csrf', undefined, undefined, manualCookie);
    const csrfData = this.bodyOf(csrfRes).data || {};
    this.csrfToken = csrfData.token || '';
    this.csrfHeaderName = csrfData.headerName || 'X-XSRF-TOKEN';
    this.addStep(
      '1. GET /auth/csrf',
      String(csrfRes.statusCode),
      'code=' + this.codeOf(csrfRes) + '，token ' + (this.csrfToken ? '已获取' : '缺失'),
      this.cookieHint(csrfRes, manualCookie),
    );
    if (!this.csrfToken) {
      this.finish(false, 'NO-GO：拿不到 CSRF token');
      return;
    }

    const headers = {};
    headers[this.csrfHeaderName] = this.csrfToken;

    const loginRes = await this.request(
      'POST',
      '/auth/login',
      { phone: this.data.phone, password: this.data.password },
      headers,
      manualCookie,
    );
    this.addStep(
      '2. POST /auth/login',
      String(loginRes.statusCode),
      'code=' +
        this.codeOf(loginRes) +
        (this.messageOf(loginRes) ? '，' + this.messageOf(loginRes) : ''),
      this.cookieHint(loginRes, manualCookie),
    );
    if (loginRes.statusCode !== 200) {
      this.finish(false, 'NO-GO（' + modeName + '）：登录 403/401，Cookie 会话不可用');
      return;
    }

    const rotated = (this.bodyOf(loginRes).data || {}).csrfToken;
    if (rotated) this.csrfToken = rotated;

    const meRes = await this.request('GET', '/me', undefined, undefined, manualCookie);
    this.addStep(
      '3. GET /me',
      String(meRes.statusCode),
      'code=' + this.codeOf(meRes) + (this.messageOf(meRes) ? '，' + this.messageOf(meRes) : ''),
      this.cookieHint(meRes, manualCookie),
    );

    this.finish(
      meRes.statusCode === 200,
      meRes.statusCode === 200
        ? 'GO（' + modeName + '）：Cookie 会话可用'
        : 'NO-GO（' + modeName + '）：/me 未通过',
    );
  },

  runAuto() {
    this.runFlow(false);
  },

  runManual() {
    this.runFlow(true);
  },

  async checkMe() {
    if (this.data.running) return;
    this.setData({ running: true });
    const meRes = await this.request('GET', '/me', undefined, undefined, true);
    this.addStep(
      '冷启动后 GET /me（手动 Cookie）',
      String(meRes.statusCode),
      'code=' + this.codeOf(meRes) + (this.messageOf(meRes) ? '，' + this.messageOf(meRes) : ''),
      this.cookieHint(meRes, true),
    );
    this.setData({ running: false });
  },

  finish(ok, text) {
    this.setData({ running: false, verdict: text, verdictClass: ok ? 'ok' : 'bad' });
  },
});
