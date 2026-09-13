# Logging Guidelines

> Two logging mechanisms coexist: the `@LoggerManage` AOP layer for controller entry/exit, and SLF4J (via Lombok `@Slf4j` or explicit `LoggerFactory`) everywhere else. A custom `ZLog` utility exists in `core/common` but is mostly legacy.

---

## Controller Layer: `@LoggerManage` (required on API endpoints)

Defined in `core/common/.../aop/LoggerManage.java`, handled by `LoggerAdvice.java` (pointcut: `within(com.zaze..*) && @annotation(loggerManage)`).

Annotate every controller method with a human-readable Chinese description — this is the established style (`ShowcaseController`, `AppController`, `AdController`):

```kotlin
@GetMapping("/all")
@ResponseBody
@LoggerManage(description = "获取所有应用信息")
fun getAllApps(): Response<List<AppVo>> { ... }
```

What the aspect logs automatically:
- **Before**: method signature + all parameters.
- **AfterReturning**: description + result object.
- **AfterThrowing**: description + exception (at `error` level).

Consequence: parameters and return values are serialized into logs — do not use `@LoggerManage` on endpoints that receive or return secrets/credentials.

---

## General SLF4J Usage

- Kotlin/Java classes use Lombok `@Slf4j` (`MyWebSocket.java`) or a protected logger field (`ShowcaseController.java`):
  ```java
  protected Logger logger = LoggerFactory.getLogger(this.getClass());
  ```
- Use parameterized messages, not string concatenation: `log.info("onOpen: {}, {}", uid, online.get())`.
- Level usage in this codebase (follow it):
  - `info` — request lifecycle, business milestones.
  - `error` — exceptions and connection events (existing WebSocket code logs lifecycle at `error`; keep exceptions at `error`, prefer `warn`/`info` for new non-failure events).
  - `debug` — diagnostics; used by `ZLog.d`.
- **Partial-failure loops log at `warn` and continue.** `AppMarketCollector` wraps each ingest target in try/catch, does `log.warn("采集失败 target={}", target.repo, e)`, records a human-readable message, and moves on — one bad upstream must not abort the batch. Use parameterized placeholders, not concatenation (Kotlin uses `LoggerFactory.getLogger(...)` since Kotlin classes do not use Lombok).

---

## `ZLog` Utility (`core/common/.../log/ZLog.java`)

Static logger facade with level gating (`setLogLevel`), caller-stack tagging (`setNeedStack`), and a pluggable `ZLogFace`. API: `ZLog.i(tag, message)` etc.

Rules:
- Do **not** introduce `ZLog` into new feature code — use SLF4J. It exists for utility classes that predate the Spring wiring (`AdRulesLoader` comments reference it).
- Known bug: `ZLog.closeAlwaysPrint()` sets `alwaysPrint = true` instead of `false`. If you touch `ZLog`, fix or note it — do not rely on `closeAlwaysPrint` behavior.

---

## What NOT to Log

- Passwords, tokens, secrets — also avoid `@LoggerManage` on such endpoints entirely (the aspect dumps all arguments).
- Large payloads: `LoggerAdvice.parseParams` skips only when there are more than 1024 params; big `@RequestBody` objects will be fully stringified — for heavy endpoints, prefer a short custom `log.info` instead of relying on the aspect.
