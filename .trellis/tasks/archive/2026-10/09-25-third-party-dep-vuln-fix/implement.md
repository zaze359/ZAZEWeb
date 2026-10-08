# 实施计划：第三方依赖漏洞修复

## 前置

- 构建必须用 JDK 11：`export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`
- 分支：`feature_app_market`（改完按项目惯例还需 cherry-pick 到 master，GitHub 按默认分支告警）

## S1 · 显式声明的依赖改版本

| 文件 | 行 | 改动 |
|---|---|---|
| `core/common/build.gradle.kts` | 19 | `gson:2.8.6` → `gson:2.13.2`（BOM 托管 2.9.1，此处必须显式写死，否则被降级） |
| `core/database/build.gradle.kts` | 23 | `h2:2.1.214` → `h2:2.3.232`（runtimeOnly，仅测试/冒烟用） |
| `core/network/build.gradle.kts` | 20 | `okhttp:4.10.0` → `okhttp:4.12.0` |
| `build.gradle.kts`（根） | 61 | `okhttp:4.10.0` → `okhttp:4.12.0` |
| `feature/appmarket/build.gradle.kts` | 35 | `okhttp:4.10.0` → `okhttp:4.12.0` |
| `feature/auth/build.gradle.kts` | 23 | `spring-security-crypto:5.3.3.RELEASE` → `5.7.14` |

> okhttp 有 **3 处**声明，必须全改，否则 Gradle 冲突解析后版本不一致。

## S2 · 传递依赖的版本覆盖（snakeyaml / logback）

在根 `build.gradle.kts` 的 `dependencies` 块内新增显式声明（附注释说明为何停在该线）：

```kotlin
    // 安全版本覆盖：snakeyaml 1.33 是 1.x 线最后一版，修 CVE-2022-25857(HIGH) 等；
    // 不能升 2.x —— Spring Boot 2.7 的 YamlPropertiesFactoryBean 依赖 1.x API。
    implementation("org.yaml:snakeyaml:1.33")
    // logback 1.2.13 是 1.2 线最后一版，修 CVE-2023-6378 / CVE-2023-6481(HIGH)；
    // 不能升 1.3+/1.5+ —— 需要 SLF4J 2.x，而 Boot 2.7 用 SLF4J 1.7.x。
    implementation("ch.qos.logback:logback-classic:1.2.13")
```

## S3 · 编译验证

```bash
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
./gradlew bootJar --console=plain
```

失败处理（按序回退）：
- 报 Kotlin 版本不兼容（okhttp 4.12 / okio 3.6 拉高 stdlib）→ okhttp 退回 4.10.0 并单独强制
  `implementation("com.squareup.okio:okio:3.4.0")`；仍失败则维持现状并在 AC5 记残留。
- 报其它解析错误 → 记录具体错误再决定。

## S4 · 依赖树核对（AC1）

```bash
./gradlew dependencies --configuration runtimeClasspath --console=plain | grep -E "gson|h2|okhttp|okio|snakeyaml|logback|spring-security"
```
确认全部解析为目标版本（snakeyaml 1.33、logback 1.2.13、okhttp 4.12.0、okio ≥ 3.4.0、h2 2.3.232、
gson 2.13.2、spring-security-crypto 5.7.14）。

## S5 · 冒烟起服（AC3 / AC4）

先确认端口无旧实例：`lsof -nP -iTCP:8081 -sTCP:LISTEN`，再用 H2 配置起服（后台 `run_in_background=true`），
确认日志出现 `Started ... seconds`：

```bash
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar --server.port=8081 \
  --spring.datasource.url='jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
  --spring.jpa.hibernate.ddl-auto=create --spring.cache.type=simple
```

回归接口（curl 一律加 `--noproxy '*'`）：门户首页、应用列表、应用详情、应用市场、登录。
若 H2 2.3.x 导致 Hibernate 建表/方言报错 → 回退 2.2.224 → 2.2.220。

## S6 · OSV 复扫（AC5）

复用既有流程：导出依赖坐标 → `POST https://api.osv.dev/v1/querybatch` → `GET /v1/vulns/{id}` 取 severity。
核对目标 CVE 是否消失，并记录残留项：
snakeyaml CVE-2022-1471（需 2.0）、logback 2025/2026 年 CVE（需 1.5.x）、
spring-security-crypto CVE-2025-22228（需 5.7.16，公开仓库无）。

## 执行结果（实测记录）

### okhttp / okio：按回退方案停在 4.10.0（AC1 偏差）

okhttp 升到 4.12.0 后编译失败，实测错误：

```
e: core/network/.../OkHttpClientUtil.kt: (19, 37): Class 'okhttp3.Request' was compiled with an
incompatible version of Kotlin. The binary version of its metadata is 1.8.0, expected version is 1.6.0.
```

原因（已从 Gradle 缓存的 pom 核实）：okhttp 4.12.0 依赖 `kotlin-stdlib-jdk8:1.8.21`（metadata 1.8.0），
而本项目 Kotlin 编译器是 1.6.21。okhttp 4.11+ 全部如此，因此 4.x 线只能停在 4.10.0。
**未单独强制 okio 3.4.0**：okio 3.4.0 同样由 Kotlin 1.9 编译，会触发同类错误。

→ **AC1 部分不满足**：okhttp = 4.10.0（计划 4.12.0）、okio = 3.0.0（计划 ≥ 3.4.0）。
残留 okio CVE-2023-3635（MODERATE）。要根治需先升 Kotlin 编译器，另立任务。

### 关键发现：子模块声明的版本会被根 project 的 BOM 覆盖

按 S1 只改子模块后，依赖树显示版本已变，但**打进 boot jar 的仍是 BOM 的旧版本**：
gson 2.9.1、h2 2.1.214、spring-security-crypto 5.7.11。

机制：根 project 的 `io.spring.dependency-management` 会把 BOM 托管版本应用到**整棵解析树
（含传递依赖）**；`overriddenByDependencies=true`（默认）只保护**本 project 直接声明**的版本，
对传递依赖无效 → 子模块声明的版本对根而言是传递依赖，被 BOM 覆盖。
子模块自身编译用它声明的版本，但打进 boot jar 的是根解析出来的版本。

→ 修正：这几项必须在**根** `build.gradle.kts` 再声明一遍（与既有 okhttp 同一套路）。
**验证手段必须以 `unzip -l build/libs/*.jar` 看到的实际版本为准，不能只看 `dependencies` 输出。**

### 实测结果

- **AC1**：gson 2.13.2 ✅ / h2 2.3.232 ✅ / snakeyaml 1.33 ✅ / logback-classic+core 1.2.13 ✅ /
  spring-security-crypto 5.7.14 ✅ / okhttp 4.10.0 ❌（计划 4.12.0）/ okio 3.0.0 ❌（计划 ≥3.4.0）
- **AC2**：`bootJar` BUILD SUCCESSFUL ✅
- **AC3**：`Started DemoApplicationKt in 8.381 seconds` ✅（H2 冒烟，端口 8082）
- **AC4**：登录 200 ✅、`/api/v1/auth/me` 200 ✅、`/api/v1/app/all` 200 ✅、
  `/api/v1/appmarket/apps` 200 ✅、`/api/v1/appmarket/apps/1` 200 ✅
  （`/center` 与 `/admin` 500 系基线遗留：templates 目录已被远程删除，与本次改动无关）
- **AC5**：OSV 复扫 118 → 108 条，修掉 10 条，零新增风险。残留 4 项：
  snakeyaml CVE-2022-1471（需 2.0，Boot 2.7 不兼容）、logback 2025/2026 年 CVE（需 1.5.x）、
  spring-security-crypto CVE-2025-22228（需 5.7.16，公开仓库无）、okio CVE-2023-3635（需 Kotlin 1.9）
- **AC6**：只改 `build.gradle.kts`（6 个文件 +35/-4），业务代码零改动 ✅

### 后续建议（本任务未做）

根目前把 snakeyaml/logback 声明成 `implementation`，但根代码并不直接使用它们（"假直接依赖"）；
且"根 + 子模块重复声明"存在静默漂移风险（改子模块忘改根 → 产物悄悄回退）。
更干净的写法是根用 `dependencyManagement { dependencies { dependency(...) } }` 或
`extra["gson.version"] = "2.13.2"` 覆盖 BOM 属性（6 个包在 BOM 里都有对应属性）。
okhttp 的 3 处显式声明例外——它高于 BOM 值且被直接编译使用，应保留。

## S7 · 提交与同步

- 只提交 `build.gradle.kts` 改动（业务代码零改动 → 确认 `git status` 无其它文件）。
- 提交后 cherry-pick 到 master 并各跑一次 `./gradlew bootJar` 验证，再 push 两条分支。
- Trellis 收尾：journal（`add_session.py --commit <work hash>`）→ `task.py archive --skip-branch-validation`。

## 回滚

`git revert` 本任务提交即可（改动集中在 build 文件，无数据迁移）。
