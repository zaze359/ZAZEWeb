package com.zaze.server.repository;

import java.util.List;

import com.zaze.server.model.Showcase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {
    //    @Query("select s from Showcase s")
//    Page<Showcase> findAll(Pageable pageable);
    @Query("delete from Showcase s where s.id in ?1")
    void deleteByIds(long[] ids);

    @Query("select s from Showcase s where s.tags like ?1")
    List<Showcase> findByTagsLike(Pageable pageable, String tags);
}