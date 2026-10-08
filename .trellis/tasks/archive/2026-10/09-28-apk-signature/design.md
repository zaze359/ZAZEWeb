# 技术设计：APK 签名与完整性分析

> 任务：`09-28-apk-signature` ｜ 需求 `prd.md` ｜ 执行计划 `implement.md`
> 前置契约来自 `09-28-apk-inspect-core`（`ApkAnalysis` 与 `zipReader`），本任务只**新增**文件与**增补**函数。

## 1. 架构与边界

```
File + ZipEntry[] + cdOffset
        │
        ├─(v2/v3/v3.1) readSigningBlock()  ──▶ id→value ──▶ extractSignerCerts()  ──┐
        │                                                                          │ X.509 DER[]
        └─(v1)          findV1Entries() ──▶ readEntryBytes() ──▶ PKCS#7 → certs ──┘
                                                                                   │
                                                          der.ts / x509.ts ◀───────┘
                                                                   │
                                                    ApkSignatureSummary ──▶ ApkSignatureCard
```

- 边界：**纯浏览器本地**，无网络、无后端、不写库；不改 vendor 库。
- 分工：`der.ts` / `x509.ts` 是通用 ASN.1 层（不含 APK 知识）；`signingBlock.ts` / `v1Signature.ts`
  是 APK 知识层；`signature.ts` 是对外编排；UI 只消费 `ApkSignatureSummary`。
- 对 core 的侵入面（仅两处）：
  1. `zipReader.ts` 增补 `readEntryBytes()`（child 1 已预留 `readSlice`，本任务只加解压读条目）；
  2. `analyzeApk.ts` 增加一个 `signature` 阶段并把结果写进 `ApkAnalysis.signature`。
  两条都**不改** core 已有函数的语义与签名。

## 2. 模块清单（新增文件）

| 文件 | 职责 |
|------|------|
| `frontend/src/utils/apk/der.ts` | 最小 DER 解析：`parseDer(bytes, maxDepth)` → `DerNode { tag, headerLength, length, value, children }`；OID 解码；整数/字符串取值；长格式长度；严格边界与递归深度保护 |
| `frontend/src/utils/apk/x509.ts` | `parseCertificate(der)` → `RawCert`（subject/issuer/serial/validity/sigAlg）；Name(RDN) 解析；`OID_NAME` / 签名算法 OID 映射；`formatName()` |
| `frontend/src/utils/apk/signingBlock.ts` | `readSigningBlock(file, cdOffset)` → `Map<number, Uint8Array>`；`extractSignerCertificates(value, scheme)` → DER[] |
| `frontend/src/utils/apk/v1Signature.ts` | 定位 `META-INF/*.{RSA,DSA,EC}`；解密压缩条目 → PKCS#7 → 证书 DER[] |
| `frontend/src/utils/apk/signature.ts` | 对外 `analyzeSignature(file, entries, cdOffset)` → `ApkSignatureSummary \| null`（含指纹计算、去重、降级收敛） |
| `frontend/src/utils/apk/zipReader.ts`（**增补**） | `readEntryBytes(file, entry, { maxBytes })`：local header 定位 → 按 method 解压（STORED 直读 / DEFLATE 走 `DecompressionStream`） |
| `frontend/src/components/apk/ApkSignatureCard.vue`（**改写**） | 由 core 的「未分析」占位改为渲染真实数据（compact/full 两档） |
| `frontend/src/utils/apk/analyzeApk.ts`（**增补**） | 新增 `signature` 阶段；失败只写 `warnings` |

## 3. 数据契约（`types.ts` 中 `ApkSignatureSummary` 定稿）

```ts
export interface ApkSignerCert {
  subject: string            // "CN=..., O=..., C=..."（openssl 风格）
  issuer: string
  subjectCN?: string
  serial?: string            // 十六进制
  sha256?: string            // 冒号分隔大写十六进制；crypto.subtle 不可用时 undefined
  sha1?: string
  fingerprintError?: string  // 指纹不可用原因
  notBefore?: string         // ISO 8601
  notAfter?: string
  signatureAlgorithm?: string // "SHA256withRSA" 等；未收录回退 OID
  selfSigned?: boolean
  debugCert?: boolean        // subject 含 CN=Android Debug
}

export interface ApkSignatureSummary {
  schemes: { v1: boolean; v2: boolean; v3: boolean; v3_1: boolean }
  v1EntryNames: string[]      // 命中的 META-INF 签名条目（排障用）
  signerCount: number
  certs: ApkSignerCert[]      // 按 sha256 去重；无指纹时按 subject+serial 去重
  integrityVerified: false    // 字面量 false：类型层面禁止声称「已验证」
  degraded: string[]          // 降级原因（同步进 ApkAnalysis.warnings）
}
```

`integrityVerified: false` 是刻意设计 —— 让「未校验有效性」成为**类型约束**而非文案约定（R5/R7）。

## 4. 关键算法

### 4.1 最小 DER 解析（`der.ts`）

- 只支持 definite length（不定长 `0x80` 直接报错）；长格式长度最多 4 字节（>2^31 视为异常）。
- 结构：`{ tag: number, headerLength, length, value: Uint8Array, children?: DerNode[] }`；
  仅对 constructed 位（`tag & 0x20`）解析 children。
- 保护：`maxDepth = 24`、每个节点 `length <= 剩余字节`、child 遍历指针必须严格前进（防止畸形长度导致死循环）。
- 提供 `readOid(node)`（40x+y + base128）、`readInteger(node)`、`readString(node)`
  （UTF8String `0x0C` / PrintableString `0x13` / IA5String `0x16` / T61String `0x14` / NumericString `0x12` / BMPString `0x1E` → UTF-16BE）。

### 4.2 APK Signing Block（`signingBlock.ts`）

1. 读 `[cdOffset - 24, cdOffset)`：`bytes[8..24]` 必须等于 `APK Sig Block 42`，否则判定无 v2/v3 块（不是错误）。
2. `size2 = uint64LE(bytes[0..8])`；`blockStart = cdOffset - (size2 + 8)`；
   若 `size2` 越界（`blockStart < 0` 或 `size2 + 8 > MAX_BLOCK = 4MB`）→ 降级 + `warnings`。
3. 读整块 `[blockStart, cdOffset)`，跳过前 8 字节，循环解析 `(uint64 length, uint32 id, value)`，
   直到剩余 24 字节（`size2` + magic）为止；每次循环校验 `length >= 4` 且不越界。
4. 收集 `Map<id, value>`；只关心 `0x7109871a`(v2) / `0xf05368c0`(v3) / `0x1b93ad61`(v3.1)。

### 4.3 从 Signing Block 取证书（**最容易踩的坑**）

v2 与 v3 的 signer 结构**不同**，必须按 ID 分派：

```
v2 signer : length-prefixed( signedData ) | length-prefixed( signatures ) | length-prefixed( publicKey )
v3 signer : length-prefixed( signedData ) | uint32 minSdkVersion | uint32 maxSdkVersion
            | length-prefixed( signatures ) | length-prefixed( publicKey )
v3.1 沿用 v3 结构（minSdk=0 表示旋转集合，通常伴随 v3 存在）

signedData      : length-prefixed( digests ) | length-prefixed( certificates ) | length-prefixed( attrs )
certificates    : 连续的 length-prefixed( X.509 DER )
```

- 容错：解析过程中任一长度越界 → 记录 `degraded`，返回已取到的证书，不整体失败。
- 多个 signer → 全部遍历（AC4）。

### 4.4 v1（JAR 签名）取证书（`v1Signature.ts`）

1. 从 `ZipEntry[]` 中筛名称匹配 `^META-INF/.*\.(RSA|DSA|EC)$`（大小写不敏感）；记录 `v1EntryNames`。
2. `readEntryBytes(file, entry, { maxBytes: 512 * 1024 })`：
   - local header 定位：读 `entry.localHeaderOffset` 处 30 字节 → `nameLen` / `extraLen` →
     `dataStart = localHeaderOffset + 30 + nameLen + extraLen`；
     **压缩大小一律以中央目录的 `compressedSize` 为准**（用了 data descriptor 时 local header 里是 0）。
   - `method === 0` → 直接返回；`method === 8` → `DecompressionStream('deflate-raw')` 解压；
     其它 method → 降级。
3. PKCS#7 导航（`ContentInfo`）：
   - `root.children[1]`（`[0] EXPLICIT`）→ 其 `children[0]` = `SignedData` SEQUENCE。
   - 在 `SignedData.children` 中找**第一个 tag `0xA0` 且其子节点均为 SEQUENCE、且每个子节点的
     `children[0]` 又是 SEQUENCE** 的节点 → 即 `certificates [0] IMPLICIT SET OF Certificate`（避开 `crls [1]`）。
   - 形状判定失败时的兜底：递归扫描所有节点，收集「`SEQUENCE{ SEQUENCE{...}, ... }` 且含 ≥2 个 SEQUENCE 子节点」
     的节点作为候选证书，取全部。
4. 无 `DecompressionStream` 且条目非 STORED → 降级：`degraded.push('当前浏览器不支持解压 v1 签名文件，未能提取证书')`，
   但 v1 方案本身仍报告为存在（AC5）。

### 4.5 X.509 字段解析（`x509.ts`）

- `Certificate ::= SEQUENCE { tbsCertificate SEQUENCE, signatureAlgorithm SEQUENCE, signatureValue BIT STRING }`
- 定位 tbs 内各字段用 **tag 判定而非固定索引**（version 为可选 `[0] EXPLICIT`）：
  `[0]` 存在 → `sigAlg = c[2]`、`issuer = c[3]`、`validity = c[4]`、`subject = c[5]`；否则整体左移 1。
- `validity`：`UTCTime(0x17)` `YYMMDDHHMMSSZ`（`YY >= 50` → 19xx，否则 20xx）、
  `GeneralizedTime(0x18)` `YYYYMMDDHHMMSSZ` → 转 ISO 字符串。
- `Name`：`SEQUENCE OF RDN`，每个 RDN 为 `SET(0x31) OF SEQUENCE{ OID, value }`；
  已知 OID → 短名（`2.5.4.3 CN`、`2.5.4.6 C`、`2.5.4.7 L`、`2.5.4.8 ST`、`2.5.4.10 O`、`2.5.4.11 OU`、
  `1.2.840.113549.1.9.1 E`，未知保留点分 OID）；拼接顺序按 RDN 逆序（对齐 `openssl subject=` 输出）；
  多值 RDN 用 `+` 连接。
- 签名算法 OID 映射：`sha256WithRSAEncryption` / sha384 / sha512 / `ecdsa-with-SHA256|384|512` /
  `RSASSA-PSS` / `dsa-with-sha256`，未收录回退原始 OID（AC3）。

### 4.6 指纹与去重（`signature.ts`）

- `crypto.subtle.digest('SHA-256' | 'SHA-1', certDer)` → 大写 hex，每 2 位用 `:` 连接（apksigner 风格）。
- `crypto.subtle` 不可用 → 每个证书写 `fingerprintError`，整体加一条 `warnings`（不失败）。
- 去重键：`sha256`；无指纹时用 `subject + serial`。
- `debugCert`：`subject` 含 `CN=Android Debug`（大小写不敏感）。

### 4.7 接入 `analyzeApk`

- 阶段顺序：`zip` → `manifest` → `signature` → `hash` → `done`（签名阶段放在清单之后，避免与 vendor 库整包读取并发）。
- 签名阶段整体 `try/catch`：失败 → `signature = null` + `warnings.push(...)`，其余维度不受影响（R6）。
- `cdOffset` 由 `readCentralDirectory()` 一并返回（若 core 未返回，core 侧补一个返回值，属**接口扩展不破坏**：以新增可选字段方式）。

## 5. UI 设计（`ApkSignatureCard.vue`）

- **方案徽章**：`v1` `v2` `v3` `v3.1` 四枚；存在 = 实心（admin-accent 底、白字），缺失 = 灰描边；
  徽章下方一行摘要文字（如「v2 + v3」/「仅 v1：Android 11+ 部分场景可能装不上」）。
- **证书区**：每张证书一块 ——
  - 第一行 `subject`（`CN` 加粗）+ 「调试证书」橙色标签（`debugCert`）；
  - `issuer`（自签名时显示「自签名」标签）；
  - 有效期 `notBefore → notAfter`；
  - 指纹：compact 显示 `SHA-256` 前 8 组 + `…`；full 显示完整 `SHA-256` 与 `SHA-1`，各带复制按钮；
  - `指纹不可用` 时显示 `fingerprintError` 文案（不显示空行）。
- **固定脚注**（R5）：`仅检测签名方案与证书，未校验签名有效性`，样式为次要信息（`text-admin-muted`），
  full 视图给出更长的说明（不校验 digest / 证书链 / 信任锚）。
- **无签名**：显示「未检测到签名信息」+ 提示可能是自定义打包或异常包，**不**判定为错误。
- **降级**：`degraded` 非空时在卡片内以黄色提示条展示（与 core 的 `warnings` 区不重复渲染同一句）。
- 主题与语义色沿用 `design.md`（core）§5：Tailwind 原生调色板 + admin 令牌，不新增令牌。

## 6. 兼容性与降级矩阵

| 场景 | 行为 |
|------|------|
| 无 v2/v3 块（magic 不匹配） | 不是错误；`schemes.v2 = v3 = v3_1 = false` |
| 块声明长度越界 / >4MB | 降级：不解析该块，写 `degraded` |
| `DecompressionStream` 不可用 | v1 方案仍报告；证书标 `degraded` 原因 |
| `crypto.subtle` 不可用 | 结构信息照常，指纹标不可用 |
| DER 畸形（截断/超长） | 单证书解析失败不影响其它证书；全失败则 `degraded` |
| 多签名者 / 多证书 | 全部列出（AC4） |
| 无 `META-INF` 且无签名块 | `signature` 返回含空 `certs` 的摘要（UI 显示「未检测到签名信息」） |

## 7. 权衡

1. **自研 DER 而非引库**（如 `pkijs`/`asn1js`）：两个库体积大（数百 KB）且本任务只需
   读证书 + 取 Name/validity，自研约 300 行、零依赖、可控性更好；代价是需自测覆盖畸形输入。
2. **不校验签名有效性**：v2 digest 校验需按 `1MB 对齐` 重算分段 digest 并解析哈希算法，
   v1 需比对 `SF`/`MANIFEST` 摘要 —— 成本远超收益，且「查看详情」不承担验签职责（用词上也规避误导）。
3. **指纹用 WebCrypto 而非纯 JS**：性能好且是标准实现；不可用时降级而非自己实现 SHA。
4. **v3.1 只做存在性检测**：签名轮换的完整校验需要 rotation 逻辑，收益低。

## 8. 回滚

- 新增文件删除 + `ApkSignatureCard.vue` 还原为「未分析」占位 + `analyzeApk.ts` 移除 `signature` 阶段。
- `zipReader.ts` 的 `readEntryBytes` 是纯新增函数，保留亦不影响 core，但为干净回滚建议一并删除。
- 无数据/接口副作用。

## 9. 风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| v2/v3 signer 结构混用 | 证书提取失败或取到垃圾字节 | 按 block ID 分派结构（§4.3）；对两种样本各验证一次 |
| PKCS#7 结构变体（有的包无 certificates 字段） | v1 取不到证书 | 形状判定 + 兜底递归；取不到时降级而非报错 |
| 畸形 DER 导致死循环/越界 | 页面卡死 | 严格长度校验 + `maxDepth` + 指针必须前进（§4.1） |
| 指纹显示与 `apksigner` 不一致（分隔符/大小写） | AC2 判定困难 | 统一「冒号分隔大写」；验收时归一化后比对 |
| 大签名块（多签名者/轮换） | 读入内存过大 | 阈值 4MB + 降级 |
| `DecompressionStream` 浏览器差异 | v1 证书缺失 | 明确降级文案（AC5），不静默 |
