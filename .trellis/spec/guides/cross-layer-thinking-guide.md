# Cross-Layer Thinking Guide

> **Purpose**: Think through data flow across layers before implementing. Grounded in **zaze-server**'s actual chain (Spring Boot + Thymeleaf + JPA).

---

## The Problem

**Most bugs happen at layer boundaries**, not within layers.

Common cross-layer bugs in this project:
- Controller returns `Response<T>` but the page JS reads `res` instead of `res.data`
- Entity is returned directly and lazy/relationship fields blow up during serialization
- A date is a `Date` in the entity, epoch millis in the VO, and the mapper forgets one of them
- A new entity column is added but the MySQL table is never altered (no migration tool)

---

## This Project's Layers

```
Browser (Thymeleaf page + jQuery)         ← static/js/*.js, templates/*.html
        │  HTTP JSON  {code,data,msg}
Controller  (@RestController / @Controller)   → returns Response<T> / view name
        │  method call (entities must NOT cross this line as DTOs)
Service (interface) → ServiceImpl (@Service)   → @Transactional / @Cacheable / @CacheEvict
        │  repository call
Repository (extends BaseRepository)            → derived queries / @Query
        │  JPA
Entity (pojo/, @Entity, flat FK columns)
        │  mapped by asVo()/asPojo() in model/
VO (vo/, API-facing)
```

Two **entry surfaces** per product feature: a read-only **portal** (`/appmarket`, `/api/v1/appmarket/*`) and a separate **admin** surface (`/appmarket/admin`, `/api/v1/appmarket/admin/*`) — see `feature/appmarket/`.

There is also an **ingress from outside**: `AppMarketCollector` pulls upstream JSON (GitHub Releases) and writes entities directly, bypassing the controller layer. That external boundary has its own contract (see Mistake 4).

---

## Before Implementing Cross-Layer Features

### Step 1: Map the Data Flow

Write down how the value moves. Example — "show an app's download sources on the detail page":

```
GitHub API JSON → GitHubReleaseDto → AppVersion/DownloadSource entities
   → AppMarketService.getAppDetail → AppDetailVo (+ AppVersionVo.sources)
   → {code,data,msg} → appmarket.js renderDetail() → DOM
```

For each arrow ask: what format? what can go wrong? who validates?

### Step 2: Identify Boundaries and Their Contracts

| Boundary | Contract | Common pitfall here |
|----------|----------|---------------------|
| Browser ↔ Controller | JSON `{code,data,msg}`; page routes return a Thymeleaf view name | JS reading the envelope root instead of `res.data` |
| Controller ↔ Service | Service interface; **VO in, VO out** | returning entities / leaking `pojo` types |
| Service ↔ Repository | Repository returns entities; derived method names must match Kotlin property names | a typo in `findByAppIdAndVersionName` fails at startup, not compile time |
| Repository ↔ DB | Schema from entity annotations; **no migrations** | adding a column needs a manual `ALTER TABLE` on MySQL |
| Service ↔ External API | External JSON parsed into collector-owned DTOs; normalize at ingress | leaking upstream field names upward; not normalizing version tags |
| feature `*` ↔ `core/*` | `core/*` is feature-free | putting a controller/endpoint in `core/*` |
| Backend ↔ Frontend (assets) | Thymeleaf fragment `common/head::static` + global `ctx` | hardcoding `/` instead of `ctx` prefix |

### Step 3: Define Contracts

For each boundary, state the exact input shape, output shape, and failure mode **before** coding.

---

## Common Cross-Layer Mistakes

### Mistake 1: Implicit Format Assumptions

**Bad**: assuming a date shape across layers.
**Good**: entity `Date` ↔ VO epoch millis, converted explicitly in the `model/` mapper (`this.releaseDate?.time`). Convert once, at the mapper.

### Mistake 2: Scattered Validation

**Bad**: re-validating the same field in several layers.
**Good**: validate once at the entry point. This project has **no Bean Validation** — the admin UI checks required fields before POST, and the controller/JS boundary owns it. Do not sprinkle ad-hoc checks.

### Mistake 3: Leaky Abstractions

**Bad**: a controller returning an `@Entity` (lazy-loading and field-leak risk), or the frontend knowing about column names.
**Good**: `pojo/` entities never leave the service layer; the API always speaks `vo/`. Records/sources are assembled via flat-FK queries in the service, not lazy navigation.

### Mistake 4: Every Consumer Parsing The Same External Payload

**Bad**: two classes each reading GitHub release JSON with their own field access.
**Good**: one owner for the external contract — `AppMarketCollector` owns `GitHubReleaseDto`/`GitHubAssetDto` and the version-normalization rules; nothing downstream sees the raw payload.

**Rule**: for external JSON / config files, create one owner for the type definitions, the parse point, and any normalization (e.g. strip leading `v` from a tag).

### Mistake 5: Duplicated "Not Found" Semantics

**Bad**: inventing an HTTP 404 path when the codebase has none.
**Good**: follow the existing convention — reads return `Response(null)`, mutations return `Response(false)`, and `code` stays `200`. Callers inspect `data` (see `AppMarketAdminApiController`).

---

## Checklist for Cross-Layer Features

Before implementation:
- [ ] Mapped the complete data flow (including any external ingress)
- [ ] Identified every layer boundary and the exact format on each side
- [ ] Decided where validation happens (entry point only)
- [ ] Confirmed which existing slice to mirror (`feature/showcase` for data, `feature/appmarket` for portal+admin+ingress)

After implementation:
- [ ] Tested edge cases (null / empty / not-found) at the boundary
- [ ] Verified entities are mapped to VOs before leaving the service
- [ ] Checked the round-trip: DB → VO → JSON → JS renders correctly
- [ ] If a column/field changed, updated entity **and** the MySQL table (`ALTER`) **and** the seed/collector JSON
- [ ] New read/write split followed the portal-vs-admin controller convention

---

## When to Create Flow Documentation

Create detailed flow docs (task `design.md`) when:

- The feature spans 3+ layers
- An external system feeds data in (collector/ingest)
- The data format is complex or changes between layers
- The feature has caused bugs before
