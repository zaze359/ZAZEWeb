package com.zaze.server.feature.app.controller

import com.zaze.server.common.aop.LoggerManage
import com.zaze.server.common.controller.BaseController
import com.zaze.server.common.controller.Response
import com.zaze.server.feature.app.vo.AppVo
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("api/v1/app")
class AppController : BaseController() {
//    @Autowired
//    private lateinit var AppService: AppService

    @GetMapping("/all")
    @ResponseBody
    @LoggerManage(description = "获取所有应用信息")
    fun getAllApps(): Response<List<AppVo>> {
        val list = (1..10).map {
            AppVo(
                "app$it",
                "com.zaze.app.$it",
                11,
            )
        }
        return Response(list)
    }

    @PostMapping("/add")
    @LoggerManage(description = "添加应用信息")
    fun addAppInfo(@RequestBody appVo: AppVo): Response<AppVo> {
        return Response(appVo)
    }

}