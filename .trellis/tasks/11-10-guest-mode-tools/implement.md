# 执行计划：游客模式 + 工具箱拆分

> 任务：`11-10-guest-mode-tools`。需求见 `prd.md`，设计见 `design.md`。
> 顺序：先放开后端（能被 curl 验证），再修回退，再改前端游客态，最后搬工具箱。每步有门禁。

## 0. 前置检查

```bash
cd /Users/zhaozhen/Documents/GitRepository/ZAZEWeb
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
java -version                      # 必须 11
lsof -i:3306 && echo "MySQL 在跑 → 后端可启动，验收可全跑" || echo "MySQL 未起 → 后端类验收会 blocked"

cd frontend && npm run typecheck   # 基线
```

- [ ] JDK 11、typecheck 基线绿。
- [ ] 记录本机是否可启动后端（决定后端验收是"跑通"还是"blocked"）。
- [ ] 先看 `IncrementalPatchApiController` 的 `/incremental-patch` 到底返回什么（`Read` 该文件），确认里面没有敏感字段——**如果它返回了不该公开的内容，就把这条从白名单里去掉**，并回写 design 决策。

**门禁**：`incremental-patch` 的返回体没看清之前，不要动 `PUBLIC_PATTERNS`。

## 1. 后端：放开游客读权限

- [ ] `AuthInterceptor.kt`：`LOGIN_PATTERNS` 改为 `emptyList<String>()`，并加注释说明"当前无只需登录的路径；游客模式的门户与只读接口已由 PUBLIC_PATTERNS 放行；将来新增回填即可"。`ADMIN_PATTERNS` 与 `preHandle` 逻辑**一字不动**。
- [ ] `WebMvcConfig.kt`：`PUBLIC_PATTERNS` 追加 `/api/v1/appmarket/apps`、`/api/v1/appmarket/apps/**`、`/api/v1/appmarket/incremental-patch`（若第 0 步判定不公开，则不加最后一条）。

```bash
./gradlew compileJava compileKotlin --offline
```

- [ ] 编译通过。

### curl 权限矩阵（后端启动后，不带任何 cookie）

```bash
BASE=http://localhost:8080
for u in / /appmarket /appmarket/3 /api/v1/appmarket/apps?page=1\&size=5 /api/v1/appmarket/apps/categories /api/v1/appmarket/admin/apps /admin; do
  printf "%-45s %s\n" "$u" "$(curl -s -o /dev/null -w '%{http_code} %{redirect_url}' "$BASE$u")"
done
```

- [ ] `/`、`/appmarket`、`/appmarket/3` → 200（第 3 条依赖第 2 步的回退修复；先做第 2 步再看）。
- [ ] `/api/v1/appmarket/apps*`、`/apps/categories` → 200 且有数据。
- [ ] `/api/v1/appmarket/admin/apps` → 401。
- [ ] `/admin` → 302 到 `/login`（页面请求走重定向，不是 JSON）。

**回滚点 A**：若 `/api/v1/appmarket/apps` 仍 401，先确认 `PUBLIC_PATTERNS` 是否被 `/**` 末尾匹配问题影响（AntPathMatcher 对 `/apps/**` 与 `/apps` 的差异），必要时把 `/apps/**` 换成显式三条。

## 2. 后端：SPA 回退支持多段

- [ ] `VueSpaController.kt`：`@GetMapping("/", "/{p1:[^\\.]*}", "/{p1:[^\\.]*}/{p2:[^\\.]*}", "/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}")`，注释写明"上限 3 段，扩展时同步加"。
- [ ] 重启后端，复跑：

```bash
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/appmarket/3     # 200
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/tools/apk       # 200（此时路由还未加，回退本身即 200）
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/assets/index.js # 200 或 404-as-static，不应被 forward 成 html
```

- [ ] 三条符合预期，且 `curl -s http://localhost:8080/appmarket/3 | head -3` 返回的是 `index.html` 内容。

## 3. 前端：游客态

- [ ] `src/api/client.ts`：`request()` 增加第三参 `opts?: { silent401?: boolean }`；`me()` 传入 `{ silent401: true }`。其余接口不动。
- [ ] `src/components/NavBar.vue`：
  - `items` 增加 `{ to: '/tools', label: '工具箱' }`（所有访客可见）；
  - 未登录时右侧渲染「登录」（`router-link to="/login"`，沿用现有幽灵胶囊样式）；已登录分支保持原样。
- [ ] `src/views/LoginView.vue`：表单下加「先随便逛逛 →」回 `/`，并补一句"浏览门户与工具箱无需登录"。

```bash
npm run typecheck
```

- [ ] 通过。

## 4. 前端：工具箱

- [ ] 新增 `src/views/ToolsView.vue`：工具卡列表（当前仅「APK 分析」），portal 令牌（`portal-surface` / `portal-border` / `shadow-tile`），标注「无需登录 · 文件不上传」。
- [ ] `src/router/index.ts`：
  - 新增 `/tools`（theme `portal`）与 `/tools/apk`（theme `portal`，组件 `ApkAnalyzerView`）；
  - 新增 `{ path: '/admin/apk', redirect: '/tools/apk' }`；
  - 移除原 `/admin/apk` 的组件路由。
- [ ] `src/views/AdminView.vue`：删「APK 分析」按钮与其 `router.push('/admin/apk')`（保留「从 APK 导入」）。
- [ ] `src/views/ApkAnalyzerView.vue`：19 处 `admin-*` → `portal-*`（按 `design.md` 4.3 映射表，**注意 `bg-admin-accent text-white` → `bg-portal-mint text-portal-bg` 的反转**）；顶部返回按钮从 `/admin` 改为 `/tools`。
- [ ] `src/components/apk/ApkSignatureCard.vue`：加 `variant?: 'portal' | 'admin'`（默认 `'admin'`），内部用映射表切换 class；`ApkAnalyzerView` 传 `portal`，`ApkImportModal` 不传。
- [ ] `src/components/apk/ApkAnalysisPanel.vue`：检查是否有硬编码浅色（`bg-white` / `text-gray-*` / `bg-gray-*`）；有则同样加 variant，无则不动。
- [ ] 确认 `ApkImportModal`（后台）里的签名卡仍是浅色后台样式（打开弹窗看一眼）。

```bash
npm run typecheck && npm run build
grep -rn "admin-" src/views/ApkAnalyzerView.vue src/components/apk/ | grep -v "variant" || echo "无 admin- 残留"
```

- [ ] typecheck/build 通过；除 `variant` 映射表外无 `admin-` 残留。

**回滚点 B**：若工具页出现"暗底上出现浅色块"或"浅底上出现暗色文字"，先确认是哪个组件漏了 variant，不要用全局 CSS 覆盖——那会让后台弹窗一起变。

## 5. 验证矩阵

### A. 游客（无痕窗口 / 无 cookie）

- [ ] A1 `/` 门户完整：列表有数据、分类可切、搜索可用、详情可进、下载链接可见。
- [ ] A2 导航无「管理后台」，有「登录」与「工具箱」。
- [ ] A3 `/admin` → 被引导到登录页（不是白屏、不是空表格）。
- [ ] A4 直接地址栏输入 `/appmarket/3` 并刷新 → 正常渲染（不是 404）。

### B. 工具箱

- [ ] B1 `/tools` 可用，视觉为门户暗色，与后台明显区分。
- [ ] B2 `/tools/apk` 上传真实 APK：包名/版本/权限/签名证书齐全（游客态，纯前端链路）。
- [ ] B3 `/admin/apk` 自动跳 `/tools/apk`。
- [ ] B4 后台头部无「APK 分析」按钮；「从 APK 导入」仍在，弹窗内签名卡为浅色后台样式。

### C. 登录回归

- [ ] C1 登录后导航出现「管理后台」；`/admin` 与全部 CRUD/采集/外部导入与改动前一致。
- [ ] C2 退出登录后回到游客态（导航恢复「登录」，后台入口消失）。

### D. 门禁

- [ ] D1 `./gradlew compileJava compileKotlin --offline`。
- [ ] D2 `./gradlew :feature:appmarket:compileKotlin --offline`。
- [ ] D3 `npm run typecheck && npm run build`。

## 6. 文档

- [ ] 新建 `docs/guest-mode.md`（或 README 增加一节），写清：
  1. 游客能做什么（门户 + 工具箱只读）、不能做什么（后台、所有写操作）；
  2. 权限矩阵表（照抄 `design.md` 第 1 节）；
  3. 工具箱入口与"文件不上传、全部本地解析"的说明；
  4. 加新接口时的规矩：只读接口要显式进 `PUBLIC_PATTERNS`，写接口放 `**/admin/**`；
  5. SPA 回退上限 3 段的注意事项。

## 7. 收尾

- [ ] `git status --short` 只含预期：3 个后端文件、`client.ts`、`NavBar.vue`、`LoginView.vue`、`AdminView.vue`、`ApkAnalyzerView.vue`、`ApkSignatureCard.vue`（+ 可能的 `ApkAnalysisPanel.vue`）、`router/index.ts`、`ToolsView.vue`、`docs/guest-mode.md`。
- [ ] 更新 `.trellis/spec/backend/index.md`：补一条"游客模式下只读接口白名单在 `WebMvcConfig.PUBLIC_PATTERNS`；新增写接口必须落 `**/admin/**`"。
- [ ] 关掉本地起的后端与前端进程。

## 命令速查

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
./gradlew compileJava compileKotlin --offline && ./gradlew bootRun

BASE=http://localhost:8080
curl -s -o /dev/null -w "%{http_code} %{redirect_url}\n" $BASE/
curl -s -o /dev/null -w "%{http_code}\n" "$BASE/api/v1/appmarket/apps?page=1&size=5"
curl -s -o /dev/null -w "%{http_code}\n" $BASE/api/v1/appmarket/admin/apps
curl -s -o /dev/null -w "%{http_code}\n" $BASE/appmarket/3

cd frontend && npm run typecheck && npm run build && npm run dev
```
