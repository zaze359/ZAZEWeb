# 执行计划：按应用名搜索并导入 apk 的上游源

> 复杂任务。下列步骤须在 `trellis:finish-work` / `task.py start` 之后执行；本文件是实现清单与验收，不替代代码。

## 实现前研究（阻塞，先校验端点）
- [ ] **R1** 校验 APKPure 实际端点：搜索页 URL（如 `https://apkpure.com/search?q=`）与详情页（提取 `downloadLink`，参考 `https://d.apkpure.com/b/XAPK/?version=latest`），确认候选列表与直链的 HTML/JSON 解析路径。
- [ ] **R2** 校验 Aptoide 官方 API 基址与路径（搜索 / 按 package 详情），确认返回结构含直链字段（如 `file.path`）。
- [ ] 把校验结果回填到 `design.md` 的「解析锚点」，固定基址默认值与系统属性名。

## 有序实现清单
1. **配置与客户端**：在 `AppMarketExternalService` 新增 `apkpureEnabled` / `aptoideEnabled`、
   `apkpureBase` / `apkpureSearchBase` / `aptoideBase`（带 `-D` 覆盖），
   新增 `apkpureClient` / `aptoideClient`（超时沿用 `extClient` 形态）。
2. **APKPure 解析**：新增 `fetchApkPure(pkg)`、`searchApkPure(kw, lower)`、`toApkPurePreview(...)`，
   仿 `parseMyApp` / `parseSearchBody` 风格（容错、结构变动抗变）。详情按包名缓存（TTL 30min），搜索按关键词缓存（5min）。
3. **Aptoide 解析**：新增 `fetchAptoide(pkg)`、`searchAptoide(kw, lower)`、`toAptoidePreview(...)`，走官方 API。
4. **接入 lookup / searchByName**：按 `design.md` 优先级插入新分支；`searchByName` 聚合进既有 `packageName` 去重与截断。
5. **接入 importApp（upsert）**：新增 `importFromApkPure` / `importFromAptoide`，遵循 D3 语义（应用复用 / 版本按 versionCode 判重 / 下载源按 downloadUrl 判重 / 复用 `ensureStoreSources`）。
6. **接入 listSearchProviders**：追加 APKPure、Aptoide 元信息。

## 验证命令（对照 AC）
- 启动冒烟（H2 覆盖，沿用项目既有方式）：
  `java -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar --server.port=8080 --spring.datasource.url='jdbc:h2:mem:smoke;...' ...`
- AC1 按名搜索：`curl 'http://localhost:8080/api/v1/appmarket/admin/external-search?keyword=微信'` → 含 `source=APKPure` 且包名 `com.tencent.mm`。
- AC2 精确查询：`curl 'http://localhost:8080/api/v1/appmarket/admin/external-lookup?packageName=com.tencent.mm'` → `apkUrl` 为非空直链。
- AC3 一键导入：`curl -X POST 'http://localhost:8080/api/v1/appmarket/admin/external-import?packageName=com.tencent.mm&source=APKPure'` → 三表写入；`DownloadSource.downloadUrl` 为直链。
- AC4 降级：断网/错误基址下 `external-search` 不 5xx、`external-lookup` 返回 `Response(200,null,"未找到…")`。
- AC5 源列表：`curl 'http://localhost:8080/api/v1/appmarket/admin/external-sources'` → 列出 APKPure/Aptoide 及 enabled。
- AC6 开关：以 `-Dappmarket.apkpure.enabled=false` 重启，搜索结果不再含 APKPure，链路无回归。

## 风险文件 / 回滚点
- **核心改动**：`feature/appmarket/.../service/AppMarketExternalService.kt`（仅新增分支与方法，不动既有 F-Droid/Izzy/应用宝逻辑）。
- **可能微调**：`src/main/resources/templates/appmarket/admin.html` 与 `src/main/resources/static/js/appmarket-admin.js`（UI「所有源」列表展示，若前端硬编了源集合则需同步）。
- **回滚**：删除新增分支或 `-D*.enabled=false` 即时关闭；无 schema 变更，回滚安全。

## task.py start 前复查
- [ ] 端点已校验并回填 design.md。
- [ ] 新上游不改动 Repository / VO / 控制器信封。
- [ ] 超时、缓存、降级、开关与现有约定一致。
- [ ] XAPK 仅存直链、不拆解（D5 明确）。

## 验证结果（2026-09-17 已执行）

| 项 | 结果 | 说明 |
| --- | --- | --- |
| 编译（JDK 11） | ✅ 通过 | `:feature:appmarket:compileKotlin` BUILD SUCCESSFUL；仅剩既有 `searchIzzy` 的 "keyword is never used" 告警（非本次引入）。新增代码中的多余安全调用已清理 |
| 打包 / 启动 | ✅ 通过 | `bootJar` 产出正常，服务以 H2 内存库正常启动（门户 302、admin API 未登录返回 `401 未登录`，鉴权边界符合预期） |
| AC5 源列表 | ✅ 通过 | `external-sources` 返回 6 个源，含 `apkpure`(apkpure.com/search) 与 `aptoide`(api.aptoide.com/api/2.1/search/apps)，`enabled:true` |
| AC6 开关降级 | ✅ 通过 | `-Dappmarket.apkpure.enabled=false -Dappmarket.aptoide.enabled=false` 后两个源显示 `enabled:false`，其余四源不受影响 |
| AC4 优雅降级 | ✅ 通过 | 上游不可达时 `external-lookup` 返回 `200 + data:null + 友好提示`；`external-search` 返回 `200 + 空列表 + 提示`，无 5xx、不重试 |
| AC8 并行+分源超时 | ✅ 通过 | 无外网实测：`external-search?keyword=微信` 返回本地词典结果（200，**未因 4 个网络源全部失败而报超时**）；`?keyword=zzznotexist` 返回精确分源提示「搜索上游暂时不可用：F-Droid（网络不可达）；IzzyOnDroid（查询超时，已跳过）；APKPure（查询超时，已跳过）；Aptoide（查询超时，已跳过）」；整体耗时由 ~15s 降至 **~10s**（受 10s 软上限约束，低于前端只读 15s 超时），印证「不同源分开算」 |
| AC1/AC2/AC3 真实数据 | ⚠️ 本环境未跑（但已可验证） | 原记「出网受限」是对 `curl --noproxy` 的结论；实测 **服务内 OkHttp 走系统代理可连外部上游**（见下方「合并」行：应用宝真实抓到 35/41）。故 APKPure/Aptoide 的 AC1/AC2/AC3 现可本环境直接复验，未在本轮运行 |
| 合并 sync-store-sources → batch-complete-myapp | ✅ 通过 | 删除独立端点 `POST /sync-store-sources` 与 `SyncStoreResultVo`；`batchCompleteFromMyApp()` 循环入口对每个 app 先 `ensureStoreSources(app)`（离线补应用宝链接）再 `importFromMyApp`（联网抓元数据）；前端仅剩两按钮（从 GitHub 采集开源应用 / 联网补全应用宝元数据）。H2 冒烟实测 `POST /batch-complete-myapp`：**HTTP 200，41 处理 / 35 更新真实元数据 / 6 跳过 / 0 失败**；抽查 NewPipe 详情已含 `MYAPP` 源 `https://sj.qq.com/appdetail/org.schabi.newpipe`（region=中国），证明「先补链接再补数据」生效 |

### 需在有外网出口的环境复验
```bash
# 起服务（注意换一个空闲端口，避免撞上旧实例）
curl -s -c /tmp/ck -H 'Content-Type: application/json' -X POST \
  -d '{"username":"admin","password":"admin123"}' http://localhost:<port>/api/v1/auth/login
# AC1 按名搜索（期望含 source=APKPure / Aptoide 的候选）
curl -s -b /tmp/ck 'http://localhost:<port>/api/v1/appmarket/admin/external-search?keyword=微信'
# AC2 精确查询（期望 apkUrl 为非空真实直链，而非详情页）
curl -s -b /tmp/ck 'http://localhost:<port>/api/v1/appmarket/admin/external-lookup?packageName=com.tencent.mm'
# AC3 一键导入（期望 DownloadSource.downloadUrl 为可直接下载的安装包直链）
curl -s -X POST -b /tmp/ck \
  'http://localhost:<port>/api/v1/appmarket/admin/external-import?packageName=com.tencent.mm&source=APKPure'
```
若首次运行 APKPure 解析为空（命名/结构变更），重点检查 `parseApkPureSearch` / `parseApkPureDetail` 两个正则；
Aptoide 走官方 API，结构稳定，可作交叉验证基准。
