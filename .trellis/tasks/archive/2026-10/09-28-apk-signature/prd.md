# 需求：APK 签名与完整性分析

> 父任务：`09-28-apk-inspector` ｜ 依赖 `09-28-apk-inspect-core`（ZIP 读取能力 + `ApkAnalysis.signature` 契约 + 面板组件）
> 本任务是三个子任务里**技术风险最高**的一个：需要自研 DER/ASN.1 最小解析。

## 目标与用户价值

收录 APK 时判断「这个包是谁签的、用什么签名方案」：

- **是否可信来源**：证书 CN/O 能看出是官方发布证书还是 `Android Debug` 调试证书（调试包不该进图鉴）
- **兼容性风险**：只有 v1 签名的包在 Android 11+ 上部分场景装不上（v2 是 Android 7+ 的要求）
- **同源核验**：同一应用的多个版本若证书指纹不同，说明换过签名（可能要卸载重装），收录时值得标注

## 已确认的技术事实

- 三种签名方案落点不同：
  - **v1（JAR 签名）**：`META-INF/*.RSA` / `*.DSA` / `*.EC`（PKCS#7 DER）+ `META-INF/*.SF` + `MANIFEST.MF`，
    是 ZIP 里的**普通条目**（可能 DEFLATE 压缩）。
  - **v2 / v3 / v3.1（APK Signature Scheme）**：数据在 **APK Signing Block** —— 位于 ZIP 中央目录**之前**，
    末尾 16 字节为 magic `APK Sig Block 42`；块内为 `(uint64 长度, uint32 ID, value)` 序列，
    ID `0x7109871a` = v2、`0xf05368c0` = v3、`0x1b93ad61` = v3.1。
- 证书是 X.509 DER；v2/v3 的证书在块内的 length-prefixed 结构里，v1 的证书在 PKCS#7 `ContentInfo → SignedData → certificates`。
- 浏览器可直接 `crypto.subtle.digest('SHA-256'|'SHA-1', certDer)` 算指纹，
  但**定位证书 DER 与解析字段必须自研 ASN.1**。
- `DecompressionStream('deflate-raw')` 是解压 v1 条目的唯一浏览器途径（Chrome 103+ / Safari 16.4+ / Firefox 113+）；
  不支持时若条目为 STORED 仍可读。
- vendor 的 `app-info-parser` **不做任何签名相关解析**，无法复用。
- 前端无测试框架，门禁 `npm run typecheck` + `npm run build`。

## 需求

- **R1 方案探测**：识别 v1 / v2 / v3 / v3.1 是否存在，返回布尔集合；只做「存在性」，不做有效性。
- **R2 证书提取**：
  - v2/v3 路径：从 Signing Block 按 `signer → signed data → certificates` 逐层取 X.509 DER；
  - v1 路径：读 `META-INF/*.RSA|*.DSA|*.EC` 条目（必要时用 `DecompressionStream` 解压）→ 解析 PKCS#7 取证书；
  - 多个签名者 / 多证书**全部列出**，不取第一个了事。
- **R3 证书信息**：每个证书给出
  `SHA-256 指纹`（冒号分隔大写十六进制）、`SHA-1 指纹`、`subject`（CN/O/OU/C 等，按 openssl 风格拼接）、
  `issuer`、`notBefore` / `notAfter`、`签名算法`（OID → 名称，未收录则回退 OID）、`自签名` 标记。
- **R4 接入内核与 UI**：
  - `analyzeApk` 增加 `signature` 阶段（进度回调新增 stage），结果填入 `ApkAnalysis.signature`；
  - `ApkSignatureCard.vue`（core 已建的空占位）改为渲染真实数据：
    compact = 方案徽章 + 首个证书的 CN 与指纹短形式；full = 全部证书 + 完整指纹 + 有效期 + 一键复制指纹。
- **R5 诚实标注**：UI 必须显式写明「**仅检测签名方案与证书，未校验签名有效性**」，
  不得使用「签名有效 / 验证通过」这类措辞。
- **R6 降级**：任一路径失败（无 `DecompressionStream`、签名块超阈值、DER 解析异常）只降级签名维度，
  写入 `warnings`；`signature` 可为 `null` 或部分字段缺失，**绝不影响** SDK/权限/文件构成维度。
- **R7 不做有效性校验**：不实现 v2/v3 digest 与 APK 内容比对、不校验证书链与信任锚（见 Out of Scope）。

## 验收标准

- **AC1** 方案探测：v1-only 包识别为 `{v1: true, v2: false, v3: false}`；同时带 v1+v2 的包两者都为真；
  纯 v2/v3 包正确；v3.1（签名轮换）包能识别。
- **AC2** 证书 SHA-256 与 SHA-1 指纹与 `apksigner verify --print-certs <apk>` 输出**归一后完全一致**
  （消除空格/冒号差异、统一大小写后比较）；有 `keytool -printcert -jarfile` 时一并对照。
- **AC3** `subject` / `issuer` 的 CN、O、C 与 `apksigner` 输出一致；调试包能识别出 `CN=Android Debug`
  （UI 据此提示「调试证书」）。
- **AC4** 多签名者 / 多证书包：证书数量与 `apksigner` 报告一致，全部列出。
- **AC5** 浏览器不支持 `DecompressionStream`（DevTools 临时删除该 API 模拟）时：
  v1 方案仍被报告，证书区标注「当前浏览器不支持解压该条目，未能提取证书」，并出现在 `warnings`。
- **AC6** 异常输入（签名块声明长度超出文件、DER 截断、无 `META-INF` 签名）→ 面板显示降级结果与原因，不报错白屏。
- **AC7** 面板明确出现「未校验签名有效性」说明（R5）。
- **AC8** 同一 APK 在查看器（full）与导入弹窗（compact）显示的方案与指纹一致。
- **AC9** `npm run typecheck` 与 `npm run build` 通过；无后端接口调用、无数据库改动。

## Out of Scope

- **签名有效性校验**：v2/v3 digest 与内容比对、`SF`/`MANIFEST` 摘要比对、证书链构建、信任锚判定
  —— 成本极高，一律不做（R7）。
- v4 签名（`*.idsig` 独立文件）、时间戳（TSA）、公证/源戳的**验证**（只检测 v3.1 ID 是否存在）。
- 证书吊销（CRL/OCSP）、密钥强度评估（RSA 位数 / EC 曲线）、证书透明度。
- 组件清单、DEX 扫描、加固识别（父任务已排除）。
- 把签名信息落库 / 门户展示。
