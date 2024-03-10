package com.zaze.server.core.network

import okhttp3.OkHttpClient
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
@SpringBootTest
class OkHttpClientUtilTest {

    @Autowired
    private lateinit var okHttpClient: OkHttpClient

    private lateinit var okHttpClientUtil: OkHttpClientUtil

    @Before
    fun init() {
        okHttpClientUtil = OkHttpClientUtil(okHttpClient)
    }

    @Test
    fun testGet() {
        val response = okHttpClientUtil.doGet("https://www.baidu.com")
        println("response(${response.isSuccessful}): ${response.body?.string()}")
    }
}