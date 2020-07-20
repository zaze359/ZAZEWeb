package com.zaze.server.web;

import java.util.List;

import javax.annotation.Resource;

import com.zaze.server.model.Showcase;
import com.zaze.server.service.ShowcaseService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/")
public class IndexControl {

    @Resource
    ShowcaseService showcaseService;

    @RequestMapping("/")
    public String index() {
        return "index";
    }

    @RequestMapping("/admin")
    public String admin(Model model) {
        List<Showcase> list = showcaseService.getShowcaseList();
        Showcase showcase = new Showcase(0L, "明日方舟", "https://ak.hypergryph.com/",
                "《明日方舟》（《明日方舟Arknights》）是一款策略手游。你将作为罗德岛的一员,与罗德岛公开领导人阿米娅一同,雇佣人员频繁进入天灾影响后的高危地区,救助受难人群,处理矿石争端,以及对抗未知阻碍—— “罗德岛”的战术头脑,需要您的对策,请指引我们的航向。",
                "Game,Wiki", "https://ak.hypergryph.com/user/public/images/logo.png", System.currentTimeMillis(),
                System.currentTimeMillis());
        list.add(showcase);
        model.addAttribute("showcases", list);
        return "admin/admin";
    }
}