package com.llew.file.cache.engine.usage;

import com.llew.file.cache.engine.config.HttpProxyCacheUsage;
import com.llew.file.cache.engine.exception.HttpProxyCacheException;

import java.io.File;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/28
 */

public final class LruUnLimitedDiskUsage implements HttpProxyCacheUsage {

    public LruUnLimitedDiskUsage() {
    }
    @Override
    public void touch(File file) throws HttpProxyCacheException {
        // do nothing
    }
}