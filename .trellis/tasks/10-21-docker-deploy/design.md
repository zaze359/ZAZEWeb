# 设计：ZAZEWeb Docker 容器化部署

> 任务：`10-21-docker-deploy`。需求见 `prd.md`，执行顺序见 `implement.md`。本文件只做技术设计。

## 1. 总体形态

单仓库根上下文，一个多阶段 `Dockerfile` + 一个 `docker-compose.yml`（app + mysql 两服务，命名卷持久化）。不引入新框架、不改业务代码，仅新增 4 个文件：`Dockerfile`、`.dockerignore`、`docker-compose.yml`、`docs/docker.md`。

```
docker compose up --build
        │
        ▼
┌──────────────────────────┐   ┌──────────────────────────┐
│ app (zaze-web)           │   │ mysql (zaze-mysql)       │
│  temurin:11-jre          │──▶│  mysql:8.0               │
│  app.jar + data/ +       │   │  命名卷 mysql-data       │
│  static/(vendor,sql)     │   │  healthcheck: mysqladmin │
│  healthcheck: curl /     │   └──────────────────────────┘
│  8080                    │
└──────────────────────────┘
```

## 2. Dockerfile 多阶段设计

| Stage | 基础镜像 | 职责 | 关键点 |
|-------|----------|------|--------|
| `frontend` | `node:22-slim` | `npm ci` + `vite build` → `/out` | Debian 系（esbuild 无 musl 构建，见 D2）；`--outDir /out` 覆盖配置里的 `../src/main/resources/static` |
| `backend` | `gradle:7.5.1-jdk11` | `gradle bootJar --no-daemon` → `build/libs/zaze-server-*.jar` | 与 wrapper 版本精确一致（D1）；先写 `local.properties`（D3）；Gradle 缓存用 BuildKit cache mount |
| 运行层 | `eclipse-temurin:11-jre` | 拷 jar + `data/`，非 root 运行 | 装 curl 供 healthcheck；`TZ=Asia/Shanghai`；`JAVA_OPTS` 入口 |

**Stage 间数据流**：
- `frontend` 产物 `/out`（assets + index.html）→ `backend` 阶段 `COPY --from=frontend /out/ ./src/main/resources/static/`（与仓库带来的 `static/vendor`、`static/sql` 合并；vite `emptyOutDir:false` 语义保留）。
- `backend` 产物 `build/libs/zaze-server-*.jar` → 运行层 `/app/app.jar`（glob 拷贝，不写死版本号）。
- 仓库根 `data/` 经 `COPY . .` 进入 `backend`，运行层再单独 `COPY data/ /app/data/`；`WORKDIR /app`，保证 `AdController` 的相对路径读取行为与本地一致。

**构建阶段顺序（backend）**：
1. `RUN echo "useLocalMaven=false" > local.properties`（`settings.gradle.kts` 无条件加载该文件，缺失即构建失败）
2. `COPY . .`（已被 `.dockerignore` 瘦身）
3. `COPY --from=frontend /out/ ./src/main/resources/static/`
4. `RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon`（不跑测试：bootJar 不依赖 test，且测试环境依赖在容器内不可控；测试仍由宿主侧 `./gradlew test` 把关）

**运行层要点**：`USER appuser`（Debian 系 `useradd` 建）；`HEALTHCHECK` 用 `curl -fsS http://localhost:8080/`（门户首页恒 200，避开 actuator 可能的鉴权）；入口 `java $JAVA_OPTS -jar app.jar`，默认 `JAVA_OPTS=-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom`。

## 3. docker-compose 设计

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/dev?...（与原 URL 参数一致，host 换服务名）
      SPRING_DATASOURCE_USERNAME / PASSWORD: 与 mysql 服务一致
    depends_on:
      mysql: { condition: service_healthy }
    restart: unless-stopped
  mysql:
    image: mysql:8.0
    command: --character-set-server=utf8mb4 --collation-server=utf8mb4_unicode_ci
    environment: MYSQL_DATABASE=dev / MYSQL_USER / MYSQL_PASSWORD / MYSQL_ROOT_PASSWORD
    volumes: ["mysql-data:/var/lib/mysql"]
    healthcheck: mysqladmin ping
volumes: ["mysql-data"]
```

- 省略 `version:`（已废弃）。
- MySQL 8.0 默认 `caching_sha2_password`，mysql-connector-j 8.x + 原 URL 已带 `allowPublicKeyRetrieval=true`，兼容；连接失败时的降级手段记入文档。
- 账号密码用显式值（`zaze`/`zaze`，文档注明按需修改），不引 `.env` 文件（个人站点，保持零依赖启动）。

## 4. 关键决策与取舍

| # | 决策 | 理由 |
|---|------|------|
| D1 | 后端构建用 `gradle:7.5.1-jdk11` 官方镜像而非 `./gradlew` + temurin | wrapper 版本 7.5.1 与镜像精确一致，省一次 ~120MB 发行版下载；升级时改 `ARG GRADLE_IMAGE` 并同步 wrapper |
| D2 | 前端构建用 `node:22-slim`，不用 alpine | esbuild 的 npm 平台包只有 glibc 构建，musl 下 `npm ci` 直接失败 |
| D3 | 构建阶段内生成 `local.properties`（`useLocalMaven=false`），仓库文件进 `.dockerignore` | `settings.gradle.kts` 强制加载该文件；`useLocalMaven=true` 会让容器构建去连宿主才有的 8081 私服 |
| D4 | **不挂 SQL 到 initdb**（对用户初选「挂载 SQL 目录」的修正） | `migrate_*.sql` 是 `ALTER TABLE`，首启时表还不存在（ddl-auto 在应用启动后才建表），挂载即报错；`schema.sql` 为空。新库 schema 由 `ddl-auto=update` 直接建出（icon_url 按实体即为 TEXT，无需迁移）。老库迁移仍在文档中说明手动执行 |
| D5 | 数据源覆盖全走环境变量，不动 `application.yml` | env 优先级高于任何 profile 配置文件；本地 `bootRun` 行为零变化；无需读取（当前审批不可用）yml 中的具体账号 |
| D6 | 镜像不带 redis 服务 | 全仓无 Redis 使用代码；classpath 上的 autoconfig 是惰性建 factory，不连通也不影响启动 |
| D7 | npm 源经 `ARG NPM_REGISTRY` 默认 npmmirror（compose 传入） | 国内拉取 npmjs.org 慢/不稳；Maven 侧已配阿里云镜像，无需处理 |
| D8 | `.dockerignore` 排除 `static/assets`、`static/index.html` | 每次构建重新生成，避免仓库里的旧产物把上下文撑大且造成陈旧资产残留；`static/vendor`、`static/sql` 保留 |

## 5. 兼容性与风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| `docker` 未运行 / 无权限 | 验证阶段全阻塞 | implement.md 第 0 步先 `docker info` 确认；不可用则只交付文件 + 文档，验证项标记 blocked |
| 宿主 8080 被占用（本机 bootRun 常用） | app 容器起不来 | 文档给出改宿主端口方法（`ports: "18080:8080"`） |
| 首次构建下载量大（gradle 依赖 + node_modules） | 首次慢 | 国内双源（D1/D7）；gradle cache mount 加速二次构建 |
| 应用侧还有未发现的相对路径/落盘依赖 | 容器内行为与本地不一致 | 验证清单覆盖门户/后台/API 三条链路；发现问题按 implement.md 回滚点处理 |
| MySQL 8 认证插件兼容 | 连不上库 | `allowPublicKeyRetrieval=true` 已在 URL；兜底：command 加 `--default-authentication-plugin=mysql_native_password`（文档注明） |
| yml/properties 含敏感信息，本轮读取被审批拦截 | 设计只能基于 spec 与 grep 结论 | env 覆盖方案不依赖其内容；若后续发现 profile 结构差异，按 Phase 回滚到 1.1 修订 design |

## 6. 回滚形态

全部为**新增文件**（`Dockerfile`、`.dockerignore`、`docker-compose.yml`、`docs/docker.md`），无存量文件改动 → 回滚 = `docker compose down -v`（清栈与卷）+ 删除 4 个文件即可完全恢复原状。
