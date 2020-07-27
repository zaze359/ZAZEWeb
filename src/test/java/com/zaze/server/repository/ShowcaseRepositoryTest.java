package com.zaze.server.repository;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-27 11:48 下午
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class ShowcaseRepositoryTest {
    @Autowired
    private ShowcaseRepository showcaseRepository;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void deleteById() {
        showcaseRepository.deleteById(1L);
    }

    @Test
    public void deleteByIds() {
        List<Long> list = new ArrayList<>();
        list.add(0L);
        showcaseRepository.deleteByIds(list);
    }

    @Test
    public void findByTagsLike() {
//        showcaseRepository.findByTagsLike("%New%");
    }
}