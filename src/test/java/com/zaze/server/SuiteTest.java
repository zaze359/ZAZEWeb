package com.zaze.server;

import com.zaze.server.controller.ShowcaseTest;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-28 12:06 上午
 */
@Suite.SuiteClasses({DemoApplicationTests.class, ShowcaseTest.class})
@RunWith(Suite.class)
public class SuiteTest {
}
