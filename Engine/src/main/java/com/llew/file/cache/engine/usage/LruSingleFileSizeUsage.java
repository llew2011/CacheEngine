package com.llew.file.cache.engine.usage;

import com.llew.file.cache.engine.config.HttpProxyCacheConfig;

import java.io.File;

/**
 * <br/><br/>
 *
 * @author llew
 * @date 2017/12/29
 */

public class LruSingleFileSizeUsage extends BaseCommonDiskUsage {

    private long mMaxFileSize;

    public LruSingleFileSizeUsage(HttpProxyCacheConfig cacheConfig, long maxFileSize) {
        super(cacheConfig);
        this.mMaxFileSize = maxFileSize;
    }

    @Override
    public boolean accept(File file, long totalFileCount, long totalFileSize) {
        return null != file && file.length() < this.mMaxFileSize;
    }
}