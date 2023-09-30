package com.zaze.server.vo

import com.google.gson.annotations.SerializedName

/**
 * Description :
 * @author : zaze
 * @version : 2023-09-08 22:04
 */
data class AdRules(
    /** 包名， null表示通用，所有应用都匹配*/
    @SerializedName("package_name")
    val packageName: String?,
    /** 规则 */
    @SerializedName("popup_rules")
    val popupRules: List<AdRule>?
)

data class AdRule(
    /** 是resId或者 文本，用于匹配是否是需要过滤的AD节点 */
    val id: String?,
    /** 是resId或者 文本，表示操作的节点，用于关闭AD */
    val action: String?,
)