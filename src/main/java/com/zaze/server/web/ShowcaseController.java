package com.zaze.server.web;

import java.util.List;

import javax.annotation.Resource;

import com.zaze.server.model.Showcase;
import com.zaze.server.service.ShowcaseService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShowcaseController {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    ShowcaseService showcaseService;

    @RequestMapping(value = "/search", method = RequestMethod.GET)
    List<Showcase> searchByTags(Model model, String tags) {
        logger.info("tags:" + tags);
        return showcaseService.findByTagsLike(tags);
    }

    @RequestMapping(value = "/add", method = RequestMethod.POST)
    void addShowcase(Showcase showcase) {
        showcaseService.saveShowcase(showcase);
    }
}