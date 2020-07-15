package com.zaze.server.repository;

import com.zaze.server.model.Showcase;

import org.springframework.boot.autoconfigure.data.web.SpringDataWebProperties.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ShowcaseRepository extends JpaRepository<Showcase, Long> {

    @Query("select s from Showcase s")
    Page<Showcase> findALL(Pageable pageable);
}