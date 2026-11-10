# 游客模式 + 工具箱拆分（APK 分析移出后台）

> 任务：`11-10-guest-mode-tools`（复杂任务）。需求见本文件，技术设计见 `design.md`，执行计划见 `implement.md`。

## 背景

两件事凑在一起：

1. **门户现在其实是"会员专区"**：后端 `AuthInterceptor.LOGIN_PATTERNS` 把 `/`、`/appmarket`、`/appmarket/**`、`/api/v1/appmarket/**` 全划进"登录才可访问"，未登录直接被 302 到 `/login`。作为个人站点的门面，访客连看一眼都要账号，不合理。
2. **管理后台里混着"不是后台"的东西**：`/admin/apk`（APK 分析）是纯浏览器工具——读文件、解析清单、算哈希、验签名，一个接口都不调，却挂在 ADMIN 权限和管理后台的浅色 UI 下面。

## 目标

- **游客模式**：未登录也能完整浏览门户（列表、分类、搜索、详情、下载链接），后台与所有写操作仍需登录。
- **工具箱**：把 APK 分析移出后台，成为公开的 `/tools` 工具区，跟后台彻底分开（路由、导航、视觉都不再属于后台）。
- **顺手修一个既有缺陷**：后端 SPA 回退只匹配单段路径，`/appmarket/3`、`/tools/apk` 这类两段路径直接访问/刷新会 404。

## 需求

- **R1 门户页面免登录**：`/`、`/appmarket`、`/appmarket/**` 不再要求登录（页面由后端伺服时也能游客直达）。
- **R2 只读接口匿名可访问（显式白名单）**：`/api/v1/appmarket/apps`、`/api/v1/appmarket/apps/**`、`/api/v1/appmarket/incremental-patch` 放行；**其余 `/api/v1/appmarket/**` 保持受保护**，`/admin/**` 与 `/api/v1/appmarket/admin/**` 继续要求 ADMIN。不做"整个前缀放开"。
- **R3 前端游客态**：导航栏在未登录时显示「登录」入口（不是空白），管理员才显示「管理后台」；登录页提供「先随便逛逛 →」返回首页，不再把登录当唯一出路。
- **R4 工具箱**：新增 `/tools`（工具索引）与 `/tools/apk`（APK 分析，游客可用），使用门户主题；`/admin/apk` 301/重定向到 `/tools/apk`；后台头部移除「APK 分析」按钮（「从 APK 导入」保留在后台）。
- **R5 共享组件主题解耦**：`ApkSignatureCard` 目前硬绑 `admin-*` 类，被后台的 APK 导入弹窗与工具页共用 → 加 `variant` 参数（默认 admin，工具页传 portal），**不允许**全局把 admin token 刷成 portal token（否则后台弹窗会变暗底）。
- **R6 SPA 回退支持多段路径**：`VueSpaController` 的回退正则从单段扩展到 ≤3 段，使 `/appmarket/:id`、`/tools/apk` 直接访问与刷新都能落到 `index.html`。
- **R7 文档与验证**：在 `docs/`（或 README 一节）说明游客模式边界与工具箱入口；按验收清单逐条验证。

## 已确认的技术事实（探查结论）

- `AuthInterceptor`（`src/main/java/com/zaze/server/config/AuthInterceptor.kt`）分两级：`ADMIN_PATTERNS`（`/admin/**`、`/api/v1/appmarket/admin/**` 等，必须 ADMIN）与 `LOGIN_PATTERNS`（`/`、`/appmarket/**`、`/api/v1/appmarket/**`，登录即可）。`WebMvcConfig.PUBLIC_PATTERNS` 是拦截器的 exclude 白名单（`/login`、`/api/v1/auth/**`、静态资源等）。
- `/api/v1/appmarket` 前缀下**不在 admin 命名空间的接口全是 GET**：`AppMarketApiController` 的 `/apps`、`/apps/categories`、`/apps/{id}`，与 `IncrementalPatchApiController` 的 `/incremental-patch`。这是"显式白名单可穷举"的前提。
- `/api/v1/auth/me` 匿名时返回 **HTTP 200 + `Response(code=0, data=null)`**（`AuthApiController.me()` 直接包 `authService.currentUser()`）→ 前端不会触发 401 跳转，游客态不需要动 `request()` 的 401 逻辑。**但必须在实现时回归确认**（后端若改成 401，游客会被弹去登录页）。
- `VueSpaController`：`@GetMapping("/", "/{path:[^\\.]*}")` —— **单段**匹配，`/appmarket/3`、`/tools/apk` 这类两段路径不会被回退，生产（Spring 伺服）下直接访问会 404。这是既有缺陷，本次一并修。
- `ApkAnalyzerView.vue` 有 19 处 `admin-*` 类、`ApkSignatureCard.vue` 20 处，`ApkAnalysisPanel.vue` 0 处（说明面板本身是中性类，可安全共用）。`ApkImportModal`（后台）复用 `ApkAnalysisPanel` + `ApkSignatureCard`。
- 前端路由 `meta.theme` 决定整页皮肤（`portal` 暗色 / `admin` 浅色），`App.vue` 按此切背景与导航变体。
- 主题令牌：`portal.{bg,bg2,surface,surface2,border,border2,text,muted,mint,mint2,amber,sky,coral}`；`admin.{bg,surface,border,text,muted,accent}`。

## 约束

- **不放开任何写接口**；`/admin/**` 与 `**/admin/**` 的鉴权语义一个字不改。
- **不引用户体系**（不做注册、不做普通用户角色、不做游客 session）；游客 = 没有 session 的匿名请求。
- 「从 APK 导入」仍留在后台（它要写库、要管理员），不在本次移出范围。
- 不改门户视觉基线（薄荷绿/中性底/无发光），工具区只是把既有组件换个皮肤；后台视觉不因本次改动变化（`ApkSignatureCard` 的默认 variant 必须仍是 admin）。
- 后端改动只碰 `AuthInterceptor` / `WebMvcConfig` / `VueSpaController` 三个文件，且都是"放行规则/回退正则"级别。

## 验收标准

**A. 游客（无 session / 无 cookie）**

- [ ] `curl -i http://localhost:8080/` → 200 且返回 `index.html` 内容（**不是** 302 → `/login`）。
- [ ] `curl -i http://localhost:8080/appmarket` → 200；`curl -i http://localhost:8080/appmarket/3` → 200（验证 R6 回退）。
- [ ] `curl -s "http://localhost:8080/api/v1/appmarket/apps?page=1&size=5"` → 200 且有数据；`/apps/categories`、`/apps/{id}` 同样 200。
- [ ] `curl -i http://localhost:8080/api/v1/appmarket/admin/apps` → 401（未被误放开）。
- [ ] `curl -i http://localhost:8080/api/v1/appmarket/incremental-patch` → 按设计放行（不是 401）。
- [ ] 浏览器无痕窗口打开 `/`：门户完整可见（列表、分类、搜索、详情、下载链接），导航无「管理后台」、有「登录」。
- [ ] 无痕访问 `/admin`：被引导到登录页（不是白屏、不是空表格）。

**B. 工具箱**

- [ ] 无痕访问 `/tools` → 工具索引可用，视觉为门户暗色（不是后台浅色）。
- [ ] 无痕访问 `/tools/apk` → 上传真实 APK 能解析出包名/版本/权限/签名证书（说明纯前端链路完整）。
- [ ] `/admin/apk` → 自动跳到 `/tools/apk`（旧书签不废）。
- [ ] 后台头部不再有「APK 分析」按钮；「从 APK 导入」仍在且功能正常（弹窗里 `ApkSignatureCard` 仍是浅色后台样式）。

**C. 登录用户回归**

- [ ] 登录后导航出现「管理后台」，`/admin` 与全部 CRUD、采集、外部导入与改动前一致。
- [ ] 任何写接口在游客态仍返回 401，后台不会被游客改动。

**D. 门禁**

- [ ] `./gradlew compileJava compileKotlin --offline` 通过。
- [ ] `./gradlew :feature:appmarket:compileKotlin --offline` 通过。
- [ ] `npm run typecheck`、`npm run build` 通过。
- [ ] 工具区相关文件 `grep -c "admin-"` 为 0（`variant` 映射表内部除外）。

## Notes

- 已确认决策（2026-11-10）：① 游客**只读全开**（含下载链接）；② 只移出 **APK 分析器**，「从 APK 导入」与采集/外部导入留在后台；③ 移出后放**独立工具箱、公开免登录**。
- **风险提示（需要你知晓并接受）**：R1 一旦生效，门户内容对全网可见（此前是登录后才可见）。这是"游客模式"的必然代价；如果只想让部分内容公开，得改成"游客可见字段裁剪"，属于另一档设计。
- 与 `11-10-split-deploy`（两端分开部署）无冲突，但都动到访问边界：若两者并行实施，先落本任务的放行规则，再改部署形态，避免同时改鉴权与跨域导致问题难以归因。
