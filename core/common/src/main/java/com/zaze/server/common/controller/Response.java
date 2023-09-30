package com.zaze.server.common.controller;

/**
 * Description :
 *
 * @author : ZAZE
 * @version : 2020-07-28 12:32 上午
 */
public class Response<T> {
    private int code = 200;
    private T data;
    private String msg = "请求成功";

    public Response() {
    }
    public Response(T data) {
        this.data = data;
    }

    public Response(int code, T data, String msg) {
        this.code = code;
        this.data = data;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    @Override
    public String toString() {
        return "Response{" +
                "code=" + code +
                ", data=" + data +
                ", msg='" + msg + '\'' +
                '}';
    }
}
