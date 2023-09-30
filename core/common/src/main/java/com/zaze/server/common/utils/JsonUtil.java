package com.zaze.server.common.utils;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Vector;

/**
 * Description :
 * date : 2015-11-27 - 17:11
 *
 * @author : zaze
 * @version : 1.0
 */
public class JsonUtil {

    private static GsonBuilder gsonBuilder;

    /**
     * json String 转换为bean
     *
     * @param json     json字符串
     * @param classOfT 转换bean
     * @param <T>      T
     * @return T
     */
    public static <T> T parseJson(String json, Class<T> classOfT) {
        if (json == null || classOfT == null) {
            return null;
        }
        try {
            return create().fromJson(json, classOfT);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * bean 转换为 jsonStr
     *
     * @param obj Object
     * @return jsonStr
     */
    public static String objToJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof JsonObject || obj instanceof JsonArray) {
            return obj.toString();
        } else {
            try {
                return create().toJson(obj);
            } catch (Throwable e) {
                e.printStackTrace();
                return null;
            }
        }
    }


    /**
     * 解析json列表字符串（去除了内部空对象）
     *
     * @param json json
     * @param type json : new TypeToken<List<T>>(){}.getType()
     * @param <T>  T
     * @return List
     */
    public static <T> List<T> parseJsonToList(String json, Type type) {
        List<T> list = parseJsonToListHasNull(json, type);
        if (list != null) {
            Collection<T> collection = new Vector<>();
            collection.add(null);
            list.removeAll(collection);
        }
        return list;
    }

    /**
     * 解析json列表字符串, List内部可以为空
     *
     * @param json json
     * @param type type
     * @param <T>  T
     * @return List
     */
    public static <T> List<T> parseJsonToListHasNull(String json, Type type) {
        if (json == null || type == null) {
            return null;
        }
        try {
            return create().fromJson(json, type);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }

    public static <T> List<T> parseJsonArrayToList(JsonArray jsonArray, Type type) {
        return parseJsonToList(jsonArray.toString(), type);
    }

    // --------------------------------------------------

    public static Gson create() {
        if (gsonBuilder == null) {
            gsonBuilder = new GsonBuilder();
        }
        return gsonBuilder.create();
    }
}
