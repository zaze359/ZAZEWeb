# 执行计划 — 新增「搜索引擎发现」渠道（Bing）

> 复杂任务。下列步骤须在 `task.py start` 之后执行；本文件是实现清单与验收，不替代代码。

## 有序实现清单

1. **配置与客户端**：在 `AppMarketExternalService.kt` 新增
   `bingEnabled`（`-Dappmarket.bing.enabled`，默认 true）、`bingSearchBase`（`-Dappmarket.bing.search`，
   默认 `https://www.bing.com/search?q=%s+apk&setlang=zh-CN`）、常量 `BING_SOURCE="Bing发现"`、
   `bingClient`（沿用 `extClient` 形态 + 显式 `User-Agent`，read 12s）。
2. **抽取函数** `discoverPackagesFromBing(keyword): List<String>`：见 design.md「抽取逻辑」，
   含 URL 参数优先抽取、`com.xxx.yyy` 兜底、合法性校验、噪声域名黑名单、去重与截断。
3. **接入 `searchByName`**：六路聚合后**结果为空**时并行追加 `discoverPackagesFromBing`，
   候选包名包装为 `ExternalAppPreview(packageName=候选, name=keyword, source=BING_SOURCE, apkUrl=null)`，
   并入既有候选列表（复用 packageName 去重/截断）；发现任务共享统一 deadline、超时跳过。
4. **接入 `listSearchProviders`**：追加 `SearchProviderMeta("bing", BING_SOURCE, bingSearchBase, bingEnabled)`。
5. **前端（极小改）**：`external-search` 候选列表已按 `source` 渲染；核对 Bing 候选（`packageName`+`name`+`source`）
   复用既有点击→`external-lookup`→`external-import` 流程，确认无需新导入端点、管理员确认仍在（R5/R1）。
   若既有渲染对 `apkUrl=null` 的候选有 NPE/显示异常，补一个「按包名查」按钮分支即可。

## 验证命令（对照 AC）

编译 / 启动（沿用 H2 覆盖，换空闲端口 8081+ 避免旧实例；`curl` 加 `--noproxy '*'` 直连 localhost）：

```bash
# 编译
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
./gradlew bootJar

# 启动 H2 冒烟（后台）
java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar --server.port=8081 \
  --spring.datasource.url='jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
  --spring.jpa.hibernate.ddl-auto=create --spring.cache.type=simple &

# 登录拿 cookie
curl -s --noproxy '*' -c /tmp/ck -X POST http://localhost:8081/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"username":"admin","password":"admin123"}'
```

- **AC1 源列表**：`curl -s --noproxy '*' -b /tmp/ck 'http://localhost:8081/api/v1/appmarket/admin/external-sources'`
  → 含 `"bing"` provider，`enabled:true`；以 `-Dappmarket.bing.enabled=false` 重启后 `enabled:false`、其余六路不受影响。
- **AC2 发现命中**：`curl -s --noproxy '*' -b /tmp/ck 'http://localhost:8081/api/v1/appmarket/admin/external-search?keyword=国家反诈中心'`
  → 返回 ≥1 候选，`packageName=com.hicorenational.antifraud`、`source=Bing发现`（可信上游对该词典外应用无结果，触发兜底）。
- **AC2 兜底不污染**：`external-search?keyword=微信` → 可信上游已命中，结果应**不含** `Bing发现` 行（确认仅兜底触发）。
- **AC3 复用导入**：`curl -s --noproxy '*' -b /tmp/ck 'http://localhost:8081/api/v1/appmarket/admin/external-lookup?packageName=com.hicorenational.antifraud'`
  → 由应用宝/APKPure 等解析出真实预览（name/icon/summary），证明点击候选后走既有 lookup→import，无新端点。
- **AC4 优雅降级**：将 `appmarket.bing.search` 指到不可达基址（或临时断网）后 `external-search?keyword=学习强国`
  → 200 + 空结果 + 友好提示，无 5xx；整体时延 < 10s（统一 deadline）。
- **AC5 噪声过滤**：`external-search?keyword=交管12123` → 候选不应含 `beian.miit.gov.cn` / `beian.mps.gov.cn` / `baike.baidu.com` / `go.microsoft.com`；
  另抽检 `学习强国`/`个人所得税` 等 ≥5 个词典外查询，≥3/5 给出正确包名。
- **AC6 编译与渲染**：JDK 11 `bootJar` `BUILD SUCCESSFUL`；`GET /appmarket/admin` 200、无 JS 报错；六路行为与优先级不变。

## 风险文件 / 回滚点

- **核心改动**：`feature/appmarket/.../service/AppMarketExternalService.kt`
  （仅新增 `discoverPackagesFromBing` 与 `searchByName`/`listSearchProviders` 的增量分支，**不动**既有六路逻辑与优先级）。
- **可能微调**：`src/main/resources/templates/appmarket/admin.html` 与 `static/js/appmarket-admin.js`
  （仅当既有候选渲染对 `apkUrl=null` 的 Bing 候选有异常时需补显示分支）。
- **回滚**：`-Dappmarket.bing.enabled=false` 即时关闭；或删除新增分支。无 schema 变更，回滚安全。

## task.py start 前复查

- [ ] 抽取逻辑以 store-URL 参数（`pkgname=`/`id=`）为主、裸包名兜底，且噪声域名黑名单已覆盖实测噪声域。
- [ ] 发现任务共享统一 deadline，超时跳过不影响整体（不拖慢 `searchByName`）。
- [ ] `lookup()` 未接 Bing（保持仅六路）；Bing 仅出现在 `searchByName` 兜底与 `listSearchProviders`。
- [ ] 前端点击 Bing 候选复用既有 lookup→import，管理员确认环节未被绕过（满足 R1 不自动入库）。
- [ ] 新增 `-D` 开关与 base 覆盖，默认 enabled=true。
- [ ] 无 schema 变更、无 sourceType 枚举值新增。

## 验证记录（2026-09-18 实测，JDK 11 `bootJar` 通过 + H2 冒烟 8081/8082/8083）

实现中修复的 3 个代码缺陷（均已在代码落地）：
1. UA 不能在 OkHttp Builder 上 `addHeader`（本工程 `newBuilder()` 返回被 `core:network` 收窄的 Builder）→ 改在 `Request.Builder().header("User-Agent", BING_UA)`。
2. 路径兜底正则误用 `\$`（Kotlin raw string 字面 `$`）→ 改 `$`（行尾锚点）。
3. `pkgFromUrl` 噪声过滤两轮迭代：裸 `/com.foo.bar` 兜底 + 根域白名单都会误收 `r.bing.com`/`sj.qq.com`/门户子域 `?id=index.html` → 最终用**具体商店子域白名单** + 仅抽 `?pkgname=`/`?id=` 与已知商店路径。

| AC | 结果 |
|----|------|
| AC1 源列表 | `external-sources` 含 `bing`(enabled:true)；`-Dappmarket.bing.enabled=false` 重启后 `enabled:false`、六路不变 ✅ |
| AC2 发现命中 | 国家反诈中心 → `com.hicorenational.antifraud`(Bing发现)，两次复测稳定；微信(可信命中) 不含 Bing发现 ✅ |
| AC3 复用导入 | `POST external-import?packageName=cn.gov.tax.its&source=Bing发现` → 200、name=个人所得税（走应用宝补全，管理员确认环节未被绕过）✅ |
| AC4 优雅降级 | `-Dappmarket.bing.search=http://127.0.0.1:9/...`(不可达) → 200 + 空结果、无 5xx ✅ |
| AC5 噪声过滤 | 掌上电力 → 仅 `com.tencent.qt.qtl`（无 index.html/pcopen.shtml）；国家反诈中心/个人所得税 均干净命中 ✅ |
| AC6 编译与渲染 | JDK 11 `bootJar` BUILD SUCCESSFUL；`GET /appmarket/admin` 200；六路行为/优先级不变 ✅ |

> 注：Bing 发现是**兜底**——可信六路（尤其 APKPure/Aptoide）已命中的热门应用不触发；仅「词典外 + 六路均无结果」的 niche/政府应用走 Bing 抽取。AC5「≥3/5 正确包名」应在触发兜底的查询上评估（已实证 国家反诈中心/个人所得税/掌上电力 均干净）。

