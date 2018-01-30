package com.llew.file.cache.engine.config;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/30
 */

public interface HttpProxyCacheCallback {

    /**
     * when fetching datum has error, this method will be called
     *
     * @param url the cache url
     * @param e   the error
     */
    void error(String url, HttpProxyCacheException e);

    /**
     * Fetching datum from network and Writing to the local disk.
     *
     * @param url      the cache url
     * @param percent  percent of bytes download so far. Scope:[0-100]
     * @param finished <code>true</code> means fetching datum complete, otherwise <code>false</code>
     */
    void progress(String url, long percent, boolean finished);
}