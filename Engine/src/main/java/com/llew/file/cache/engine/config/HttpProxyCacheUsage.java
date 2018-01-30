package com.llew.file.cache.engine.config;

import com.llew.file.cache.engine.exception.HttpProxyCacheException;

import java.io.File;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public interface HttpProxyCacheUsage {

    void touch(File file) throws HttpProxyCacheException;
}
