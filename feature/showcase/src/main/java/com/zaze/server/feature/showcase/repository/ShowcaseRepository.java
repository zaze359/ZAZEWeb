package com.zaze.server.feature.showcase.repository;


import com.zaze.server.database.repository.BaseRepository;
import com.zaze.server.feature.showcase.pojo.Showcase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import javax.transaction.Transactional;
import java.util.List;

public interface ShowcaseRepository extends BaseRepository<Showcase, Long> {
    //    @Query("select s from Showcase s")
//    Page<Showcase> findAll(Pageable pageable);

    // nativeQuery = true 表示 from后面用 表明，否则用类名。
    @Transactional
    @Modifying
    @Query(value = "delete from showcase s where s.id in :ids", nativeQuery = true)
    void deleteByIds(@Param("ids") List<Long> ids);

    @Query(value = "select * from showcase s where s.tags like :tags", nativeQuery = true)
    List<Showcase> findByTagsLike(Pageable pageable, @Param("tags") String tags);
}