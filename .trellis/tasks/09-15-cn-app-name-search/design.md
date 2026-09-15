# 技术设计：国内应用按名搜索与应用宝详情补全

## 1. 总体数据流

```
管理员输入「微信」
   │
   ├─ 前端 isPackageOrUrl() = false ──▶ GET /admin/external-search?keyword=微信
   │                                        │
   │                                        ├─ ① 本地词典（新增，即时、离线）
   │                                        ├─ ② F-Droid 搜索（已有）
   │                                        └─ ③ IzzyOnDroid 索引（已有）
   │                                   按 packageName 去重后返回候选列表
   │
   └─ 点击「导入」──▶ POST /admin/external-import?packageName=com.tencent.mm&source=应用宝
                          │
                          └─ 应用宝详情页 SSR 抓取 ──▶ 解析 __NEXT_DATA__
                                 │
                                 ├─ 应用不存在 → 建应用 + 建版本 + 下载源
                                 └─ 应用已存在 → 补全元数据 + 追加新版本（upsert）
```

词典**只提供候选包名**，不提供元数据；真实元数据一律以应用宝返回为准。
这样即使词典包名写错，应用宝查不到就会报错，不会写入脏数据（AC4）。

## 2. 词典

### 2.1 文件与结构

位置：`feature/appmarket/src/main/resources/data/appmarket_cn_dict.json`

```json
[
  { "name": "微信", "packageName": "com.tencent.mm", "category": "社交" },
  { "name": "抖音", "packageName": "com.ss.android.ugc.aweme", "category": "短视频" }
]
```

- 与 `appmarket_seed.json` 同目录、同风格（JSON 数组），随代码发布、随 jar 打包。
- 最终 **96 条**（经应用宝逐条校验、剔除不匹配项后的结果），覆盖社交 / 短视频 / 视频音乐 / 购物支付 / 出行 / 工具办公 / 教育 / 金融 / 游戏 / 输入法 等 19 个分类。
- **生成方式**：先按常识产出候选，再用脚本逐条请求应用宝详情页校验（比对返回 `name` 与预期），
  剔除 / 修正不匹配项，最终产出的词典是**已校验**的。

### 2.2 加载与匹配

新增 `@Component class AppMarketCnDict`（放在 `feature/appmarket/.../service/`）：

- 构造时从 classpath 读取 `data/appmarket_cn_dict.json`，解析失败则记录警告并使用空词典
  （**不因词典问题导致应用启动失败**）。
- `fun match(lower: String): List<CnDictEntry>`：名称或包名 `contains(lower)`，
  名称前缀匹配优先、其次包含匹配，截断到上限（30）。
- 无状态、只读，线程安全。

### 2.3 为什么不用数据库表

词典是「代码的一部分」，应随版本发布、可 code review、无需运维；且只有百来条，
加载进内存查表是微秒级，建表反而引入迁移与同步成本。

## 3. 应用宝上游

### 3.1 端点与配置

| 项 | 值 |
|---|---|
| 基址 | `https://sj.qq.com/appdetail/`（可用 `-Dappmarket.myapp.base=` 覆盖） |
| 请求 | `GET <base><packageName>`，`User-Agent: zaze-appmarket-external` |
| 开关 | `-Dappmarket.myapp.enabled=false`（默认 true） |
| 超时 | 新 client：connect 8s / read 15s / write 15s / call 20s（页面约 300KB，比 F-Droid 大） |
| 缓存 | 按 packageName 内存缓存，TTL 30min（`@Volatile` map，与 Izzy 索引同风格） |

> 注意：必须像 `extClient` 那样**同时**显式覆盖 connect/read/write/call 四类超时，
> 仅设 `callTimeout` 会被底层 `readTimeout`（配置值 60s）接管而无法快速失败。

### 3.2 解析契约

响应 HTML 中取出 `<script id="__NEXT_DATA__" …>{…}</script>`，用 `JsonParser` 解析为
`JsonObject`，**递归遍历**找第一个 `pkg_name == packageName` 的对象（不依赖固定的
`dynamicCardResponse.data.components[i].data.itemData[j]` 路径，页面结构调整时更抗变）。

### 3.3 字段映射（→ `ExternalAppPreview`）

| ExternalAppPreview | 应用宝字段 | 说明 |
|---|---|---|
| `packageName` | `pkg_name` | |
| `name` | `name` | |
| `summary` | `description` ?: `editor_intro` | 简介较长，导入时截断到字段允许长度 |
| `iconSrc` | `icon` | CDN URL，如 `http://pp.myapp.com/ma_icon/0/icon_10910_…/256` |
| `developer` | `developer` ?: `operator` | |
| `category` | `cate_name` ?: `cate_name_new` | |
| `latestVersionName` | `version_name` | |
| `latestVersionCode` | — | **应用宝不提供**，留 null |
| `sizeMb` | `apk_size / 1024 / 1024` | |
| `officialUrl` | `<base><packageName>` | 详情页链接 |
| `source` | 常量 `"应用宝"` | |
| `sourceUrl` | `<base><packageName>` | |

**复用现有 `ExternalAppPreview`，不新增字段**——`latestVersionName / sizeMb / developer /
category / iconSrc` 已存在，契约零变更，前端无需改渲染逻辑。

### 3.4 失败语义

抓取失败 / 非 200 / 解析不到匹配对象 → 返回 `null`，
等价于「该上游未找到」。不重试（上游不可达时重试只会叠加超时）。

## 4. 服务层改动（`AppMarketExternalService`）

### 4.1 lookup 链

```
F-Droid（开源，有 APK 直链）→ IzzyOnDroid → 应用宝（新增，兜底国内应用）
```

应用宝放最后：开源应用在前两个上游已能拿到直链，应用宝无法提供 APK 直链，
仅作为国内应用的兜底。

### 4.2 searchByName

```
本地词典（新增，最先，即时）→ F-Droid → IzzyOnDroid → 按 packageName 去重 → 截断 30
```

词典排最前，保证去重后国内应用候选保留「本地词典」来源标记，且**不依赖网络**（AC5）。
词典项只有 name + packageName + source，其余字段为 null，前端需容忍（现有渲染已处理 null）。

### 4.3 importApp（语义变更点）

现状：`importApp` 先查重，包名已存在即抛异常，再按 `source` 分派到 F-Droid / Izzy。

新增应用宝分支需要 upsert，因此调整分派顺序：

```kotlin
val existing = appRepository.findByPackageName(pkgName)
return when {
    source == MYAPP        -> importFromMyApp(pkgName, existing)   // upsert
    existing != null       -> throw IllegalArgumentException("包名 $pkgName 已存在，无需重复导入")
    source == "IzzyOnDroid" -> importFromIzzy(pkgName)
    else                   -> importFromFdroid(pkgName)
}
```

F-Droid / Izzy 行为完全不变（仍拒绝重复导入），只有应用宝走 upsert。

### 4.4 importFromMyApp upsert 语义

- **不存在**：建 App（名称 / 包名 / 分类 / 开发商 / 简介 / 图标 CDN URL / 官网=详情页）
  → 建 AppVersion（versionName、sizeMb、releaseDate 由 `update_time` 换算）
  → `collector.ensureStoreSources(app)` 补应用宝详情页下载源
- **已存在**：
  - 元数据**只填空不覆盖**已有值？——否。seed 里的图标是 favicon、版本是占位值，
    需要被修正。因此：**图标**在为空或以 `.ico`/`favicon` 结尾时覆盖；
    **简介 / 开发商 / 分类**在当前为空时覆盖；**名称**不覆盖（人工可能改过）。
  - 版本：`versionName` 与已有版本都不同 → 追加；已存在同版本名 → 跳过。
  - 同样调用 `ensureStoreSources` 补下载源（幂等）。

### 4.5 listSearchProviders

追加两项，供管理端「所有源」展示：

```
SearchProviderMeta("dict", "本地词典", "classpath:data/appmarket_cn_dict.json", dictEnabled)
SearchProviderMeta("myapp", "应用宝", myappBase, myappEnabled)
```

## 5. 图标策略（AC7）

| 来源 | iconUrl |
|---|---|
| 应用宝 | CDN URL（`http://pp.myapp.com/…`），几十字节 |
| APK 解析 | data URI（base64），沿用上一子任务的实现 |

- 应用宝补全时：当前图标为空或为 favicon 才覆盖为 CDN URL。
- APK 导入时：写入 data URI（APK 内图标保真度最高，不因补全而退化）。

`App.iconUrl` 已在上个子任务扩为 `TEXT`，两种值都能存，无需再改 schema。

## 6. 兼容性

- `ExternalAppPreview` 结构不变 → 前端列表渲染、预览逻辑零改动。
- 新增 `source` 取值 `"应用宝"` / `"本地词典"` → 前端只是多显示一个来源标签。
- 词典 / 应用宝均可用系统属性关闭，关闭后行为退化为改动前。
- 无数据库 schema 变更（iconUrl 已是 TEXT）。

## 7. 回滚

功能集中在新增文件（词典 JSON、`AppMarketCnDict`）+ `AppMarketExternalService` 内部新增方法；
`importApp` 的分派改写是唯一触及既有逻辑处，且 F-Droid / Izzy 分支行为保持原样。
回滚即 revert 提交；或用 `-Dappmarket.myapp.enabled=false` 单独关闭应用宝上游。

## 8. 待定 / 后续

- 批量补全：对库中所有已有应用按包名批量跑应用宝（`syncStoreSources` 已有类似形态），
  可一键把 seed 41 个应用全部刷成真实数据。本次不做（需考虑 41 次请求的耗时与限流）。
- 词典热更新：目前随代码发布，后续如需运营维护可改存数据库或外部文件。
