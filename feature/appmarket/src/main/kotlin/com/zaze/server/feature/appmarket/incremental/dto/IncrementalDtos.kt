package com.zaze.server.feature.appmarket.incremental.dto

/**
 * 差分包元数据（管理端 / 下发接口共用）。
 * `ratio = patchSize / newSize`，用于直观展示增量比（< 1 即比整包小）。
 */
data class PatchMetaVo(
    val key: String,
    val oldVersionId: Long,
    val newVersionId: Long,
    val oldSha256: String,
    val newSha256: String,
    val oldSize: Long,
    val newSize: Long,
    val patchSize: Long,
    val ratio: Double,
    val cached: Boolean
)

/**
 * 差分包 sidecar（落盘为 `<key>.json`，与 `<key>.patch` 并列）。
 * 同时作为 patch 缓存的元数据来源，并在命中缓存时直接转成 [PatchMetaVo]。
 */
data class PatchSidecar(
    val oldVersionId: Long,
    val newVersionId: Long,
    val oldSha256: String,
    val newSha256: String,
    val oldSize: Long,
    val newSize: Long,
    val patchSize: Long,
    val ratio: Double,
    val createdAt: Long
) {
    fun toMeta(key: String, cached: Boolean): PatchMetaVo = PatchMetaVo(
        key = key,
        oldVersionId = oldVersionId,
        newVersionId = newVersionId,
        oldSha256 = oldSha256,
        newSha256 = newSha256,
        oldSize = oldSize,
        newSize = newSize,
        patchSize = patchSize,
        ratio = ratio,
        cached = cached
    )
}

/** 已缓存差分包列表项（管理端 `GET /patches`）。 */
data class PatchListVo(
    val key: String,
    val ratio: Double,
    val patchSize: Long,
    val newSize: Long,
    val createdAt: Long
)

/** 自校验结果（管理端 `POST /verify`）。 */
data class VerifyVo(
    val pass: Boolean,
    val oldSha256: String,
    val newSha256: String,
    val reconSha256: String,
    val patchSize: Long
)

/** 差分包不可用时的降级载荷（下发接口返回）。 */
data class PatchUnavailableVo(val reason: String)
