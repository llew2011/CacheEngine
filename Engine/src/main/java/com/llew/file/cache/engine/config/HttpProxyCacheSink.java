package com.llew.file.cache.engine.config;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;

import java.io.Closeable;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public interface HttpProxyCacheSink extends Closeable {

    /**
     * init
     *
     * @param config the global config
     * @param url    the cache url
     * @throws HttpProxyCacheException the error
     */
    void init(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException;

    /**
     * total number of byte has cached
     *
     * @return the total number of bytes has cached
     * @throws HttpProxyCacheException the error
     */
    long available() throws HttpProxyCacheException;

    /**
     * read data from cached file
     *
     * @param buffer the buffer
     * @param offset the offset
     * @param length the length
     * @return the length has read
     * @throws HttpProxyCacheException the error
     */
    int read(byte[] buffer, long offset, int length) throws HttpProxyCacheException;

    /**
     * append data to the end of cached file
     *
     * @param data   the new data
     * @param length the really length of data
     * @throws HttpProxyCacheException
     */
    void append(byte[] data, int length) throws HttpProxyCacheException;

    /**
     * after the cache has finished, this method should be called
     *
     * @throws HttpProxyCacheException
     */
    void complete() throws HttpProxyCacheException;

    /**
     * determine the cache has completed or not
     *
     * @return <code>true</code> means cache complete, otherwise <code>false</code>
     */
    boolean isCompleted();

    /**
     * create a new instance of HttpProxyCacheSink
     *
     * @param config the global config
     * @return
     * @throws HttpProxyCacheException
     */
    HttpProxyCacheSink clone(HttpProxyCacheConfig config) throws HttpProxyCacheException;
}