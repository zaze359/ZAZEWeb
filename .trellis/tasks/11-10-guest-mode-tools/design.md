# 设计：游客模式 + 工具箱拆分

> 任务：`11-10-guest-mode-tools`。需求见 `prd.md`，执行顺序见 `implement.md`。本文件只做技术设计。

## 1. 权限矩阵：改前 vs 改后

| 路径 | 现在 | 改后 | 依据 |
|------|------|------|------|
| `/`、`/appmarket`、`/appmarket/**` | 需登录（302→/login） | **公开** | `LOGIN_PATTERNS` 移除 |
| `/api/v1/appmarket/apps`、`/apps/**` | 需登录 | **公开** | `PUBLIC_PATTERNS` 白名单 |
| `/api/v1/appmarket/incremental-patch` | 需登录 | **公开** | 同上（客户端下载补丁用，本身无 session 概念） |
| `/api/v1/appmarket/**`（其余） | 需登录 | 不变 | 刻意不放开整个前缀 |
| `/admin/**`、`/api/v1/appmarket/admin/**` | ADMIN | 不变 | `ADMIN_PATTERNS` 不动 |
| `/api/v1/auth/**` | 公开 | 不变 | 本来就是 |
| `/tools`、`/tools/apk` | 不存在 | **公开** | 不在任何拦截列表内，天然放行 |

原则：**白名单可穷举就写死，前缀整体放开只留给"确定只读"的场景**——现在非 admin 的 appmarket 接口确实全是 GET，但"因为我今天全是 GET 所以放开整个前缀"这种理由，会在下一个人加写接口时变成漏洞。

## 2. 后端改动（三处，都是规则级）

### 2.1 `AuthInterceptor.kt`

```kotlin
companion object {
    /** 仅管理员（不变） */
    private val ADMIN_PATTERNS = listOf(...)

    /**
     * 游客模式：门户页面与只读接口已通过 WebMvcConfig.PUBLIC_PATTERNS 放行，
     * 当前不存在"只需登录、不需要 ADMIN"的路径，故此处为空。
     * 若将来新增（例如"用户中心"），把路径加回这里即可恢复两级鉴权。
     */
    private val LOGIN_PATTERNS = emptyList<String>()
}
```

保留两级结构而不是删掉分支：回退时只加一行路径，不必重写 `preHandle`。同时给 `LOGIN_PATTERNS.any{}` 留空集合语义（对空集 `any` 恒为 false，行为等价于"不拦截"）。

### 2.2 `WebMvcConfig.kt`

`PUBLIC_PATTERNS` 追加三条：

```kotlin
"/api/v1/appmarket/apps",
"/api/v1/appmarket/apps/**",
"/api/v1/appmarket/incremental-patch"
```

注意 `/apps/**` 已覆盖 `/apps/categories` 与 `/apps/{id}`，但仍显式写上 `/apps` 本体（AntPathMatcher 里 `/apps/**` 是否匹配 `/apps` 取决于实现细节，写死更保险）。

### 2.3 `VueSpaController.kt`（修既有缺陷）

```kotlin
@GetMapping(
    "/",
    "/{p1:[^\\.]*}",
    "/{p1:[^\\.]*}/{p2:[^\\.]*}",
    "/{p1:[^\\.]*}/{p2:[^\\.]*}/{p3:[^\\.]*}"
)
fun spa(): String = "forward:/index.html"
```

- 现状 `/{path:[^\\.]*}` 只匹配**一段**，`/appmarket/3`、`/tools/apk` 直接访问/刷新会 404 —— 这是既有 bug，本次必须修，否则工具箱上线即"只能点进去、不能刷新"。
- 上限 3 段：现有路由最深 2 段，留一段余量。
- 每段都排除点号，`/assets/index.js`、`/favicon.ico` 不会被吞掉（它们由静态资源处理器负责）。
- 不用 `/**`：那会把含点号的路径也纳入候选，靠 Spring 的优先级"碰巧"正确，不如正则显式。

## 3. 前端：游客态

### 3.1 关键结论：不需要动 401 逻辑，但要加一道防御

`/api/v1/auth/me` 匿名时是 **200 + `data: null`**，所以游客打开首页不会触发 `request()` 的 401 跳转。但这是后端"碰巧"的行为，一旦有人给 `me()` 加上"未登录返回 401"，**所有游客都会被弹到登录页**——这种脆弱性值得花 5 行防住：

```ts
// request 增加第三个参数
async function request<T>(path: string, init?: RequestInit, opts?: { silent401?: boolean }) {
  ...
  if (res.status === 401) {
    if (!opts?.silent401 && window.location.pathname !== '/login') window.location.href = '/login'
    throw new ApiError('未登录', 401, 'unauthorized')
  }
  ...
}

me: () => request<UserVo | null>('/auth/me', undefined, { silent401: true })
```

只给 `me()` 用。后台/写接口维持"401 就跳登录"，因为这表示"你的会话没了"，提示是对的。

### 3.2 `NavBar.vue`

```ts
const items = computed(() => [
  { to: '/', label: '首页' },
  { to: '/appmarket', label: '应用图鉴' },
  { to: '/tools', label: '工具箱' },                                  // 所有人，含游客
  ...(auth.user?.role === 'ADMIN' ? [{ to: '/admin', label: '管理后台' }] : [])
])
```

右侧区域：`auth.user` 为空时显示「登录」按钮（`router-link to="/login"`），沿用现有幽灵胶囊样式，不新增配色。游客与登录用户的区别只在"有没有用户 chip / 后台入口 / 退出按钮"。

### 3.3 `LoginView.vue`

- 表单下方加「先随便逛逛 →」（`router-link to="/"`），文案点明"浏览门户与工具箱无需登录"。
- 登录成功后的 `window.location.href = '/'` 保持不变（需要刷新鉴权态与导航）。

## 4. 前端：工具箱

### 4.1 路由

```ts
{ path: '/tools',      name: 'tools',        component: ToolsView,      meta: { theme: 'portal' } },
{ path: '/tools/apk',  name: 'apk-analyzer', component: ApkAnalyzerView, meta: { theme: 'portal' } },
{ path: '/admin/apk',  redirect: '/tools/apk' },   // 旧书签兼容
```

`meta.theme` 决定 `App.vue` 的整页皮肤与导航变体：工具区走 `portal`（暗色），与后台彻底分开。

### 4.2 `ToolsView.vue`（新增）

一张或几张工具卡，卡片结构照抄门户既有卡（`portal-surface` + `portal-border` + `shadow-tile`）：

- 标题：APK 分析
- 一句话：解析包名/版本/权限/签名，全部在浏览器本地完成，文件不上传
- 状态位：「无需登录」
- 入口 → `/tools/apk`

只画实际的工具，不要为"将来可能有的工具"摆空位。

### 4.3 主题迁移与共享组件

两个组件的实际情况不同，处理方式也不同：

| 组件 | admin-* 出现次数 | 处理 |
|------|------------------|------|
| `ApkAnalyzerView.vue`（仅工具页用） | 19 | 直接改为 portal 令牌 |
| `ApkAnalysisPanel.vue`（工具页 + 后台导入弹窗共用） | 0 | 检查是否用了硬编码浅色（`bg-white`/`text-gray-*`）；有则加 variant，无则不动 |
| `ApkSignatureCard.vue`（两处共用） | 20 | **加 `variant?: 'portal' \| 'admin'`，默认 `'admin'`**；工具页传 `portal`，后台弹窗不传 |

映射表（机械替换，但有两个必须留意的反转）：

| admin | portal | 注意 |
|-------|--------|------|
| `admin-bg` | `portal-bg` | 页面底 |
| `admin-surface` | `portal-surface` | 卡片底 |
| `admin-border` | `portal-border` | 强调描边用 `portal-border2` |
| `admin-text` | `portal-text` | |
| `admin-muted` | `portal-muted` | 透明度后缀（`/70`）保留 |
| `admin-accent` | `portal-mint` | **反转**：`bg-admin-accent text-white` → `bg-portal-mint text-portal-bg`（薄荷是亮色，白字不可读） |
| `hover:bg-admin-bg` | `hover:bg-portal-surface2` | 暗色下 hover 要提亮，不是压暗 |

`ApkSignatureCard` 的 variant 实现用一张映射表，别在模板里写两遍结构：

```ts
const skin = computed(() => props.variant === 'portal'
  ? { card: 'border-portal-border bg-portal-surface', label: 'text-portal-muted', value: 'text-portal-text', badge: 'bg-portal-surface2 text-portal-mint' }
  : { card: 'border-admin-border bg-white',            label: 'text-admin-muted',  value: 'text-admin-text',  badge: 'bg-admin-bg text-admin-accent' })
```

### 4.4 `AdminView.vue`

- 删除「APK 分析」按钮（含 `router.push('/admin/apk')`）。
- 「从 APK 导入」保留（它写库、要管理员）。
- 不加任何指向工具箱的跳转（后台是后台，工具是工具；想用工具走顶部导航）。

## 5. 关键决策

| # | 决策 | 理由 |
|---|------|------|
| D1 | 只读接口用**显式白名单**，不放开 `/api/v1/appmarket/**` | 前缀放开会让"未来新增的写接口"默认公开；白名单失效模式是"游客被打回登录"（吵但安全），前缀失效模式是"写接口裸奔"（静默危险） |
| D2 | `LOGIN_PATTERNS` 置空但保留结构 | 回退成本一行；删分支则要重写 `preHandle`，不值得 |
| D3 | 给 `me()` 加 `silent401` | 游客态把"首页能否打开"绑在后端一个接口的状态码上太脆；5 行换掉一整类事故 |
| D4 | 工具区走 `portal` 主题，而不是"后台主题但换个入口" | "移出后台"如果连皮肤都没移出，用户认知不会变；且公开工具与后台浅色 UI 混在一起容易让人以为在后台里 |
| D5 | 共享组件用 `variant` 而不是全局换 token | 全局替换会让后台的「从 APK 导入」弹窗变成暗底浅字，属于把一个问题换成另一个问题 |
| D6 | `/admin/apk` 重定向而非删除 | 旧书签/聊天记录里的链接不能 404；重定向成本一条路由 |
| D7 | 修 `VueSpaController` 多段回退 | 不修的话工具箱"能点进去但一刷新就 404"，等于交付一个半成品；顺带修好既有的 `/appmarket/:id` 深链 |
| D8 | 不引"游客 session / 匿名用户"概念 | 游客就是"没有 session 的请求"，够用；引入匿名身份会把鉴权复杂度抬高一个量级 |

## 6. 风险与兼容性

| 风险 | 影响 | 缓解 |
|------|------|------|
| 门户内容对全网公开 | 之前"登录才可见"的预期被改变 | 需求里已明确提示；如需部分裁剪，另开"游客可见字段"设计，不在本次范围内 |
| `incremental-patch` 公开 | 若接口返回了不该公开的信息则暴露 | 实现时先看该接口返回什么（补丁二进制 + 元信息），确认无敏感字段；有则从白名单移除 |
| 共享组件主题串味 | 后台导入弹窗变暗底 / 工具页出现浅底块 | `variant` 默认 admin；验收里专门有一条"后台弹窗仍是浅色" |
| 游客访问 `/admin` 先渲染再被 401 弹走 | 一闪而过的空表格 | 可接受（有跳转反馈）；如需更干净，`AdminView` 挂载前判断 `auth.user?.role !== 'ADMIN'` 直接跳登录 |
| 回退上限 3 段 | 未来更深的路径又要改 | 注释写明上限与扩展位置 |
| 搜索引擎抓取 | 公开后可能被索引 | 若不想被索引，加 `robots.txt` 或 `noindex`，属于独立决策，本次不做 |

## 7. 回滚形态

| 位置 | 回滚 |
|------|------|
| `AuthInterceptor.kt` | 把 `LOGIN_PATTERNS` 恢复为原四条 |
| `WebMvcConfig.kt` | 删掉三条 API 白名单 |
| `VueSpaController.kt` | 恢复单段 `@GetMapping("/", "/{path:[^\\.]*}")` |
| 前端 | 删 `/tools` 路由与 `ToolsView.vue`；`ApkAnalyzerView` 可改回 `/admin/apk`；`NavBar` 去掉工具箱项与游客登录按钮 |

后端三处都是规则级改动，回滚即"把列表加回去"，不涉及数据与业务逻辑。
