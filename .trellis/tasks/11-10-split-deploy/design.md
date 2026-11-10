# 设计：两端分开部署 + 无后端可用

> 任务：`11-10-split-deploy`。需求见 `prd.md`，执行顺序见 `implement.md`。本文件只做技术设计。

## 1. 总体形态

不做数据层抽象、不做 mock。只做三件事：**接口地址可配**、**失败可读**、**不依赖接口的能力真正脱钩**。

```
拓扑 A · 单机同源（现状，保持不变）
┌──────────────────────────────┐
│ Spring Boot :8080            │
│  ├─ /            静态产物     │
│  └─ /api/v1      接口         │
└──────────────────────────────┘
前端 VITE_API_BASE 默认 /api/v1 → 同源，无 CORS、cookie Lax 即可

拓扑 B · 静态托管 + 同域反代（推荐上线形态，本任务不强制但文档给）
┌───────────┐  /api/*  ┌──────────┐
│ nginx     │─────────▶│ 后端     │
│ 静态 dist │  反向代理 │ 内网:8080│
└───────────┘          └──────────┘
同源 → 无需 CORS，cookie 正常

拓扑 C · 真跨域（本任务新建能力）
┌──────────────┐  跨域 XHR + cookie  ┌────────────────┐
│ 静态站 :5173 │────────────────────▶│ 后端 api.xx:443│
│ /  /admin    │◀────────────────────│ CORS 白名单     │
└──────────────┘                    └────────────────┘
需要：后端 CORS 开关 + 白名单 + 凭证；cookie SameSite=None; Secure → 必须 HTTPS
```

## 2. 前端：接口地址与错误归一化

### 2.1 `src/api/client.ts`（只改这一处，全站生效）

```ts
const API_BASE = (import.meta.env.VITE_API_BASE || '/api/v1').replace(/\/+$/, '')
const TIMEOUT_MS = 15_000

export type ApiErrorKind = 'network' | 'bad-response' | 'unauthorized' | 'forbidden' | 'business'

export class ApiError extends Error {
  constructor(message: string, readonly status?: number, readonly kind: ApiErrorKind = 'business') {
    super(message)
    this.name = 'ApiError'
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), TIMEOUT_MS)
  let res: Response
  try {
    res = await fetch(API_BASE + path, {
      credentials: 'include',                       // 同源等价 same-origin，跨域才真正带 cookie
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      signal: ctrl.signal,
      ...init
    })
  } catch (e) {
    const aborted = (e as Error)?.name === 'AbortError'
    throw new ApiError(
      aborted
        ? `请求超时（${TIMEOUT_MS / 1000} 秒），后端可能未启动或响应过慢`
        : '无法连接后端服务，请确认后端已启动、地址与跨域配置正确',
      undefined,
      'network'
    )
  } finally {
    clearTimeout(timer)
  }

  if (res.status === 401) {
    if (window.location.pathname !== '/login') window.location.href = '/login'
    throw new ApiError('未登录', 401, 'unauthorized')
  }
  if (res.status === 403) throw new ApiError('需要管理员权限', 403, 'forbidden')

  // 关键：先判 Content-Type 再解析。后端没起时 dev 是 500+text/html、静态站是 404+HTML，
  // 直接 res.json() 会抛 "Unexpected token '<'" —— 那是最没用的报错。
  if (!(res.headers.get('content-type') || '').includes('application/json')) {
    throw new ApiError(`后端服务不可用（HTTP ${res.status}），请确认后端已启动`, res.status, 'bad-response')
  }

  const body = (await res.json()) as ApiResponse<T>
  if (body.code !== 0 && body.code !== 200) {
    throw new ApiError(body.msg || '请求失败', res.status, 'business')
  }
  return body.data
}
```

要点：

| 点 | 说明 |
|----|------|
| `credentials: 'include'` | 同源请求下与 `same-origin` 等价；跨域时才会真正带上 cookie |
| 只 `catch` fetch 本身 | fetch 只在「网络不可达 / CORS 被拒 / abort」时 reject；HTTP 500 是**正常 resolve** 的，所以必须另判 Content-Type |
| 401 语义不变 | 沿用现有跳 `/login`，并保留 `/login` 不自我跳转的判断 |
| `ApiError.kind` | 给调用方留分类余地（现在视图只用 `message`，不影响） |
| 超时 15s | 后端半死（TCP 通但不回）时不至于永远转圈 |

视图层零改动：所有页面都是 `catch (e) => show(e.message)`。

### 2.2 环境变量

```ts
// src/vite-env.d.ts
interface ImportMetaEnv {
  readonly VITE_API_BASE?: string
  readonly VITE_BASE?: string
  readonly VITE_OUT_DIR?: string
  readonly VITE_ROUTER_MODE?: 'history' | 'hash'
}
```

| 变量 | 默认 | 用途 |
|------|------|------|
| `VITE_API_BASE` | `/api/v1` | 跨域时写 `https://api.example.com/api/v1` |
| `VITE_BASE` | `/` | 子路径部署（`/zaze/`）或相对部署（`./`） |
| `VITE_OUT_DIR` | `../src/main/resources/static` | 静态构建时改 `dist` |
| `VITE_ROUTER_MODE` | `history` | 无 fallback 的静态托管用 `hash` |

## 3. 前端：把「不依赖接口的功能」真正解耦

### 3.1 APK 分析（唯一真正需要修的非接口功能）

- 现状：`PARSER_SRC = '/vendor/app-info-parser/app-info-parser.min.js'` → 绝对路径，靠 Spring 静态资源伺服；`frontend/` 无 `public/`，dev 与静态托管都 404。
- 改法：
  1. 把后端 `src/main/resources/static/vendor/` 复制到 `frontend/public/vendor/`（Vite 的 `publicDir` 会原样带到产物根）；
  2. `PARSER_SRC` 改为 `import.meta.env.BASE_URL + 'vendor/app-info-parser/app-info-parser.min.js'`（`BASE_URL` 恒以 `/` 结尾，天然支持子路径部署）；
  3. 加载失败的 reject 文案改成可读的「解析库加载失败，请确认 /vendor/app-info-parser 可访问（HTTP xxx）」。
- 迁移后：dev（:5173）、静态托管、Spring 伺服三种形态都能拿到脚本。
- 副本关系：`public/vendor` 成为前端权威副本；`npm run build` 输出到 `static/` 时会把它写回去（内容一致），另加 `sync:vendor` 脚本用于后端更新 vendor 后重新同步。

### 3.2 其余非接口能力（现状已可用，仅需确认不被接口失败拖垮）

| 能力 | 依赖 | 后端不在时的表现 |
|------|------|------------------|
| 路由跳转 / 主题 / 导航 / 页脚 | 无 | 正常 |
| 首页 hero 等静态区 | 无 | 正常 |
| 列表 / 详情 / 分类 / 后台表格 | 接口 | 显示可读错误 + 可重试，不卡 loading |
| 登录 | 接口 | 报「无法连接后端服务…」 |

### 3.3 页面级降级核对（实现时必须逐项确认的不变式）

> 不变式：任何 `await api.*` 要么自己 `catch`，要么由调用方的 `catch` 兜住；**loading 必须在 `finally` 复位**，否则失败时页面永远停在"加载中…"。

| 文件 | 现有写法 | 需要确认 |
|------|----------|----------|
| `store/auth.ts` | `init()` 有 try/catch/finally | 已是可读失败（静默置 null），不改 |
| `HomeView.vue` | `api.apps` then/catch | 失败时是否正确退出 loading、是否显示错误 |
| `AppMarketView.vue` | `api.apps` + `appCategories` | 两个请求各自失败时的兜底；分类失败不应让整页空白 |
| `AppDetailView.vue` | `onMounted` try/catch | 失败时显示「应用不存在 / 加载失败」而非空白 |
| `AdminView.vue` | `.catch(show('error'))` | 已可读化（受益于 R2） |
| `VersionManagerModal.vue` | 多处 `getApp` + 写操作 | 失败时弹窗内要有提示，不能静默 |

原则：**只补 `try/catch/finally` 与提示文案，不改数据流与视图结构。**

## 4. 前端：构建形态

`vite.config.ts` 从环境变量取值，默认值与现状完全一致：

```ts
export default defineConfig({
  base: process.env.VITE_BASE ?? '/',
  build: {
    outDir: process.env.VITE_OUT_DIR ?? '../src/main/resources/static',
    emptyOutDir: false   // 保住 static/vendor、static/sql
  }
})
```

| 命令 | base | 产物 | 场景 |
|------|------|------|------|
| `npm run dev` | `/` | — | 开发（proxy 或 `VITE_API_BASE` 跨域） |
| `npm run build` | `/` | `src/main/resources/static` | 现状生产（Spring 伺服） |
| `npm run build:static` | `./` | `frontend/dist` | 静态托管（配 `VITE_ROUTER_MODE=hash` 可无 fallback） |
| `npm run preview` | — | — | 验静态产物（自带 history fallback） |
| `npm run sync:vendor` | — | `public/vendor` | 后端 vendor 更新后同步 |

`base: './'` 的约束：相对路径下**只能配 hash 路由**（history 深链会让相对资源解析到 `/appmarket/xxx/assets/...`）。子路径部署（`/zaze/`）则用绝对 base + 服务器 fallback。

## 5. 后端：CORS 开关（默认关闭）

新增 `src/main/java/com/zaze/server/config/CorsConfig.kt`：

```kotlin
@Configuration
@ConditionalOnProperty(prefix = "zaze.cors", name = ["enabled"], havingValue = "true")
class CorsConfig(
    @Value("\${zaze.cors.allowed-origins:}") private val allowedOrigins: String
) {
    @Bean
    fun corsFilter(): FilterRegistrationBean<CorsFilter> {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = allowedOrigins.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true     // session cookie 必须
            maxAge = 3600
        }
        val source = UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", config) }
        return FilterRegistrationBean(CorsFilter(source)).apply { order = Ordered.HIGHEST_PRECEDENCE }
    }
}
```

设计理由：

| 点 | 理由 |
|----|------|
| `CorsFilter` 而非 `addCorsMappings` | 预检 `OPTIONS` 必须在 `AuthInterceptor` 之前被应答。Filter 在 handler mapping / interceptor 之前，顺序确定；不依赖"MVC 预检不进拦截器"这条隐含行为 |
| `@ConditionalOnProperty` 默认关闭 | 严格的安全默认：不配就完全等同今天的行为，不可能因为引入本次改动而误开放跨域 |
| `allowedOriginPatterns` 而非 `allowedOrigins` | `allowCredentials = true` 下不允许 `*`，用 patterns 才能表达白名单 |
| `allowCredentials = true` | 鉴权是 HttpSession，不带凭证等于没登录 |
| 注册到 `/**` | 接口都在 `/api/v1/**`，但运维常直接从 `/api` 前缀调，注册宽一点更省事；鉴权仍由拦截器管 |

**cookie 部分零代码**，用 Spring 原生配置项（环境变量即可）：

```bash
ZAZE_CORS_ENABLED=true
ZAZE_CORS_ALLOWED_ORIGINS=https://zaze.example.com
SERVER_SERVLET_SESSION_COOKIE_SAME_SITE=none
SERVER_SERVLET_SESSION_COOKIE_SECURE=true     # 必须 HTTPS
```

## 6. 跨域的硬限制（必须写进文档，否则一定踩）

浏览器对**跨站** cookie 的规则：`SameSite=None` 必须同时 `Secure`，而 `Secure` 要求 HTTPS。

| 环境 | 结果 |
|------|------|
| 生产：前端 https://a.com，后端 https://api.a.com | ✅ 正常 |
| 本地：前端 http://localhost:5173，后端 http://localhost:8080 | ❌ 登录"成功"但下次请求 401（cookie 被丢弃） |
| 本地 mkcert 双 HTTPS | ✅ |
| 同域反代（拓扑 B） | ✅ 无跨域，最省事 |

三条兜底（文档给命令）：

1. **同域反代**：nginx `location /api/ { proxy_pass http://127.0.0.1:8080; }`，前端 `VITE_API_BASE` 保持默认 → 本任务之外但最推荐；
2. **本地 HTTPS**：mkcert 签发 `localhost` 证书，前端 `--host` + https、后端 `server.ssl.*`；
3. **仅调试**：Chrome 启动参数临时放宽（不写入任何脚本）。

另一个必须写清的点：分开部署后**门户 HTML 变成公开可达**（静态服务器不认后端登录），但数据接口仍在 401 后面 —— 没登录时页面能打开、数据拿不到、会跳 `/login`。这是拓扑变化带来的语义变化，不是 bug。

## 7. 关键决策

| # | 决策 | 理由 |
|---|------|------|
| D1 | 不做本地数据层（推翻上一版设计） | 用户口径是「接口正常报错、非接口功能可用」，不是「伪造一份数据」。假数据会带来"我改的怎么没进库"的长期困惑 |
| D2 | 错误归一化集中在 `request()` | 视图层本来就是 `catch → show(message)`，一处改动全站可读，零视图逻辑改动 |
| D3 | 判 `Content-Type` 再 `res.json()` | 后端没起时响应体是 HTML，直接解析只能得到 `Unexpected token '<'` |
| D4 | CORS 用 Filter + 默认关闭 | 预检顺序确定；安全默认，不配即等于今天 |
| D5 | cookie 走 Spring 配置项，不写代码 | 少一处代码就少一处回归面；运维用环境变量即可 |
| D6 | vendor 迁到 `public/` + `BASE_URL` 拼路径 | 这是 APK 分析"脱离后端"的唯一硬依赖；`BASE_URL` 顺带解决子路径部署 |
| D7 | `build:static` 复用同一份 vite 配置 | 只改 base/outDir，避免出现第二套构建逻辑（两套配置必然漂移） |
| D8 | `base: './'` 只配 hash 路由 | 相对 base + history 深链必然 404，文档写死这条规则而不是让人试 |

## 8. 风险与兼容性

| 风险 | 影响 | 缓解 |
|------|------|------|
| CORS 被拒在浏览器里只表现为 `TypeError: Failed to fetch` | 分不清"后端没起"还是"跨域没配" | 统一文案同时提示两种可能；文档给排查顺序（先 DevTools Console 看 CORS 报错，再看 Network 状态码） |
| 跨域登录态在本地 HTTP 下不成立 | 本地联调"登录不进去" | 文档明写 + 三条兜底；验收项标 `blocked` 而非假装通过 |
| `public/vendor` 与 `static/vendor` 两份副本漂移 | APK 解析库版本不一致 | `npm run build` 会覆盖 `static/vendor`；另加 `sync:vendor`；文档写明权威副本是 `public/` |
| 分开部署后门户 HTML 公开 | 与"门户需登录"的旧语义不符 | 文档点明；如需真保护，前端侧无法解决（静态文件），只能靠反代加 basic auth |
| `VITE_BASE=./` + history 路由 | 深链资源 404 | 只允许 `./` 配 hash；实现里在 `vite.config.ts` 加一行启动告警 |
| 后端误配 `secure=true` 却用 HTTP | 登录后立刻掉线 | 文档列为「排查第一项」 |
| 15s 超时对「慢但正常」的采集类接口偏短 | 长任务被中断 | 超时只包 `request()` 的 HTTP 往返；SSE（`TraceModal`）走独立链路，不受影响 |

## 9. 回滚形态

| 位置 | 改动 | 回滚 |
|------|------|------|
| `src/api/client.ts` | BASE 可配 + 错误归一化 | `git checkout -- src/api/client.ts` |
| `src/utils/apk/manifestParser.ts` | `PARSER_SRC` + 报错文案 | 同上 |
| `frontend/public/vendor/` | 新增资产 | 删目录 |
| `vite.config.ts` / `package.json` | base/outDir 读 env + 脚本 | 还原两个文件 |
| `views/*.vue` | 仅补 catch/finally 与文案 | 同上 |
| `src/main/java/.../CorsConfig.kt` | 新增，默认关闭 | 删文件（不配 `zaze.cors.enabled` 时它本就不生效） |

后端只多一个**默认不激活**的配置类，回滚零风险。
