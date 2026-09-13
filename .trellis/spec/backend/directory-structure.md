# Directory Structure

> How zaze-server code is organized. Module list is defined in `settings.gradle.kts`.

---

## Module Map

```
zaze-server (root = the single Spring Boot app, package com.zaze.server)
├── src/main/java/com/zaze/server/      # Application entry + cross-feature pages
│   ├── DemoApplication.kt              # @SpringBootApplication + @EnableOkHttp + @EnableJpaRepositories
│   ├── controller/                     # IndexController (Thymeleaf), AdminController (Thymeleaf pages)
│   └── config/                         # MyRedisConfiguration
├── src/main/resources/
│   ├── application.yml                 # server + datasource URL + ok.http.* config
│   ├── application.properties          # ddl-auto=update, spring.cache.*, actuator exposure (application.bak is an inert backup)
│   ├── templates/                      # Thymeleaf templates (pages + admin/ + appmarket/)
│   └── static/                         # css/js/images; vendored bootstrap + jquery under static/vendor/
├── core/                               # Reusable libraries — NO feature controllers
│   ├── common/                         # Response/PageResponse, BaseController, AOP logging, ZLog, JSON + file utils
│   ├── database/                       # BaseEntity, BaseRepository, shared entities (Roles, Item, Props)
│   └── network/                        # OkHttp module: @EnableOkHttp annotation, OkHttpConfiguration, HttpUtil
├── feature/                            # Business features, each self-contained
│   ├── showcase/                       # FULL reference slice: controller/service/impl/repository/pojo/vo/model
│   ├── application/                    # Controller-only slice (api/v1/app) with vo
│   ├── ad/                             # Controller + JSON rule loader (reads data/*.json)
│   ├── message/                        # WebSocket: @ServerEndpoint /ws/{uid}, Message encoder/decoder, WebSocketConfig
│   └── appmarket/                      # 应用市场：pojo/vo/model/service/repository/controller + dto/ + collector/ + bootstrap/；
│                                       #   门户页 /appmarket 与管理后台 /appmarket/admin；种子与采集目标 JSON 在 resources/data/
└── data/                               # Runtime JSON data files (ltt_rules.json, zaze_rules.json)
```

Dependency direction: `feature/*` → `core/database` + `core/common` → (root app depends on all). `core/network` is standalone and wired via the `@EnableOkHttp` import annotation on the root app; a feature that needs the OkHttp client (e.g. `feature/appmarket`) depends on `project(":core:network")` **and** re-declares `implementation("com.squareup.okhttp3:okhttp:4.10.0")` to avoid the BOM transitive downgrade (see [quality-guidelines.md](./quality-guidelines.md)).

---

## Module Rules

1. **Every library/feature module must disable bootJar** — only the root produces an executable jar:
   ```kotlin
   tasks.bootJar { enabled = false }
   tasks.jar { enabled = true }
   ```
2. **New feature module checklist**:
   - Add `include(":feature:<name>")` in `settings.gradle.kts`.
   - Depend on `project(":core:common")` (and `core:database` if it persists data).
   - Put code under a module's `com/zaze/server/feature/<name>/` package — the Kotlin Gradle plugin registers **both** the module's src/main/java and src/main/kotlin source roots, so either works. Existing modules (incl. Kotlin files like `ShowcaseService.kt`) live under src/main/java; `feature/appmarket` is the one module that uses src/main/kotlin. Match the module you are extending rather than moving files.
   - The root app scans `com.zaze.server` (`DemoApplication.kt` `scanBasePackages`), so any package under it is picked up automatically — do not add extra `@ComponentScan`.
3. **core/* modules must stay feature-free.** Controllers, endpoints, and business flows belong in `feature/*` or the root app.
4. **Feature package layout** (copy from `feature/showcase`):
   ```
   feature/<name>/src/main/java/com/zaze/server/feature/<name>/
   ├── controller/   # @RestController, returns Response<T> / PageResponse<T>; page controllers return view names
   ├── service/      # interface
   ├── service/impl/ # @Service implementation, caching annotations live here
   ├── repository/   # Spring Data JPA interfaces extending BaseRepository
   ├── pojo/         # JPA entities (table mapping)
   ├── vo/           # API-facing DTOs
   ├── model/        # asVo()/asPojo() mapper extension functions
   ├── dto/          # (optional) request/form DTOs for admin or mutating APIs (see appmarket)
   ├── collector/    # (optional) upstream fetch/ingest services (see appmarket's GitHub collector)
   └── bootstrap/    # (optional) CommandLineRunner seed loaders (see appmarket)
   ```
   A feature may expose **two controllers**: a read-only portal controller (`/appmarket`) and a separate admin controller (`/appmarket/admin` + `/api/v1/<domain>/admin/*`). Keep read and write services split (`XxxService` vs `XxxAdminService`).

---

## Naming Conventions

- Packages: root is always `com.zaze.server...` even inside feature modules (required for the shared component scan).
- JPA entity classes go in `pojo/` (Kotlin data class) named after the table concept (`Showcase`, `Roles`); API DTOs go in `vo/` with a `Vo` suffix (`ShowcaseVo`, `AppVo`).
- Repository interfaces end in `Repository`, service interfaces in `Service`, implementations in `ServiceImpl`.
- Controller routes: REST APIs use `api/v1/<domain>/...` (`AppController`, `AdController`) or plain domain paths (`/showcase`); page routes use `@Controller` + view names (`AdminController`).

---

## Examples

- Full persisting slice: `feature/showcase/` (also shows caching via `@Cacheable`/`@CacheEvict`).
- Lightweight read-only API: `feature/application/`.
- WebSocket feature: `feature/message/` (encoder/decoder pair + `@ServerEndpoint`).
- Full product with portal + admin + upstream ingestion: `feature/appmarket/` — read-only portal API (`AppMarketApiController`) and page (`AppMarketPageController`), admin API/page (`AppMarketAdmin*Controller`), seed loader (`bootstrap/AppMarketSeedLoader`), upstream collector using OkHttp (`collector/AppMarketCollector` + `resources/data/appmarket_collect_targets.json`), form DTOs in `dto/`.
