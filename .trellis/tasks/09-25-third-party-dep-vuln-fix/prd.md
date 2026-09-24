# 升级第三方依赖修复已知漏洞（gson / h2 / okhttp / snakeyaml / logback / spring-security-crypto）

## Goal

OSV 扫描发现依赖树 129 条漏洞；Spring Boot 2.7 自身已 EOL 无法修，本任务只处理可不升 Boot
单独升级的第三方库，把能修的修掉，并保证编译通过、应用可起、接口回归正常。

## 背景

Spring Boot 已从 2.3.1 升到 2.7.18（任务 09-24-springboot-upgrade-2718）。随后用 OSV 漏洞库对运行时
依赖树（116 个包）做批量扫描，得到 **129 条命中**（10 CRITICAL / 45 HIGH / 47 MODERATE / 14 LOW），
远超 GitHub Dependabot 报的 "1 moderate"（Dependabot 只扫 manifest 且有滞后）。

根因是 **Spring Boot 2.7 线与 Spring Framework 5.3.x 已经 EOL**：2026 年的新 CVE
（CVE-2026-4184x / CVE-2026-4185x、BIT-tomcat-2026-*、Thymeleaf CVE-2026-40477/40478 等）修复版本
只存在于 6.x/7.x，老版本的受影响区间永远命中。**在 2.7 内升级无法解决这一类。**

## Requirements（非目标）

- 不升级 Spring Boot / Spring Framework / Tomcat / Thymeleaf / Jackson / Netty 自身
  （EOL，需 Boot 3.x + JDK 17 才能根治，另立任务评估）。
- 不做 javax → jakarta 迁移。
- 不改动任何业务代码（除非升级导致 API 不兼容必须适配，需在 implement.md 记录）。

## 依赖现状与目标版本

| 依赖 | 当前 | BOM 2.7.18 托管 | 目标 | 修掉的漏洞 |
|---|---|---|---|---|
| `com.google.code.gson:gson` | 2.8.6（显式写死，反低于 BOM 的 2.9.1） | 2.9.1 | **2.13.2** | CVE-2022-25647 (HIGH) |
| `com.h2database:h2` | 2.1.214 | 2.1.214 | **2.3.232** | CVE-2022-45868 (HIGH) |
| `com.squareup.okhttp3:okhttp` | 4.10.0 | 4.9.3 | **4.12.0** | 4.x 线最后稳定版 |
| `com.squareup.okio:okio` | 3.0.0（随 okhttp 传递） | — | **3.6.0+**（随 okhttp 4.12.0） | CVE-2023-3635 (MODERATE) |
| `org.yaml:snakeyaml` | 1.30（传递） | 1.30 | **1.33** | CVE-2022-25857 (HIGH)、CVE-2022-38749/38750/38751/38752、CVE-2022-41854 (MODERATE) |
| `ch.qos.logback:logback-classic/core` | 1.2.12（传递） | 1.2.12 | **1.2.13** | CVE-2023-6378、CVE-2023-6481 (HIGH) |
| `org.springframework.security:spring-security-crypto` | 5.3.3.RELEASE（显式写死） | 5.7.11 | **5.7.14** | 5.7 线最新（见下方残留说明） |

### 兼容性硬约束（已核实）

- **snakeyaml 只能停在 1.x**（1.33 是 1.x 最后一版）。Spring Boot 2.7 的 `YamlPropertiesFactoryBean`
  依赖 1.x API，升 2.x 会破坏配置加载。
- **logback 只能停在 1.2.x**（1.2.13 是 1.2 线最后一版）。1.3+ / 1.5+ 需要 SLF4J 2.x，
  而 Boot 2.7 用的是 SLF4J 1.7.x。
- **okhttp 停在 4.x**（4.12.0 是 4.x 最后一版）。5.x 是 Kotlin 重写的新线，与本项目 Kotlin 1.6.21
  组合风险高，不在本任务范围。
- **spring-security-crypto CVE-2025-22228（HIGH）无法在 5.7 线修复**：OSV 给出的修复版本是 5.7.16，
  但 Maven Central 上 5.7.x 开源线**最高只到 5.7.14**（5.7.16 属商业支持版本，公开仓库没有）。
  修复版本 5.8.18 / 6.x 又与 Boot 2.7 不兼容。→ 本任务升到 5.7.14 收窄差距，该 CVE **残留并记录**。

## Acceptance Criteria

- [ ] **AC1** 依赖树解析后：gson ≥ 2.13、h2 ≥ 2.2.220、okhttp = 4.12.0、okio ≥ 3.4.0、
      snakeyaml = 1.33、logback = 1.2.13、spring-security-crypto = 5.7.14。
- [ ] **AC2** `./gradlew bootJar` 在 JDK 11 下 BUILD SUCCESSFUL，产物 jar 正常生成。
- [ ] **AC3** 应用能以 H2 冒烟配置正常启动（日志出现 `Started ... seconds`，无 Bean 循环/类加载报错）。
- [ ] **AC4** 关键接口回归通过（门户首页、应用列表、应用详情、应用市场、登录）。
- [ ] **AC5** OSV 复扫：上述 6 个包对应的目标 CVE 不再命中；仍命中条目有明确文字记录
      （snakeyaml CVE-2022-1471、logback 2025/2026 年 CVE、spring-security-crypto CVE-2025-22228）。
- [ ] **AC6** 改动只涉及 `build.gradle.kts`（gson/h2/okhttp 显式版本 + snakeyaml/logback 版本覆盖
      + spring-security-crypto 版本），业务代码零改动。

## 风险与回滚

- okhttp 4.12.0 / okio 3.6.0 是 Kotlin 库，可能拉高 `kotlin-stdlib` 传递版本，与 Kotlin 1.6.21
  编译器组合有风险。若编译或启动报 "incompatible Kotlin version"，回退到 okhttp 4.10.0 +
  单独强制 okio 3.4.0；仍不行则维持现状并在 AC5 记录残留。
- h2 升到 2.3.x 可能影响 Hibernate `H2Dialect` + `ddl-auto=create`。若冒烟启动报方言/建表错误，
  逐级回退到 2.2.224 → 2.2.220。
- 回滚方式：`git revert` 本任务提交（改动集中在 build 文件，无数据迁移）。
