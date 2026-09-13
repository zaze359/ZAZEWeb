# Error Handling

> Current reality: zaze-server has **no global exception handler and no custom exception types**. All API responses go through the common envelope, and failures surface as HTTP 500 from Spring's default handling. Read this page to stay consistent — and to know what is deliberately still missing.

---

## Response Envelope (required)

All JSON endpoints return `Response<T>` (`core/common/.../controller/Response.java`):

```json
{ "code": 200, "data": { ... }, "msg": "请求成功" }
```

- `code` defaults to `200`; the constructor `Response(int code, T data, String msg)` is the way to return an error code + message.
- `BaseController.result()` (`core/common/.../controller/BaseController.java`) returns `Response<String>("ok")` — use it for success-with-no-payload endpoints (see `ShowcaseController.deleteShowcases`).
- Paginated endpoints return `PageResponse<T>` (`total`, `totalNotFiltered`, `rows`), matching the bootstrap-table frontend format (see `ShowcaseController.searchByTags`).

Example error shape to use when adding error returns:

```kotlin
return Response(500, null, "解析规则失败")
```

---

## Exception Behavior Today

1. **No `@ControllerAdvice` / `@ExceptionHandler` exists anywhere in the codebase.** Uncaught exceptions produce Spring Boot's default whitelabel/JSON error.
2. `LoggerAdvice` (`core/common/.../aop/LoggerAdvice.java`) logs any exception thrown from a method annotated `@LoggerManage` via `@AfterThrowing` — so controller failures *are* logged, but the client still gets the default 500 body.
3. Explicit `try/catch` is rare. Where used, exceptions are wrapped and rethrown (`MyWebSocket.onMessage`) or logged and swallowed (`OkHttpConfiguration.sslSocketFactory`).

---

## Conventions for New Code

- **Do not invent a second response format.** Return `Response(code, data, msg)` with a non-200 `code` for business errors; keep HTTP status semantics for transport errors only (`@ResponseStatus(HttpStatus.CREATED)` on creates is the existing precedent, see `ShowcaseController`).
- **There is no 404 convention.** "Not found" is signalled in the payload with `code` left at `200`: missing reads return `Response(null)` and failed mutations return `Response(false)`. See `AppMarketAdminApiController` (`Response<AppDetailVo?>`, `Response<Boolean>`). Callers must inspect `data`, not rely on `code`.
- Validate inputs at the controller boundary (current code does this manually, e.g. splitting the `ids` string in `ShowcaseController.deleteShowcases`, or trimming/required-field checks in the admin UI before POST). There is no Bean Validation in the project — do not add `javax.validation` to one endpoint silently; if a feature needs it, introduce it consistently and note it.
- If you add a global `@RestControllerAdvice`, place it in `core/common/.../controller/` so every feature gets it, and update this page.

---

## Known Gaps (do not replicate, listed for awareness)

- No global handler → stack traces/whitelabel can leak on unhandled errors.
- No error code catalog — codes are ad-hoc integers; keep `200` for success and document any new code in the endpoint's `@LoggerManage` description or the task notes.
- `AdController` caches loaded JSON in nullable fields without synchronization — a failed parse is retried on the next request with no error surface.
