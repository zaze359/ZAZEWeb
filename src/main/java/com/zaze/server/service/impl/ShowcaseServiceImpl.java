package com.zaze.server.service.impl;

import java.util.List;

import com.zaze.server.model.Showcase;
import com.zaze.server.repository.ShowcaseRepository;
import com.zaze.server.service.ShowcaseService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ShowcaseServiceImpl implements ShowcaseService {
    @Autowired
    private ShowcaseRepository showcaseRepository;

    @Override
    public void saveShowcase(Showcase showcase) {
    }

    @Override
    public List<Showcase> findByTagsLike(String tags) {
        return showcaseRepository.findByTagsLike(tags);
    }

}