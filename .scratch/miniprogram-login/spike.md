# 小程序登录 · Phase 0 会话传输 Spike

> 状态：DevTools 与真机登录链路均已验证（2026-11-20）；生产 HTTPS 域名待验
> 目的：证明 `wx.request` 能否携带后端 `YHFC_C` Cookie 会话与 `X-XSRF-TOKEN`。

## 结论（DevTools）

**GO（手动 Cookie 回传）**，传输方案确定为：

- 小程序端维护**内存 Cookie jar**：从响应头 `Set-Cookie` 提取 `YHFC_C`，后续请求手动写入 `Cookie` 头。
- **不能**依赖微信网络栈自动管理 Cookie：自动模式下每次请求都新建会话（每步响应都重新下发 `YHFC_C`），导致登录 CSRF 失败（40301）、`/me` 401。
- 后端鉴权无需改动，不启用 `X-Session-Token` 回退。

## 实验记录

| 模式 | 1. GET /auth/csrf | 2. POST /auth/login | 3. GET /me | 冷启动 /me |
|---|---|---|---|---|
| A. 自动 Cookie | 200 | **403**（40301 无权限 = CSRF 失败） | **401**（40101） | — |
| B. 手动 Cookie | 200（未回传） | **200** | **200** | 200 |

补充观察：

- A 模式三次响应均带 `Set-Cookie: YHFC_C`，说明 POST 未携带旧 Cookie，服务端每次新建会话；CSRF token 与会话不匹配，被 `accessDeniedHandler` 映射为 `40301`。
- B 模式第 2 步在请求前回传第 1 步拿到的 `YHFC_C`，登录成功；第 2 步响应下发轮换后的 `YHFC_C`，第 3 步使用新值 `200`。
- 环境：微信开发者工具，本地后端 `http://localhost:8080`，dev profile（mock 短信/微信），测试账号 1002。
- 小程序 AppID：`wx732fce8d86025296`。

## 真机验证（2026-11-20 已完成）

- [x] 真机 `wx.login` → AUTH-20 → 首次绑定手机号（AUTH-21）→ 进入赛程成功
- [x] 真机允许手动设置 `Cookie` 头，`YHFC_C` 会话在请求间正常回传
- [x] 绑定后 `/c/me` 返回已登录用户；赛程/详情/球队可取到数据
- [ ] 设备型号 / OS 版本 / 基础库版本（待补）
- [ ] 冷启动后自动静默重登（`app.ts` restore → login）
- [ ] 生产 HTTPS 域名 + 小程序后台 request 合法域名登记

验证方式说明：真机通过**电脑局域网 IP + HTTP**（`http://10.103.47.17:8080/api/v1/c`），并在真机调试面板开启「不校验合法域名」；不是生产 HTTPS 域名。因此 Phase 4 的代码级前置门已过，但**上线前仍需在已备案 HTTPS 域名 + 合法域名登记下复验一次**。

未回退 `X-Session-Token`，Cookie jar 方案成立。

## 对计划的影响

- Phase 2 后端**不引入** token 回退，只做 mini 登录接口。
- Phase 3 小程序端 `utils/request.ts` 必须内置 Cookie jar（内存，不落 storage）。
- 删除公众号链路（Phase 4）的前置条件：真机登录链路通过（已满足）。
