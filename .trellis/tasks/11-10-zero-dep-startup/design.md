# 设计：零依赖启动（H2 默认 profile）

> 任务：`11-10-zero-dep-startup`。需求见 `prd.md`，执行顺序见 `implement.md`。本文件只做技术设计。

## 1. 总体思路

不动业务代码、不动已有配置文件内容，只做两件事：

1. **加一份 profile 配置**：`application-standalone.yml` 覆盖数据源为嵌入式 H2 文件库；
2. **把默认 profile 指向它**：`application.properties` 追加 `spring.profiles.default=standalone`。

`mysql` 不是一个需要新建的 profile —— 它就是「不激活 standalone 时」的现状：`application.yml` 里的 MySQL 数据源。

```
                 ┌──────────── 无 external service ────────────┐
./gradlew bootRun│                                           │
(无 profile)      │ spring.profiles.default=standalone        │
                 │   → application-standalone.yml            │
                 │     jdbc:h2:file:./data/h2/zaze;MODE=MySQL │
                 └───────────────────────────────────────────┘

                 ┌──────────── 原路径（零变化）────────────────┐
SPRING_PROFILES_  │ spring.profiles.default 被 active 覆盖      │
ACTIVE=mysql      │   → application.yml 的 MySQL 数据源        │
                 └───────────────────────────────────────────┘
```

**为什么默认 profile 而不是「让用户自己加参数」**：目标是「不起服务也能跑」，任何需要记参数、改 IDE 运行配置的方案都等于没解耦。默认 profile 是 Spring Boot 官方机制，`SPRING_PROFILES_ACTIVE` / `-Dspring.profiles.active` 天然可以覆盖，不引入魔法。

## 2. `application-standalone.yml` 内容设计

```yaml
spring:
  datasource:
    url: jdbc:h2:file:./data/h2/zaze;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
    driver-class-name: org.h2.Driver
    username: sa
    password: ""
    hikari:
      pool-name: h2-standalone
      maximum-pool-size: 4
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    properties:
      hibernate:
        dialect: org.hibernate.dialect.H2Dialect
  cache:
    type: simple
```

逐项理由：

| 配置 | 作用 |
|------|------|
| `file:./data/h2/zaze` | 文件库（不是 `mem:`），重启后数据在；相对路径与现有 `data/ltt_rules.json` 的相对路径语义一致（要求 CWD=仓库根） |
| `MODE=MySQL` | 让 H2 走 MySQL 兼容语法；已实测 TEXT 列、带别名 SELECT/DELETE 均可 |
| `DATABASE_TO_LOWER=TRUE` | 表名/列名统一小写，避免原生 SQL（`showcase`、`appmarket_app` 等全小写）与 Hibernate 生成名大小写不一致 |
| `DEFAULT_NULL_ORDERING=HIGH` | 贴近 MySQL 排序中 NULL 的位置，减少分页/排序结果差异 |
| `driver-class-name` | 显式指定，不依赖 Boot 从 URL 推断；若 `application.yml` 里已有 `com.mysql.*` 的 driver 声明，此处覆盖 |
| `database-platform` + `hibernate.dialect` | **双保险**：`application.yml` 可能硬编码了 MySQL 方言（该文件含敏感内容，读取受审批限制，无法确认），两处都写死 H2Dialect 才稳妥 |
| `cache.type: simple` | 与 Redis 彻底脱钩（`@EnableCaching` 未开启，实际无缓存；此设置只为语义清晰、避免 RedisCacheManager 被创建） |
| `hikari.maximum-pool-size: 4` | 单机文件库不需要大池，减少 H2 文件锁竞争 |

**不做的事**：不加 `AUTO_SERVER=TRUE`。它会让 H2 起一个本地 TCP 服务，语义上又变成「起了个服务」，且容器/主机名变化时容易出锁问题。需要并行查看数据时，用第 5 节的做法（停应用后用 H2 Shell 只读打开），或临时加参数——写进文档而不是写死配置。

## 3. 默认 profile 的落点

在 `src/main/resources/application.properties` **末尾追加一行**：

```properties
spring.profiles.default=standalone
```

- 选它而不是 `application.yml`：`application.yml` 含数据源凭据，读取受审批限制；`application.properties` 只需追加，不需要读取既有内容。
- 选它而不是「改 `DemoApplication.kt` 里 `setAdditionalProfiles`」：代码里判空 active 再塞 profile 属于隐式魔法，且与本项目「配置归配置、代码归代码」的风格不符；`spring.profiles.default` 是 Boot 原生语义，`SPRING_PROFILES_ACTIVE` 的优先级天然高于它。
- `.properties` 与 `.yml` 同时在 classpath 时，同名 key 以 `.properties` 为准；`spring.profiles.default` 只在 properties 里出现，无冲突。
- Spring Boot 2.4+ 的 ConfigData 会在解析 profile 之前就加载 `application.properties` 用于读取 `spring.profiles.active/default`，因此放这里生效（非推测，是 Boot 文档明确支持的用法）。

## 4. 运行时行为与数据

| 关注点 | standalone（默认） | mysql（显式） |
|--------|-------------------|---------------|
| 数据源 | `jdbc:h2:file:./data/h2/zaze` | `application.yml` 原 MySQL 配置 |
| 建表 | `ddl-auto=update`（properties 里已有，两个 profile 共用） | 同左 |
| 种子数据 | 空库首次启动由 `AppMarketSeedLoader` / `AuthSeedLoader` 灌入 | 现状不变（库里已有数据则跳过） |
| 持久化 | `data/h2/zaze.mv.db` | MySQL 数据目录 |
| Redis | 不需要 | 不需要（现状也不需要） |
| `data/ltt_rules.json` | 相对路径读取，照旧 | 同左 |

`data/h2/` 必须进 `.gitignore`（库文件 + `*.trace.db`）。注意 `data/` 目录本身是被跟踪的（`ltt_rules.json` 在里面），所以 .gitignore 只能加子路径 `data/h2/`，不能用 `data/`。

## 5. 开发者体验

- **清库重来**：停应用 → 删 `data/h2/` → 重启（SeedLoader 会重灌种子）。
- **看数据**：`java -cp <h2.jar> org.h2.tools.Shell -url "jdbc:h2:file:./data/h2/zaze;MODE=MySQL" -user sa -password ""`；H2 2.3.232 的 jar 在 Gradle 缓存里，文档给出定位命令。
- **切 MySQL**：`SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun`；IDE 里设 Active profiles=`mysql`。
- **两套数据割裂**：这是已确认的取舍（空库起步，不做迁移）；文档里一句话写明「standalone 与 mysql 是两套独立数据」。

## 6. 关键决策

| # | 决策 | 理由 |
|---|------|------|
| D1 | 用嵌入式 H2，而非「DB 不可用时降级为内存 Map 仓储」 | 后者要给 4 个 JPA 仓储写内存实现、还要处理 auth/showcase，属于重写数据层；H2 零改动就满足「不起外部服务」 |
| D2 | H2 用**文件**模式而非 `mem:` | 后台增删改要能跨重启保留，否则零依赖模式只能看不能改；已确认选文件库 |
| D3 | 默认 profile（`spring.profiles.default`）而非要求传参 | 目标就是「零操作可跑」；且 env / 系统属性可覆盖，不牺牲 MySQL 路径 |
| D4 | 只覆盖 datasource / dialect / cache，不覆盖 `ddl-auto` | `ddl-auto=update` 在 properties 里，两个 profile 共用同一套表管理策略，避免出现「H2 建表、MySQL 不建表」的分裂 |
| D5 | 不动 `application.yml` | 该文件是 MySQL 路径的实现，且含凭据；profile 覆盖已足够 |
| D6 | 不新建 `mysql` profile 文件 | `mysql` = 「不激活 standalone」的现状，多一个空 profile 文件只会制造「到底哪个生效」的困惑 |
| D7 | 不加 `AUTO_SERVER`、不引 h2 console web | 前者把 H2 变成 TCP 服务（违背目标）；后者是额外攻击面（且管理后台已有自己的入口） |
| D8 | 文档放 `docs/local-dev.md` | 与 `10-21-docker-deploy` 计划的 `docs/docker.md` 并列，不挤 README |

## 7. 风险与兼容性

| 风险 | 影响 | 缓解 |
|------|------|------|
| `application.yml` 里可能硬编码了 MySQL driver / dialect / `spring.jpa.properties.*` | standalone 下仍尝试 MySQL 方言 → 启动失败 | standalone yml 里把 driver、database-platform、`hibernate.dialect` 全部写死；如仍冲突，用 `SPRING_JPA_PROPERTIES_HIBERNATE_DIALECT` 环境变量临时压制并回写配置 |
| `application.properties` 里可能有 `spring.config.activate.on-profile` 之类的分档写法 | 追加的默认 profile 不生效 | 实现时 `grep -n "spring.profiles\|activate.on-profile"` 确认；有冲突则改到 `application.yml`（需用户授权读取） |
| H2 与 MySQL 的 `TEXT`（CLOB）类型差异 | 超长文本读写行为差异 | 已实测建表/读写可用；业务代码不做类型假设（backend spec 禁止按库分支） |
| H2 文件被两个进程同时打开 | 第二个进程启动失败（文件锁） | 文档说明「先停 bootRun 再用 Shell 看」；不启用 AUTO_SERVER |
| 相对路径 `./data/h2` 依赖 CWD | 从其他目录启动会在别处建库 | 与现有 `data/ltt_rules.json` 的行为一致；文档写明「从仓库根启动」，IDE 里检查 Working directory |
| 老数据不在 H2 里 | 门户看到的是种子数据而非自己维护的数据 | 已确认取舍；文档明示两套数据独立，需要老数据时切 `mysql` |
| `H2 2.3.232` 与 Boot 2.7 BOM 的 H2 版本不一致 | Boot 2.7 BOM 自带 2.1.214，根项目显式钉了 2.3.232 | 以根 build 的 `runtimeOnly` 为准（已验证 jar 存在且在会打进 boot jar） |

## 8. 回滚形态

改动仅 4 处，全部可逆：

1. 删 `src/main/resources/application-standalone.yml`；
2. 从 `application.properties` 删掉 `spring.profiles.default=standalone` 一行；
3. 删 `docs/local-dev.md`；
4. 删 `.gitignore` 里的 `data/h2/` 两行 + 本地删 `data/h2/` 目录。

→ 回到「必须起 MySQL」的原状，无残留。
