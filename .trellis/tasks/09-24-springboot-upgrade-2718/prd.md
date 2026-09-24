# Spring Boot 2.3.1 升级至 2.7.18（修复依赖漏洞）

## Goal

起因：GitHub 在推送后提示默认分支存在 1 个 moderate 依赖漏洞（Dependabot #26）。该告警页匿名访问返回 404，
本机无 `gh` CLI，改用 GitHub Advisory 公开 API 按依赖版本反查，发现**问题不止那一条**：
项目根依赖 Spring Boot 2.3.1.RELEASE（2020 年，已过维护期）实际命中 CVE-2022-22965 **Spring4Shell（critical）**、
CVE-2022-22970（high）、CVE-2021-22118（high）及多条 medium。

目标：升级到 **Spring Boot 2.7.18**（最后一个支持 JDK 11 的 2.x），连带把 Spring Framework 5.3.31 / Tomcat 9.0.83 /
Jackson 2.13.5 / Hibernate 5.6.15 / MySQL Connector 8.0.33 一并跃迁到受维护版本，一次性清除已知 CVE。

## Requirements

### R1 版本跃迁
根 `build.gradle.kts` 的 Spring Boot 插件版本 `2.3.1.RELEASE` → `2.7.18`，其余依赖版本交由依赖管理自动带动。

### R2 构建约束（硬性）
- **必须使用 JDK 11 构建**（Kotlin 1.6.21 + Lombok 1.18.24 的组合不支持更高版本；默认 JDK 17 会报
  `IllegalAccessError: lombok.javac.apt.LombokProcessor`）
- **不升级 Kotlin**（保持 1.6.21，恰好是 Spring Boot 2.7.x 管理的版本）
- **不升级到 Spring Boot 3.x**（需 JDK 17，与上述约束冲突）

### R3 兼容性修补
必须处理以下已知升级破坏点（详见 `design.md`）：
- Spring Boot **2.6+ 默认禁止 Bean 循环引用** —— 先全仓排查循环依赖；目标是不留 `spring.main.allow-circular-references` 逃生开关
- `application.yml` 与 `application.properties` **双文件共存**（properties 优先），2.4+ 对多文档/Profile 处理有变更
- Hibernate 5.4 → 5.6 的 SQL 方言与 `ddl-auto` 行为差异

### R4 业务零改动
不重构、不顺带改无关功能，应用市场的业务逻辑与对外行为保持不变（含 SSE 追踪、分源探测、国内外分组、应用宝按名搜索）。

## Acceptance Criteria

- [ ] **AC1 构建**：`JAVA_HOME=<jdk11> ./gradlew bootJar` 成功，`build/libs/zaze-server-0.0.1-SNAPSHOT.jar` 正常产出
- [ ] **AC2 依赖核实**：`./gradlew dependencies --configuration runtimeClasspath` 显示 Spring Framework **5.3.31**、
      Tomcat **9.0.83**、mysql-connector **8.0.33**（或 2.7.18 管理的更高安全版本）
- [ ] **AC3 启动**：H2 冒烟实例正常起来，日志出现 `Started ... seconds`；**无循环依赖报错、无 schema 报错**
- [ ] **AC4 核心链路回归**（逐条冒烟确认）：
      - [ ] `POST /api/v1/auth/login`（admin/admin123）返回 200 并下发会话
      - [ ] 门户页 `/appmarket` 返回 200
      - [ ] 管理页 `/appmarket/admin` 返回 200
      - [ ] `GET /api/v1/appmarket/admin/external-search?keyword=微信` 返回应用宝候选、带版本与图标
      - [ ] `GET /api/v1/appmarket/admin/external-lookup?packageName=cn.gov.tax.its` 命中并返回预览
      - [ ] 一键导入链路可用（含 SSE `/import-tasks/{id}/stream`）
- [ ] **AC5 无退化**：各端点响应结构与耗时量级与升级前一致，不引入新报错
- [ ] **AC6 Actuator**：`/actuator/health` 可达，与 `management.endpoints.web.exposure.include` 现状一致

## Notes

- Dependabot #26 的确切条目仍待仓库主在 GitHub 登录后确认
  （`https://github.com/zaze359/ZAZEWeb/security/dependabot/26`）。**不影响本任务范围** ——
  无论那一条具体是哪个，上述 medium 清单与 critical/high 都指向同一根因（Spring Boot 2.3.1 过旧），升级即覆盖。
- 可复用的排查手法：GitHub Advisory 公开 API 匿名可用
  `curl -H 'Accept: application/vnd.github+json' "https://api.github.com/advisories?ecosystem=maven&affects=<group>%3A<artifact>&severity=medium"`
- 回滚成本低：改动集中在根 `build.gradle.kts` 一行版本号 + 必要兼容性修补，`git revert` 即可回到 2.3.1，
  无数据迁移、无破坏性 schema 变更。
