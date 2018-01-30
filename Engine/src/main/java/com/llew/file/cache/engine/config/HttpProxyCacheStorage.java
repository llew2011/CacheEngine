package com.llew.file.cache.engine.config;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;
import com.llew.file.cache.engine.info.HttpProxyCacheSourceInfo;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public interface HttpProxyCacheStorage {

    /**
     * provide cached file info by url, if not cached in memory or database, the value will be null
     *
     * @param url the cache url
     * @return the value will be null if not cached in memory or database
     * @throws HttpProxyCacheException the error
     */
    HttpProxyCacheSourceInfo get(String url) throws HttpProxyCacheException;

    /**
     * cache file info by url, the info will be cached in memory and database
     *
     * @param url  the cache url
     * @param info the cache info
     * @throws HttpProxyCacheException the error
     */
    void put(String url, HttpProxyCacheSourceInfo info) throws HttpProxyCacheException;

    /**
     * release resources
     *
     * @throws HttpProxyCacheException the error
     */
    void release() throws HttpProxyCacheException;
}
