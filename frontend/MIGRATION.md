# ZAZEWeb · Vue3 POC 集成与回退说明

> 适用范围：在保留现有 Spring Boot + Thymeleaf（`/`、`/appmarket`、`/login`）的前提下，
> 试点新增一套 Vue3 应用，挂在 `/vue` 前缀下，复用后端已有 `/api/v1/*` 接口（Session Cookie 同源鉴权）。
> 本文件同时是任务 #9（集成复检）的结论与任务 #10（迁移 / 回退）的文档部分。
> 构建与联调需用户放行后执行（见 §3）。

## 1. 这个 POC 新增 / 修改了什么

**前端工程**（`frontend/`，Vite + Vue3 + TS + Tailwind）
- `frontend/src/main.ts` `App.vue` `router/index.ts` `vite.config.ts` `tailwind.config.js` `postcss.config.js` `main.css` `index.html`
- 门户：`views/HomeView.vue` `views/AppMarketView.vue` `views/AppDetailView.vue` + `components/portal/*`
- 后台：`views/AdminView.vue` + `components/admin/*`
- 基建：`api/client.ts` `types.ts` `store/auth.ts` `utils/appMeta.ts` `vite-env.d.ts`

**后端（零侵入式改动）**
- 新增 `src/main/java/com/zaze/server/config/VueSpaController.kt`：history 路由回退。
- 修改 `src/main/java/com/zaze/server/config/AuthInterceptor.kt`：`LOGIN_PATTERNS` 增加 `/vue`、`/vue/**`。
- 构建产物目录 `src/main/resources/static/vue/`（由 `npm run build` 生成，**不手工维护**）。

## 2. 工作原理（#9 复检结论：全部成立，无需改代码）

- `vite.config.ts`：`base: '/vue/'`、`build.outDir: '../src/main/resources/static/vue'`、`server.proxy['/api'] → http://localhost:8080`。
- `VueSpaController`：`GET /vue`、`/vue/`、`/vue/{path:[^\\.]*}`（只匹配**不含点号**的路径）统一 `forward:/vue/index.html`，由 Vue Router 接管；真实静态资源 `/vue/index.html`、`/vue/assets/*.js` 带点号，不命中该映射，由 Spring 静态资源处理器返回 → **不会转发死循环**。
- `AuthInterceptor`：`/vue/**` 归入 `LOGIN_PATTERNS`（登录即可访问，与门户同语义）；`/api/v1/appmarket/admin/**` 归入 `ADMIN_PATTERNS`（需 ADMIN 角色）。静态资源在 `WebMvcConfig` 的 `excludePathPatterns` 中，不重复校验。
- `application.yml` 中 `server.servlet.context-path: /`（根部署），故 `/vue` 与 `/api` 均无前缀偏移。
- 生产环境 SPA 与后端同源，`/api` 直接打到本服务，无需 CORS；开发态由 Vite 代理 `/api` 到 `:8080`。

## 3. 如何构建与运行（需用户放行 install / build / 启动后端）

```bash
cd frontend
npm install
npm run build        # 产物输出到 ../src/main/resources/static/vue

# 回到项目根启动 Spring Boot
./gradlew bootRun     # 或 java -jar build/libs/*.jar
```

访问地址（均登录后）：
- 门户首页：http://localhost:8080/vue/
- 应用图鉴：http://localhost:8080/vue/appmarket
- 应用详情：http://localhost:8080/vue/appmarket/:id
- 管理后台（需 ADMIN）：http://localhost:8080/vue/admin

联调要点：
- 开发态 `npm run dev` → http://localhost:5173，热更新；`/api` 经 Vite 代理转发到 `:8080`。
- 刷新 /vue/admin 等深链：请求落到 `VueSpaController` → 转发 `index.html` → 前端路由接管，正常。
- 退出登录：`auth.logout()` 调 `/api/v1/auth/logout` 后跳 `/login`。

## 4. 如何回退（零侵入，四步）

现有 Thymeleaf 页面（`/`、`/appmarket`、`/appmarket/admin`、`/login`）完全不受影响，回退只需：

1. 删除 `src/main/java/com/zaze/server/config/VueSpaController.kt`；
2. 在 `AuthInterceptor.kt` 的 `LOGIN_PATTERNS` 中移除 `/vue`、`/vue/**` 两行；
3. 删除 `frontend/` 目录（或保留但不再构建）；
4. 删除 `src/main/resources/static/vue/` 目录。

## 5. 已知范围与限制

- **后台「外部导入 / APK 解析 / SSE 实时链路」尚未移植**：遗留 `static/js/appmarket-admin.js` 相关逻辑较重，本期 POC 仅含核心 CRUD + 触发采集（`POST /appmarket/admin/collect`）。
- **分类 / 排序 / 热度**：
  - 列表「分类」字段后端已返回（`AppVo.category`，门户与后台均在用）；
  - 「排序 / 热度」在门户端按 `versionCount` 派生（见 `src/utils/appMeta.ts`），待后端 feature 源码可用时可补真实字段。
- **appmarket 业务以预编译 jar（`feature/`）形式提供**，本工作区无其源码；如需后端字段增强，需在 feature 源码侧进行，无法在本工作区直接改。

## 6. 任务进度

- 已完成：#1 #2 #4 #5 #6 #7 #8 #9 #3（install / build）
- 待执行（需用户放行）：#10（实际联调，本文档已先备好）
