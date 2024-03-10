package com.zaze.server.feature.showcase.repository;

import com.zaze.server.feature.showcase.TestApplication;
import com.zaze.server.feature.showcase.pojo.Showcase;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-27 11:48 下午
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestApplication.class)
@Slf4j
public class ShowcaseRepositoryTest {
    @Autowired
    private ShowcaseRepository repository;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void deleteById() {
//        repository.save(new Showcase(-1L, new Date(), new Date(), null, null, null, null, null));
//        Assert.assertTrue(repository.existsById(1L));
//        repository.deleteById(1L);
//        Assert.assertFalse(repository.existsById(1L));
    }

    @Test
    public void findByTagsLike() {
//        showcaseRepository.findByTagsLike("%New%");
    }
}