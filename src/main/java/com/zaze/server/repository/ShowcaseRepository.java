package com.zaze.server.repository;

import java.util.List;
import com.zaze.server.model.Showcase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {
    // @Query("select s from Showcase s")
    // Page<Showcase> findALL(Pageable pageable);

    @Query("select s from Showcase s where tags like ?1")
    List<Showcase> findByTagsLike(String tags);
}