# Code Reuse Thinking Guide

> **Purpose**: Stop and think before creating new code — does it already exist? Written for **zaze-server** (Kotlin/Java Spring Boot), so the "already exists" inventory below is the actual project, not generic advice.

---

## The Problem

**Duplicated code is the #1 source of inconsistency bugs.**

When you copy-paste or rewrite existing logic:
- Bug fixes don't propagate (e.g. one mapper fixes a null default, the other doesn't)
- Behavior diverges over time
- The codebase becomes harder to understand

---

## Before Writing New Code

### Step 1: Search First

```bash
# find an existing type/function
grep -rn "keyword" feature core src

# find all call sites before changing a shared value
grep -rn "value_to_change" .
# zsh note: use `grep -E "a|b"` for alternation — BSD grep does not support "\|" in BRE
```

### Step 2: Ask These Questions

| Question | If Yes... |
|----------|-----------|
| Does a similar class/function exist? | Use or extend it |
| Is this pattern used in another feature? | Follow the existing pattern (e.g. copy `feature/showcase` for a new slice) |
| Could this be a shared utility? | Put it in `core/common` (or `core/database`) — **not** in `core/*` if it is feature-specific |
| Am I copying code from another file? | **STOP** — extract to a shared place |

---

## Reuse Inventory (what this project already gives you)

Check this table before writing a new version of any of these:

| You are about to write… | Reuse instead | Where it lives |
|-------------------------|---------------|----------------|
| A REST response shape | `Response<T>` / `PageResponse<T>` envelope | `core/common/src/main/java/com/zaze/server/common/controller/` |
| Controller base / no-payload result | `BaseController.result()` | same package |
| A repository base | extend `BaseRepository<T, ID>` (never `JpaRepository` directly) | `core/database/src/main/java/com/zaze/server/database/repository/BaseRepository.java` |
| Entity id/timestamps | `BaseEntity` fields (Java extends it; Kotlin data class replicates the fields) | `core/database/.../model/BaseEntity.java` |
| Entity → API mapping | `asVo()` / `asPojo()` extension functions in the feature's `model/` | e.g. `feature/appmarket/.../model/Mappers.kt` |
| JSON parse/serialize | `JsonUtil` (`parseJson`, `objToJson`, `parseJsonToList`) + `String.jsonToList(...)` from `JsonExt.kt` (Gson) | `core/common/.../utils/JsonUtil.java`, `.../ext/JsonExt.kt` |
| Controller entry/exit logging | `@LoggerManage(description = "中文描述")` | `core/common/.../aop/LoggerManage.java` |
| Page assets + context path | Thymeleaf fragment `common/head::static` and the global `ctx` | `src/main/resources/templates/common/head.html` |
| Browser AJAX envelope handling | read `res.data` from the `{code,data,msg}` envelope; reuse the `ctx`-prefixed base URL pattern | `src/main/resources/static/js/appmarket.js` |
| A page = a new feature slice | copy the `feature/showcase` package layout (controller/service/impl/repository/pojo/vo/model) | `feature/showcase/` |
| External HTTP call | the injected `OkHttpClient` bean (`@EnableOkHttp`), not a new client | `core/network/.../OkHttpConfiguration.java` |

---

## Common Duplication Patterns

### Pattern 1: Copy-Paste Functions

**Bad**: copying a Kotlin mapper into a second service.
**Good**: keep the mapping in the feature's `model/` package and import it.

Real example to avoid: `AppMarketServiceImpl` and `AppMarketAdminServiceImpl` both assemble an `AppDetailVo` from an app + its versions + sources. The admin side re-implemented it instead of sharing the read method. When touching either, consider consolidating the assembly into one place (`model/` or a shared helper) rather than editing two copies.

### Pattern 2: Parallel Controllers

**Bad**: bolting admin endpoints onto the read-only portal controller.
**Good**: a feature exposes a **portal** controller and an **admin** controller over the same service/persistence (see `AppMarketApiController` vs `AppMarketAdminApiController`), with the read service and write service kept separate (`AppMarketService` vs `AppMarketAdminService`).

### Pattern 3: Repeated Constants

**Bad**: hardcoding a route prefix, cache name, or JSON file name in several places.
**Good**: one constant per concern (`@CacheConfig(cacheNames = ["appmarket"])` at class level; route prefix in `@RequestMapping`).

### Pattern 4: Re-implementing a Decoder / Projection

**Bad**: two consumers each parsing the same external payload (e.g. GitHub release JSON) with their own field access.
**Good**: one DTO + one parse point. `AppMarketCollector` owns the GitHub DTOs (`GitHubReleaseDto`, `GitHubAssetDto`) and is the only reader of that payload.

**Rule**: if the same untyped/raw payload is read in 2+ places, create a shared DTO/decoder before adding a third reader.

---

## When to Abstract

**Abstract when**:
- The same code appears 3+ times
- The logic is complex enough to have bugs (mapping, parsing, cascade deletes)
- Multiple features might need it (then it belongs in `core/*`)

**Don't abstract when**:
- It is used once
- It is a trivial one-liner
- The abstraction would be more complex than the duplication

---

## After Batch Modifications

When you've made similar changes to multiple files:

1. **Review**: did you catch all instances? (e.g. a new required field on `App` → also update the seed JSON, the collector DTOs, the admin form, the VO)
2. **Search**: grep for the old shape / field name to find missed spots
3. **Consider**: should this be abstracted now that it repeats?

---

## Checklist Before Commit

- [ ] Searched for existing similar code (`feature/`, `core/`)
- [ ] No copy-pasted mapper/parser/validation that should be shared
- [ ] Constants and route prefixes defined in one place
- [ ] Response envelope reused; no second response format
- [ ] New slice follows an existing feature's structure rather than inventing one
- [ ] Shared value changed in one place did not leave stale copies elsewhere
