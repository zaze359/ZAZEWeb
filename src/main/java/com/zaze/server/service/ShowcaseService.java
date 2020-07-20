package com.zaze.server.service;

import java.util.List;

import com.zaze.server.model.Showcase;

public interface ShowcaseService {

    List<Showcase> getShowcaseList();

    void saveShowcase(Showcase showcase);

    List<Showcase> findByTagsLike(String tags);
}