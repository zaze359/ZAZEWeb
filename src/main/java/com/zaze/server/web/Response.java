package com.zaze.server.web;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-28 12:32 上午
 */
public class Response {
    private int code = 200;
    private String responseBody = "操作成功";

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public void setResponseBody(String responseBody) {
        this.responseBody = responseBody;
    }
}
