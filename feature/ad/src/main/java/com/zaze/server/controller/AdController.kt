package com.zaze.server.controller

import com.zaze.accessibility.AdRulesLoader
import com.zaze.server.vo.AdRules
import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController
import java.io.File
import java.io.FileInputStream

@RestController
@RequestMapping("/api/v1/ad")
class AdController : BaseController() {
    private var lttAdRules: AdRules? = null
    private var adRulesList: List<AdRules>? = null

    @GetMapping("/rules/all")
    @LoggerManage(description = "获取所有 Ad规则")
    @ResponseBody
    fun getAllAdRules(): Response<List<AdRules>> {
        val allRules = ArrayList<AdRules>()
        allRules.add(loadLttAdRules())
        allRules.addAll(loadAdRules())
        return Response(allRules)
    }


    @GetMapping("/rules/ltt")
    @ResponseBody
    fun getLttAdRules(): Response<AdRules> {
        return Response(loadLttAdRules())
    }

    private fun loadLttAdRules(): AdRules {
        return lttAdRules ?: AdRulesLoader.parseLTTRules(FileInputStream(File("data/ltt_rules.json"))).apply {
            lttAdRules = this
        }
    }


    @GetMapping("/rules/zaze")
    @ResponseBody
    fun getAdRules(): Response<List<AdRules>> {
        return Response(loadAdRules())
    }

    private fun loadAdRules(): List<AdRules> {
        return adRulesList ?: AdRulesLoader.parseRules(FileInputStream(File("data/zaze_rules.json"))).apply {
            adRulesList = this
        }
    }
}