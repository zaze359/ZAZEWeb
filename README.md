# zaze-server（简易 Web 后端）

一个基于 **Spring Boot** 的 Kotlin/Java 多模块后端项目，提供门户页面与 RESTful 接口。当前已内置「应用市场」门户功能：展示应用列表，每个应用包含多个版本，每个版本聚合多个不同来源的下载地址（暂不做自有二进制存储，下载地址收集公开外链）。

## 技术栈

### 后端
- Java 11 + Kotlin 1.6.21
- Spring Boot 2.3.1
- Spring MVC / Spring Data JPA
- Thymeleaf（服务端模板，门户页渲染）
- Spring Cache（基于 Redis）
- WebSocket（消息模块）
- OkHttp（网络采集 / 外部请求，见 `core:network`）

### 存储与中间件
- MySQL（JPA 自动建表，`spring.jpa.hibernate.ddl-auto=update`）
- Redis（缓存，默认 `localhost:6379`）

### 前端（门户）
- Bootstrap + jQuery（复用项目内置 `static/vendor` 资源，通过 `common/head` 片段统一加载）
- 服务端模板渲染，无独立前端构建步骤

## 模块结构

| 模块 | 说明 |
|------|------|
| 根模块 `zaze-server` | 启动类 `DemoApplicationKt`、全局配置、门户模板（`templates/`）与静态资源（`static/`）、对外接口入口 |
| `core:common` | 通用基础：`Response<T>` 统一响应信封、`@LoggerManage` 日志切面、`JsonExt` / `FileUtil` 工具、`BaseController` |
| `core:database` | 数据层基础：`BaseEntity`、`BaseRepository`、JPA 相关配置 |
| `core:network` | 网络层：OkHttp 自动配置（注解 `@EnableOkHttp`） |
| `feature:showcase` | 示例业务模块（完整垂直切片，含 Redis 缓存，可作为新功能参照模板） |
| `feature:application` | 应用相关接口 |
| `feature:ad` | 广告模块（规则加载） |
| `feature:message` | WebSocket 消息模块 |
| `feature:appmarket` | **应用市场门户（本次新增）**：应用 / 版本 / 下载源三级模型 + 门户页 + API |
| `feature:auth` | 轻量会话认证：用户实体、登录 / 登出、种子账号，供门户与后台做角色区分 |

> 约定：`feature/*` 与 `core/*` 模块均**禁用 `bootJar`**，只有根模块是可执行包；子模块通过 `implementation(project(...))` 打入根模块 jar。

## 环境要求

- **JDK 11**（Kotlin 1.6.21 / Gradle 7.5.1 不兼容更高版本 JDK）
- **Gradle 7.5.1**（项目自带 Gradle Wrapper，无需手动安装）
  - 本机若只装了更高版本 JDK（如 17），旧版 Lombok 的注解处理器会直接报错，必须用 JDK 11 构建：
    `export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`（Homebrew `openjdk@11` 的路径）
- **MySQL 5.7+**（默认库名 `dev`，账号 `root` / `123456`，详见下方配置）
- **Redis**（缓存使用，默认 `localhost:6379`；如暂不需要可改为 `spring.cache.type=simple` 或 `none`）

## 配置说明

### 1. `local.properties`（构建必需）
`settings.gradle.kts` 在构建开始时读取根目录的 `local.properties`，缺失会导致构建失败。内容为：

```properties
useLocalMaven=false
```

> 该文件已被 `.gitignore` 忽略，属于本地环境配置，不会进入版本库。

### 2. 数据源与缓存（`src/main/resources/`）
- `application.yml`：服务器端口（默认 `8080`）、MySQL 数据源（`jdbc:mysql://localhost:3306/dev`）、OkHttp 超时、登录会话时效（`server.servlet.session.timeout`，当前 `7d`，生产建议改短并配合 HTTPS）。
- `application.properties`：`spring.jpa.hibernate.ddl-auto=update`（启动时按实体自动建/更表，无需手写 schema）、`spring.cache.type=redis`、`management.endpoints.web.exposure.include=*`（actuator 全量暴露）。

如需对接自己的环境，直接修改以上两个文件中的 `spring.datasource.*` 与 `spring.redis.*` 即可。

## 构建

```bash
# 使用项目自带的 Gradle Wrapper（需先确保 JDK 11）
./gradlew assemble      # 编译并打包，产出根模块可执行 jar
./gradlew build         # 等同 assemble 并运行测试（如有）
```

产出物：`build/libs/zaze-server-0.0.1-SNAPSHOT.jar`

## 本地运行（开发）

```bash
./gradlew bootRun
```

启动后访问（首页与业务页均需先登录）：
- 门户首页：<http://localhost:8080/>（未登录会自动跳转登录页）
- 登录页：<http://localhost:8080/login>
- 应用市场门户首页：<http://localhost:8080/appmarket>
- 应用详情页：<http://localhost:8080/appmarket/{id}>
- 应用市场 API：`http://localhost:8080/api/v1/appmarket/apps`、`/api/v1/appmarket/apps/{id}`
- 监控端点：`http://localhost:8080/actuator/**`

## 部署

### 方式一：可执行 jar
```bash
./gradlew bootJar                                   # 产出 build/libs/zaze-server-0.0.1-SNAPSHOT.jar
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar
```

### 方式二：反向代理（推荐生产）
前置 Nginx 反向代理到 `localhost:8080`，并通过环境变量覆盖配置（无需改动代码/配置文件）：

```bash
SERVER_PORT=8080 \
SPRING_DATASOURCE_URL='jdbc:mysql://<db-host>:3306/dev?serverTimezone=GMT%2B8&characterEncoding=utf-8&useSSL=false' \
SPRING_DATASOURCE_USERNAME=<user> \
SPRING_DATASOURCE_PASSWORD=<pwd> \
SPRING_REDIS_HOST=<redis-host> \
SPRING_REDIS_PORT=6379 \
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar
```

> Spring Boot 支持用大写下划线环境变量覆盖配置项（如 `SPRING_DATASOURCE_URL` → `spring.datasource.url`）。

### 部署前置检查
1. 目标机器已安装 **JDK 11**。
2. MySQL 已创建 `dev` 库，账号密码与配置一致；表结构会在首次启动时由 JPA 自动生成。
3. Redis 可用（默认 `localhost:6379`）；若不使用缓存，将 `spring.cache.type` 改为 `simple` 或 `none`。
4. 防火墙放行服务端口（默认 8080）或仅对内网 / 反向代理开放。

## 功能入口速查

| 功能 | 路径 | 说明 |
|------|------|------|
| 门户首页 | `GET /` | 需登录；按角色渲染入口卡片（管理员额外显示「管理后台」） |
| 登录页 | `GET /login` | 唯一无需登录即可访问的页面 |
| 登录 / 登出 / 当前用户 | `POST /api/v1/auth/login`、`POST /api/v1/auth/logout`、`GET /api/v1/auth/me` | 登录返回 `Response<UserVo>`，密码错误返回 `code=401` |
| 应用市场门户（列表） | `GET /appmarket` | Thymeleaf 门户页，应用卡片列表（需登录） |
| 应用详情（版本 + 来源） | `GET /appmarket/{id}` | 应用详情页，展开各版本与多来源下载链接 |
| 应用列表 API | `GET /api/v1/appmarket/apps` | 返回 `Response<List<AppVo>>` 信封 |
| 应用详情 API | `GET /api/v1/appmarket/apps/{id}` | 返回 `Response<AppDetailVo>`，含嵌套 `versions[].sources` |

## 应用市场数据说明

- **数据模型**：应用（`App`）→ 版本（`AppVersion`，外键 `appId`）→ 下载源（`DownloadSource`，外键 `versionId`），三级一对多。
- **暂不做自有存储**：下载地址均为收集自网上的公开外链（GitHub / F-Droid / 官方 / APKMirror 等），不托管二进制文件。
- **种子数据**：项目启动时若 `app` 表为空，`AppMarketSeedLoader`（`CommandLineRunner`）会从 classpath 资源 `data/appmarket_seed.json` 写入示例数据（4 个真实开源应用，每版本 ≥2 个来源）。
- **后续扩展**：将 `AppMarketSeedLoader` 中「人工整理 JSON」替换为 `AppMarketCollector`（借助 `core:network` 的 OkHttp 自动抓取真实下载页/接口）即可进入自动化采集阶段，上层 Service / VO 无需变动。

## 登录与权限

采用 **轻量 Session 自建认证**（未引入完整 Spring Security，只用 `spring-security-crypto` 做 BCrypt 校验）：

- 用户实体 `app_user`（`feature:auth`），角色 `ADMIN` / `USER`，密码只存 BCrypt 哈希。
- 登录成功后把 `UserVo` 放进 Session（键 `AUTH_USER`），登出即移除。
- 根模块 `AuthInterceptor` + `WebMvcConfig` 做服务端强制鉴权，分两级：

| 级别 | 路径 | 要求 |
|------|------|------|
| 管理员 | `/appmarket/admin`、`/appmarket/admin/**`、`/api/v1/appmarket/admin/**`、`/admin`、`/admin/**` | 已登录 **且** ADMIN |
| 登录即可 | `/`、`/appmarket`、`/appmarket/**`、`/api/v1/appmarket/**` | 已登录 |
| 放行 | `/login`、`/api/v1/auth/**`、静态资源、`/error` | 无需登录 |

- 未授权时：`/api/**` 返回 JSON 信封并设置 HTTP 401 / 403；页面请求重定向（未登录 → `/login`，已登录但权限不足 → `/`）。
- 页面上的角色区分：`PortalModelAdvice` 向所有模板注入 `user` / `isAdmin`，导航条 `common/nav::portal` 据此决定是否渲染「管理后台」入口。**服务端拦截才是真正的边界**，前端隐藏只是体验。

### 内置种子账号

首次启动且 `app_user` 表为空时，`AuthSeedLoader` 会用 BCrypt 加密写入 `feature/auth/src/main/resources/data/auth_seed.json` 中的账号：

| 用户名 | 密码 | 角色 | 可见入口 |
|--------|------|------|----------|
| `admin` | `admin123` | ADMIN | 应用市场 + 管理后台 |
| `user` | `user123` | USER | 仅应用市场 |
| `guest` | `guest123` | USER | 仅应用市场 |

> 这是开发环境便利方案。**上线前请删除或修改种子账号**（改 `auth_seed.json` 或直接改库里密码），否则等于公开了管理员口令。

## 已知约定与注意事项

- 统一响应格式为 `Response<T>{ code, data, msg }`；分页沿用 `PageResponse` 以对齐前端 bootstrap-table。
- 控制器方法建议加 `@LoggerManage(description="中文描述")` 以启用出入参与异常日志切面（注意该切面会序列化参数，涉密端点慎用）。
  - 因此**登录接口不加该注解**，且 `AuthService` 刻意不把 `HttpSession` / 明文密码作为方法入参，改为注入 `HttpServletRequest` 代理取会话。
- 所有子模块都在 `build.gradle.kts` 里显式声明了 `sourceCompatibility = 11` 与 `jvmTarget = "11"`，避免构建结果跟随本机 JDK 版本漂移。
- 仓库当前无数据库迁移工具，表结构变更依赖 JPA `update`；生产环境建议评估改为 `none` 并配合迁移脚本。
- 部分模块存在历史技术债（如信任所有 SSL、相对路径读取文件等），新增代码请勿复制这些写法。
