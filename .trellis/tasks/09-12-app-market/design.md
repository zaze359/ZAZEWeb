# Design — 应用市场门户

## 模块与目录

新增 `feature/appmarket/`，包根 `com.zaze.server.feature.appmarket`（命中 root 的 `scanBasePackages="com.zaze.server"`）。

```
feature/appmarket/
├── build.gradle.kts                         # 依赖 core:common + core:database + JPA/cache/web
└── src/main/
    ├── kotlin/com/zaze/server/feature/appmarket/
    │   ├── pojo/
    │   │   ├── App.kt                        # @Entity appmarket_app
    │   │   ├── AppVersion.kt                # @Entity appmarket_version（含 appId:Long 外键列）
    │   │   └── DownloadSource.kt            # @Entity appmarket_source（含 versionId:Long 外键列）
    │   ├── vo/
    │   │   ├── AppVo.kt
    │   │   ├── AppVersionVo.kt
    │   │   ├── DownloadSourceVo.kt
    │   │   └── AppDetailVo.kt               # AppVo + List<AppVersionVo>
    │   ├── model/
    │   │   └── Mappers.kt                   # App.asVo / AppVersion.asVo / DownloadSource.asVo
    │   ├── repository/
    │   │   ├── AppRepository.kt
    │   │   ├── AppVersionRepository.kt      # findByAppId(appId)
    │   │   └── DownloadSourceRepository.kt  # findByVersionId(versionId)
    │   ├── service/
    │   │   ├── AppMarketService.kt          # interface
    │   │   └── impl/AppMarketServiceImpl.kt # @Service + @Cacheable/@CacheEvict
    │   ├── controller/
    │   │   ├── AppMarketApiController.kt    # @RestController /api/v1/appmarket
    │   │   └── AppMarketPageController.kt   # @Controller /appmarket（Thymeleaf 门户）
    │   └── bootstrap/
    │       └── AppMarketSeedLoader.kt       # CommandLineRunner，classpath 种子入库
    └── resources/
        └── data/
            └── appmarket_seed.json          # 公开下载地址（GitHub/F-Droid/官方）
```

## 数据模型（扁平外键，规避 JPA 关系级联坑）

不建立双向 `@OneToMany` 关系，三个实体各自独立、`@ManyToOne` 关系改为 Long 外键列 + repository 查询拼接。理由：沿用项目 Showcase 风格（实体为承载字段的纯数据表），读多写少（仅种子写入），避免 lazy/eager、equals、cascade 复杂度。

| 表 | 字段 |
|----|------|
| `appmarket_app` | id, createTime, updateTime, name, packageName, category, developer, summary(TEXT), iconUrl, officialUrl |
| `appmarket_version` | id, createTime, updateTime, appId(Long), versionName, versionCode(Long?), releaseDate(DATE?), sizeMb(Long?), changelog(TEXT) |
| `appmarket_source` | id, createTime, updateTime, versionId(Long), sourceName, sourceType(enum 字符串: GITHUB/FDROID/APKMIRROR/OFFICIAL/OTHER), downloadUrl, region, note |

实体沿用现有约定：Kotlin `data class`，自带头 `id/createTime/updateTime`（不复用 Java `BaseEntity`）。`sourceType` 用普通 `String` 列（避免引入枚举映射复杂度，值由种子数据约束）。

## 接口

### JSON API（`AppMarketApiController`）
- `GET /api/v1/appmarket/apps` → `Response<List<AppVo>>`，附带 versionCount。
- `GET /api/v1/appmarket/apps/{id}` → `Response<AppDetailVo>`，detail 含 `List<AppVersionVo>`，每个 version 含 `List<DownloadSourceVo>`。

### 门户页（`AppMarketPageController`）
- `GET /appmarket` → 视图 `appmarket/index`（空容器，JS 拉取 `/api/v1/appmarket/apps` 渲染卡片）。
- `GET /appmarket/{id}` → 视图 `appmarket/detail`（JS 拉取详情并渲染版本与下载源；`id` 写入 model 供页面取用）。

## 前端（沿用 Thymeleaf + Bootstrap + jQuery）

- `src/main/resources/templates/appmarket/index.html`：应用卡片网格，点击展开版本/来源（JS 控制）。
- `src/main/resources/templates/appmarket/detail.html`：单应用详情（版本列表 + 每版本来源按钮）。
- `src/main/resources/static/js/appmarket.js`：jQuery `$.getJSON` 调 API，渲染卡片、版本、来源按钮（`<a target="_blank" href=...>`）。
- 复用 `static/vendor/bootstrap` 与现有 css（default.css/nav.css）。头部导航在 `templates/common/head.html`。

## 缓存策略（对齐 showcase）

`AppMarketServiceImpl` 加 `@CacheConfig(cacheNames=["appmarket"])`：读方法 `@Cacheable`，种子写入后 `@CacheEvict(allEntries=true)`。注意 cache 在 dev/重启会清空，无需复杂失效逻辑。

## 种子数据（收集网上的——本期以整理 JSON 实现）

`appmarket_seed.json` 内置 ≥3 个真实开源 Android 应用，每应用 ≥1 版本，每版本 ≥2 来源（GitHub Releases / F-Droid / 官方）。`AppMarketSeedLoader` 在应用启动时检查 `appmarket_app` 是否为空，为空则解析 JSON 写入 `App / AppVersion / DownloadSource` 三表（按 JSON 顺序保证外键一致）。

种子数据作为「首次启动即有数据」的兜底；真实采集已由 `AppMarketCollector` 实现（见下节），可由管理后台手动触发。

## 注册与构建改动

1. `settings.gradle.kts` 增加 `include(":feature:appmarket")`。
2. root `build.gradle.kts` 的 `dependencies` 增加 `implementation(project(":feature:appmarket"))`。
3. `feature/appmarket/build.gradle.kts`：`bootJar{enabled=false}`、`jar{enabled=true}`、依赖 `core:common`、`core:database`、jpa、cache、web（web 已由 common 的 api 传递，仍显式加 starter-data-jpa / data-redis / cache + jdbc 对齐 showcase）。

## 风险 / 注意

- `core/common` 中 `JsonUtil`/`JsonExt` 基于 Gson；种子解析用 `String.jsonToList(AppSeedDto::class.java)`。
- 种子文件路径用 `ClassPathResource("data/appmarket_seed.json")`，不依赖进程工作目录（规避 AdController 用相对路径的既有坑）。
- 沿用数据库 Hibernate DDL 自动建表（项目无迁移工具）；新表首次启动由实体注解生成。

---

## 管理后台与自动采集（追加）

### 目标
提供管理后台页面，支持「触发自动采集」与「管理应用（应用/版本/下载源 CRUD）」。

### 页面与接口
- 页面：`AppMarketAdminPageController` → `GET /appmarket/admin`（模板 `appmarket/admin.html`）。
  该字面量路径比门户的 `/appmarket/{id}` 模板路径优先匹配，二者不冲突。
- 接口：`AppMarketAdminApiController` → `/api/v1/appmarket/admin`
  - `POST /collect` 触发采集
  - `GET /apps`、`GET /apps/{id}`
  - `POST /apps`、`PUT /apps/{id}`、`DELETE /apps/{id}`（级联删版本与下载源）
  - `POST /apps/{appId}/versions`、`DELETE /versions/{id}`（级联删下载源）
  - `POST /versions/{versionId}/sources`、`DELETE /sources/{id}`

### 服务分层
- 只读门户走 `AppMarketService`；读写管理走 `AppMarketAdminService`（写方法 `@Transactional` + `@CacheEvict(allEntries=true)`）。
- 表单 DTO 放 `dto/AdminDtos.kt`（`AppFormDto` / `VersionFormDto` / `SourceFormDto` / `CollectResultVo`）。Kotlin data class 可直接作为 `@RequestBody`（classpath 已有 `jackson-module-kotlin`）。

### 自动采集 `AppMarketCollector`
- 读取 `resources/data/appmarket_collect_targets.json`（provider + repo + 应用元信息 + releaseLimit）。
- 当前 provider 仅 `GITHUB`：OkHttp 调 `https://api.github.com/repos/{repo}/releases?per_page={n}`，解析后**幂等 upsert**：
  - App 按 `packageName` 判重；Version 按 `appId + versionName` 判重；Source 按 `versionId + downloadUrl` 判重。
  - 版本名归一（去前导 `v`），`versionCode` 取数字，`sizeMb` 取首个 asset 体积，`releaseDate` 解析 `published_at`。
- 手动触发而非定时，避免无节制抓取上游；单个目标失败不影响其余目标（逐目标 try/catch 记入 messages）。
- 目标仓库需有 release 资产（实测 NewPipe / BinaryEye / KeePassDX 均发布 APK）。

### 前端
- `templates/appmarket/admin.html` + `static/js/appmarket-admin.js`（jQuery + Bootstrap，沿用 `common/head::static` 与全局 `ctx`）。
- 应用列表表格 + 应用/版本/下载源弹窗、采集结果弹窗。

### 构建注意
- 采集需 OkHttp：`feature/appmarket` 依赖 `core:network` 并**显式**声明 `implementation("com.squareup.okhttp3:okhttp:4.10.0")`，否则经传递依赖会被 Spring Boot BOM 降级到 3.14.9（`Response.code/body` 变包私有字段 → 编译失败）。详见 `.trellis/spec/backend/quality-guidelines.md`。

### 验证（JDK 11 + H2 内存库）
- `./gradlew assemble` 通过；应用启动后：门户与 admin 全部路由 200。
- `POST /collect` 首次：targets=3、appsCreated=2、versionsAdded=9、sourcesAdded=12；再次触发 0/0/0（幂等）。
- CRUD 全流程（建应用→改→加版本→加源→删源→删版本→删应用）均通过，级联删除生效。

## 登录与角色可见性（追加需求）

需求：重做首页，增加登录；管理员可见「管理后台 + 应用市场」，普通用户只可见「应用市场」。

### 方案选择
- **认证方式**：轻量 Session 自建，不引入完整 Spring Security；仅依赖 `spring-security-crypto:5.3.3.RELEASE` 做 BCrypt。
- **账号来源**：内置种子账号（`AuthSeedLoader` + `data/auth_seed.json`），首次启动、`app_user` 为空时写入；密码明文只在种子文件里，入库前 BCrypt 加密。不做注册/找回。
- **首页**：简洁门户（问候 + 入口卡片），不做复杂仪表盘。

### 结构
- 新模块 `feature:auth`：`User`/`UserRole`、`UserRepository`、`UserVo`、`AuthService(Impl)`、`AuthApiController`、`AuthPageController`、`AuthSeedLoader`。
  - 依赖 `core:common` + `core:database`，不反向依赖任何 feature。
- 根模块新增：`config/AuthInterceptor`（鉴权）、`config/WebMvcConfig`（注册 + 放行）、`config/PortalModelAdvice`（模板注入 `user`/`isAdmin`）。
  - 放在 root 而非 feature 内，是为了避免 `appmarket -> auth` 的横向模块依赖。
- 模板：`templates/index.html`（门户首页，重写）、`templates/auth/login.html`、`templates/common/nav.html`（公共导航片段）。

### 关键约束（踩坑点）
- `@LoggerManage` 切面会序列化方法入参 → **登录接口不加该注解**；`AuthService` 不把 `HttpSession`/明文密码当入参，改为注入 `HttpServletRequest` 代理取会话。
- 未授权响应要分形态：`/api/**` 或 `Accept: application/json` → JSON 信封 + HTTP 401/403；页面 → 重定向（未登录→`/login`，已登录但权限不足→`/`，避免把已登录用户踢回登录页）。
- 前端隐藏入口只是体验，服务端拦截器才是边界（普通用户直接 GET `/appmarket/admin` 会被拦）。
- Kotlin 块注释可嵌套：KDoc 里不要写 `/**`（如 `/api/v1/auth/**`），否则编译报 "Unclosed comment"。

### 构建环境
- 本机只有 JDK 17 时，旧版 Lombok 注解处理器会崩（`IllegalAccessError: jdk.compiler does not export ...`），必须用 JDK 11：`export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`。
- 同时给 9 个子模块的 `build.gradle.kts` 补了 `sourceCompatibility/targetCompatibility = 11` 与 `jvmTarget = "11"`：原先只有 root 声明，子模块会跟随构建机 JDK 产出变体，root 要求 11 时依赖解析直接失败。

### 验证（JDK 11 + H2 内存库，curl 带 cookie）
| 场景 | 结果 |
|------|------|
| 匿名 `GET /`、`/appmarket/`、`/appmarket/admin` | 302 → `/login` |
| 匿名 `GET /api/v1/appmarket/apps` | 401 + `{"code":401,"msg":"未登录"}` |
| 密码错误 | `{"code":401,"msg":"用户名或密码错误"}` |
| user 登录后 `GET /` | 200，页面含应用市场入口、**不含**管理后台入口 |
| user `GET /appmarket/` | 200；`GET /appmarket/admin` → 302 回 `/`；`POST /api/v1/appmarket/admin/collect` → 403 JSON |
| admin 登录后 `GET /` | 200，**含**管理后台入口 |
| admin `GET /appmarket/admin`、`GET /api/v1/appmarket/admin/apps` | 200 |
| `GET /api/v1/auth/me` | 返回 `role=ADMIN` 的 UserVo（不含密码哈希） |
| 登出后 `GET /` | 302 → `/login` |
