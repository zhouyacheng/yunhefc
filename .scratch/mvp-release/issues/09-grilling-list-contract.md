# 09 — M12 列表契约与 B 端列表复用

Type: grilling
Status: resolved
Blocked by: 01

## Question

基于交付缺口盘点，确定 M12 的统一列表契约：

- 后端分页/筛选/排序参数命名、白名单、空结果行为与错误码；沿用 05 §2.2 的 `Page<T>`。
- 前端筛选状态进 URL 的方式；C 端赛程筛选与分页。
- B 端列表组件复用边界（Element Plus 表格/分页/筛选），哪些列表必须改、哪些已满足。
- 明确不引入共享查询 DSL 或额外抽象，除非现有 controller 重复到不可维护。

需求 LIST-01~05 已定义交互；本票只解决实现契约与缺口修复边界。事实指针：01 答案、需求 LIST-01~05、05 §2.2、02 §4–§7、现有 Matches/Registrations/Events/Players 列表实现。

## Answer

### 1. 后端契约（冻结现状）

- 分页：`page` 默认 1、≥1；`pageSize` 默认 10、白名单 10/20/50/100；MAT-02/MAT-09 例外允许 1–100 任意整数。响应统一 `Page<T>`（`items/total/page/pageSize/totalPages`），不改。
- 命名：保留现有语义化参数名，不重命名、不引入统一 Query 对象或查询 DSL。
- 筛选：缺省/空字符串 = 不过滤；显式非法枚举/越界 = `40001 VALIDATION_FAILED`。
- 校验缺口（唯一后端改动）：`INV-04` 补 `type∈{0,1}`、`status∈{0,1,2}`；`AUTH-13` 补 `status∈{0,1,2}`。pageSize 已由 `PageQuery.of` 校验，无需再补；其余列表的枚举/范围与 sort 校验已齐备。
- 排序：`sort=field:asc|desc`；MAT-02/09 仅 `startAt`，PLR-05 白名单 jersey/skill/position/region，REG-03/05/06、ATT-03/04、INV-04、AUTH-13 固定排序且无 sort 参数；非法 sort → 40001。
- 空结果：200 + 空 Page，列表不返回 404；M12 不新增错误码。
- 新接口 NTF-01/AUD-01 按 05 §3.10/§3.11 实现，并遵守上述分页与错误口径。

### 2. C 端赛程（C02）

- 筛选：类型、地点（文本模糊）、状态、年份、月份；点「搜索」生效并回第 1 页；「重置」清空并立即请求。
- 年份下拉 = 当前年往前 5 年；月份 1–12；选月份时只传 `month=YYYY-MM`，未选月份时传 `season`。
- URL：参数名与后端一致（`type/venue/status/season/month/page/pageSize`），只写非默认值，`router.replace`；加载时按 URL 初始化并直接请求；`sort`/`scope` 固定不暴露。
- 分页：10/20/50/100；空结果展示重置入口（LIST-05）。

### 3. B 端列表

- 不抽共享表格组件、分页 composable 或查询 DSL；各页使用 Element Plus 原生 `el-table/el-pagination/筛选表单`，统一四条约定：pageSize 10/20/50/100、筛选生效回第 1 页、加载/失败/空态、空态文案。
- B07：筛选 `name/region/type/admin/status/position/skill`，点「搜索」生效；`el-table` 表头单列 `sortable="custom"` 映射 sort（jersey/skill/position/region），默认号码升序；分页 10/20/50/100。
- B10：筛选 `from/to/actor/action/targetType/targetId`，点「搜索」生效并回第 1 页（据此覆盖 02 §6.12「筛选变更后回第 1 页」的表述）；固定时间倒序；分页 10/20/50/100。
- B11：仅分页，不补筛选 UI；固定 id 倒序。
- B02：维持 status 筛选 + 分页，不加深类型/赛季筛选；B03–B06 内部固定 `pageSize=100`，不受 LIST-04 约束。
- NTF-01 列表（C06 内）：`unread` + `page/pageSize`，时间倒序；由 C06 页面票实现。

### 4. 覆盖与验收

- 契约适用所有分页列表；M12 自身落地 = C02 完整筛选/URL + INV-04/AUTH-13 校验 + 02 补充 + 06 决策记录；B07/B10/B11/NTF-01 由页面票按契约实现。
- 测试：M12 自身补 C02 Playwright（筛选/搜索/重置/URL 恢复/空态）与后端单测（PageQuery 白名单、MatchQueryParsing 非法值、PLR-05 sort 白名单、INV-04/AUTH-13 非法 type/status）；B 端列表 e2e 随各页面票。
- 文档：02 §5.2 补搜索与 URL 语义、§6.9 补表头排序、§6.12 改为搜索生效；06 记一条；05 不改。
