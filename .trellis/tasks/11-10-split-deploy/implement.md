# 执行计划：两端分开部署 + 无后端可用

> 任务：`11-10-split-deploy`。需求见 `prd.md`，技术设计见 `design.md`。
> 顺序有依赖：先让"失败可读"（第 1-2 步），再解耦非接口功能（第 3 步），再谈分开部署（第 4-5 步）。每步有门禁。

## 0. 前置检查

```bash
cd frontend
node -v && npm -v
ls node_modules > /dev/null || npm ci
npm run typecheck                      # 基线必须绿，记录既有报错，别把历史债算进来
lsof -i:8080 || echo "8080 空闲 → A 组验收有效"
lsof -i:3306 && echo "MySQL 在跑 → 后端可启动，C 组可验" || echo "MySQL 未起 → C 组可能 blocked"
ls -la ../src/main/resources/static/vendor/app-info-parser/ | head -5
```

- [ ] typecheck 基线绿。
- [ ] 确认 vendor 目录存在且含 `app-info-parser.min.js`。
- [ ] 记录本机能否启动后端（决定 C 组验收是否 blocked）。

**门禁**：typecheck 不绿 → 先定位存量问题。

## 1. 前端：接口地址可配 + 错误归一化

- [ ] `src/api/client.ts`：`const API_BASE = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/+$/, '')`。
- [ ] `request()` 按 `design.md` 2.1 重写：`AbortController` 15s 超时、`credentials: 'include'`、`fetch` 异常→`network` 类可读错误、401 保持跳转、403 独立文案、**先判 `Content-Type` 再 `res.json()`**、业务错误沿用 `msg`。
- [ ] 新增 `src/vite-env.d.ts`（或补进现有）的 `VITE_API_BASE` / `VITE_BASE` / `VITE_OUT_DIR` / `VITE_ROUTER_MODE` 类型声明。

### 纯逻辑验证（不启浏览器）

`request()` 的四条失败分支可以用 Node + fetch 桩覆盖，避免"靠肉眼猜报错"：

```bash
cd frontend && ./node_modules/.bin/esbuild src/api/client.ts --bundle --format=esm --platform=node --outfile=/tmp/client.mjs
```

写临时脚本 `/tmp/check-client.mjs`（不进仓库），替换 `globalThis.fetch` 后断言：

- [ ] fetch reject(`TypeError`) → message 含「无法连接后端服务」；
- [ ] fetch reject(`AbortError`) → message 含「请求超时」；
- [ ] resolve 500 + `content-type: text/html`（模拟 vite proxy 错误页）→ message 含「后端服务不可用（HTTP 500）」，**不含** `Unexpected token`；
- [ ] resolve 200 + JSON `{code:401}`（或 status 401）→ 抛「未登录」；403 → 「需要管理员权限」；
- [ ] resolve 200 + `{code:0,data:...}` → 正常返回 data；
- [ ] `VITE_API_BASE` 未设时请求 URL 以 `/api/v1` 开头；设为 `http://x/api/v1/` 时结尾斜杠被规整（不出现 `//`）。

**门禁**：四条分支的文案都对，才进第 2 步。文案写错的成本比逻辑错更高——它是用户唯一能看到的东西。

## 2. 页面级降级核对

按 `design.md` 3.3 的表逐文件核对**不变式**：`await api.*` 有 catch、`loading` 在 `finally` 复位。

- [ ] `store/auth.ts`（预期无需改）
- [ ] `views/HomeView.vue`
- [ ] `views/AppMarketView.vue`（注意 `apps` 与 `appCategories` 两个请求，分类失败不该让整页空白）
- [ ] `views/AppDetailView.vue`（失败显示「加载失败 / 应用不存在」而非空白）
- [ ] `views/AdminView.vue`
- [ ] `components/admin/VersionManagerModal.vue`（弹窗内失败要给提示，不能静默）

```bash
npm run typecheck
```

- [ ] 通过；`git diff` 里这几处只有 try/catch/finally 与文案，**没有**数据流改动。

## 3. vendor 资源迁移（APK 分析与后端脱钩）

- [ ] `mkdir -p public/vendor && cp -R ../src/main/resources/static/vendor/* public/vendor/`
- [ ] 校验一致性：`diff -r ../src/main/resources/static/vendor public/vendor`（应无差异）。
- [ ] `src/utils/apk/manifestParser.ts`：`PARSER_SRC` 改为 `` `${import.meta.env.BASE_URL}vendor/app-info-parser/app-info-parser.min.js` ``；加载失败的 reject 文案改为可读提示（含 HTTP 状态）。
- [ ] `package.json` 加 `"sync:vendor": "node -e \"...copyFileSync 递归...\""`（或两步 `cp -R`，保持零依赖）。
- [ ] `.gitignore` 不加 `public/`（资产要提交）。

```bash
npm run dev   # 不启后端
curl -s -o /dev/null -w "%{http_code}\n" http://localhost:5173/vendor/app-info-parser/app-info-parser.min.js   # 200
```

- [ ] dev server 能直接吐出 vendor 脚本（200 且 `content-length` 接近源文件）。
- [ ] 浏览器打开 `/admin/apk` 上传真实 APK：解析出包名/版本/权限/签名（后端完全没起）。

**门禁**：这一步不过，后面静态部署的 APK 分析一定废。

## 4. 构建形态

- [ ] `vite.config.ts`：`base: process.env.VITE_BASE ?? '/'`、`build.outDir: process.env.VITE_OUT_DIR ?? '../src/main/resources/static'`、`emptyOutDir: false` 保持不变。
- [ ] 加一行启动告警：`base` 以 `./` 开头且未设 hash 路由时 `console.warn` 提示深链会 404（实现级小守卫）。
- [ ] `router/index.ts`：`VITE_ROUTER_MODE === 'hash'` 用 `createWebHashHistory`，否则 `createWebHistory('/')`。
- [ ] `package.json`：`"build:static": "VITE_BASE=./ VITE_OUT_DIR=dist VITE_ROUTER_MODE=hash vite build"`（清空 dist 用独立 `rm -rf dist` 前置，别依赖 `emptyOutDir:false`）。
- [ ] `.gitignore`（仓库根）加 `frontend/dist/`。

```bash
npm run build && ls ../src/main/resources/static | head   # assets index.html sql vendor 都在
npm run build:static && ls dist | head
npm run preview
```

- [ ] `build` 后 `static/vendor`、`static/sql` 仍在（`emptyOutDir:false` 语义未破）。
- [ ] `dist` 与 `static` 均含 `index.html` 与 `assets/`；`git status` 里没有 `dist/`。

## 5. 后端：CORS 开关

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
java -version   # 必须 11
```

- [ ] 新增 `src/main/java/com/zaze/server/config/CorsConfig.kt`，内容按 `design.md` 第 5 节（`@ConditionalOnProperty` 默认关、`CorsFilter` + `Ordered.HIGHEST_PRECEDENCE`、`allowedOriginPatterns`、`allowCredentials=true`、`maxAge=3600`）。
- [ ] 文件头写清：默认关闭意味着"不配置就完全等同今天"；开启需同时设 `SERVER_SERVLET_SESSION_COOKIE_SAME_SITE=none` + `SECURE=true`，并用 HTTPS。
- [ ] **不动** `WebMvcConfig.kt` / `AuthInterceptor.kt`（预检由 Filter 提前应答，不需要改拦截器）。

```bash
./gradlew compileJava compileKotlin --offline
```

- [ ] 编译通过。

## 6. 验证矩阵

### A. 无后端（不启 JVM）

- [ ] A1 `npm run dev` 打开 `/`：导航/页脚/hero 正常；列表区显示「无法连接后端服务…」；**不是** `Unexpected token '<'`；不白屏。
- [ ] A2 `/appmarket`、`/appmarket/3`、`/admin`、`/login` 逐个打开：都有可读提示，控制台无 unhandled promise rejection。
- [ ] A3 `/admin/apk` 上传真实 APK：包名/版本/权限/签名证书齐全。

### B. 有后端 · 同源（回归）

- [ ] B1 后端启动，`npm run dev`（默认 proxy）：门户列表/搜索/分类/详情/后台 CRUD/登录与改动前一致。
- [ ] B2 未登录访问后台 → 跳 `/login`；`/login` 不自跳死循环。

### C. 有后端 · 真跨域

```bash
# 后端（示例）
ZAZE_CORS_ENABLED=true ZAZE_CORS_ALLOWED_ORIGINS=http://localhost:5173 ./gradlew bootRun
# 前端
VITE_API_BASE=http://localhost:8080/api/v1 npm run dev

# 预检（命令行直验，不依赖浏览器）
curl -i -X OPTIONS http://localhost:8080/api/v1/appmarket/admin/apps \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: GET" | head -20
```

- [ ] C1 预检返回 2xx，且带 `Access-Control-Allow-Origin: http://localhost:5173` 与 `Access-Control-Allow-Credentials: true`（**不是** 401）。
- [ ] C2 浏览器里门户 GET 列表成功，DevTools Network 可见上述两个响应头。
- [ ] C3 `Origin: http://127.0.0.1:5173`（不在白名单）→ 无 `Access-Control-Allow-Origin`，浏览器侧请求失败。
- [ ] C4 不设 `ZAZE_CORS_ENABLED` 重启后端 → 同样的预检**不带** CORS 头（证明默认关闭）。
- [ ] C5 跨域登录态：本地 HTTP + `SameSite=None; Secure` 不成立 → 标 `blocked`，并按 `docs/split-deploy.md` 的 HTTPS/反代路径在具备条件时补验。

### D. 静态构建

- [ ] D1 `npm run build:static && npm run preview`（后端不启动）→ A1~A3 全部成立。
- [ ] D2 子路径形态至少验一种：`VITE_BASE=/zaze/ npm run build` 后起静态服务器，资源 200、路由可跳。

### E. 门禁

- [ ] E1 `npm run typecheck && npm run build` 通过。
- [ ] E2 `./gradlew compileJava compileKotlin --offline` 与 `./gradlew :feature:appmarket:compileKotlin --offline` 通过。

**回滚点 B**：若 C 组验证发现"必须改拦截器才能放行预检"，先停——设计上不应该出现（Filter 在最新执行顺序）。此时回 `design.md` 第 5 节重新评估，不要顺手改 `AuthInterceptor`（那是把部署问题扩散成鉴权问题）。

## 7. 文档

- [ ] 新建 `docs/split-deploy.md`：
  1. 三种拓扑对照表（单机同源 / 静态托管+反代 / 真跨域）与各自需要配什么；
  2. 配置清单：`VITE_API_BASE`、`VITE_BASE`、`VITE_ROUTER_MODE`、`ZAZE_CORS_ENABLED`、`ZAZE_CORS_ALLOWED_ORIGINS`、两个 cookie 变量；
  3. **跨域 cookie 的硬限制**：HTTP 下测不了登录态；三条兜法（反代 / mkcert / 调试参数）；
  4. 静态托管 fallback 写法（nginx `try_files`、Caddy `try_files`、GitHub Pages `404.html`），以及 `base: './'` 必须配 hash 的规则；
  5. 排查顺序：先看 Console 的 CORS 报错 → 再看 Network 状态码 → 最后才怀疑代码；
  6. 语义变化说明：分开部署后门户 HTML 公开可达，数据仍在 401 后面；
  7. vendor 资产的权威副本与 `sync:vendor` 的使用时机。

## 8. 收尾

- [ ] `npm run typecheck && npm run build` 最后全绿。
- [ ] `git status --short` 只含预期文件：`src/api/client.ts`、`src/utils/apk/manifestParser.ts`、`public/vendor/**`、`vite.config.ts`、`router/index.ts`、`vite-env.d.ts`、`package.json`、`.gitignore`、少量 view 的 catch 补丁、`CorsConfig.kt`、`docs/split-deploy.md`；**不含** `dist/`、临时脚本。
- [ ] 关掉 dev/preview/后端进程。
- [ ] 更新 `.trellis/spec/backend/index.md`：补一句「跨域部署需显式开启 `zaze.cors.enabled` 并配置 cookie SameSite/Secure」，避免下次重新摸索。

## 命令速查

```bash
cd frontend
npm run typecheck && npm run build
npm run dev                       # 无后端也能看
VITE_API_BASE=http://localhost:8080/api/v1 npm run dev
npm run build:static && npm run preview
npm run sync:vendor

# 后端
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
./gradlew compileJava compileKotlin --offline
ZAZE_CORS_ENABLED=true ZAZE_CORS_ALLOWED_ORIGINS=http://localhost:5173 ./gradlew bootRun
curl -i -X OPTIONS http://localhost:8080/api/v1/appmarket/admin/apps -H "Origin: http://localhost:5173" -H "Access-Control-Request-Method: GET"
```
