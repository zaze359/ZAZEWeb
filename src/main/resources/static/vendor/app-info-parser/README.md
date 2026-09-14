# app-info-parser

浏览器端解析 APK（同时支持 IPA），用于管理后台「从 APK 导入」自动提取应用元数据。

- 版本：1.1.6
- 许可：MIT（见同目录 `LICENSE`）
- 上游：<https://github.com/chenquincy/app-info-parser>（npm: `app-info-parser`）
- 产物：`app-info-parser.min.js`（UMD；浏览器下挂到 `window.AppInfoParser`）

## 用法

```js
new window.AppInfoParser(file).parse().then(function (r) {
    // r.package            包名
    // r.versionName        版本名
    // r.versionCode        版本号
    // r.application.label  应用名
    // r.icon               base64 data URI 图标
});
```

入参为浏览器 `File` / `Blob`。

**限制**：受 CORS 约束，浏览器无法直接 `fetch()` 远端 APK 后解析（官网/应用宝均无 CORS 头），
本库只能解析本地选择的文件——这也是「从 APK 导入」采用本地选文件流程的原因。

## 加载方式

管理后台页面**不静态引入**本文件，改为点击「从 APK 导入」时按需加载，
避免 459 KB 拖慢首屏。加载入口见 `static/js/appmarket-admin.js`。

## 升级

如需升级，替换 `app-info-parser.min.js` 并同步 `LICENSE`，确认全局对象仍为
`window.AppInfoParser`、返回字段结构未变。
