package com.zaze.server.web;

import java.util.List;

import javax.annotation.Resource;

import com.zaze.server.model.Showcase;
import com.zaze.server.service.ShowcaseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

@Controller
@RequestMapping("/")
public class ShowcaseController {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    ShowcaseService showcaseService;

    @RequestMapping("/search")
    List<Showcase> searchByTags(String tags) {
        return showcaseService.findByTagsLike("%" + tags + "%");
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    void addShowcase(Showcase showcase) {
        showcaseService.saveShowcase(showcase);
    }
}