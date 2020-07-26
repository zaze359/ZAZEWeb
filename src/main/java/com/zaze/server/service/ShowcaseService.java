package com.zaze.server.service;

import java.util.List;

import com.zaze.server.model.Showcase;
import org.springframework.data.domain.Pageable;

public interface ShowcaseService {

    void saveShowcase(Showcase showcase);

    void deleteShowcases(long[] ids);

    List<Showcase> getShowcaseList();

    List<Showcase> findByTagsLike(Pageable pageable, String tags);
}