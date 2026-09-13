# Database Guidelines

> Spring Data JPA over MySQL. No migration tool is configured — schema is managed by Hibernate DDL against the `dev` database. The active setting is `spring.jpa.hibernate.ddl-auto=update` in `src/main/resources/application.properties` (the datasource URL lives in `application.yml`; `application.bak` is an inert backup, not loaded).

---

## Core Abstractions

All shared persistence bases live in `core/database/`:

- **`BaseEntity`** (`core/database/.../model/BaseEntity.java`) — `@MappedSuperclass` with `Long id` (`@Id @GeneratedValue`), `createTime` (`@CreationTimestamp`, `updatable = false`), `updateTime` (`@UpdateTimestamp`). New entities should extend it (Java) or replicate its fields (Kotlin, see below).
- **`BaseRepository<T, ID>`** (`core/database/.../repository/BaseRepository.java`) — `@NoRepositoryBean` interface extending `JpaRepository`. Custom repositories extend this, not `JpaRepository` directly.

---

## Entity Pattern (Kotlin data class)

Reference: `feature/showcase/.../pojo/Showcase.kt`. Entities are Kotlin data classes with nullable columns defaulted to `null` and timestamps defaulted to `Date()`:

```kotlin
@Entity
@Table(name = "showcase")
data class Showcase(
    @Id @GeneratedValue
    val id: Long = -1,
    @Column(updatable = false)
    @CreationTimestamp
    val createTime: Date = Date(),
    @UpdateTimestamp
    val updateTime: Date = Date(),
    @Column val title: String? = null,
)
```

- Table name is explicit via `@Table(name = "...")` (lowercase, snake-less single word in current code).
- Nullable columns are modeled as `String? = null` in Kotlin — do not use platform types.
- Java entities may use Lombok `@Data @Builder` on top of `BaseEntity` (see `Roles.java`).

### Flat Foreign Keys (no JPA relationships)

`feature/appmarket/` models a three-level aggregate (**App → AppVersion → DownloadSource**) with **plain `Long` foreign-key columns and no JPA relationship annotations** (`@OneToMany`/`@ManyToOne`/`@JoinColumn` are absent). Each level is queried separately and assembled in the service:

```kotlin
// AppVersion.kt — the parent is just a column, not a relation
@Column val appId: Long = -1,

// repository derived finders
fun AppVersionRepository.findByAppId(appId: Long): List<AppVersion>
fun DownloadSourceRepository.findByVersionId(versionId: Long): List<DownloadSource>
```

Why: avoids lazy-loading, cascade and `equals`/`hashCode` pitfalls that bite mutable JPA graphs. Follow this shape for new nested data instead of adding `@OneToMany`.

Consequence — **cascade delete is manual**. Deleting a parent must first delete its children, inside one `@Transactional` service method (see `AppMarketAdminServiceImpl.deleteApp`):

```kotlin
@Transactional
override fun deleteApp(id: Long): Boolean {
    if (!appRepository.existsById(id)) return false
    versionRepository.findByAppId(id).forEach { v ->
        sourceRepository.deleteAll(sourceRepository.findByVersionId(v.id))
    }
    versionRepository.deleteAll(versionRepository.findByAppId(id))
    appRepository.deleteById(id)
    return true
}
```

Note the Kotlin entity default `id: Long = -1` (non-null) means Spring Data treats `save()` as **merge**, not persist. That is fine and is how `AppMarketSeedLoader` / the collector insert new rows — merge on an absent id performs an INSERT (with one extra SELECT). Do not "fix" it by nulling the id; keep the `-1` convention consistent with existing entities.

---

## Entity ↔ VO Mapping

**Never expose entities from controllers.** Entities (`pojo/`) and API DTOs (`vo/`) are separate, mapped by extension functions in the feature's `model/` package — reference `feature/showcase/.../model/ShowcaseModel.kt`:

```kotlin
fun Showcase.asVo(): ShowcaseVo { ... }
fun ShowcaseVo.asPojo(): Showcase { ... }
```

Rules:
- VO timestamps are epoch millis (`Long`), entity timestamps are `Date` — conversion happens in the mapper.
- Nullable entity fields get non-null defaults in the VO (`title = this.title ?: ""` for required VO fields).
- Mappers are pure functions; no repository or service calls inside them.

---

## Repository Pattern

Reference: `feature/showcase/.../repository/ShowcaseRepository.java`.

```java
public interface ShowcaseRepository extends BaseRepository<Showcase, Long> {
    @Transactional
    @Modifying
    @Query(value = "delete from showcase s where s.id in :ids", nativeQuery = true)
    void deleteByIds(@Param("ids") List<Long> ids);

    @Query(value = "select * from showcase s where s.tags like :tags", nativeQuery = true)
    List<Showcase> findByTagsLike(Pageable pageable, @Param("tags") String tags);
}
```

- Write operations **must** have both `@Transactional` and `@Modifying` (current code uses `javax.transaction.Transactional`).
- Native queries are the house style; `nativeQuery = true` means the table name goes after `from`, otherwise the entity class name.
- Prefer derived query methods for simple finds (see the commented `findByItems_Item_Name` in `RolesRepository.java` for the syntax reference).

### Idempotent Upsert (find-then-insert)

When ingesting external data repeatedly (see `AppMarketCollector`), do **not** blind-insert. Guard each level with a derived finder and skip when present, so re-running is a no-op:

```kotlin
val app = appRepository.findByPackageName(pkg) ?: appRepository.save(App(...))
val version = versionRepository.findByAppIdAndVersionName(app.id, name)
    ?: versionRepository.save(AppVersion(appId = app.id, versionName = name, ...))
if (sourceRepository.findByVersionIdAndDownloadUrl(version.id, url) == null) {
    sourceRepository.save(DownloadSource(versionId = version.id, downloadUrl = url, ...))
}
```

This is why the appmarket repositories carry `findByPackageName`, `findByAppIdAndVersionName`, and `findByVersionIdAndDownloadUrl`. A second collection run must report `0` inserts.

---

## Transactions & Caching

- No explicit transaction templates — service methods rely on repository-level `@Transactional`.
- Caching is Spring cache annotations on the service impl (`ShowcaseServiceImpl.kt`): `@CacheConfig(cacheNames = [...])` at class level, `@Cacheable` on reads, `@CacheEvict` on writes/deletes. Any new feature touching cache must follow this pairing or reads will serve stale data.

---

## Migrations & Schema Changes

There is no Flyway/Liquibase. Schema comes from entity annotations at startup. Consequences:

- Adding/changing an entity column requires a manual `ALTER TABLE` on the target MySQL database — document it in the task notes when needed.
- Do not rename columns casually; there is no migration history to protect data.

---

## Common Mistakes

- Forgetting `@Modifying` on a `@Query` write → the query silently fails at runtime.
- Returning entities directly from controllers → lazy-loading and field leakage issues; map to VO first.
- Putting `@Cacheable` without a matching `@CacheEvict` on the write path → stale cache after save/delete.
- Using H2 in main code paths — H2 is `runtimeOnly` in `core:database` so it *is* on the app runtime classpath (usable for local smoke runs via an overridden datasource URL), but the production datasource is MySQL. Never branch on the database in business code.
- Blind-inserting during re-ingestion → duplicate rows. Use the find-then-insert upsert above.
- Deleting a parent row without clearing children → orphaned versions/sources (no JPA cascade is configured).
