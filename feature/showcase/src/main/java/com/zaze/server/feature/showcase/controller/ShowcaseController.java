package com.zaze.server.feature.showcase.controller;

import com.zaze.server.common.aop.LoggerManage;
import com.zaze.server.common.controller.BaseController;
import com.zaze.server.common.controller.PageResponse;
import com.zaze.server.common.controller.Response;
import com.zaze.server.feature.showcase.pojo.Showcase;
import com.zaze.server.feature.showcase.service.ShowcaseService;
import com.zaze.server.feature.showcase.vo.ShowcaseVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/showcase")
public class ShowcaseController extends BaseController {
    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ShowcaseService showcaseService;

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

    @GetMapping(value = "/")
    @LoggerManage(description = "获取全部 showcase ")
    @ResponseBody
    public Response<List<ShowcaseVo>> getAll() {
        List<ShowcaseVo> showcases = showcaseService.getShowcaseList();
        return new Response<>(showcases);
    }

    @GetMapping(value = "/search")
    @LoggerManage(description = "分页搜索 : showcase by tags ")
    public PageResponse<ShowcaseVo> searchByTags(@RequestParam(value = "offset", defaultValue = "0") Integer offset,
                              @RequestParam(value = "limit", defaultValue = "10") Integer limit,
                              @RequestParam(value = "tags", defaultValue = "") String tags) {
        Sort sort = Sort.by(Sort.Direction.DESC, "id");
        Pageable pageable = PageRequest.of(offset / limit, limit, sort);
        List<ShowcaseVo> showcases;
        if (tags.isEmpty()) {
            showcases = showcaseService.getShowcaseList();
        } else {
            showcases = showcaseService.findByTagsLike(pageable, tags);
        }
        return new PageResponse<>(showcases.size(), showcases.size(), showcases);
    }


    @Deprecated
    @PostMapping(value = "/add")
    @LoggerManage(description = "添加showcase")
    public void addShowcase(ShowcaseVo showcase) {
        showcaseService.saveShowcase(showcase);
    }

    @PostMapping(value = "/create")
    @LoggerManage(description = "创建一个showcase")
    @ResponseStatus(HttpStatus.CREATED)
    public Response<ShowcaseVo> create(@RequestBody ShowcaseVo showcase) {
        return new Response<>(showcaseService.saveShowcase(showcase));
    }

    @RequestMapping(value = "/delete", method = RequestMethod.DELETE)
    @LoggerManage(description = "批量删除showcase")
    public Response<String> deleteShowcases(@RequestParam(value = "ids") String idStr) {
        String[] idStrArray = idStr.split(",");
        List<Long> ids = new ArrayList<>();
        for (String str : idStrArray) {
            ids.add(Long.parseLong(str));
        }
        showcaseService.deleteShowcases(ids);
        return result();
    }
}