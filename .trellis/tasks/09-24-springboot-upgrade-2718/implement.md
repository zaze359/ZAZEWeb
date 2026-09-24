# 执行计划：Spring Boot 2.3.1 → 2.7.18

## 步骤

- [ ] **S1 改版本**：根 `build.gradle.kts` 中 Spring Boot 插件 `2.3.1.RELEASE` → `2.7.18`（唯一一处版本声明）
- [ ] **S2 编译**：`JAVA_HOME=<jdk11> ./gradlew bootJar`，确认产物 `build/libs/zaze-server-0.0.1-SNAPSHOT.jar` 时间戳更新
      - 编译失败则逐条分析报错（预期来源：Kotlin 版本、Lombok 注解处理、依赖缺失），不盲目加配置
- [ ] **S3 依赖核实**：`./gradlew dependencies --configuration runtimeClasspath`，与 `design.md` 第 2 节逐行核对实际解析版本
- [ ] **S4 启动验证**：起 H2 冒烟实例（换空闲端口，先 `lsof` 确认），确认日志 `Started ... seconds`，**重点看有无循环依赖 / schema 报错**
- [ ] **S5 业务回归**：按 PRD AC4 六条逐项 curl 验证（登录 / 门户页 / 管理页 / 按名搜索 / 精确查询 / 导入链路 + SSE）
- [ ] **S6 记录遗留**：若启用了任何兼容开关（如 `allow-circular-references`、`ant_path_matcher`），在 PRD Notes 与提交信息中标注为技术债
- [ ] **S7 提交**：`fix(deps): Spring Boot 2.3.1 升级至 2.7.18 修复依赖漏洞` —— 提交前先 `git branch --show-current` 确认分支

## 关键约束

- **JDK 11 构建**，不得切到 JDK 17
- 不改 Kotlin 版本、不动业务逻辑
- 任何兼容开关的引入都要有明确原因，默认目标是**零开关**

## 已知坑（沿用项目历史教训）

- 构建前必须 `export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home`
- 冒烟 curl 一律加 `--noproxy '*'` 直连 localhost
- 测登录态要「一次性登录并写 cookie jar」——同一脚本里登录两次会因会话已存在而不再下发 Set-Cookie，导致后续请求全 401 的假象
- 起服前先 `lsof -nP -iTCP:<port> -sTCP:LISTEN` 确认端口归属，旧实例占端口会造成「改了代码却仍是旧行为」的假象
- 后台起服务必须用后台执行（前台 `nohup &` 会随 shell 返回被回收）
