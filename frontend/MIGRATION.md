# ZAZEWeb · 前端迁移说明（Vue3 已完全取代遗留 Thymeleaf）

> 适用范围：**完全迁移**。Vue3 应用即站点本体，挂在站点根 `/`；原有 Spring Boot + Thymeleaf
> 那一套（`/` 首页、`/appmarket` 门户、`/appmarket/admin` 后台、`/login`、社交/个人空间 `model/*`
> 等）的模板与页面控制器已全部删除。后端只保留 `@RestController` 接口与根 SPA 回退控制器。
> 本文件是任务 #11 之后的「完全迁移」落地说明（替代原 POC 集成文档）。

## 1. 现在是什么样

- **站点本体 = Vue3 应用**，根部署（`base: '/'`），由 `VueSpaController` 在 `/` 与所有不含点号
  的前端路由上回退到 `static/index.html`（Vue Router 接管）。
- **登录 = Vue 登录页**：`/login` 路由的 `LoginView.vue`，复用后端既有 JSON 接口
  `POST /api/v1/auth/login`（`{username,password}`），零后端改动；遗留 Thymeleaf 登录页已删。
- **遗留模板 / 页面控制器已删除**：
  - `src/main/resources/templates/**` 全删（含 `appmarket/`、`auth/`、`admin/`、`model/`、
    `layout/`、`common/`、`test/`、`index.html`）。
  - 仅有的 `@Controller`（页面渲染）类 `AppMarketPageController`、`AppMarketAdminPageController`、
    `AuthPageController` 已删；保留 `@RestController`（接口）与 `VueSpaController`。
  - 遗留静态资源 `static/js`、`static/css`、`static/images`、`static/vendor/{bootstrap,jquery,bootstrap-table}`
    已删；保留 `static/vendor/app-info-parser`（APK 解析用）与 `static/sql`。
- **保留**：`@RestController` 全部接口（`/api/v1/appmarket/**`、`/api/v1/auth/**` 等）、
  `PortalModelAdvice`（无害，保留）、`authService` 等。

## 2. 工作原理（复检结论：成立，无需改代码）

- `frontend/vite.config.ts`：`base: '/'`，`build.outDir: '../src/main/resources/static'`
  （产物 `index.html` + `assets/` 直接进 Spring 静态根）；`server.proxy['/api'] → :8080`。
- `VueSpaController`：`@GetMapping("/", "/{path:[^\\.]*}")` → `forward:/index.html`。
  - 仅匹配**不含 "."** 的路径，因此 `/index.html`、`/assets/xxx.js`、`/favicon.ico` 等真实静态
    资源不命中，由 Spring 静态资源处理器返回 → 无转发死循环。
  - `/api/**` 等接口由更具体的 `@RestController` 处理，优先级高于本回退。
- `AuthInterceptor`：`/admin`、`/admin/**` 归 `ADMIN_PATTERNS`（需 ADMIN）；`/`、`/appmarket`、`/appmarket/**`、
  `/api/v1/appmarket/**` 归 `LOGIN_PATTERNS`（登录即可）。已移除原 POC 的 `/vue`、`/vue/**` 放行。
- `WebMvcConfig` 公开路径（拦截器 exclude）：`/login`、`/api/v1/auth/**`、`/error`、`/favicon.ico`、
  `/index.html`、`/assets/**`、`/css`、`/js`、`/images`、`/vendor`、`/sql` 等。`/login` 始终公开。
- 前端 `request()`：遇 401 时，若当前不在 `/login` 则跳 `/login`（**已在 /login 不跳**，避免重定向死循环）；
  `auth.login()` 成功后 `window.location.href = '/'` 刷新拉取鉴权态。

## 3. 如何构建与运行

```bash
cd frontend
npm install
npm run build        # 产物输出到 ../src/main/resources/static（index.html + assets/）
npm run typecheck    # vue-tsc 类型检查门禁（推荐提交前跑）

# 回到项目根启动 Spring Boot（需用户放行起后端）
./gradlew bootRun     # 或 java -jar build/libs/*.jar
```

访问地址（登录后）：
- 门户首页：http://localhost:8080/
- 应用图鉴：http://localhost:8080/appmarket
- 应用详情：http://localhost:8080/appmarket/:id
- 管理后台（需 ADMIN）：http://localhost:8080/admin
- 登录页：http://localhost:8080/login

联调要点：
- 开发态 `npm run dev` → http://localhost:5173，热更新；`/api` 经 Vite 代理转发到 `:8080`。
- 刷新 `/admin`、`/appmarket/123` 等深链：请求落到 `VueSpaController` → 转发 `index.html` → 前端路由接管，正常。
- 退出登录：`auth.logout()` 调 `POST /api/v1/auth/logout` 后跳 `/login`。

## 4. 如何回退（已是 git 提交，用版本控制还原）

本次迁移是普通 git 提交，回退不再需要「删文件四步」：
1. 查看迁移相关提交：`git log --oneline -- frontend/MIGRATION.md src/main/resources/templates feature/*/src/main/kotlin/.../controller/*PageController.kt`。
2. 整体回退：`git revert <迁移提交>`（或 `git checkout <迁移前提交> -- <路径>` 精确还原某个文件）。
3. 还原后如需遗留页面，从迁移前提交取回 `templates/**` 与对应 `@Controller` 即可。

## 5. 已知范围与限制

- **appmarket 业务以预编译 jar（`feature/`）形式提供**，本工作区有 `feature/*/src/main/kotlin` 源码可读可改；
  但运行态接口契约与遗留脚本一致，前端已对齐。如需后端字段增强，在 feature 源码侧进行。
- 静态资源保留 `static/vendor/app-info-parser`（浏览器端 APK 解析）、`static/sql`（数据目录）。
- 遗留「社交 / 个人空间」（`model/*`、`/center` 等）页面已随完全迁移删除，这些 URL 现在由 Vue 接管或 404。

## 6. 任务进度

- 已完成：#1–#11（Vue3 门户 + 后台全功能）、类型检查门禁（vue-tsc）、**完全迁移（删除遗留 Thymeleaf 与页面控制器，Vue 根部署 + Vue 登录页）**。
- 联调（起后端真实验收）由用户执行。
