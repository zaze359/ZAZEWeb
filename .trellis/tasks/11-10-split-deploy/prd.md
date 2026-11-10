# 两端分开部署 + 无后端可用（弱化后端概念）

> 任务：`11-10-split-deploy`（复杂任务，由 `11-10-frontend-local-mode` 改名并改写方向）。需求见本文件，技术设计见 `design.md`，执行计划见 `implement.md`。
> 前序任务 `11-10-zero-dep-startup`（H2 默认 profile）已作废；本任务**不做**本地数据层（曾规划过，已放弃）。

## 背景

现状是「前端产物只能由 Spring Boot 伺服」的耦合形态：

- 前端 `src/api/client.ts` 的 BASE 写死 `/api/v1`，只认同源；
- APK 分析依赖 `window.AppInfoParser`，脚本地址写死绝对路径 `/vendor/app-info-parser/...`，而该文件只存在于**后端**的 `src/main/resources/static/vendor/`（`frontend/` 没有 `public/`）；
- `request()` 没有网络层错误归一化：后端没起时 vite proxy 返回 `500 + text/html`，`res.json()` 直接抛 `Unexpected token '<' ...` 这种给用户看的鬼话；
- 后端没有任何 CORS 配置，鉴权又基于 session cookie（`request.getSession(false)`），所以两个域名/端口分开部署时，跨域请求会被浏览器拦、登录态也带不过去。

结果：想看个页面、用一下 APK 分析，就得先把 JVM + MySQL 拉起来。

## 目标

1. **无后端可用**：后端没起时，前端页面正常打开、路由正常、不白屏；依赖接口的地方按**正常错误路径**给出可读提示；不依赖接口的功能（APK 分析、门户壳层）照常可用。
2. **两端可分开部署**：前端产物是纯静态文件，可托管在任意静态服务器；后端独立域名/端口，通过配置即可对接（真跨域，含 CORS 与凭证）。

**明确不做**：本地数据层 / mock 数据 / localStorage 副本 / 双模自动切换（此前设计已废弃）。后端数据只有一份，就是真后端。

## 需求

- **R1 接口地址可配**：`VITE_API_BASE`（默认 `/api/v1`，兼容现状），请求统一 `credentials: 'include'`；去掉"只能同源"的隐含假设。
- **R2 错误归一化（"正常报错逻辑"）**：`client.ts` 的 `request()` 把四类失败翻译成可读的中文 `ApiError`：
  - 网络不可达 / CORS 被拒 → 「无法连接后端服务，请确认后端已启动、地址与跨域配置正确」
  - 响应不是 JSON（代理 500 错误页、静态站 404）→ 「后端服务不可用（HTTP xxx）」
  - `401` 未登录 → 维持现有行为（跳 `/login`，`/login` 自身不跳避免死循环）
  - `403` → 「需要管理员权限」；业务错误 → 沿用后端 `msg`
  - 另加请求超时（15s），避免后端半死时无限转圈。
- **R3 非接口功能不依赖后端**：把 `app-info-parser` 放进 `frontend/public/vendor/`，脚本地址改为基于 `import.meta.env.BASE_URL`（支持子路径部署）；`/admin/apk` 在后端不在时能完整解析 APK。
- **R4 页面级降级**：确认 6 个 view 的接口失败路径都能退出 loading 并显示错误（不出现"永远加载中"、不产生未捕获的 promise rejection）。
- **R5 静态构建**：`build:static` 产出 `frontend/dist/`（可 `preview` 直接验）；`VITE_BASE` 支持子路径；history 模式需服务器 fallback，另提供 `VITE_ROUTER_MODE=hash` 适配无 fallback 的托管。
- **R6 后端 CORS（默认关闭）**：新增可开关的 CORS 配置（`zaze.cors.enabled=true` + `zaze.cors.allowed-origins` 白名单），默认不生效 → 现有同源部署行为零变化；开启时允许凭证。
- **R7 文档** `docs/split-deploy.md`：两种部署拓扑、配置项清单、跨域 + cookie 的坑（SameSite/Secure/HTTPS）、排查顺序、静态托管 fallback。
- **R8 验证**：见验收标准。

## 已确认的技术事实（探查结论）

- `client.ts` 单文件收敛全部 API（`BASE = '/api/v1'` + `request()` + `api` 对象），视图层只调 `api.*`，错误统一 `catch → show('error', (e as Error).message)` → **改 `request()` 一处，全站报错文案即可可读化**。
- `utils/apk/manifestParser.ts`：`const PARSER_SRC = '/vendor/app-info-parser/app-info-parser.min.js'`，按需插 script 等 `window.AppInfoParser`；`frontend/` 无 `public/`，dev/静态托管下该路径 404 → **APK 分析目前离不开后端**。
- 后端静态资源里确实有该文件（`src/main/resources/static/vendor/app-info-parser/`，459KB 量级）。
- 后端 `WebMvcConfig.kt` 只注册了鉴权拦截器，**无任何 CORS 配置**；`AuthInterceptor` 对 `/api/**` 未通过时回 401 JSON。
- 鉴权是 HttpSession（`request.getSession(false)`），无 token 机制 → 跨站部署时 cookie 必须 `SameSite=None; Secure`（即 HTTPS），否则登录态不带。
- `AuthInterceptor.LOGIN_PATTERNS` 含 `/`、`/appmarket`、`/appmarket/**` → 现在由后端伺服时门户本身需登录；分开部署后 **HTML 变公开可达，但数据接口仍在 401 后面**（语义变化，需在文档里写清）。

## 约束

- 后端改动**仅限**新增 CORS 配置与其开关；不改任何业务逻辑、不改接口契约、不动鉴权实现。
- 不做本地数据层、不引 mock、不引新前端依赖（不加 axios/Pinia）。
- 不改门户视觉基线与后台排版；`public/vendor` 只是资产迁移。
- 生产构建输出目录不变（`src/main/resources/static`），现有部署方式照旧可用；`frontend/dist/` 不进 git。
- CORS 默认关闭，绝不能让"没配就放开跨域"成为默认行为。

## 验收标准

**A. 无后端（`lsof -i:8080` 无监听，不启 JVM）**

- [ ] `npm run dev` 打开 `/`：页面渲染完整（导航、页脚、hero），列表区显示可读错误「无法连接后端服务…」，**不是** `Unexpected token '<'`，不白屏。
- [ ] `/appmarket`、`/appmarket/3`、`/admin`、`/login` 均能打开并给出可读提示；控制台无未捕获 promise rejection。
- [ ] `/admin/apk` 上传一个真实 APK：能解析出包名、版本、权限、签名证书（说明 vendor 已由前端自身伺服，与后端无关）。

**B. 有后端 · 同源（回归）**

- [ ] 后端启动后 `npm run dev`（默认 proxy）：门户列表、搜索、分类、详情、后台 CRUD、登录全部与改动前一致。
- [ ] 后端未登录时 401 仍跳 `/login`，且 `/login` 页不会自我重定向循环。

**C. 有后端 · 真跨域**

- [ ] 后端以 `ZAZE_CORS_ENABLED=true`、`ZAZE_CORS_ALLOWED_ORIGINS=http://localhost:5173` 启动；前端 `VITE_API_BASE=http://localhost:8080/api/v1 npm run dev`：门户 GET 列表成功（DevTools 可见 `Access-Control-Allow-Origin` 与 `Allow-Credentials: true`）。
- [ ] 预检 `OPTIONS /api/v1/appmarket/admin/apps` 返回 2xx（未被鉴权拦截成 401）。
- [ ] 不在白名单的源（如 `http://127.0.0.1:5173`）请求被拒。
- [ ] 默认不配 `zaze.cors.*` 时，跨域请求仍被浏览器拦（证明默认关闭有效、未误开放）。
- [ ] 跨域**登录态**：HTTP + `SameSite=None; Secure` 在浏览器中不成立 → 本项在本地 http 环境标记 `blocked`，按文档用 HTTPS（mkcert）或反代验证；文档必须写明该限制与三条绕法。

**D. 静态构建**

- [ ] `npm run build` 产物仍落在 `src/main/resources/static`，`vendor/`、`sql/` 仍在。
- [ ] `npm run build:static && npm run preview`（后端不启动）：A 组所有行为成立。
- [ ] `VITE_BASE=/xxx/` 或 `VITE_BASE=./ + VITE_ROUTER_MODE=hash` 两种子路径形态至少各验一次资源与路由可加载。

**E. 门禁**

- [ ] `npm run typecheck`、`npm run build` 通过。
- [ ] `./gradlew compileJava compileKotlin --offline` 通过（新增 CORS 配置类编译无碍）。
- [ ] `./gradlew :feature:appmarket:compileKotlin --offline` 不受影响。
- [ ] `git status` 不含 `frontend/dist/`、不含临时脚本。

## Notes

- 用户口径（2026-11-10）：「走正常的思路，后端没起来的时候接口走正常报错逻辑；不依赖接口的操作能正常进行」＋「两端可以分开部署」。
- 已确认：后端**加 CORS**（真跨域），而不是只做同域反代；因此接受"跨域登录态在本地 HTTP 下测不了"的限制，文档给 HTTPS/反代两条兜底。
- 前序 `11-10-zero-dep-startup`（H2 profile）已作废；本地数据层方案（种子 JSON + localStorage 双模）已放弃，相关设计不在本任务内。
