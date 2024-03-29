package com.zaze.server.common.ext

import com.zaze.server.common.utils.JsonUtil
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * 解析json列表字符串（去除了内部空对象）
 * @param json json
 * @param <T>  T
 * @return List
 **/
fun <T> String.jsonToList(clazz: Class<T>): List<T>? {
    return JsonUtil.parseJsonToList(this, object : ParameterizedType {
        override fun getActualTypeArguments(): Array<Type> {
            return arrayOf(clazz)
        }

        override fun getRawType(): Type {
            return List::class.java
        }

        override fun getOwnerType(): Type? {
            return null
        }
    })
}

fun Any?.toJsonString(): String? {
    return JsonUtil.objToJson(this)
}