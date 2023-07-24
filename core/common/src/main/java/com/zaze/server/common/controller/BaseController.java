package com.zaze.server.common.controller;

import org.jetbrains.annotations.NotNull;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-28 12:33 上午
 */
public class BaseController {

    public @NotNull Response<String> result() {
        return new Response<>("ok");
    }
}
