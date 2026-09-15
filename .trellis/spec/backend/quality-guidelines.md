# Quality Guidelines

> Practical standards for zaze-server: how to build/test, which language to use where, and the anti-patterns already present in the codebase that new code must not copy.

---

## Build & Test Commands

```bash
./gradlew build          # full build + tests
./gradlew test           # all module tests
./gradlew :feature:showcase:test   # single module
./gradlew bootRun        # run the app (port 8080, context-path /)
```

- **JDK 11 required.** Kotlin 1.6.21 / Gradle 7.5.1 target Java 11, and the managed Lombok version is too old for JDK 17. On a machine whose only JDK is 17, compilation dies with `IllegalAccessError: class lombok.javac.apt.LombokProcessor ... module jdk.compiler does not export com.sun.tools.javac.processing`. Export JDK 11 first, e.g. `export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home` (Homebrew `openjdk@11`).
- Every module's `build.gradle.kts` declares `sourceCompatibility`/`targetCompatibility = 11` **and** `tasks.withType<KotlinCompile> { kotlinOptions { jvmTarget = "11" } }`. Without it, submodules publish a variant matching the build machine's JDK (e.g. 17) and the root app — which requires 11 — fails dependency resolution with "Incompatible because this component declares a component compatible with Java 17".

- Tests use JUnit 5 (`useJUnitPlatform()`), aggregated per module via `SuiteTest` classes (`core/database/.../SuiteTest.java`, `src/test/java/com/zaze/server/SuiteTest.java`).
- Module tests needing a Spring context use the local `TestApplication` pattern (`core/database/src/test/java/com/zaze/server/database/TestApplication.kt`) with H2; network tests carry their own `core/network/src/test/resources/application.yml`.
- Maven repos: Aliyun mirrors are primary; `local.properties` can switch to a local Nexus via `useLocalMaven=true`.

---

## Language Mixing Rules

The codebase mixes Kotlin and Java. House rules:

- **New feature code: Kotlin first** (`AppController.kt`, `ShowcaseServiceImpl.kt`). Existing Java files may stay Java when touched lightly — do not rewrite Java→Kotlin opportunistically.
- Kotlin Spring classes need `kotlin("plugin.spring")` (all modules already apply it).
- Java classes use Lombok (`@Slf4j`, `@Data`, `@Builder`); Kotlin classes do **not** use Lombok — use data classes and default values. Modules combining both (e.g. `feature/showcase`) configure `kotlinLombok { lombokConfigurationFile(file("lombok.config")) }`.
- JSON: Gson is the house library (`core:common` exposes `JsonUtil.java` + `JsonExt.kt` — `String.jsonToList(...)`). Use these helpers instead of adding Jackson databind calls by hand (Jackson exists only via Spring's web starter).

---

## Required Patterns

1. All JSON endpoints return `Response<T>` / `PageResponse<T>` from `core:common` (see [error-handling.md](./error-handling.md)).
2. Entities never leave the service layer — map with `asVo()`/`asPojo()` extensions (see [database-guidelines.md](./database-guidelines.md)).
3. Controller methods get `@LoggerManage(description = "...")` (see [logging-guidelines.md](./logging-guidelines.md)).
4. Repository write queries get `@Transactional` + `@Modifying`.
5. Library/feature modules disable `bootJar` (only the root app is executable).

---

## Forbidden Patterns (and known debt — do not copy)

| Pattern | Where it exists | Why not |
|---------|-----------------|---------|
| Trust-all `X509TrustManager` + `hostnameVerifier { true }` | `core/network/.../OkHttpConfiguration.java` | Disables TLS validation. Acceptable only for this internal demo client; never copy into new network code. |
| Relative-path file reads (`File("data/ltt_rules.json")`) | `AdController.kt` | Depends on the process working directory. New file loading should resolve paths via classpath or explicit config. |
| Returning `void` / raw lists from controllers | `ShowcaseController.addShowcase` (deprecated) | Breaks the response envelope contract; deprecated endpoints already marked `@Deprecated`. |
| Unsynchronized mutable cache fields in controllers | `AdController.lttAdRules` | Not thread-safe under concurrent requests; use a service bean with proper caching (`@Cacheable`). |
| Manual pagination math on full lists | `ShowcaseServiceImpl.findByTagsLike` (in-memory `subList`) | Loads everything then slices; prefer `Pageable` passed to the repository query. |
| `@Deprecated` endpoints without replacement notes | `ShowcaseController.addShowcase` | When deprecating, point to the replacement (`/create` in this case) in the KDoc/JavaDoc. |

---

## Dependency & Version Notes

Spring Boot's dependency-management BOM (`io.spring.dependency-management`) is authoritative for managed libraries. Two traps observed in practice:

- **Transitive downgrade.** `core:network` declares `api("com.squareup.okhttp3:okhttp:4.10.0")`, and its own configuration resolves 4.10.0. But when another module inherits okhttp *transitively* through `core:network`, the BOM constraint downgrades it to **3.14.9**. Symptom: `Cannot access 'code': it is package-private in 'Response'` (OkHttp 3.x exposes `code`/`body` as package-private fields, not properties). Fix: any module that uses OkHttp directly must declare it explicitly — `implementation("com.squareup.okhttp3:okhttp:4.10.0")` — exactly as the root app and `core:network` already do. This keeps compile-time aligned with the runtime jar (which ships 4.10.0).
- **H2 version.** `core:database` declares `runtimeOnly("com.h2database:h2:2.1.214")`, but the BOM resolves the app's runtime H2 to **1.4.200**. So H2 URLs must not use 2.x-only options (e.g. `MODE=LEGACY` fails at startup). H2 is on the runtime classpath already — no need to add it for local smoke runs.

Also worth knowing: `jackson-module-kotlin` is on the classpath (auto-registered by Spring Boot), so Kotlin data classes work directly as `@RequestBody` DTOs — no need for mutable Java-style form classes.

### External HTTP Integration (OkHttp)

Patterns for calling third-party HTTP/HTML upstreams from the app (learned building the app-market external-importer):

- **Always override all four timeouts on a derived `OkHttpClient`.** `core:network`'s injected `okHttpClient` only sets `connect/read/writeTimeout` (large values) and leaves `callTimeout` unset. Overriding **only** `callTimeout` is not enough — a slow body read is still governed by `readTimeout`, so the call never times out fast. You must explicitly override `connectTimeout`, `readTimeout`, `writeTimeout`, **and** `callTimeout` on `okHttpClient.newBuilder()`.
- **Domestic app stores have no server-side name search.** Verified 2026-09-15: 应用宝 (`searchDetail.htm?kw=`) and 小米 return client-rendered home pages; 华为 needs a signed request (`rtnCode 1002`); 酷安 returns 403; OPPO/vivo/APKPure/APKMirror/APKCombo are unreachable from the sandbox. Do **not** attempt to scrape search results.
- **But some detail pages are SSR.** 应用宝 `https://sj.qq.com/appdetail/<packageName>` returns ~300KB HTML with an embedded `__NEXT_DATA__` JSON. Parse it and **recursively walk** for the object whose `pkg_name` matches the target (do not hardcode `components[i].data.itemData[j]` paths — they break on page restructures). The detail page exposes `version_name` + `update_time` + `md_5` but **no `version_code`** and **no APK direct link**.
- **Treat upstream failures as "not found".** Return `null`, no retry (retrying a timed-out upstream only stacks timeouts). Cache expensive responses in-memory with a TTL (应用宝 detail pages ~300KB; cache 30 min).

### Kotlin trap: nested block comments

Kotlin block comments **nest**. Writing an Ant-style pattern such as `/api/v1/auth/**` inside a KDoc opens a nested comment that is never closed → `Unclosed comment` compile error. In doc comments, describe path patterns without `**` (e.g. "`/api/v1/auth/` 下的接口"). `"/api/v1/auth/**"` inside a **string literal** is fine — the trap is only in comments.

---

## Code Review Checklist

- [ ] Module boundaries respected: no controllers in `core/*`, no cross-feature imports.
- [ ] Response envelope used; entities mapped to VOs.
- [ ] `@LoggerManage` on new endpoints; no secrets in logged args.
- [ ] Repository queries: `@Modifying`/`@Transactional` on writes; native query uses table name.
- [ ] New module registered in `settings.gradle.kts` with `bootJar` disabled and dependencies on `core:common` (+ `core:database` if persisting, + `core:network` if it needs an HTTP client).
- [ ] Any directly-used managed library (e.g. okhttp) is pinned to the runtime version explicitly, so the Spring BOM cannot downgrade it transitively (see Dependency & Version Notes).
- [ ] Nested data uses flat FK columns with manual cascade delete, not JPA relationships; re-ingestion paths are idempotent (find-then-insert).
- [ ] Tests: JUnit 5, aggregated in the module's `SuiteTest` when adding test classes.
- [ ] KDoc/Javadoc contains no `**` path patterns (Kotlin block comments nest → `Unclosed comment`).
- [ ] No business logic branches on the database vendor; no new JSON libraries beyond the Gson helpers.
