// APK 解析内核对外聚合入口。
// 解析内核（utils/apk/*）是纯逻辑，无 Vue / DOM 依赖；
// 详情面板（components/apk/*）与查看器（views/ApkAnalyzerView.vue）只消费这里导出的类型与函数。

export { analyzeApk } from './analyzeApk'
export { analyzeSignature } from './signature'
export { loadManifestParser, parseManifest } from './manifestParser'
export { readCentralDirectory } from './zipReader'
export { summarizeFiles, emptyFiles } from './fileStats'
export { describePermission, shortPermissionName } from './permissions'
export { androidVersionName } from './androidVersions'
export { formatBytes, formatCount } from '../format'
export * from './types'
