package com.zaze.server.core.network

import com.zaze.server.core.network.ext.urlDecode


/**
 * Description :
 * @author : zaze
 * @version : 2022-12-10 22:12
 */
data class HttpUrl(
    /**
     * http or https
     */
    val scheme: String = "http",
    val host: String = "",
    val port: Int = -1,
) {
    var pathSegments: List<String>? = null
        private set

    var queryParams: List<Param>? = null
        private set

    fun setPaths(pathStr: String?): HttpUrl {
        this.pathSegments = pathStr?.split("/")?.filter {
            it.isNotEmpty()
        }
        return this
    }

    fun setParams(paramStr: String?): HttpUrl {
        queryParams = paramStr?.let {
            if (it.startsWith("#")) it.substring(1) else it
        }?.split("&")?.map(::parseParam)
        return this
    }

    private fun parseParam(paramStr: String): Param {
        return paramStr.split("=").takeIf {
            it.size == 2
        }?.let {
            Param(it[0], it[1].urlDecode())
        } ?: Param(paramStr, "")
    }

    override fun toString(): String {
        return "HttpUrl(scheme='$scheme', host='$host', port=$port, pathSegments=$pathSegments, queryParams=$queryParams)"
    }
}

data class Param(val key: String, val value: String, val encrypt: String? = null)