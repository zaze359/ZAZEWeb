# PRD — 应用市场门户（App Market Portal）

> Task: `09-12-app-market` · Module: `feature/appmarket`

## 背景

zaze-server 当前已有 showcase / application / ad / message 等 feature 模块，但缺少「应用市场」类功能。本次新增一个**门户型**功能：展示应用列表，每个应用可有多版本，每个版本可挂多个不同来源的下载地址。

约束（用户明确）：**第一阶段不做自有二进制存储**，下载地址直接收集网上公开来源（GitHub Releases、F-Droid、官方站点等），系统只存储元数据与外链 URL。

## 目标

1. 应用列表门户页（Thymeleaf + Bootstrap，沿用项目既有前端栈）。
2. 三级数据模型：**应用(App) → 版本(Version) → 下载源(DownloadSource)**。
3. 配套 JSON API，供门户页 JS 异步加载与渲染。
4. 启动期种子数据加载：从 classpath 读取已整理的公开下载地址 JSON 并入库（首次为空时写入），满足「收集网上的」诉求。

## 非目标（本期不做）

- 自有文件存储 / 二进制托管、下载代理、鉴权、计费。
- 自动化爬虫（仅提供可扩展的种子数据 + 未来 Collector 扩展点说明）。

## 用户故事 / 验收标准

- AC1：访问 `/appmarket` 能看到应用卡片列表（名称、图标、分类、简介、版本数）。
- AC2：点击应用可见其全部版本；点击版本可见多个来源下载按钮（来源名 + 类型 + 链接），点击直接跳转外部地址。
- AC3：JSON API `GET /api/v1/appmarket/apps` 返回应用列表；`GET /api/v1/appmarket/apps/{id}` 返回含版本与下载源的完整详情。
- AC4：首次启动且数据库为空时，自动写入种子数据（≥3 个真实应用，每应用 ≥1 版本，每版本 ≥2 来源）。
- AC5：新模块按项目规范注册（settings.gradle.kts、root build 依赖、bootJar 禁用），并通过编译。

## 依赖与影响

- 新增 `feature/appmarket` 模块；root `build.gradle.kts` 增加 `implementation(project(":feature:appmarket"))`。
- 复用 `core:common`（Response/PageResponse、BaseController、JsonUtil、@LoggerManage）与 `core:database`（BaseRepository）及 JPA / cache。
- 不改现有模块；不引入新 JSON 库（沿用 Gson 工具类）。
