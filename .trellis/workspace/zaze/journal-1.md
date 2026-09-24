# Journal - zaze (Part 1)

> AI development session journal
> Started: 2026-09-12

---



## Session 1: 应用市场导入外部资源：SSE 追踪 / 分源探测 / 应用宝按名搜索 / 国内外源分组
<!-- trellis-session: v=2 fp=5745f21bf321c4d6 -->

**Date**: 2026-09-23
**Task**: 应用市场导入外部资源：SSE 追踪 / 分源探测 / 应用宝按名搜索 / 国内外源分组
**Branch**: `master`

### Summary

导入链路改为两段式 SSE 实时推送（ThreadLocal 埋点 ImportTracer）；各上游改为各自计时各自超时并返回逐源状态明细；应用宝按名搜索接入官方接口 dc_pcyyb_official（推翻此前「客户端渲染抓不到」的误判），候选去重改为合并补齐；上游按可达性拆分为国内组/国外备选组，国内未命中才降级查国外（搜索耗时 15s→1s）；前端候选与状态明细按每个上游源分区展示。同时调研 apps.microsoft.com 并否决接入（只有 Windows Store ProductId，无 Android 包名）。

### Git Commits

| Hash | Message |
|------|---------|
| `2975c2c` | feat(appmarket): 导入外部资源链路增强 |

### Status

[OK] **Completed**


## Session 2: Spring Boot 2.3.1 升级至 2.7.18 修复依赖漏洞
<!-- trellis-session: v=2 fp=8dbc600c02f5cbdc -->

**Date**: 2026-09-25
**Task**: Spring Boot 2.3.1 升级至 2.7.18 修复依赖漏洞
**Branch**: `feature_app_market`

### Summary

将 Spring Boot 从 2.3.1.RELEASE 升级到 2.7.18，修复 Spring4Shell 等依赖 CVE；同步处理 MySQL 驱动坐标变更与多模块插件版本一致性。

### Main Changes

- 根项目 + 9 个子模块的 org.springframework.boot 插件版本统一为 2.7.18（只改根项目会报 plugin already on the classpath with a different version）
- MySQL 驱动坐标 mysql:mysql-connector-java 改为 com.mysql:mysql-connector-j（2.7 起 BOM 中旧坐标不再有托管版本）
- 核实升级后关键依赖版本：Spring 5.3.31 / Tomcat 9.0.83 / Jackson 2.13.5 / Hibernate 5.6.15.Final / Logback 1.2.12 / MySQL 8.0.33

### Git Commits

| Hash | Message |
|------|---------|
| `15758c3` | fix(deps): Spring Boot 2.3.1 升级至 2.7.18 修复依赖漏洞 |

### Testing

- [OK] JDK 11 + ./gradlew bootJar 编译通过，产物 build/libs/zaze-server-0.0.1-SNAPSHOT.jar 正常生成
- [OK] 应用启动无循环依赖报错（Spring Boot 2.6+ 默认禁止 Bean 循环引用）
- [OK] AC4 六项接口回归通过（门户首页 / 应用列表 / 详情 / 应用市场 / 登录 / 管理后台导入页）
- [OK] 与 stash 回退到 2.3.1 做 A/B 对比，确认 /admin 500 与 /appmarket/123 404 是远程基线自身问题，非本次升级引入

### Status

[OK] **Completed**

### Next Steps

- 可选：修复远程基线遗留的 Thymeleaf 模板问题（admin/showcase 模板已删但控制器残留导致 /admin 500）
- 可选：评估应用市场按名搜索时国外源降级触发条件（应用宝模糊匹配恒返回 3 条，使「国内未命中」难成立）
