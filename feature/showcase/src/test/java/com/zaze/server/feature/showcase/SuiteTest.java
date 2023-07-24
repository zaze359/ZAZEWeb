package com.zaze.server.feature.showcase;

import com.zaze.server.feature.showcase.repository.RolesRepositoryTest;
import com.zaze.server.feature.showcase.repository.ShowcaseRepositoryTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Description : 套件
 *
 * @author : ZAZE
 * @version : 2020-07-28 12:06 上午
 */
@Suite.SuiteClasses({RolesRepositoryTest.class, ShowcaseRepositoryTest.class})
@RunWith(Suite.class)
public class SuiteTest {
}
