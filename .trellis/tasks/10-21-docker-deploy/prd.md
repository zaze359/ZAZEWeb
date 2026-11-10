# ZAZEWeb Docker 容器化部署

> 任务：`10-21-docker-deploy`（复杂任务）。本文件只写**需求、约束、验收**；技术设计见 `design.md`，执行计划见 `implement.md`。

## 目标

一条命令把 ZAZEWeb 跑起来：`docker compose up --build` 从零构建镜像并启动「应用 + MySQL」两个容器，门户、管理后台、API 全流程可用，MySQL 数据落在命名卷中可持久化。

## 已确认的技术事实（来自对仓库的探查）

- 后端 Spring Boot 2.7.18 / Kotlin 1.6.21 / **JDK 11** / Gradle 7.5.1（wrapper 已钉死）；managed Lombok 与 JDK 17 不兼容（见 quality-guidelines）→ **构建阶段必须 JDK 11**。
- 前端 Vue3 + Vite 6，`frontend/package-lock.json` 存在（可用 `npm ci`）；产物 `base:'/'`，正常输出 `src/main/resources/static`；该目录下 `vendor/`（app-info-parser）与 `sql/` 为常驻资产，**不得清空**（vite 配置 `emptyOutDir:false` 也保证了这点）。
- 数据源为 **MySQL**（`application.yml`：`jdbc:mysql://localhost:3306/dev`）；表结构由 `spring.jpa.hibernate.ddl-auto=update` 托管（backend spec 明确记录）。
- `spring-boot-starter-data-redis` 在 classpath，但全仓 grep `RedisTemplate` 等无命中 → **代码未使用 Redis**，compose 不起 redis 服务。
- `migrate_appmarket_icon_url.sql` 是对**老库**的 `ALTER TABLE ... MODIFY COLUMN`；全新库 ddl-auto 会直接按实体建出 TEXT 列 → **不挂 MySQL initdb**（否则首启因表不存在而失败）。`schema.sql` 为空文件，仅预留。
- `AdController` 以相对路径读 `data/ltt_rules.json`（quality-guidelines 记录的已知技术债）→ 运行镜像必须携带仓库根 `data/` 目录且 `WORKDIR=/app`。
- `settings.gradle.kts` **强制读取** `local.properties`；仓库里 `useLocalMaven=true` 指向本机 8081 私服 → 容器构建阶段必须内生成 `useLocalMaven=false`，并把仓库的 `local.properties` 排除出构建上下文。
- 仓库当前无 `Dockerfile` / `docker-compose.yml` / `.dockerignore`；`docs/` 目录尚不存在（本次新建，README 不改动）。
- Vite 6 依赖 esbuild，esbuild npm 包**不支持 musl** → 前端构建阶段用 Debian 系 Node 镜像（`node:*-slim`），**不用 alpine**。

## 需求

- **R1 多阶段 Dockerfile**（仓库根）：`node:22-slim` 前端构建 → `gradle:7.5.1-jdk11` 后端构建（`gradle bootJar`）→ `eclipse-temurin:11-jre` 运行层；构建阶段内生成 `local.properties`（`useLocalMaven=false`）。
- **R2 `.dockerignore`**：排除 `frontend/node_modules`、`**/build`、`build/`、`.gradle`、`.git`、`.trellis`、`local.properties`、`src/main/resources/static/assets` 与 `static/index.html`（每次构建重新生成，避免陈旧产物）；**保留** `static/vendor`、`static/sql`、`data/`。
- **R3 `docker-compose.yml`**：`app` + `mysql:8.0` 两服务；MySQL 用命名卷持久化、带 healthcheck；app `depends_on: condition: service_healthy`；app 带容器 healthcheck；端口 `8080:8080`；`restart: unless-stopped`。
- **R4 配置外置**：通过 `SPRING_DATASOURCE_URL/USERNAME/PASSWORD` 环境变量把数据源指向 `mysql` 服务名，**不修改** `application.yml` / `application.properties` 的本地开发行为（env 优先级高于配置文件）。
- **R5 运行时资产**：bootJar、`data/`（AdController 相对路径读取）、`static/vendor`、`static/sql` 全部进镜像。
- **R6 使用文档** `docs/docker.md`：构建、启动、查看日志、停止/清理、卷备份、改端口、常见问题。
- **R7 端到端验证**：按 `implement.md` 的验证清单逐条跑通并记录结果。

## 约束

- 不升级 JDK / Boot / Kotlin / Gradle 版本；不引新框架；不改任何业务代码。
- 不牵动门户与管理后台 UI；不改变现有本地开发流（`bootRun` / `vite dev` 照旧）。
- 国内网络可完成构建：Maven 走 `settings.gradle.kts` 里已配置的阿里云镜像；npm 默认经 build-arg 走 npmmirror（不需要可删）。
- 迁移 SQL 不挂 initdb（见上文事实）；新库表结构由 ddl-auto 负责。

## 验收标准

- [ ] `docker compose up --build` 一条命令从零构建并起栈（两台容器均 healthy）。
- [ ] `GET /` 返回门户 `index.html`；`GET /api/v1/appmarket/apps?page=1&size=5` 返回分页 JSON（`list/total/page/size`）。
- [ ] `GET /admin` 返回 200（SPA 回退到 index.html）。
- [ ] 写入一条数据后 `docker compose restart`，数据仍在（命名卷生效）。
- [ ] `docker compose exec mysql` 能看到 ddl-auto 建出的表（如 `appmarket_app`）。
- [ ] `docs/docker.md` 覆盖：build / up / logs / down / 卷备份 / 改端口 / 常见问题。
- [ ] 仓库既有门禁不受影响：`./gradlew :feature:appmarket:compileKotlin --offline`、`npm run typecheck` 仍如常通过（本次不改代码，回归确认即可）。

## Notes

- 用户已确认：compose 带 MySQL 容器；多阶段全自动构建；SQL 目录挂载方案（实现时按上文事实修正为「仅 ddl-auto 建表 + 不挂 initdb」，理由见 design.md 决策 D4）。
- 赵臻术语约定：「暂存」= `git stash`，提交时注意用词。
