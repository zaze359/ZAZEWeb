package com.zaze.server.repository;

import java.util.List;
import com.zaze.server.model.Showcase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {
    // @Query("select s from Showcase s")
    // Page<Showcase> findALL(Pageable pageable);
    List<Showcase> findByTagsLike(String tags);
}