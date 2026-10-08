# 执行计划：APK 签名与完整性分析

> 任务：`09-28-apk-signature` ｜ 需求 `prd.md` ｜ 设计 `design.md`
> 前置：`09-28-apk-inspect-core` 已交付 `zipReader`（`readSlice` / `readCentralDirectory`）与
> `ApkSignatureCard.vue` 占位组件。本任务不修改 core 已有函数语义。
> 状态：**实现完成 + 解析层已对照 openssl 验证**（2026-10-08）。

## P0 前置

- [x] core 已交付：`types.ts` 的 `ApkSignatureSummary` 占位类型、`ApkSignatureCard.vue` 占位、
      `analyzeApk` 的 `signature` 阶段、`readCentralDirectory()` 返回 `cdOffset`。
- [x] 签名样本（本地临时目录 `/tmp/apktest`，不入库）：
      1. **v1-only**：用 `openssl req -x509` 生成自签证书，`crl2pkcs7` 打成 PKCS#7，以 STORED 写入
         `META-INF/CERT.RSA` 重建 `test_signed.apk`（真实 PKCS#7 结构，可提取证书）。
      2. **v2+v3 / v3.1 / 多签名者**：无本地真实 release 包，**留待用户侧真实样本**（AC1/AC4 部分项待核）。
      3. **debug 证书**：`CN=Android Debug` 自签证书已生成，验证 `debugCert` 命中（AC3）。
- [x] 核对工具：`openssl` 可用，作为字段/指纹基准（ground truth）。`apksigner` / `keytool` 本机未确认，
      AC2/AC3 以 openssl 输出归一后比对（已在下方记录）；两者具备时应补跑并写入记录。

## P1 DER 基础层

- [x] `frontend/src/utils/apk/der.ts`：`parseDer` / `readOid` / `readInteger` / `readString`，
      含长格式长度（≤4 字节）、`maxDepth=24`、越界与指针前进断言（`design.md` §4.1）。
- [x] 自查：对真实证书 DER，`parseDer` 正确产出 TLV 树；畸形输入（截断、长度越界）抛 `DerError` 不崩。

## P2 X.509 解析层

- [x] `frontend/src/utils/apk/x509.ts`：`parseCertificate()` + `formatName()` + OID 映射表（`design.md` §4.5）。
- [x] 自查：与 `openssl x509 -inform DER -noout -subject -issuer -dates -serial` 输出一致。
      修复 2 个 bug：① `parseCertificate` 把 `parseDer` 返回的 root 当成外层包装、多取一层 `children[0]`
      （root 本就是 `Certificate` SEQUENCE）；② 序列号首字节 `0x00`（DER 正整数符号位补零）未去掉，
      现规范化去掉前导 00，与 apksigner/openssl 显示一致。

## P3 APK Signing Block（v2/v3/v3.1）

- [x] `frontend/src/utils/apk/signingBlock.ts`：`readSigningBlock()` + `extractSignerCertificates(value, scheme)`，
      **按 ID 分派 v2/v3 的 signer 结构**（v3 多跳 8 字节 min/maxSdk，`design.md` §4.3）。
- [ ] 自查（待真实 v2/v3 样本）：对现代 release 包打印 ID 列表确认含 `0x7109871a` / `0xf05368c0` /
      `0x1b93ad61`，且 CD 前 16 字节 magic 命中。当前仅在 v1-only 样本上验证「无块→全 false」。

## P4 v1 签名路径

- [x] `frontend/src/utils/apk/zipReader.ts` 的 `readEntryBytes()`（local header 定位 + STORED/DEFLATE）。
- [x] `frontend/src/utils/apk/v1Signature.ts`：定位 `META-INF/*.{RSA,DSA,EC}` → 取字节 → PKCS#7 导航 → 证书 DER[]。
      修复 1 个 bug：`extractPkcs7Certs` 同样多取一层 `children[0]`（root 即 `ContentInfo` SEQUENCE），已修正。
- [x] 自查：对 `test_signed.apk`（真实 PKCS#7 条目）成功提取 1 张证书；对无签名块样本不误报。

## P5 编排与指纹

- [x] `frontend/src/utils/apk/signature.ts`：`analyzeSignature()` —— 组装 `schemes` / `v1EntryNames` /
      证书解析 / 指纹（SHA-256 + SHA-1，冒号分隔大写）/ 按 sha256 或 subject+serial 去重 /
      `integrityVerified: false` / `degraded` 收敛。
- [x] `analyzeApk.ts` 增补 `signature` 阶段（顺序 `zip → manifest → signature → hash`），失败只写 `warnings`。

## P6 UI 接入

- [x] `frontend/src/components/apk/ApkSignatureCard.vue`（`design.md` §5）：
      方案徽章 / 证书列表 / 调试证书与自签名标签 / compact 与 full 两档指纹 / 复制按钮 /
      固定脚注「仅检测签名方案与证书，未校验签名有效性」/ 无签名与降级文案。
- [ ] 同一 APK 在查看器（full）与导入弹窗（compact）一致性（AC8）：依赖 `09-28-apk-import-detail`（child 2），待其落地后共验。

## P7 验证

- [x] `cd frontend && npm run typecheck`：通过（vue-tsc --noEmit）。
- [x] `cd frontend && npm run build`：通过（102 模块，产物进 `src/main/resources/static`）。
- [x] 解析层对照 openssl（`/tmp/apktest`，Node 24 + esbuild 打包纯逻辑后运行）：见下方「验证记录」。
- [ ] 降级演练 AC5：DevTools 删除 `DecompressionStream` / `crypto.subtle` 各跑一次（headless 未做，待浏览器侧）。
- [x] 畸形输入演练 AC6：4 字节假 `CERT.RSA` 样本 → 面板显示降级 `DER 内容越界`、不白屏、不死循环。

## 验证记录（2026-10-08，对照 openssl）

> 方法：用 esbuild 把 `x509.ts`/`v1Signature.ts`/`signature.ts`/`zipReader.ts` 打包为 ESM，在 Node 24 读取
> `openssl` 生成的证书与合成签名 APK 运行；指纹以 `openssl x509|dgst` 为基准。

**AC1 方案探测**（v1-only 合成包 `test_signed.apk`）：
```
schemes = { v1: true, v2: false, v3: false, v3_1: false }   ✓
v1EntryNames = ["META-INF/CERT.RSA"]                          ✓
```
（v2+v3 / v3.1 / 多签名者真实样本待用户侧补充核对。）

**AC2 指纹**（证书 c1，自研 vs openssl 归一后一致）：
```
SHA-256: 9F:18:1E:C7:26:86:D5:C7:76:0F:42:C0:FC:D0:8C:60:0C:81:02:41:83:68:64:8A:0D:F9:FB:0C:02:FC:5B:AB
SHA-1  : 26:1C:87:49:94:98:A3:4C:9E:D6:7B:B5:D1:59:DA:3B:F4:16:91:16
```
openssl 同值 ✓

**AC3 subject/issuer + 调试识别**（c1）：
```
subject = C=US, OU=Dev, O=Example, CN=Android Debug   （openssl 同，逆序拼接）
issuer  = 同上（自签）
serial  = 1272145DA3B5F33AB96FA3E2DC2DFB7939904C3C    （已去前导 00）
notBefore / notAfter 与 openssl -dates 一致
signatureAlgorithm = SHA256withRSA
debugCert = true（命中 CN=Android Debug）✓
```

**AC4 多证书**：代码对 `parseSignedData` 的证书段连续读取、全部入列；真实多签名者样本待用户侧。

**AC5 无 DecompressionStream**：`signature.ts` 中 `crypto.subtle` 缺失时 `fingerprintError` 标注、
`zipReader.inflateRaw` 缺失时抛错被 `analyzeV1` 捕获进 `degraded`；headless 未做，待浏览器侧 DevTools 演练。

**AC6 畸形输入**：4 字节假 `CERT.RSA` → `analyzeSignature` 返回 `degraded:["DER 内容越界"]`、`signerCount:0`、
面板不白屏 ✓。

**AC7 诚实标注**：`ApkSignatureCard.vue` 固定脚注「仅检测签名方案与证书，未校验签名有效性」✓。

**AC8 viewer/import 一致性**：待 `09-28-apk-import-detail` 落地后共验。

**AC9 门禁**：typecheck + build 均通过；无后端接口调用、无数据库改动、无鉴权常量改动 ✓。

## 风险文件与回滚点

| 文件 | 风险 | 回滚点 |
|------|------|--------|
| `frontend/src/utils/apk/zipReader.ts` | 增补函数可能影响 core 的文件构成统计 | 只新增 `readEntryBytes`，不动既有函数 |
| `frontend/src/utils/apk/analyzeApk.ts` | 阶段顺序变化影响进度文案与耗时 | 单文件小改；异常时移除 `signature` 阶段即回到 core 行为 |
| `frontend/src/components/apk/ApkSignatureCard.vue` | 被 child 2 的弹窗复用（compact） | 保留 props 契约（只吃 `analysis`） |
| 自研 DER 解析 | 畸形输入可能导致死循环/白屏 | `maxDepth` + 长度校验 + 指针前进断言；AC6 已演练 |

## 结论

签名分析功能（v1 路径 + 自研 DER/X.509/PKCS#7 + 指纹 + UI）**实现完成**，风险最高的解析层已对照
openssl 验证通过，端到端在合成签名 APK 上跑通（方案探测 / 证书解析 / 调试证书识别 / 指纹 / 降级均正确）。
遗留：**真实 v2+v3 / v3.1 / 多签名者样本**与 **AC5 浏览器侧降级演练** 需用户侧或浏览器环境补充；
**AC8** 随 `09-28-apk-import-detail` 共验。
