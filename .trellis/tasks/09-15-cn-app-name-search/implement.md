# 实施计划：国内应用按名搜索与应用宝详情补全

前置：子任务 `09-15-cn-app-name-search`（父任务 `09-12-app-market`）。
`prd.md` / `design.md` 已就绪。开始实施前需 `task.py start`。

## 步骤

### 1. 生成并校验词典

- [x] 用脚本按常识产出候选条目（约 150 条，覆盖社交 / 短视频 / 视频音乐 / 购物支付 /
      出行 / 工具办公 / 教育 / 金融 / 游戏 / 输入法 / 系统工具）
- [ ] 逐条请求 `https://sj.qq.com/appdetail/<pkg>`，比对返回 `name` 与预期名称，
      剔除 / 修正不匹配项；记录失败清单
- [ ] 产出 `feature/appmarket/src/main/resources/data/appmarket_cn_dict.json`
      （`[{"name","packageName","category"}]`，category 取应用宝返回的 `cate_name`）
- [ ] 校验：词典条数、JSON 合法、无重复包名

### 2. 新增 `AppMarketCnDict` 组件

- [ ] 新建 `feature/appmarket/.../service/AppMarketCnDict.kt`
      - `@Component`，构造时读取 `classpath:data/appmarket_cn_dict.json`
      - 解析失败 → 记 warn + 空词典（不阻断启动）
      - `fun match(lower: String): List<CnDictEntry>`，前缀匹配优先、其次包含，截断 30
- [ ] `data class CnDictEntry(val name: String, val packageName: String, val category: String?)`

### 3. 应用宝上游（抓取 + 解析）

- [ ] 在 `AppMarketExternalService` 增加配置：
      `myappBase`（`-Dappmarket.myapp.base`）、`myappEnabled`（`-Dappmarket.myapp.enabled`）
- [ ] 新增 `myappClient`：connect 8s / read 15s / write 15s / call 20s（四类都要显式覆盖）
- [ ] `fetchMyApp(pkgName)`：GET 详情页 → 正则取 `__NEXT_DATA__` → `JsonParser` 解析
      → 递归找 `pkg_name == pkgName` 的对象 → 映射为 `ExternalAppPreview(source="应用宝")`
- [ ] 按 pkgName 内存缓存，TTL 30min
- [ ] 失败（非 200 / 解析异常 / 找不到匹配项）返回 null，不重试

### 4. 接入查询链

- [ ] `lookup()`：F-Droid → Izzy → 应用宝（末尾兜底）
- [ ] `searchByName()`：本地词典 → F-Droid → Izzy → 去重 → 截断
- [ ] `listSearchProviders()`：追加 `本地词典` 与 `应用宝`

### 5. 导入（upsert）

- [ ] 改造 `importApp()` 分派：应用宝分支在查重**之前**处理；F-Droid / Izzy 保持
      「已存在则抛异常」不变
- [ ] `importFromMyApp(pkgName, existing)`：
      - 取不到详情 → `IllegalArgumentException("应用宝未找到该包名：…")`
      - 不存在 → 建 App + AppVersion（releaseDate 由 `update_time` 换算）+ 下载源
      - 已存在 → 图标为空/favicon 才覆盖；简介 / 开发商 / 分类为空才覆盖；
        版本名不同才追加；名称不覆盖
      - 两种路径都调 `collector.ensureStoreSources(app)`（幂等）
- [ ] 新方法不加 `@LoggerManage`（控制器侧已有既有风格，沿用）

### 6. 前端

- [ ] 搜索结果候选已带 `source`，确认「本地词典」标签正常显示
- [ ] 词典候选缺少版本 / 图标信息时列表不报错（现有渲染须容忍 null）
- [ ] 未命中词典时给出「可改用从 APK 导入」的引导提示

### 7. 验证

```bash
# 构建（必须 JDK 11）
export JAVA_HOME=/usr/local/opt/openjdk@11/libexec/openjdk.jdk/Contents/Home
./gradlew bootJar -x test --no-daemon

# H2 冒烟启动
"$JAVA_HOME/bin/java" -Xmx512m -jar build/libs/zaze-server-0.0.1-SNAPSHOT.jar \
  --server.port=8099 \
  --spring.datasource.url='jdbc:h2:mem:smoke;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE' \
  --spring.datasource.driver-class-name=org.h2.Driver \
  --spring.jpa.database-platform=org.hibernate.dialect.H2Dialect \
  --spring.jpa.hibernate.ddl-auto=create --spring.cache.type=simple
```

冒烟用例（先登录拿 cookie）：

| # | 用例 | 期望 |
|---|---|---|
| T1 | `GET /admin/external-search?keyword=微信` | 候选含 `com.tencent.mm`，source=本地词典（AC1） |
| T2 | `GET /admin/external-search?keyword=抖音` | 候选含 `com.ss.android.ugc.aweme` |
| T3 | `GET /admin/external-lookup?packageName=com.tencent.mm` | 返回应用宝元数据：版本名非空、图标为 pp.myapp.com（AC2 前半） |
| T4 | `POST /admin/external-import?packageName=com.demo.cnapp&source=应用宝` | 报错（应用宝无此包），不写库（AC4） |
| T5 | 导入一个词典中存在但库中没有的应用 | 新建，名称 / 版本名 / 大小 / 图标 / 简介 / 开发商 / 分类齐全（AC2） |
| T6 | 对 seed 已有应用（如微信）再导入一次 source=应用宝 | 不报错；图标被替换为 CDN URL、追加真实版本；原数据保留（AC3） |
| T7 | 重复导入同一版本 | 不产生重复版本 |
| T8 | `-Dappmarket.myapp.enabled=false` 启动后搜索「微信」 | 词典候选仍返回（AC5） |
| T9 | `GET /admin/external-sources` | 含「本地词典」与「应用宝」两项 |

### 8. 收尾

- [ ] 更新 `.workbuddy/memory/` 当日记录（新增上游、词典位置、应用宝 SSR 结论）
- [ ] 提交代码与文档（含词典 JSON）

## 回滚点

- 步骤 3–5 任一失败：revert 该步改动即可，F-Droid / Izzy 路径不受影响
- 线上仅需关闭应用宝：`-Dappmarket.myapp.enabled=false`
