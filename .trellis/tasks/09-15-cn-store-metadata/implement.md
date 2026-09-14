# Implement — 从 APK 解析导入国内应用

> Task: `09-15-cn-store-metadata` · 依据 `prd.md`（验收）与 `design.md`（设计）
> 前置：已完成 Phase 1 规划并通过最终评审

## 实施清单（按序）

### 1. 前端解析依赖
- 取 `app-info-parser` 浏览器构建产物（npm 包 `app-info-parser` 的 `dist/`），
  放入 `src/main/resources/static/vendor/app-info-parser/app-info-parser.js`。
- 同目录保留 `LICENSE`（MIT），与现有 vendor（bootstrap / jquery）一致本地引入，不依赖 CDN。
- 确认全局对象为 `window.AppInfoParser`。

### 2. 数据层：`iconUrl` 扩为 TEXT
- 文件：`feature/appmarket/src/main/kotlin/.../pojo/App.kt`
  ```kotlin
  @Column(columnDefinition = "TEXT")
  val iconUrl: String? = null,
  ```
- 新增迁移脚本到 `src/main/resources/static/sql/`（目录已存在）：
  ```sql
  -- MySQL（生产）
  ALTER TABLE appmarket_app MODIFY COLUMN icon_url TEXT;
  -- H2（本地冒烟）
  ALTER TABLE appmarket_app ALTER COLUMN icon_url TEXT;
  ```
- **注意**：`spring.jpa.hibernate.ddl-auto=update` 不会改列类型，此 DDL 必须手动执行，
  否则写入 data URI 会被 varchar(255) 截断。

### 3. DTO
- 文件：`feature/appmarket/src/main/kotlin/.../dto/AdminDtos.kt`
- 新增：
  ```kotlin
  data class ApkImportRequest(
      val packageName: String? = null,
      val name: String? = null,
      val versionName: String? = null,
      val versionCode: Long? = null,
      val iconDataUri: String? = null,
      val sizeMb: Long? = null
  )
  ```

### 4. Service：新增 `importFromApk`
- 文件：`feature/appmarket/src/main/kotlin/.../service/AppMarketAdminService.kt`
- 逻辑见 design.md「服务端处理」：校验包名 → 查/建 App → 版本去重后追加 →
  `ensureStoreSources()` 补应用宝 → `@CacheEvict(allEntries = true)` → 返回 `AppVo`。
- **前置确认**：该类是否已注入 `AppMarketCollector`（`ensureStoreSources` 的持有者）。
  `adminService.collect()` 已在用采集能力，若未直接持有 collector 则补注入。
- 状态区分通过 `msg` 表达（沿用信封约定，code 恒 200）：
  「已导入：X」/「已新增版本 vX」/「已是最新，无需重复导入」。

### 5. Controller：新增接口
- 文件：`feature/appmarket/src/main/kotlin/.../controller/AppMarketAdminApiController.kt`
- 新增 `POST /api/v1/appmarket/admin/import-from-apk`，转发 `adminService.importFromApk(req)`，
  `IllegalArgumentException` 捕获后返回 `Response(200, null, 原因)`（与现有 `external-import` 一致）。
- **重要**：本接口**不要加 `@LoggerManage`**。该切面会序列化入参进日志，
  而入参含 `iconDataUri`（可能几十 KB base64），会严重污染日志。
  （项目既有约定：涉及密码/会话的接口不加；此处同理，因入参体量大。）

### 6. 前端页面：新增入口与弹窗
- 文件：`src/main/resources/templates/appmarket/admin.html`
- 在「导入外部应用」旁新增按钮「从 APK 导入」，打开新弹窗 `#apkModal`，包含：
  - `<input type="file" id="apkFile" accept=".apk">`
  - 解析状态区（加载态 / 错误提示）
  - 解析结果预览（图标 + 包名 + 应用名 + 版本名/版本号）
  - 「确认导入」按钮
- 页面底部引入 `/vendor/app-info-parser/app-info-parser.js`。

### 7. 前端逻辑
- 文件：`src/main/resources/static/js/appmarket-admin.js`
- 新增：文件选择 → 大小校验（建议上限 200 MB）→ `AppInfoParser.parse()` →
  填充预览与待提交数据 → 提交 `ajax('POST', API + '/import-from-apk', payload, 30000)`。
- 必须处理：`result.package` 缺失视为失败；图标超 100 KB 仅预览不提交；
  catch 解析异常并提示「文件可能损坏或经过加固」。
- 入口函数挂到 `window.Admin`（如 `openApkModal` / `importFromApk`），与既有风格一致。

### 8. 不改动
- 不修改 `AppMarketExternalService`（F-Droid / IzzyOnDroid 链路保持原样）。
- 不修改门户 `appmarket.js`（data URI 天然可渲染）。

## 验证

```bash
# JDK 11 构建（必需，默认 JDK 17 会报 lombok 错）
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
./gradlew bootJar -x test

# H2 冒烟（端口 8099）
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar \
  --server.port=8099 \
  --spring.datasource.url='jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
  --spring.jpa.hibernate.ddl-auto=create \
  --spring.cache.type=simple
# 注：后台起服务必须用 run_in_background=true，nohup & 会被回收
```

**后端接口验证**（不依赖真实 APK，直接构造元数据 JSON）：
1. 登录拿会话：`POST /api/v1/auth/login` `{"username":"admin","password":"admin123"}`
2. 新应用：`POST /admin/import-from-apk` `{"packageName":"com.tencent.mobileqq","name":"QQ","versionName":"8.9.88","versionCode":1234,"iconDataUri":"data:image/png;base64,..."}`
   → 期望 `data` 非空、msg「已导入」，并检查已补应用宝下载源。
3. 重复导入同版本 → 期望 msg「已是最新」，版本数不增加。
4. 导入更高 `versionCode` → 期望 msg「已新增版本」，版本数 +1。
5. 缺 `packageName` → 期望 `data=null` 且 msg 说明原因。
6. 长 data URI 写入 → 确认未被截断（验证 TEXT 生效，需先执行 H2 ALTER）。

**前端解析验证**（需真实 APK 与浏览器，沙箱无外网，由本地自测）：
- 在管理后台选择真实 APK，确认自动填充包名/版本/图标且预览正常；
- 选择非 APK 文件，确认报错且不提交。

## 风险文件与回滚点

| 文件 | 风险 | 回滚 |
|------|------|------|
| `pojo/App.kt` + 迁移 SQL | 列类型变更；若未执行 DDL 会截断 data URI | 先清空 data URI 值再改回 varchar；元数据与外链图标不受影响 |
| `AppMarketAdminService` | 新增方法，可能引入循环依赖（collector 注入） | 移除新方法即可，不影响既有 collect/sync |
| `AppMarketAdminApiController` | 新增接口 | 删除接口；前端入口按钮同时下线 |
| `admin.html` / `appmarket-admin.js` | 前端逻辑，影响管理后台 | 回退文件即可，门户页不受影响 |
| vendor 新依赖 | 约数百 KB，仅管理后台加载 | 删除 vendor 目录与 script 引用 |

## `task.py start` 前的检查项

- [ ] `app-info-parser` 的 dist 产物已获取并放入 vendor（含 LICENSE，MIT 允许再分发）。
- [ ] `AppMarketAdminService` 与 `AppMarketCollector` 的注入关系已确认（避免循环依赖）。
- [ ] `@LoggerManage` 已确认不在新接口上使用（防止 base64 入日志）。
- [ ] 目标环境的 `appmarket_app.icon_url` 列已执行扩列 DDL。
- [ ] 图标大小阈值（100 KB）与 APK 大小上限（200 MB）已与用户确认可接受。
