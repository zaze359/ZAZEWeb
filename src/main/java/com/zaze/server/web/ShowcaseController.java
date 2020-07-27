package com.zaze.server.web;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Resource;

import com.zaze.server.comm.aop.LoggerManage;
import com.zaze.server.model.Showcase;
import com.zaze.server.service.ShowcaseService;

import lombok.extern.java.Log;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@RestController
public class ShowcaseController extends BaseController {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Resource
    ShowcaseService showcaseService;

//    @RequestMapping(value = "/search", method = RequestMethod.GET)
//    List<Showcase> searchByTags(@RequestParam(value = "page", defaultValue = "0") Integer page,
//                                @RequestParam(value = "size", defaultValue = "10") Integer size,
//                                @RequestParam(value = "tags", defaultValue = "News") String tags) {
//        logger.info("RequestParam page:" + page);
//        logger.info("RequestParam size:" + size);
//        logger.info("RequestParam tags:" + tags);
//        Sort sort = Sort.by(Sort.Direction.DESC, "id");
//        Pageable pageable = PageRequest.of(page, size, sort);
//        return showcaseService.findByTagsLike(pageable, tags);
//    }

    @RequestMapping(value = "/showcase/search", method = RequestMethod.GET)
    @LoggerManage(description = "分页搜索 : showcase by tags ")
    PageResponse searchByTags(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                              @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                              @RequestParam(value = "tags", defaultValue = "News") String tags) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageRequest.of(offset / limit, limit, sort);
        return new PageResponse(32, 32, showcaseService.findByTagsLike(pageable, tags));
    }

    @RequestMapping(value = "/showcase/add", method = RequestMethod.POST)
    @LoggerManage(description = "添加showcase")
    void addShowcase(Showcase showcase) {
        showcaseService.saveShowcase(showcase);
    }

    @RequestMapping(value = "/showcase/delete", method = RequestMethod.DELETE)
    @LoggerManage(description = "批量删除showcase")
    Response deleteShowcases(@RequestParam(value = "ids") String idStr) {
        String[] idStrArray = idStr.split(",");
        List<Long> ids = new ArrayList<>();
        for (String str : idStrArray) {
            ids.add(Long.parseLong(str));
        }
        showcaseService.deleteShowcases(ids);
        return result();
    }
}