package com.llew.file.cache.engine.config;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;

import java.io.Closeable;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public interface HttpProxyCacheSource extends Closeable {

    void init(HttpProxyCacheConfig config, String url) throws HttpProxyCacheException;

    void open() throws HttpProxyCacheException;

    void open(long offset) throws HttpProxyCacheException;

    long length() throws HttpProxyCacheException;

    String mime() throws HttpProxyCacheException;

    int read(byte[] buffer) throws HttpProxyCacheException;

    HttpProxyCacheSource clone(HttpProxyCacheConfig config) throws HttpProxyCacheException;

}