# 小程序四 Tab 与 C 端对齐 · 复审跟进

固定点：`HEAD 9b488c8`；两轴审查（Standards + Spec）。

## 已修复

- MP04 事件保存 40901/40905 冲突后静默刷新（`pages/match-detail/index.ts`）。
- MP04 状态 0–2 时「录入事件」改为可见但禁用，并展示原因；与 C03 一致。
- 头像签名 URL 校验与姓名首字抽取收拢到 `utils/avatar.ts`，「我的」与编辑资料复用。
- `formatDateTime` 改为 `YYYY/MM/DD HH:mm`，通知、我的报名、考勤时间与 C 端一致。
- 移除首页 CTA 的 `canSignup` 分支（scope creep），文案/禁用恢复完全沿用 C01。
- MP08 PLR-01 加载失败增加「重试加载」，不再静默丢失地区/副位置。
- 关键视觉对齐：页头 eyebrow 金色、标题 56rpx/700；卡片 40rpx padding + 边框；首页 Hero 标题 60rpx/700；比赛详情比分 80rpx、meta 26rpx、卡片标题 34rpx、进度条 16rpx 渐变；星级 26rpx、空星 #94a3b8。

## code-review 15 项修复（2026-11-20）

1. 状态面板点击当前状态不再提交 MAT-12，避免误写 `manualStatus` 停止自动流转。
2. `load()` 开始时重置四个子请求 loading，刷新换代失败不再永久卡加载。
3. 事件编辑冲突后刷新并更新 `editingVersion`，弹层重试使用新版本；表单不再因数据刷新被重置。
4. `loadLineup` 校验响应 side，快速切换主客队不会被乱序响应覆盖。
5. 阵容 Tab 首次打开按我方 `ourSide` 选边；队内/训练赛仍默认主队。
6. 静默刷新 404 清空详情并展示「比赛不存在」，成功路径复位 `notFound`。
7. 从「我的报名」或 MATCH 通知进入详情前标记 dirty，返回「我的」强制重载报名列表。
8. 取消报名使用 MAT-03 返回的本人报名状态，递补后文案与实际一致。
9. 事件编辑仅排除已取消/已延期，状态回退 0–2 仍可编辑本人事件。
10. NTF-02 失败返回 `null`，未读未知时不禁用「全部已读」。
11. 未读角标请求改为后台刷新，不再阻塞通知/报名列表首屏。
12. 全局卡片改为 32rpx padding + 2rpx 边框，对齐 C 端移动端 16px/1px。
13. 比赛详情 Tab/卡片间距按 px→rpx 修正（22px/18px、16px 等）。
14. 「我的」通知/报名标题 32rpx/700，身份卡姓名 48rpx/700。
15. 球队页分组/球员姓名 700，small 24rpx。

## 复审未闭环项（2026-11-20）

- #3 事件冲突重试：`load()` 改为等待 `loadEvents(seq)`，冲突后 `load(true)` 返回时 `rawEvents` 已刷新，`editingVersion` 取到新版本。
- #7 跨页报名刷新：新增 `utils/registration-signal.ts`；首页报名、比赛详情报名/取消、从「我的」进入详情均标记，`profile` `onShow` 消费后强制重载报名列表。
- #12 比赛卡片边框 1rpx → 2rpx，与其他卡片一致。
- #13 状态/事件弹层样式由 px/硬编码颜色改为 rpx 与 `--yh-*` token。

## P2/P3 修复（2026-11-20）

P2 视觉：

- 全局字体改为与 Web 相同的 `Inter, PingFang SC, Microsoft YaHei, ui-sans-serif, system-ui`。
- 星级改为 C 端同款 24×24 SVG（满星金色、半星裁切、空星描边），不再用 `☆/★` 文本。
- 首页二期能力卡对齐 C01：32rpx 内边距、2rpx 边框、16rpx 间距、标题 32rpx、正文 26rpx；Hero 间距与区块节奏按 C01 移动端。
- 比赛详情 Hero 对齐 C03：52rpx 内边距、28rpx 间距、40rpx 圆角、18rpx 底距；队伍字号 36rpx；meta 改为换行横排（10px/22px 间距）。
- 事件卡对齐 C03 移动端：16rpx/18rpx 内边距、24rpx 字号、2rpx 边框；时间轴胶囊 2rpx 边框。
- 替补标签去掉位置后缀，改为 C03 的 `姓名 #球衣号`。
- 比赛列表筛选按钮补 `filter-o` 图标。

P3 结构：

- `MATCH_STATUS_TEXT` 只保留 `presentation.ts` 一份，`match-status` 改为引用。
- 新增 `utils/shanghai.ts`，三处 `SHANGHAI_OFFSET_MS` 收拢。
- 新增 `utils/match-type.ts`，事件/阵容/详情页四处 `type === 1 || type === 2` 收拢。
- `errorMessage` 统一到 `api-errors`，删除 profile 与 paged-feed 的本地副本。
- `attendanceText` 移入 `presentation`，详情页与写操作共用。
- MP04 写操作（报名/取消/签到、状态修改、事件录入）拆到 `pages/match-detail/actions.ts`，页面 963 → 758 行；`status-sheet` 删除未使用的 `busy` 属性。
- `event-form-sheet` 去掉 `as never`，改用 `MatchSummary`/`GroupingView` 类型；删除多余 `EventCreate` 断言。
- 内部辅助函数（`matchPostponable`/`matchCancellable`/`matchScheduleError`/`isOpponentLineupSide`/`REGISTRATION_STATUS_TEXT`）不再导出。

仍保留：

- `.btn--small` 72rpx（36px）；C 端对应 Vant small 为 32px，未按 44px 触控口径放大。
- `.muted` 仍内置 24rpx 字号；C 端 `.muted` 只负责颜色，字号由上下文决定。
- `match-detail` 仍有 40+ WXML 绑定字段，属于页面数据面；继续压缩需改 WXML 结构。

## 未处理（有意保留）

- Standards：`home/team/profile` 仍有直接 `request()` 调用；报名取消流程在首页/详情/我的三处各自处理冲突重载；`match-detail` 数据字段仍多。
- Spec：剩余 1–2px 级字号/间距差异（`.btn--small`、`.muted` 字号）未逐项追平。
- 开发者工具与 iOS/Android 真机视觉验收未执行（含 SVG 星级、弹层 rpx 样式需真机确认）。
