# 技术设计：Spring Boot 2.3.1 → 2.7.18

## 1. 核心改动

只有一处版本声明，其余由 Spring Boot 依赖管理带动：

```kotlin
// build.gradle.kts（根）
plugins {
-    id("org.springframework.boot") version "2.3.1.RELEASE"
+    id("org.springframework.boot") version "2.7.18"
     id("io.spring.dependency-management") version "1.0.15.RELEASE"
     kotlin("jvm") version "1.6.21"
     ...
}
```

`io.spring.dependency-management` 保持在 `1.0.15.RELEASE`（兼容 2.7.x，不引入额外的插件升级变量）。

显式声明的依赖不受影响：`com.squareup.okhttp3:okhttp:4.10.0`（绕开了被降级到 3.14.9 的历史坑）。

## 2. 版本跃迁对照

| 依赖 | 升级前 | 升级后 | 由谁决定 |
|---|---|---|---|
| Spring Boot | 2.3.1.RELEASE | 2.7.18 | 显式 |
| Spring Framework | 5.2.7 | 5.3.31 | 依赖管理 |
| Tomcat embed | 9.0.36 | 9.0.83 | 依赖管理 |
| Jackson | 2.11.0 | 2.13.5 | 依赖管理 |
| Hibernate | 5.4.17 | 5.6.15.Final | 依赖管理 |
| MySQL Connector/J | 8.0.20 | 8.0.33 | 依赖管理 |
| Logback | 1.2.3 | 1.2.12 | 依赖管理 |
| Lombok | (随 BOM) | 1.18.24 | 依赖管理 |

升级后实际版本一律以 `./gradlew dependencies --configuration runtimeClasspath` 输出为准，不靠记忆推断。

## 3. 破坏点检查表

### 3.1 Bean 循环引用（2.6+ 默认禁止）— 高风险，已降级为低风险

Spring Boot 2.6 起 `spring.main.allow-circular-references` 默认 `false`，构造器/字段循环会导致启动直接失败。

**前置排查结论（已完成）**：
- 全仓 **12 处 `@Autowired`**，均为 Controller → Service 的字段注入
- **0 处 `@DependsOn`**
- 无发现 Service ↔ Service 互相注入的结构

→ 判定为**低风险**。但仍以实际启动为最终结论。

**原则**：目标是不启用逃生开关。若启动确实报 `The dependencies of some of the beans form a cycle`，
优先**拆解注入关系**；只有在拆解代价明显超出本次升级范围时，才退而求其次加
`spring.main.allow-circular-references=true`，并在 PRD Notes 与提交信息里显式标注为技术债。

### 3.2 MVC Path Matching（2.6+ 默认 `PathPatternParser`）

**前置排查结论**：全仓**没有**任何 `WebMvcConfigurer` / `WebMvcConfigurationSupport` / `addInterceptors` 注册类。
→ 无自定义路径匹配，**影响面为零**。

保守起见（防止未知第三方自动配置受影响），可在验证阶段视情况显式声明
`spring.mvc.pathmatch.matching-strategy=ant_path_matcher` 保持旧行为；
**默认不加**，仅在出现路径相关异常时再引入并说明原因。

### 3.3 双配置文件共存

`application.yml` 与 `application.properties` 同时存在，`application.properties` 优先级更高。
Spring Boot 2.4+ 对多文档文件与 `spring.profiles` 位置有约束，但**本项目的配置均为平铺 key**（无 `---` 多文档），
因此不受这条变更影响。验证阶段以「启动日志 + Actuator」确认实际生效值。

### 3.4 Redis 配置属性

若 yml 中使用 `spring.redis.*`（Spring Boot 2.0 起 deprecated），2.7 仍**支持**但会打警告。
升级后启动日志若出现 `spring.redis` 弃用提示，再改为 `spring.data.redis.*`。属可选优化，不阻塞验收。

### 3.5 Hibernate 5.4 → 5.6

JPA API 兼容，`spring.jpa.hibernate.ddl-auto` 语义不变。验证点：H2 冒烟启动时无 schema 报错、
实体映射无兼容性异常。

### 3.6 构建工具链（硬约束）

**必须用 JDK 11**：
```bash
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
./gradlew bootJar
```
默认 JDK 是 17（JBR），Lombok 1.18.24 在高版本 JDK 上会报
`IllegalAccessError: lombok.javac.apt.LombokProcessor ... jdk.compiler does not export`。

### 3.7 MySQL 依赖坐标变更（**实际踩到，编译直接失败**）

Spring Boot 2.7 的 BOM 把 MySQL 的坐标从 `mysql:mysql-connector-java` 换成了 **`com.mysql:mysql-connector-j`**
（对应 MySQL Connector/J 8.0.31+ 官方坐标变更）。旧坐标不再有托管版本，构建报：

```
Could not find mysql:mysql-connector-java:.
```

**修复**：`core/database/build.gradle.kts` 中改为 `api("com.mysql:mysql-connector-j")`，
版本继续由 BOM 管理（2.7.18 → 8.0.33），不写死版本号。

> 排查手法：直接读 BOM 原文确认托管坐标
> `curl https://repo1.maven.org/maven2/org/springframework/boot/spring-boot-dependencies/2.7.18/spring-boot-dependencies-2.7.18.pom | grep -A3 mysql`

### 3.8 插件版本必须在所有模块统一

根与 **9 个子模块各自独立声明**了 `org.springframework.boot` 插件版本，只改根会报：

```
The request for this plugin could not be satisfied because the plugin is already
on the classpath with a different version (2.7.18).
```

**修复**：10 处 `build.gradle.kts` 全部统一为 `2.7.18`（用脚本批量替换，避免漏改）。

## 4. 验证方案

分三步，**任何一步失败即停止并回滚分析**，不继续往下盲改：

1. **编译**：`./gradlew bootJar`（JDK 11）→ 确认产物时间戳更新
2. **依赖核实**：`./gradlew dependencies --configuration runtimeClasspath | grep -E "spring-core|tomcat-embed-core|mysql-connector"` 
   → 与第 2 节表格逐行核对
3. **启动 + 业务回归**：起 H2 冒烟实例（换空闲端口，先 `lsof` 确认没人占），按 PRD AC4 逐条 curl 验证

冒烟命令沿用既有约定（`--noproxy '*'` 直连 localhost、一次性登录写 cookie jar，避免「会话已存在不下发 Set-Cookie」的假象）。

## 5. 回滚

改动集中在根 `build.gradle.kts` 一行版本号 + 可能的少量兼容性修补，`git revert` 即可回到 2.3.1。
无数据迁移、无破坏性 schema 变更。
