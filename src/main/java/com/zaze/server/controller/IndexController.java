package com.zaze.server.controller;


import com.zaze.server.common.aop.LoggerManage;
import com.zaze.server.common.log.ZLog;
import com.zaze.server.common.utils.FileUtil;
import com.zaze.server.feature.showcase.service.ShowcaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Controller
@RequestMapping("/")
public class IndexController {

    @Autowired
    private ShowcaseService showcaseService;

    @RequestMapping("/")
    @LoggerManage(description = "加载index")
    public String index(Model model) {
        model.addAttribute("showcases", showcaseService.getShowcaseList());
        return "index";
    }

    @RequestMapping("/admin")
    @LoggerManage(description = "加载admin")
    public String admin(Model model) {
        model.addAttribute("showcases", showcaseService.getShowcaseList());
        return "admin/showcase";
    }

    @RequestMapping("/center")
    @LoggerManage(description = "加载center")
    public String center() {
        return "layout/center";
    }


    //
//    @PostMapping("/upload")
//    public String upload(@RequestParam("files") MultipartFile[] files) {
//        for(MultipartFile file : files) {
//            FileU
//        }
//    }

}