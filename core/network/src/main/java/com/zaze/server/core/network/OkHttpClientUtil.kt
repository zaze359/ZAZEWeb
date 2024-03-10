package com.zaze.server.core.network

import com.zaze.server.core.network.ext.buildGetRequest
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class OkHttpClientUtil(private val okhttpClient: OkHttpClient) {

    fun doGet(url: String, params: Map<String, String>? = null, headers: Map<String, String>? = null): Response {
        val builder = Request.Builder().url(url.buildGetRequest(params))
        headers?.forEach { (t, u) ->
            builder.addHeader(t, u)
        }
        return execute(builder.build())
    }

    fun execute(request: Request): Response {
        return okhttpClient.newCall(request).execute()
    }

}