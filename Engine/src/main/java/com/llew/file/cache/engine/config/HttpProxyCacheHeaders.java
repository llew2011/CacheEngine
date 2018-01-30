package com.llew.file.cache.engine.config;

import java.util.Map;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/31
 */

public interface HttpProxyCacheHeaders {

    /**
     * provider a set of headers that the download url need
     *
     * @param url the download url
     * @return a set of headers that the download url need
     */
    Map<String, String> appendHeaders(String url);
}
