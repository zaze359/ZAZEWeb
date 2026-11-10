# 执行计划：零依赖启动（H2 默认 profile）

> 任务：`11-10-zero-dep-startup`。需求见 `prd.md`，技术设计见 `design.md`。
> **硬性前提**：`JAVA_HOME` 必须是 JDK 11（默认 JDK 17 会在 Lombok 注解处理阶段崩，与本改动无关）。

## 0. 前置检查（一次性）

```bash
# 0.1 JDK 11
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
java -version            # 期望 11.x

# 0.2 确认 3306 没有 MySQL 在跑（这是"零依赖"的证明条件）
lsof -i:3306 || echo "3306 free → 本轮验证有效"

# 0.3 确认 H2 在运行时 classpath（不新加依赖）
./gradlew -q :dependencies --configuration runtimeClasspath 2>/dev/null | grep -i h2

# 0.4 确认现有配置里没有会打架的 profile / dialect 声明
grep -n "spring.profiles\|activate.on-profile" src/main/resources/application.properties
ls src/main/resources/application-standalone.yml 2>/dev/null || echo "standalone yml 尚不存在（预期）"
```

- [ ] 0.1 ~ 0.4 全部符合预期；若 `0.2` 显示 MySQL 在跑，先停掉再进入验证阶段。

**门禁**：JDK 不是 11 → 停，先切 JDK。`application.properties` 里若已有 `spring.profiles.active`（而非 default），说明默认 profile 机制已被占用 → 回 `design.md` 第 3 节重新选落点。

## 1. 新增 `application-standalone.yml`

- [ ] 在 `src/main/resources/` 新建 `application-standalone.yml`，内容严格按 `design.md` 第 2 节：
  - `spring.datasource.url = jdbc:h2:file:./data/h2/zaze;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH`
  - `driver-class-name: org.h2.Driver`、`username: sa`、`password: ""`、`hikari.pool-name/maximum-pool-size`
  - `spring.jpa.database-platform` 与 `spring.jpa.properties.hibernate.dialect` 都写 `org.hibernate.dialect.H2Dialect`
  - `spring.cache.type: simple`
- [ ] 加注释说明：这是零依赖本地模式，生产/原流程走 `SPRING_PROFILES_ACTIVE=mysql`；不要在这里写业务逻辑开关。

## 2. 追加默认 profile

- [ ] 在 `src/main/resources/application.properties` **末尾追加**（不改既有行）：

```properties
# 零依赖本地启动：无 active profile 时走 H2 文件库（application-standalone.yml）。
# 需要原 MySQL 路径时：SPRING_PROFILES_ACTIVE=mysql
spring.profiles.default=standalone
```

- [ ] 校验：`tail -3 src/main/resources/application.properties` 能看到这三行；`git diff --stat` 显示该文件只增行（`+3`）。

## 3. 忽略库文件

- [ ] `.gitignore` 追加：

```
# 零依赖模式的本地 H2 库文件
data/h2/
```

- [ ] 校验：`git status --short` 里不会出现 `data/h2/`；`git check-ignore -v data/h2/zaze.mv.db` 能命中规则。

## 4. 首次启动验证（核心）

```bash
# 4.1 编译门禁（先确认没把代码弄坏）
./gradlew :feature:appmarket:compileKotlin --offline

# 4.2 后台启动，日志落文件
./gradlew bootRun > /tmp/zaze-standalone.log 2>&1 &

# 4.3 等端口（不要 sleep 死等，用探测）
until curl -fsS -o /dev/null http://localhost:8080/ 2>/dev/null; do sleep 2; done
```

- [ ] 日志里能搜到 H2：
  - `grep -i "jdbc:h2\|H2Dialect\|standalone" /tmp/zaze-standalone.log`
  - 且**没有** `Communications link failure` / `Connection refused`（连 MySQL 失败的特征串）。
- [ ] `data/h2/zaze.mv.db` 已生成。

**回滚点 A**：启动失败且日志指向配置（例如仍在找 MySQL）→ 停掉进程，按 `design.md` 风险表第 1、2 行处理；不要为了"能起来"去改业务代码。

## 5. 功能验证（不依赖外部服务）

- [ ] 门户：`curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/` → `200`
- [ ] 后台 SPA：`curl -s -o /dev/null -w "%{http_code}\n" http://localhost:8080/admin` → `200`
- [ ] 分页 API：`curl -s "http://localhost:8080/api/v1/appmarket/apps?page=1&size=5"` → JSON 含 `list/total/page/size`，且 `total > 0`
- [ ] 分类 API：`curl -s "http://localhost:8080/api/v1/appmarket/apps/categories"` → 非空数组
- [ ] 后台登录：用种子管理员账号走 `POST /api/v1/auth/login`，返回 token/用户信息（账号见 `AuthSeedLoader`，不在文档里写死明文，只写"种子管理员账号"）
- [ ] 写操作：通过后台 API 改一条 app 的简介（或新增一条来源），返回成功

**回滚点 B**：若某条原生 SQL 在 H2 下报语法错（`ShowcaseRepository` 的两条已实测通过，其他模块若有遗漏）→ 记录具体 SQL 与堆栈，回到 Plan 评估「改 SQL 兼容写法 vs 该接口标注 H2 不支持」，不要在 Implement 里顺手改业务查询。

## 6. 持久化验证

```bash
# 6.1 停应用
kill %1 2>/dev/null; ./gradlew --stop

# 6.2 再起（不删 data/h2）
./gradlew bootRun > /tmp/zaze-standalone2.log 2>&1 &
until curl -fsS -o /dev/null http://localhost:8080/ 2>/dev/null; do sleep 2; done

# 6.3 复读第 5 步里改过的那条数据
curl -s "http://localhost:8080/api/v1/appmarket/admin/apps/{id}" ...
```

- [ ] 第 5 步的改动在重启后仍在 → H2 文件持久化生效。
- [ ] 种子数据没有被重复灌入（`SeedLoader` 的 `count() > 0` 生效，数量与重启前一致）。

## 7. MySQL 路径回归

```bash
# 前提：本机 MySQL 已起、库为 dev
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun > /tmp/zaze-mysql.log 2>&1 &
until curl -fsS -o /dev/null http://localhost:8080/ 2>/dev/null; do sleep 2; done
grep -i "jdbc:mysql" /tmp/zaze-mysql.log
```

- [ ] 能连上 MySQL 并启动成功（行为与改动前一致）。
- [ ] **若本机 MySQL 未起**：本条标记为 `blocked`（写清原因），不算未完成 —— 但它必须在合并前由你本人补跑一次，因为这条是"没破坏原有流程"的唯一证据。

## 8. 前端门禁

```bash
cd frontend && npm run typecheck
```

- [ ] 通过（本次不动前端，属回归确认）。

## 9. 文档

- [ ] 新建 `docs/local-dev.md`，覆盖：
  1. 默认模式：`./gradlew bootRun`（无需 MySQL / Redis），数据在 `data/h2/`；
  2. 切 MySQL：`SPRING_PROFILES_ACTIVE=mysql`，IDE 里怎么设；
  3. 两套数据互相独立（不迁移）；
  4. 清库重来：停应用 → 删 `data/h2/` → 重启；
  5. 查看数据：H2 Shell 命令（含 jar 定位：`~/.gradle/caches/modules-2/files-2.1/com.h2database/h2/`）；
  6. 常见问题：文件锁（先停应用）、CWD 不对导致库建错位置、端口占用、JDK 必须是 11。
- [ ] 文档里不写任何账号密码明文。

## 10. 收尾检查

- [ ] `git status --short` 只包含预期改动：`application-standalone.yml`（新）、`application.properties`（+3 行）、`.gitignore`（+2 行）、`docs/local-dev.md`（新）；**没有** `data/h2/`、`*.mv.db`、`*.trace.db`、`/tmp` 日志。
- [ ] `git diff src/main/resources/application.properties` 确认只增不改。
- [ ] 停掉后台进程与 Gradle daemon：`./gradlew --stop`。
- [ ] 更新 backend spec：`.trellis/spec/backend/database-guidelines.md` 里补一句「默认 profile 是 standalone(H2)，MySQL 需显式 `SPRING_PROFILES_ACTIVE=mysql`」——这是后续 AI/人最容易踩的认知差。

## 验证命令速查

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-11.jdk/Contents/Home
./gradlew :feature:appmarket:compileKotlin --offline
./gradlew bootRun                      # 零依赖模式
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
(cd frontend && npm run typecheck)
git check-ignore -v data/h2/zaze.mv.db
```
