package com.zaze.server.database.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * NoRepositoryBean表示不必生成 RepositoryBean
 */
@NoRepositoryBean
public interface BaseRepository<T, ID> extends JpaRepository<T, ID> {

    /**
     * 使用jpa的查询语法，定义通用函数
     * 查找前10，按照更新时间降序，id升序排列。
     */
    List<T> findTop10ByOrderByUpdateTimeDescIdAsc();
}