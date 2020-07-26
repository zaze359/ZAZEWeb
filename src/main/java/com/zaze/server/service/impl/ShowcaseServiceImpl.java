package com.zaze.server.service.impl;

import java.util.List;

import com.zaze.server.model.Showcase;
import com.zaze.server.repository.ShowcaseRepository;
import com.zaze.server.service.ShowcaseService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ShowcaseServiceImpl implements ShowcaseService {

    @Autowired
    private ShowcaseRepository showcaseRepository;

    @Override
    public void saveShowcase(Showcase showcase) {
        showcaseRepository.save(showcase);
    }

    @Override
    public void deleteShowcases(long[] ids) {
        showcaseRepository.deleteByIds(ids);
    }

    @Override
    public List<Showcase> getShowcaseList() {
        List<Showcase> list = showcaseRepository.findAll();
        Showcase showcase = new Showcase(0L, "明日方舟", "https://ak.hypergryph.com/",
                "《明日方舟》（《明日方舟Arknights》）是一款策略手游。你将作为罗德岛的一员,与罗德岛公开领导人阿米娅一同,雇佣人员频繁进入天灾影响后的高危地区,救助受难人群,处理矿石争端,以及对抗未知阻碍—— “罗德岛”的战术头脑,需要您的对策,请指引我们的航向。",
                "Game", "https://avatars1.githubusercontent.com/u/2918581?s=200&v=4", System.currentTimeMillis(),
                System.currentTimeMillis());
        list.add(showcase);
        return list;
    }


    @Override
    public List<Showcase> findByTagsLike(Pageable pageable, String tags) {
        List<Showcase> list = showcaseRepository.findByTagsLike(pageable, "%" + tags + "%");
        for (long i = 0; i < 100L; i++) {
            Showcase showcase = new Showcase(i, "明日方舟", "https://ak.hypergryph.com/",
                    "《明日方舟》（《明日方舟Arknights》）是一款策略手游。你将作为罗德岛的一员,与罗德岛公开领导人阿米娅一同,雇佣人员频繁进入天灾影响后的高危地区,救助受难人群,处理矿石争端,以及对抗未知阻碍—— “罗德岛”的战术头脑,需要您的对策,请指引我们的航向。",
                    tags, "https://avatars1.githubusercontent.com/u/2918581?s=200&v=4", System.currentTimeMillis(),
                    System.currentTimeMillis());
            list.add(showcase);
        }
        return list.subList((int) pageable.getOffset(), (int) Math.min(list.size(), pageable.getOffset() + pageable.getPageSize()));
    }
}