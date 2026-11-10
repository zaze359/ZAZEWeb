> **状态：已作废（2026-11-10）。** 目标由「后端换嵌入式库」上移为「弱化后端概念」，改由 `11-10-split-deploy` 承接（先走本地数据层方案，后收敛为「接口正常报错 + 两端分开部署」）。
> 保留本目录仅作参考：如果哪天真要「后端仍在、但本地不启 MySQL」，这里的 H2 profile 设计可直接复用，但**不是当前方向**。

# 零依赖启动：存储解耦（H2 默认 profile）

> 任务：`11-10-zero-dep-startup`（复杂任务）。本文件只写**需求、约束、验收**；技术设计见 `design.md`，执行计划见 `implement.md`。

## 背景与问题

当前后端把 MySQL 当成硬依赖：

- `src/main/resources/application.properties` 里 `spring.jpa.hibernate.ddl-auto=update`，数据源 URL 在 `application.yml`；
- Hibernate 启动阶段就要拿连接做 schema 更新，Hikari 默认 fail-fast（`initializationFailTimeout=1`）；
- 结果：**MySQL 没起，应用直接启动失败**，哪怕只想看门户 / 用 appmarket 的只读能力。

而 appmarket 的大量能力（门户浏览、分类筛选、外部信息查询、APK 解析、增量包、加密字典）本身不写库；只有应用/版本/来源的增删改要落库。为了看一个页面而必须起一个 MySQL，成本不合理。

## 目标

`./gradlew bootRun`（或 IDE 直接 Run）在**不启动 MySQL / Redis 等任何外部服务**的前提下跑起来，门户、管理后台、appmarket 全流程可用；同时保留原有 MySQL 运行方式，一条环境变量即可切回。

## 已确认的技术事实（对仓库与运行时的探查结论）

- H2 **已经**在运行时 classpath：根 `build.gradle.kts` 有 `runtimeOnly("com.h2database:h2:2.3.232")`，`core:database` 也有；`.trellis/spec/backend/database-guidelines.md` 明确写了「H2 可用于本地 smoke run（覆盖 datasource URL），但生产是 MySQL，业务代码不得按库类型分支」。→ **本次不需要加依赖、不需要改业务代码。**
- H2 2.3.232 在 `MODE=MySQL` 下实测通过：`@Column(columnDefinition="TEXT")` 建表（→ CLOB）、`select * from showcase s where s.tags like ?`、`delete from showcase s where s.id in (...)`（带别名 DELETE 也支持）。→ `ShowcaseRepository` 的两条原生 SQL 不需要改。
- 表结构与种子数据：`AppMarketSeedLoader` / `AuthSeedLoader` 都是 `@Component`，逻辑是「`count() > 0` 就跳过，否则灌种子」→ 空库首次启动会自带可用数据，后台可登录、appmarket 有内容。
- Redis 实际未使用：`@EnableCaching` 在 `DemoApplication.kt` 是注释掉的，全仓无 `RedisTemplate` 使用代码；`MyRedisConfiguration` 只是声明一个 `RedisTemplate` bean（Lettuce 连接是懒建，不连通不报错）。→ 零依赖模式只需把缓存类型降到 `simple`，不用起 Redis。
- `AdController` / `AdRulesLoader` 以相对路径读 `data/ltt_rules.json`，与数据库无关，零依赖模式同样可用（要求 CWD 为仓库根）。
- JPA 只有一处「DB 相关」启动逻辑：两个 SeedLoader。其余模块（message / car / network / ad / application）无数据源依赖。

## 需求

- **R1 新增 `standalone` profile**：`src/main/resources/application-standalone.yml`，数据源指向嵌入式 H2 **文件库**（`./data/h2/zaze`，`MODE=MySQL`），并把驱动、方言、缓存类型全部显式覆盖，不依赖 `application.yml` 里的 MySQL 残留配置。
- **R2 默认生效**：不指定任何 profile 时自动使用 `standalone`（默认 profile 机制），使 `./gradlew bootRun` / IDE Run 开箱即跑；`SPRING_PROFILES_ACTIVE=mysql` 或 `-Dspring.profiles.active=mysql` 时回到原 MySQL 行为。
- **R3 数据持久化**：H2 用文件模式，后台增删改在重启后仍在；库文件落在 `data/h2/`，**不提交进 git**。
- **R4 零业务代码改动**：entity / repository / service / controller 一行不改；`application.yml` 不修改（其中的 MySQL 数据源即 `mysql` profile 的实现）。
- **R5 MySQL 路径不受影响**：设 `SPRING_PROFILES_ACTIVE=mysql` 后行为与现在完全一致（同样连 `localhost:3306/dev`、同样 `ddl-auto=update`）。
- **R6 使用文档** `docs/local-dev.md`：两种模式怎么起、怎么切、库文件在哪、怎么清库重来、怎么用 H2 客户端看数据、常见问题。
- **R7 验证**：按 `implement.md` 的清单逐条跑通并记录结果（含「停 MySQL 也能起」这一条）。

## 约束

- 不升级 Boot / Kotlin / Gradle / JDK 版本；不加新依赖。
- 不动前端、不动门户与后台 UI、不动 Docker/编排（那是 `10-21-docker-deploy` 的职责）。
- 不引入「按库类型分支」的业务代码（backend spec 明确禁止）；如需区分，只能靠配置。
- `application.properties` 只允许**追加**默认 profile 一行，不改动既有行。
- 不把 `data/h2/` 下的库文件、`*.trace.db` 提交进仓库。

## 验收标准

- [ ] **MySQL 未运行**（`lsof -i:3306` 无监听）时，`./gradlew bootRun` 能启动成功，日志中数据源为 H2。
- [ ] `GET /api/v1/appmarket/apps?page=1&size=5` 返回 `Response(PageResult{list,total,page,size})` 且 `total > 0`（种子数据）。
- [ ] `GET /` 返回门户页面 200；`GET /admin` 返回 200；`GET /api/v1/appmarket/apps/categories` 返回分类计数。
- [ ] 管理后台登录可用（种子管理员账号），且能新增/修改一条 appmarket 数据。
- [ ] 重启应用（不改库文件）后，上一步改的数据仍在 → 证明 H2 文件持久化生效。
- [ ] `data/h2/` 已存在于磁盘且**未**出现在 `git status` 中（.gitignore 生效）。
- [ ] `SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun` 在 MySQL 运行时仍能正常连库（回归，允许在与 MySQL 环境对齐后执行）。
- [ ] `./gradlew :feature:appmarket:compileKotlin --offline` 通过；`npm run typecheck`（`frontend/`）通过。
- [ ] `docs/local-dev.md` 覆盖：默认模式启动、切 MySQL、改库文件位置、清库、H2 客户端查看、常见问题。

## Notes

- 已确认决策（2026-11-10）：① 独立 task，不并入 `10-21-docker-deploy`；② 默认即 H2 **文件**库；③ 不做 MySQL→H2 数据迁移，空库由 SeedLoader 起种子。
- 与 `10-21-docker-deploy` 的衔接：本任务完成后，compose 里 `app` 服务需要显式设 `SPRING_PROFILES_ACTIVE=mysql` 才会连 MySQL 容器，否则会走进 H2（该调整在 docker task 的范围内做，见其 `design.md` 修订）。
