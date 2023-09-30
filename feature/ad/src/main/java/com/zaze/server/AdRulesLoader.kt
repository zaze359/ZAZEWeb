package com.zaze.accessibility

import com.google.gson.JsonParser
import com.zaze.server.common.ext.jsonToList
import com.zaze.server.common.utils.FileUtil
import com.zaze.server.common.utils.JsonUtil
import com.zaze.server.vo.AdRule
import com.zaze.server.vo.AdRules
import java.io.InputStream

/**
 * Description :
 * @author : zaze
 * @version : 2023-09-08 01:29
 */
object AdRulesLoader {

    fun parseLTTRules(jsonInputStream: InputStream): AdRules {
        val jsonStr = FileUtil.readByBytes(jsonInputStream).toString()
        val parser = JsonParser.parseString(jsonStr)
        val jsonArray = parser.asJsonArray
        val adRulesList = ArrayList<AdRules>()
        for (element in jsonArray) {
            val jsonObj = element.asJsonObject
            jsonObj.keySet().forEach { key ->
                // key = "320552729"  随机数
                // value = "{\"popup_rules\":[{\"id\":\"立即更新\",\"action\":\"稍后更新\"}]}"
                val value = jsonObj.get(key).asString
                // 解析 popup_rules
                JsonUtil.parseJson(value, AdRules::class.java)?.let { rules ->
//                    ZLog.i(ZTag.TAG, "解析规则 success")
                    adRulesList.add(rules)
                }
            }
        }
        val rules = mutableListOf<AdRule?>()
        adRulesList.forEach {
            rules.addAll(it.popupRules ?: emptyList())
        }
//        ZLog.i(ZTag.TAG, "adRulesList size: ${adRulesList.size}")
        return AdRules(null, rules.filterNotNull())
    }

    fun parseRules(jsonInputStream: InputStream): List<AdRules> {
        val jsonStr = FileUtil.readByBytes(jsonInputStream).toString()
        return jsonStr.jsonToList(AdRules::class.java) ?: emptyList()
    }
}