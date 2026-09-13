# Backend Development Guidelines

> Conventions for **zaze-server** — a Spring Boot 2.3.1 (Java 11, Kotlin 1.6.21) multi-module Gradle project.

---

## Project at a Glance

- **Runtime**: Spring Boot 2.3.1 with Web / JPA / Redis / Cache / WebSocket / Thymeleaf; Kotlin + Java mixed sources.
- **Build**: Gradle Kotlin DSL. `settings.gradle.kts` declares all modules; the root `build.gradle.kts` is the only boot jar.
- **Response envelope**: `core/common/src/main/java/com/zaze/server/common/controller/Response.java` (`{code, data, msg}`), paging variant `PageResponse.java`.
- **Logging**: `@LoggerManage` AOP (`core/common/.../aop/LoggerAdvice.java`) on controller methods; SLF4J via Lombok `@Slf4j`.
- **Database**: Spring Data JPA over MySQL (`mysql:mysql-connector-java`), schema auto-managed via `spring.jpa.hibernate.ddl-auto=update` in `application.properties`. H2 (`com.h2database:h2`, `runtimeOnly` from `core:database`) is also on the runtime classpath and is handy for local smoke runs (override the datasource URL), but production is MySQL.
- **Reference feature slice**: `feature/showcase/` — the canonical controller → service → repository → pojo/vo stack. Copy this shape for new features that persist data.
- **Fuller feature example**: `feature/appmarket/` — adds portal + admin controllers, request DTOs (`dto/`), a `CommandLineRunner` seed loader (`bootstrap/`), and an OkHttp upstream collector (`collector/`). See [directory-structure.md](./directory-structure.md).

---

## Guidelines Index

| Guide | Description |
|-------|-------------|
| [Directory Structure](./directory-structure.md) | Gradle module map, dependency rules, where new code goes |
| [Database Guidelines](./database-guidelines.md) | BaseEntity/BaseRepository, entity vs VO, mapper extensions, queries |
| [Error Handling](./error-handling.md) | Response envelope, exception behavior, known gaps |
| [Quality Guidelines](./quality-guidelines.md) | Build commands, language mixing rules, forbidden patterns, known tech debt |
| [Logging Guidelines](./logging-guidelines.md) | @LoggerManage AOP, SLF4J usage, ZLog utility |

---

**Language**: All documentation should be written in **English**.
