# 执行计划：ZAZEWeb Docker 容器化部署

> 任务：`10-21-docker-deploy`。需求 `prd.md` / 设计 `design.md`。按顺序执行，每步含验证命令；任一步失败先修再往下，不要跳步。

## 前置检查（Gate 0）

- [ ] `docker info` 可用（Docker Desktop 已启动）；不可用则后续仅交付文件，验证项标记 blocked 并向赵臻说明
- [ ] `git status` 确认工作区仅预期改动（当前 dirty 1 paths，先确认是什么再动工）
- [ ] 二次确认：`frontend/package-lock.json` 存在（已确认）；`src/main/resources/static/{vendor,sql}` 存在（已确认）；仓库根 `data/` 存在（已确认，2 个 json）

## 实施步骤

1. [ ] **写 `.dockerignore`**（根目录）
   - 排除：`frontend/node_modules`、`**/node_modules`、`**/build`、`build`、`.gradle`、`.git`、`.trellis`、`local.properties`、`src/main/resources/static/assets`、`src/main/resources/static/index.html`、`**/.DS_Store`
   - 保留：`src/main/resources/static/vendor`、`src/main/resources/static/sql`、`data`
   - 验证：`docker build` 上下文无 node_modules（构建日志里 context size 应显著小于仓库体积）

2. [ ] **写 `Dockerfile`**（根目录，多阶段）
   - Stage `frontend`：`node:22-slim`；先 `COPY frontend/package*.json` 再 `npm ci`（缓存层），`ARG NPM_REGISTRY=https://registry.npmmirror.com`，`RUN npm ci --registry=$NPM_REGISTRY`；再 `COPY frontend/ .`；`RUN ./node_modules/.bin/vite build --outDir /out`
   - Stage `backend`：`ARG GRADLE_IMAGE=gradle:7.5.1-jdk11`；`RUN echo "useLocalMaven=false" > local.properties`；`COPY . .`；`COPY --from=frontend /out/ ./src/main/resources/static/`；`RUN --mount=type=cache,target=/home/gradle/.gradle gradle bootJar --no-daemon`
   - 运行层：`eclipse-temurin:11-jre`；`apt-get install -y --no-install-recommends curl` 并清理；建非 root 用户；`COPY --from=backend build/libs/zaze-server-*.jar /app/app.jar`；`COPY data/ /app/data/`；`WORKDIR /app`；`EXPOSE 8080`；`ENV TZ=Asia/Shanghai JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"`；`USER appuser`；`ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]`

3. [ ] **写 `docker-compose.yml`**（根目录）
   - 按 design.md §3：app + mysql:8.0 + 命名卷 + 双侧 healthcheck + `depends_on: service_healthy` + `restart: unless-stopped`
   - `SPRING_DATASOURCE_URL` 参数与原 `application.yml` 对齐（`useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=GMT%2B8&allowPublicKeyRetrieval=true`），host 换 `mysql`
   - build args 传 `NPM_REGISTRY`

4. [ ] **写 `docs/docker.md`**
   - 覆盖：前置条件（Docker）、一键启动、首次构建说明（双源加速）、查看日志、停止/清理（含 `down -v` 清数据的醒目提醒）、卷备份与恢复（`docker compose exec mysql mysqldump`）、改宿主端口、MySQL 8 认证兜底、老库图标迁移说明、常见问题（8080 占用 / 构建慢 / 连不上库）

5. [ ] **构建**：`docker compose build`（或 `docker compose build --progress=plain` 便于排错）
   - 通过标准：镜像 `zaze-web-app`（或 compose 默认命名）构建成功，无 `BUILD FAILED` / npm 错误

6. [ ] **启动**：`docker compose up -d` → `docker compose ps` 两服务均 `healthy`
   - 若 app 反复重启：`docker compose logs app` 看 datasource 连接/DDL 报错

7. [ ] **功能验证**（逐条记录结果）：
   - `curl -s http://localhost:8080/ | head -5` → 门户 index.html
   - `curl -s "http://localhost:8080/api/v1/appmarket/apps?page=1&size=5"` → 含 `list/total/page/size` 的 JSON
   - `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/admin` → 200
   - `docker compose exec mysql mysql -uzaze -pzaze dev -e "show tables; select count(*) from appmarket_app;"` → 表存在（ddl-auto 建出）
   - 写入验证：经后台或 API 造一条数据 → `docker compose restart app` → 数据仍在（卷生效）

8. [ ] **回归确认**（不改代码，确认既有门禁不受影响）：
   - `./gradlew :feature:appmarket:compileKotlin --offline`
   - `cd frontend && npm run typecheck`

9. [ ] **收尾**：验证结果回写本文件第 7 步；提交（Phase 3 走 `/commit` 流程，先 `git status` 给赵臻过目）

## 回滚点

- 步骤 5 构建失败 → 修 Dockerfile/compose 后重试，不影响仓库其他文件
- 步骤 6/7 启动或验证失败且短时间无法定位 → `docker compose down -v` 停栈，保留 4 个新文件并在任务 journal 记录阻塞原因，向赵臻汇报
- 任何阶段发现设计缺陷（如 profile 结构与假设不符）→ 回 Phase 1 修订 design.md 再继续

## Review Gates

- Gate A（步骤 4 完成后）：4 个新增文件一次性给赵臻过目，再执行构建
- Gate B（步骤 9 前）：验证清单全绿或已标注 blocked，方可提交
