package com.zaze.server.web;

import javax.annotation.Resource;
import com.zaze.server.service.ShowcaseService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class IndexControl {

    @Resource
    ShowcaseService showcaseService;

    @RequestMapping("/")
    public String index(Model model) {
        model.addAttribute("showcases", showcaseService.getShowcaseList());
        return "index";
    }

    @RequestMapping("/center")
    public String center() {
        return "layout/center";
    }
}